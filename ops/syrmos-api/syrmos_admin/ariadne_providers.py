"""OpenAI-compatible provider abstraction for the Ariadne assistant.

Every hosted or local backend Ariadne uses (Groq, Cloudflare Workers AI, a
local llama.cpp server, and optionally a paid OpenAI-compatible endpoint)
speaks the same ``/v1/chat/completions`` contract, so a single provider type
covers them all. Adding a provider means supplying a base URL, an API key
env var, and a model name, nothing more.

All calls are async (httpx.AsyncClient) so a slow or hung provider never
blocks the FastAPI event loop, and each provider has strict connect/read
timeouts so the chain fails over quickly. Failures are classified (config
vs rate limit vs server vs timeout vs network vs protocol vs empty) so a
misconfiguration reads differently from a transient outage in the logs.
Provider error bodies are redacted before logging so an echoed credential
or prompt cannot leak into journald.
"""
from __future__ import annotations

import logging
import os
import re
import time
from dataclasses import dataclass
from urllib.parse import urlparse

import httpx

logger = logging.getLogger("syrmos.ariadne")


def _f(name: str, default: float) -> float:
    try:
        return float(os.environ.get(name, "") or default)
    except ValueError:
        return default


def _i(name: str, default: int) -> int:
    try:
        return int(os.environ.get(name, "") or default)
    except ValueError:
        return default


# Strict, quick timeouts for hosted providers so the chain fails over fast.
# The local llama.cpp brain runs at a few tokens/sec, so it gets a longer
# read budget. CHAIN_DEADLINE bounds the whole chain to comfortably under
# the 30s nginx proxy_read_timeout on /api/ariadne/chat, so the offline
# reply always reaches the caller instead of a 504. All are overridable
# from the environment after measuring real latency from the Pi in Athens.
CONNECT_TIMEOUT = _f("ARIADNE_CONNECT_TIMEOUT", 2.0)
READ_TIMEOUT = _f("ARIADNE_READ_TIMEOUT", 4.0)
LOCAL_READ_TIMEOUT = _f("ARIADNE_LOCAL_READ_TIMEOUT", 25.0)
CHAIN_DEADLINE = _f("ARIADNE_CHAIN_DEADLINE", 27.0)
MIN_ATTEMPT_BUDGET = _f("ARIADNE_MIN_ATTEMPT_BUDGET", 1.0)
TEMPERATURE = _f("ARIADNE_TEMPERATURE", 0.2)
MAX_TOKENS = _i("ARIADNE_MAX_TOKENS", 400)
LOCAL_MAX_TOKENS = _i("ARIADNE_LOCAL_MAX_TOKENS", 160)
# When off (default), only the stable structured error fields are logged, not
# the raw provider body, so an echoed prompt/PII cannot be persisted. Set to
# "1" in a non-production environment to capture full bodies for debugging.
LOG_RAW_BODIES = os.environ.get("ARIADNE_LOG_RAW_BODIES", "") == "1"

_client: httpx.AsyncClient | None = None


def get_client() -> httpx.AsyncClient:
    """Lazily create a shared AsyncClient so TCP/TLS connections are reused."""
    global _client
    if _client is None or _client.is_closed:
        _client = httpx.AsyncClient(
            limits=httpx.Limits(max_keepalive_connections=8, max_connections=16),
            headers={"User-Agent": "Syrmos-Ariadne/2.0"},
            trust_env=False,
        )
    return _client


async def aclose_client() -> None:
    """Close the shared client (called on FastAPI shutdown)."""
    global _client
    if _client is not None and not _client.is_closed:
        await _client.aclose()
    _client = None


# Credential shapes that must never reach the logs, even if a provider echoes
# them back in an error body. Kept broad on purpose: the goal is redaction,
# not perfect parsing.
_SECRET_PATTERNS = [
    re.compile(r"(?i)bearer\s+[A-Za-z0-9._\-]+"),
    re.compile(r"\bgsk_[A-Za-z0-9]+"),
    re.compile(r"\bsk-[A-Za-z0-9\-]+"),
    re.compile(r"\bAIza[0-9A-Za-z._\-]+"),
    re.compile(r"\bAQ\.[A-Za-z0-9._\-]+"),
    re.compile(r"\b[A-Za-z0-9._\-]{40,}\b"),
]


def _redact(text: str) -> str:
    """Strip credential-like substrings from provider error text before it is
    logged. The stable, human-readable part of the error (status meaning,
    "model blocked", "payment required") survives, which is what makes the
    logs diagnostic; only opaque token-shaped runs are masked."""
    if not text:
        return text
    out = text
    for pat in _SECRET_PATTERNS:
        out = pat.sub("[REDACTED]", out)
    return out


