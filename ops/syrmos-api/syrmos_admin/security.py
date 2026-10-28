"""Admin authentication, CSRF and audit for the private console.

The repository is public, so admin security comes from secrets and the
network, never from obscurity:

* Cloudflare Access sits in front of /admin/ and forwards a signed JWT in the
  Cf-Access-Jwt-Assertion header. verify_cf_access_jwt() checks it with RS256
  against the team's published certs (cached for an hour), and requires the
  application audience tag (CF_ACCESS_AUD), the team issuer and a valid expiry.
  The verified email must then be in the ADMIN_EMAILS allowlist. The plain
  Cf-Access-Authenticated-User-Email header is never trusted on its own: it can
  be forged by anyone who reaches the origin without going through Access.
* HTTP_ADMIN_TOKEN (legacy name SYRMOS_ADMIN_TOKEN) is a fallback for scripts,
  sent as X-Admin-Token and compared in constant time. It only exists when the
  variable is set.
* With neither configured, every admin request is denied.

Browser sessions are protected against cross-site request forgery with a
signed token (HMAC over the identity and an issue time) that page() embeds in
every POST form. Header-token requests skip CSRF: a browser never attaches a
custom header cross-site. Every accepted mutating request writes one
admin_audit row.

All configuration is read from the environment at call time, so tests can
monkeypatch it, and the certs fetcher is a module-level function they can
replace, so no test reaches the network.
"""
from __future__ import annotations

import hashlib
import hmac
import json
import logging
import os
import secrets
import threading
import time
from typing import Any, Callable

import httpx

logger = logging.getLogger(__name__)

JWT_HEADER = "cf-access-jwt-assertion"
TOKEN_HEADER = "x-admin-token"
CSRF_FIELD = "csrf_token"
CERTS_TTL_SECONDS = 3600
# A token signed by a key we have not seen yet triggers one refetch (Cloudflare
# rotates keys), but never more often than this, so junk kids cannot hammer
# the certs endpoint.
CERTS_MIN_REFETCH_SECONDS = 60
CSRF_TTL_SECONDS = 12 * 3600
TOKEN_ADMIN = "token-admin"


class AuthError(Exception):
    pass


# --- configuration ---------------------------------------------------------

def team_domain() -> str:
    """CF_ACCESS_TEAM as a bare host: accepts 'myteam', 'myteam.cloudflareaccess.com'
    or the full https URL."""
    raw = (os.environ.get("CF_ACCESS_TEAM") or "").strip().lower()
    raw = raw.removeprefix("https://").removeprefix("http://").strip("/")
    if not raw:
        return ""
    if not raw.endswith(".cloudflareaccess.com"):
        raw = f"{raw}.cloudflareaccess.com"
    return raw


def access_audience() -> str:
    return (os.environ.get("CF_ACCESS_AUD") or "").strip()


def admin_emails() -> set[str]:
    raw = os.environ.get("ADMIN_EMAILS") or ""
    return {e.strip().lower() for e in raw.split(",") if e.strip()}


def admin_token() -> str:
    return (os.environ.get("HTTP_ADMIN_TOKEN") or os.environ.get("SYRMOS_ADMIN_TOKEN") or "").strip()


def access_configured() -> bool:
    return bool(team_domain() and access_audience() and admin_emails())


# --- Cloudflare Access JWT -------------------------------------------------

def fetch_certs(url: str) -> dict:
    """GET the Access certs document. Replaced in tests."""
    resp = httpx.get(url, timeout=5.0)
    resp.raise_for_status()
    return resp.json()


_certs_lock = threading.Lock()
_certs_cache: dict[str, Any] = {"url": "", "keys": {}, "fetched_at": 0.0}


def reset_certs_cache() -> None:
    with _certs_lock:
        _certs_cache.update({"url": "", "keys": {}, "fetched_at": 0.0})


def _load_keys(url: str, *, force: bool, now: float) -> dict:
    import jwt  # PyJWT[crypto]

    with _certs_lock:
        fresh = (
            _certs_cache["url"] == url
            and now - _certs_cache["fetched_at"] < CERTS_TTL_SECONDS
        )
        recently = now - _certs_cache["fetched_at"] < CERTS_MIN_REFETCH_SECONDS
        if fresh and not (force and not recently):
            return _certs_cache["keys"]
        doc = fetch_certs(url)
        keys: dict[str, Any] = {}
        for jwk in doc.get("keys", []):
            kid = jwk.get("kid")
            if not kid:
                continue
            try:
                keys[kid] = jwt.PyJWK(jwk, algorithm="RS256").key
            except Exception as exc:  # noqa: BLE001 - skip a malformed key, keep the rest
                logger.warning("skipping Access cert %s: %r", kid, exc)
        _certs_cache.update({"url": url, "keys": keys, "fetched_at": now})
        return keys


