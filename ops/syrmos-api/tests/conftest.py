"""Test-wide safety net: no test may make a real translation request.

`translate_from_greek` now prefers the Ariadne provider chain, which is built
from environment variables. On a developer machine that happens to export
GROQ_API_KEY or CLOUDFLARE_AI_TOKEN, a test touching translation would quietly
hit a live endpoint. Disable the backend by default; the tests that exercise it
turn it back on and supply their own fake providers.
"""

import pytest

from syrmos_admin import translation


@pytest.fixture(autouse=True)
def _offline_translation():
    translation.USE_ARIADNE = False
    translation._chain_cache = []
    # Give-up state is process-lifetime by design, so it has to be cleared
    # between tests or one test's dead provider silently skips another's.
    translation.reset_run_state()
    yield
    translation.reset_run_state()


# --- 3.0.2 fixtures: temp database, Cloudflare Access, admin client ----------

import time as _time

_SECRET_ENV = (
    "CF_ACCESS_TEAM", "CF_ACCESS_AUD", "ADMIN_EMAILS", "HTTP_ADMIN_TOKEN",
    "SYRMOS_ADMIN_TOKEN", "SYRMOS_CSRF_SECRET", "APNS_KEY_PATH", "APNS_KEY_ID",
    "APNS_TEAM_ID", "APNS_TOPIC", "FCM_SERVICE_ACCOUNT_PATH", "FCM_PROJECT_ID",
)


@pytest.fixture(autouse=True)
def _no_admin_secrets(monkeypatch):
    """Admin and push configuration from the developer's shell must never
    leak into a test: every test starts with admin closed and no push keys."""
    for name in _SECRET_ENV:
        monkeypatch.delenv(name, raising=False)
    from syrmos_admin import security

    security.reset_certs_cache()

    def _no_network(url):  # any test needing certs installs its own fake
        raise AssertionError(f"unexpected certs fetch: {url}")

    monkeypatch.setattr(security, "fetch_certs", _no_network)
    yield


@pytest.fixture
def temp_db(tmp_path, monkeypatch):
    """A migrated, empty SQLite DB that every dbmod.connect() call uses."""
    from syrmos_admin import db as dbmod

    path = str(tmp_path / "syrmos.db")
    real_connect = dbmod.connect
    monkeypatch.setattr(dbmod, "connect", lambda db_path=path: real_connect(db_path))
    conn = real_connect(path)
    dbmod.migrate(conn)
    conn.close()
    return path


@pytest.fixture
def seeded_db(temp_db):
    """temp_db plus a tiny network: M2 and M3 through Syntagma, plus T6."""
    from syrmos_admin import db as dbmod

    conn = dbmod.connect()
    conn.execute("PRAGMA foreign_keys = OFF")
    lines = [("M2", "metro", 1), ("M3", "metro", 2), ("T6", "tram", 3)]
    for lid, mode, order in lines:
        conn.execute(
            "INSERT INTO lines(id, mode, name_en, name_el, color, terminal_a, terminal_b, sort_order)"
            " VALUES(?,?,?,?,?,?,?,?)",
            (lid, mode, lid, lid, "#000000", "A", "B", order),
        )
    stations = {
        "M2": ["M2_PAN", "M2_SYN", "M2_AKR"],
        "M3": ["M3_MON", "M3_SYN", "M3_EVA", "M3_NIK"],
        "T6": ["T6_SYN", "T6_FIX"],
    }
    for lid, ids in stations.items():
        for seq, sid in enumerate(ids):
            conn.execute(
                "INSERT OR IGNORE INTO stations(id, name_en, name_el, lat, lng) VALUES(?,?,?,?,?)",
                (sid, sid, "Σύνταγμα" if sid.endswith("SYN") else sid, 37.97, 23.73),
            )
            conn.execute(
                "INSERT INTO line_stations(line_id, station_id, seq) VALUES(?,?,?)",
                (lid, sid, seq),
            )
    conn.close()
    return temp_db


class AccessKeys:
    """An RSA key pair standing in for Cloudflare Access, with a JWT minter."""

    team = "syrmos-test"
    audience = "aud-tag-123"
    email = "owner@example.com"

    def __init__(self):
        from cryptography.hazmat.primitives.asymmetric import rsa

        self.private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        self.kid = "kid-1"
        self.fetches = 0

    def jwks(self) -> dict:
        import jwt

        jwk = jwt.algorithms.RSAAlgorithm.to_jwk(self.private_key.public_key(), as_dict=True)
        jwk.update({"kid": self.kid, "alg": "RS256", "use": "sig"})
        return {"keys": [jwk]}

    def token(self, *, email=None, aud=None, iss=None, exp_in=600, kid=None, key=None, **extra) -> str:
        import jwt

        now = int(_time.time())
        claims = {
            "email": email or self.email,
            "aud": [aud or self.audience],
            "iss": iss or f"https://{self.team}.cloudflareaccess.com",
            "iat": now,
            "exp": now + exp_in,
            "sub": "user-1",
            **extra,
        }
        return jwt.encode(claims, key or self.private_key, algorithm="RS256", headers={"kid": kid or self.kid})


@pytest.fixture
def access(monkeypatch):
    from syrmos_admin import security

    keys = AccessKeys()
    monkeypatch.setenv("CF_ACCESS_TEAM", keys.team)
    monkeypatch.setenv("CF_ACCESS_AUD", keys.audience)
    monkeypatch.setenv("ADMIN_EMAILS", f"{keys.email}, second@example.com")

    def fake_fetch(url):
        assert url == f"https://{keys.team}.cloudflareaccess.com/cdn-cgi/access/certs"
        keys.fetches += 1
        return keys.jwks()

    monkeypatch.setattr(security, "fetch_certs", fake_fetch)
    return keys


@pytest.fixture
def admin_client(seeded_db, access):
    """(TestClient, headers, csrf_token) for a verified Access browser session."""
    from fastapi.testclient import TestClient

    from syrmos_admin import app as appmod
    from syrmos_admin import security

    client = TestClient(appmod.app)
    headers = {"Cf-Access-Jwt-Assertion": access.token()}
    return client, headers, security.make_csrf_token(access.email)