_SAFE_CODE = re.compile(r"^[A-Za-z0-9_.\-]{1,64}$")


def _extract_error_code(resp: httpx.Response) -> str | None:
    """The provider's stable error code, if present and shaped like a safe
    enum token. Codes are provider-defined identifiers (``model_not_found``,
    ``PAYMENT_METHOD_REQUIRED``, ``7000``), not free-form text, so they are
    safe to log; anything containing spaces, ``@``, or other free-form
    content is rejected."""
    try:
        data = resp.json()
    except ValueError:
        return None
    if not isinstance(data, dict):
        return None
    containers = [data.get("error")]
    errs = data.get("errors")
    if isinstance(errs, list) and errs:
        containers.append(errs[0])
    for container in containers:
        if isinstance(container, dict):
            code = container.get("code")
            if code is not None and _SAFE_CODE.match(str(code)):
                return str(code)
    return None


def _error_summary(resp: httpx.Response) -> str:
    """Non-free-form diagnostic for a non-200.

    Logs only the provider's stable error code (a safe enum token). The
    provider-supplied free-form message and the raw body are never logged,
    since they are provider-controlled and can echo prompt text or PII,
    unless ARIADNE_LOG_RAW_BODIES is explicitly set for debugging.
    """
    code = _extract_error_code(resp)
    if code:
        return f"code={code}"
    if LOG_RAW_BODIES:
        return _redact(resp.text[:300])
    return "error body suppressed"


@dataclass
class ProviderResult:
    """Outcome of one provider attempt, carrying observability metadata."""

    provider: str
    model: str
    ok: bool
    text: str = ""
    status: int | None = None
    latency_ms: int | None = None
    prompt_tokens: int | None = None
    completion_tokens: int | None = None
    # config | rate_limit | server | client | timeout | network | protocol | empty
    error_kind: str | None = None
    error_detail: str | None = None


def _classify(status: int) -> str:
    if status in (401, 403):
        return "config"
    if status == 429:
        return "rate_limit"
    if status >= 500:
        return "server"
    return "client"


def _ms(started: float) -> int:
    return int((time.perf_counter() - started) * 1000)


@dataclass
class OpenAICompatibleProvider:
    """A single ``/v1/chat/completions`` backend behind a uniform interface."""

    name: str
    base_url: str
    api_key: str
    model: str
    read_timeout: float = READ_TIMEOUT
    connect_timeout: float = CONNECT_TIMEOUT
    max_tokens: int = MAX_TOKENS

    @property
    def endpoint(self) -> str:
        return self.base_url.rstrip("/") + "/chat/completions"

    async def complete(
        self,
        client: httpx.AsyncClient,
        system_text: str,
        messages: list[dict[str, str]],
        timeout_override: float | None = None,
    ) -> ProviderResult:
        # Squeeze this attempt into the remaining chain budget when close to
        # the deadline; otherwise use the provider's own read timeout.
        eff_read = self.read_timeout
        if timeout_override is not None:
            eff_read = max(0.1, min(self.read_timeout, timeout_override))
        payload = {
            "model": self.model,
            "messages": [{"role": "system", "content": system_text}, *messages],
            "temperature": TEMPERATURE,
            "max_tokens": self.max_tokens,
            "stream": False,
        }
        timeout = httpx.Timeout(
            eff_read,
            connect=min(self.connect_timeout, eff_read),
            read=eff_read,
            write=eff_read,
            pool=self.connect_timeout,
        )
        started = time.perf_counter()
        try:
            resp = await client.post(
                self.endpoint,
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
                json=payload,
                timeout=timeout,
            )
        except httpx.TimeoutException as exc:
            return ProviderResult(
                self.name, self.model, False, latency_ms=_ms(started),
                error_kind="timeout", error_detail=_redact(repr(exc)),
            )
        except httpx.HTTPError as exc:
            return ProviderResult(
                self.name, self.model, False, latency_ms=_ms(started),
                error_kind="network", error_detail=_redact(repr(exc)),
            )

        latency = _ms(started)
        if resp.status_code != 200:
            return ProviderResult(
                self.name, self.model, False, status=resp.status_code,
                latency_ms=latency, error_kind=_classify(resp.status_code),
                error_detail=_error_summary(resp),
            )
        try:
            data = resp.json()
        except ValueError as exc:
            return ProviderResult(
                self.name, self.model, False, status=200, latency_ms=latency,
                error_kind="protocol", error_detail=_redact(repr(exc)),
            )

        # Defensive parsing: a 200 with a non-object root, malformed choices,
        # a non-string content, or a non-object usage must degrade to a failed
        # attempt, never an uncaught AttributeError/TypeError that would abort
        # the whole chain and 500 the request.
        text = ""
        prompt_tokens: int | None = None
        completion_tokens: int | None = None
        try:
            if isinstance(data, dict):
                choices = data.get("choices")
                if isinstance(choices, list) and choices and isinstance(choices[0], dict):
                    message = choices[0].get("message")
                    if isinstance(message, dict):
                        content = message.get("content")
                        if isinstance(content, str):
                            text = content.strip()
                usage = data.get("usage")
                if isinstance(usage, dict):
                    pt = usage.get("prompt_tokens")
                    ct = usage.get("completion_tokens")
                    prompt_tokens = pt if isinstance(pt, int) else None
                    completion_tokens = ct if isinstance(ct, int) else None
        except Exception as exc:  # noqa: BLE001 - never let parsing crash the chain
            return ProviderResult(
                self.name, self.model, False, status=200, latency_ms=latency,
                error_kind="protocol", error_detail=_redact(repr(exc)),
            )

        if not text:
            return ProviderResult(
                self.name, self.model, False, status=200, latency_ms=latency,
                error_kind="empty", error_detail="no usable content in response",
            )
        return ProviderResult(
            self.name, self.model, True, text=text, status=200, latency_ms=latency,
            prompt_tokens=prompt_tokens, completion_tokens=completion_tokens,
        )