def verify_cf_access_jwt(token: str, *, now: float | None = None) -> str:
    """Return the verified, allowlisted email for an Access JWT or raise AuthError."""
    import jwt  # PyJWT[crypto]

    team = team_domain()
    audience = access_audience()
    allowed = admin_emails()
    if not (team and audience and allowed):
        raise AuthError("Cloudflare Access is not configured")
    if now is None:
        now = time.time()
    try:
        header = jwt.get_unverified_header(token)
    except jwt.PyJWTError as exc:
        raise AuthError(f"malformed token: {exc}") from None
    if header.get("alg") != "RS256":
        raise AuthError("unexpected token algorithm")
    kid = header.get("kid")
    url = f"https://{team}/cdn-cgi/access/certs"
    try:
        keys = _load_keys(url, force=False, now=now)
        if kid not in keys:
            keys = _load_keys(url, force=True, now=now)
    except (httpx.HTTPError, ValueError) as exc:
        raise AuthError(f"cannot load Access certs: {exc}") from None
    key = keys.get(kid)
    if key is None:
        raise AuthError("unknown signing key")
    try:
        claims = jwt.decode(
            token,
            key,
            algorithms=["RS256"],
            audience=audience,
            issuer=f"https://{team}",
            options={"require": ["exp", "aud", "iss"]},
            leeway=30,
        )
    except jwt.PyJWTError as exc:
        raise AuthError(f"invalid token: {exc}") from None
    email = str(claims.get("email") or "").strip().lower()
    if not email or email not in allowed:
        raise AuthError("email not allowed")
    return email


# --- identity --------------------------------------------------------------

def identify(headers: Any) -> tuple[str, bool]:
    """(identity, needs_csrf) for a request's headers, or raise AuthError.

    needs_csrf is True for browser sessions (Access JWT), False for the header
    token, which a cross-site page cannot send.
    """
    assertion = headers.get(JWT_HEADER)
    if assertion:
        return verify_cf_access_jwt(assertion), True
    expected = admin_token()
    supplied = headers.get(TOKEN_HEADER) or ""
    if expected and supplied and hmac.compare_digest(supplied.encode(), expected.encode()):
        return TOKEN_ADMIN, False
    raise AuthError("no valid credentials")


# --- CSRF ------------------------------------------------------------------

_csrf_secret_cache: list[bytes] = []


def _csrf_secret() -> bytes:
    configured = os.environ.get("SYRMOS_CSRF_SECRET")
    if configured:
        return configured.encode()
    # A per-process secret is fine: a restart only invalidates open forms.
    if not _csrf_secret_cache:
        _csrf_secret_cache.append(secrets.token_bytes(32))
    return _csrf_secret_cache[0]


def _csrf_sig(identity: str, issued: int) -> str:
    msg = f"{identity}|{issued}".encode()
    return hmac.new(_csrf_secret(), msg, hashlib.sha256).hexdigest()


def make_csrf_token(identity: str, *, now: float | None = None) -> str:
    issued = int(now if now is not None else time.time())
    return f"{issued}.{_csrf_sig(identity, issued)}"


def check_csrf_token(identity: str, token: str | None, *, now: float | None = None) -> bool:
    if not token or "." not in token:
        return False
    issued_raw, sig = token.split(".", 1)
    try:
        issued = int(issued_raw)
    except ValueError:
        return False
    current = now if now is not None else time.time()
    if not (current - CSRF_TTL_SECONDS <= issued <= current + 60):
        return False
    return hmac.compare_digest(sig, _csrf_sig(identity, issued))


# --- audit -----------------------------------------------------------------

def payload_hash(items: list[tuple[str, Any]]) -> str:
    """sha256 over the submitted fields (sorted, CSRF token excluded). Uploaded
    files contribute their filename only."""
    norm = []
    for key, value in items:
        if key == CSRF_FIELD:
            continue
        if hasattr(value, "filename"):
            value = f"<file:{getattr(value, 'filename', '')}>"
        norm.append([str(key), str(value)])
    norm.sort()
    return hashlib.sha256(json.dumps(norm, ensure_ascii=False).encode("utf-8")).hexdigest()


def write_audit(
    connect: Callable[[], Any],
    *,
    actor: str,
    method: str,
    path: str,
    action: str | None,
    digest: str,
) -> None:
    conn = connect()
    try:
        conn.execute(
            "INSERT INTO admin_audit(actor, method, path, action, payload_sha256)"
            " VALUES(?,?,?,?,?)",
            (actor, method, path, action, digest),
        )
    finally:
        conn.close()