def _env(*names: str) -> str:
    for name in names:
        value = os.environ.get(name, "").strip()
        if value:
            return value
    return ""


def _is_https(url: str) -> bool:
    try:
        return urlparse(url).scheme == "https"
    except ValueError:
        return False


def _is_loopback(url: str) -> bool:
    try:
        host = (urlparse(url).hostname or "").lower()
    except ValueError:
        return False
    return host in ("127.0.0.1", "localhost", "::1")


def build_chain() -> list[OpenAICompatibleProvider]:
    """Assemble the provider chain from whatever is configured, in order.

    Groq (fast hosted free tier) first, then Cloudflare Workers AI, then a
    local llama.cpp server as a degraded offline brain, then an optional
    extra OpenAI-compatible provider (e.g. a paid endpoint). A provider is
    only included when its required credentials/URL are present, so the chain
    lights up provider by provider as configuration is added and never wastes
    a round trip on an unconfigured backend.

    Hosted providers must use HTTPS (their credential is sent as a Bearer
    token); a plaintext override is refused so a key cannot leak in transit.
    The local provider is allowed plaintext only to a loopback host.
    """
    chain: list[OpenAICompatibleProvider] = []

    def add_hosted(name: str, base_url: str, api_key: str, model: str) -> None:
        if not _is_https(base_url):
            logger.warning(
                "ariadne provider=%s skipped: base_url must be https (got %r)",
                name, urlparse(base_url).scheme or "none",
            )
            return
        chain.append(OpenAICompatibleProvider(
            name=name, base_url=base_url, api_key=api_key, model=model,
        ))

    groq_key = _env("ARIADNE_GROQ_API_KEY", "GROQ_API_KEY")
    if groq_key:
        add_hosted(
            "groq",
            _env("ARIADNE_GROQ_BASE_URL") or "https://api.groq.com/openai/v1",
            groq_key,
            _env("ARIADNE_GROQ_MODEL") or "openai/gpt-oss-120b",
        )

    cf_account = _env("CLOUDFLARE_ACCOUNT_ID", "ARIADNE_CLOUDFLARE_ACCOUNT_ID")
    cf_token = _env("CLOUDFLARE_AI_TOKEN", "ARIADNE_CLOUDFLARE_API_KEY")
    if cf_account and cf_token:
        add_hosted(
            "cloudflare",
            _env("ARIADNE_CLOUDFLARE_BASE_URL")
            or f"https://api.cloudflare.com/client/v4/accounts/{cf_account}/ai/v1",
            cf_token,
            _env("ARIADNE_CLOUDFLARE_MODEL") or "@cf/qwen/qwen3.8-27b",
        )

    local_url = _env("ARIADNE_LOCAL_BASE_URL")
    if local_url:
        if _is_https(local_url) or _is_loopback(local_url):
            chain.append(OpenAICompatibleProvider(
                name="local",
                base_url=local_url,
                api_key=_env("ARIADNE_LOCAL_API_KEY") or "unused",
                model=_env("ARIADNE_LOCAL_MODEL") or "Qwen3.5-2B",
                read_timeout=LOCAL_READ_TIMEOUT,
                max_tokens=LOCAL_MAX_TOKENS,
            ))
        else:
            logger.warning(
                "ariadne provider=local skipped: base_url must be https or "
                "loopback (got host %r)", urlparse(local_url).hostname or "none",
            )

    extra_key = _env("ARIADNE_EXTRA_API_KEY")
    extra_url = _env("ARIADNE_EXTRA_BASE_URL")
    extra_model = _env("ARIADNE_EXTRA_MODEL")
    if extra_key and extra_url and extra_model:
        add_hosted(_env("ARIADNE_EXTRA_NAME") or "extra", extra_url, extra_key, extra_model)

    return chain


# --- Per-provider circuit breaker -------------------------------------------
# After CB_THRESHOLD consecutive expensive failures (timeout / 5xx / network /
# rate limit) a provider is skipped for CB_COOLDOWN seconds, so a sustained
# outage costs one timeout per cooldown instead of one per request. Cheap or
# persistent failures (config 401/403, empty, protocol) never trip it, so
# enabling a blocked model recovers on the very next request. Single worker,
# cooperative single thread; `now` is passed in for deterministic tests.
CB_THRESHOLD = _i("ARIADNE_CB_THRESHOLD", 3)
CB_COOLDOWN = _f("ARIADNE_CB_COOLDOWN", 30.0)
_CB_TRIP_KINDS = frozenset({"timeout", "network", "server", "rate_limit"})


class _Breaker:
    __slots__ = ("failures", "opened_until", "probing")

    def __init__(self) -> None:
        self.failures = 0
        self.opened_until = 0.0
        self.probing = False


_breakers: dict[str, _Breaker] = {}


def _breaker(name: str) -> _Breaker:
    b = _breakers.get(name)
    if b is None:
        b = _breakers[name] = _Breaker()
    return b


def breaker_allows(name: str, now: float) -> bool:
    """Whether this request may call the provider now.

    Closed: always yes. Open (cooling down): no. Half-open (cooldown elapsed):
    exactly one caller is granted the probe and claims ownership; concurrent
    callers are refused until the probe reports back, so an outage does not
    draw a thundering herd of probes. The check-and-claim is atomic under the
    single-threaded cooperative loop (no await in between), and the caller is
    contracted to always follow a True with breaker_record so the claim is
    released.
    """
    b = _breaker(name)
    if b.opened_until == 0.0:
        return True
    if now < b.opened_until:
        return False
    if b.probing:
        return False
    b.probing = True
    return True


def breaker_record(name: str, ok: bool, error_kind: str | None, now: float) -> None:
    """Update a provider's breaker after an attempt. Success closes it; an
    expensive failure re-opens it (immediately if it was a half-open probe,
    otherwise once the failure streak hits the threshold); cheap or persistent
    failures (config/empty/protocol) only release the probe claim, so recovery
    is immediate once the underlying misconfiguration is fixed."""
    b = _breaker(name)
    b.probing = False
    if ok:
        b.failures = 0
        b.opened_until = 0.0
    elif error_kind in _CB_TRIP_KINDS:
        if b.opened_until > 0.0:  # a half-open probe failed: re-open at once
            b.opened_until = now + CB_COOLDOWN
        else:
            b.failures += 1
            if b.failures >= CB_THRESHOLD:
                b.opened_until = now + CB_COOLDOWN


def breaker_reset_all() -> None:
    """Test helper: clear all breaker state."""
    _breakers.clear()


def log_attempt(attempt: int, result: ProviderResult) -> None:
    """Structured, prompt-free record of one provider attempt.

    Failures log at WARNING so fallbacks stay visible in journald (uvicorn's
    root logger drops INFO by default); successes log at INFO. The detail
    field is already redacted of credential-shaped runs.
    """
    log = logger.info if result.ok else logger.warning
    log(
        "ariadne attempt=%d provider=%s model=%s ok=%s status=%s "
        "latency_ms=%s ptok=%s ctok=%s error=%s detail=%s",
        attempt, result.provider, result.model, result.ok, result.status,
        result.latency_ms, result.prompt_tokens, result.completion_tokens,
        result.error_kind or "-",
        "-" if result.ok else (result.error_detail or "").replace("\n", " ")[:200],
    )
