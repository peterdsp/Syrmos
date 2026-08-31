"""OpenAI-compatible provider abstraction for the Ariadne assistant.

Every hosted or local backend Ariadne uses (Groq, Cloudflare Workers AI, a
local llama.cpp server, and optionally a paid OpenAI-compatible endpoint)
speaks the same ``/v1/chat/completions`` contract, so a single provider type
covers them all. Adding a provider means supplying a base URL, an API key
env var, and a model name, nothing more.

All calls are async (httpx.AsyncClient) so a slow or hung provider never
blocks the FastAPI event loop, and each provider has strict connect/read
timeouts so the chain fails over quickly. Failures are classified (config
vs rate limit vs server vs timeout vs network) so a misconfiguration reads
differently from a transient outage in the logs.
"""
from __future__ import annotations

import logging
import os
import time
from dataclasses import dataclass

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
# read budget. All are overridable from the environment after measuring real
# latency from the Raspberry Pi in Athens.
CONNECT_TIMEOUT = _f("ARIADNE_CONNECT_TIMEOUT", 2.0)
READ_TIMEOUT = _f("ARIADNE_READ_TIMEOUT", 4.0)
LOCAL_READ_TIMEOUT = _f("ARIADNE_LOCAL_READ_TIMEOUT", 25.0)
TEMPERATURE = _f("ARIADNE_TEMPERATURE", 0.2)
MAX_TOKENS = _i("ARIADNE_MAX_TOKENS", 400)
LOCAL_MAX_TOKENS = _i("ARIADNE_LOCAL_MAX_TOKENS", 160)

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
    # config | rate_limit | server | client | timeout | network | empty
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
    ) -> ProviderResult:
        payload = {
            "model": self.model,
            "messages": [{"role": "system", "content": system_text}, *messages],
            "temperature": TEMPERATURE,
            "max_tokens": self.max_tokens,
            "stream": False,
        }
        timeout = httpx.Timeout(
            self.read_timeout,
            connect=self.connect_timeout,
            read=self.read_timeout,
            write=self.read_timeout,
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
                error_kind="timeout", error_detail=repr(exc),
            )
        except httpx.HTTPError as exc:
            return ProviderResult(
                self.name, self.model, False, latency_ms=_ms(started),
                error_kind="network", error_detail=repr(exc),
            )

        latency = _ms(started)
        if resp.status_code != 200:
            return ProviderResult(
                self.name, self.model, False, status=resp.status_code,
                latency_ms=latency, error_kind=_classify(resp.status_code),
                error_detail=resp.text[:300],
            )
        try:
            data = resp.json()
        except ValueError as exc:
            return ProviderResult(
                self.name, self.model, False, status=200, latency_ms=latency,
                error_kind="empty", error_detail=repr(exc),
            )
        choices = data.get("choices") or []
        text = ""
        if choices:
            text = (choices[0].get("message", {}).get("content") or "").strip()
        if not text:
            return ProviderResult(
                self.name, self.model, False, status=200, latency_ms=latency,
                error_kind="empty", error_detail="no content in choices",
            )
        usage = data.get("usage") or {}
        return ProviderResult(
            self.name, self.model, True, text=text, status=200, latency_ms=latency,
            prompt_tokens=usage.get("prompt_tokens"),
            completion_tokens=usage.get("completion_tokens"),
        )


def _env(*names: str) -> str:
    for name in names:
        value = os.environ.get(name, "").strip()
        if value:
            return value
    return ""


def build_chain() -> list[OpenAICompatibleProvider]:
    """Assemble the provider chain from whatever is configured, in order.

    Groq (fast hosted free tier) first, then Cloudflare Workers AI, then a
    local llama.cpp server as a degraded offline brain, then an optional
    extra OpenAI-compatible provider (e.g. a paid endpoint). A provider is
    only included when its required credentials/URL are present, so the
    chain lights up provider by provider as configuration is added and never
    wastes a round trip on an unconfigured backend.
    """
    chain: list[OpenAICompatibleProvider] = []

    groq_key = _env("ARIADNE_GROQ_API_KEY", "GROQ_API_KEY")
    if groq_key:
        chain.append(OpenAICompatibleProvider(
            name="groq",
            base_url=_env("ARIADNE_GROQ_BASE_URL") or "https://api.groq.com/openai/v1",
            api_key=groq_key,
            model=_env("ARIADNE_GROQ_MODEL") or "openai/gpt-oss-120b",
        ))

    cf_account = _env("CLOUDFLARE_ACCOUNT_ID", "ARIADNE_CLOUDFLARE_ACCOUNT_ID")
    cf_token = _env("CLOUDFLARE_AI_TOKEN", "ARIADNE_CLOUDFLARE_API_KEY")
    if cf_account and cf_token:
        chain.append(OpenAICompatibleProvider(
            name="cloudflare",
            base_url=_env("ARIADNE_CLOUDFLARE_BASE_URL")
            or f"https://api.cloudflare.com/client/v4/accounts/{cf_account}/ai/v1",
            api_key=cf_token,
            model=_env("ARIADNE_CLOUDFLARE_MODEL") or "@cf/qwen/qwen3.8-27b",
        ))

    local_url = _env("ARIADNE_LOCAL_BASE_URL")
    if local_url:
        chain.append(OpenAICompatibleProvider(
            name="local",
            base_url=local_url,
            api_key=_env("ARIADNE_LOCAL_API_KEY") or "unused",
            model=_env("ARIADNE_LOCAL_MODEL") or "Qwen3.5-2B",
            read_timeout=LOCAL_READ_TIMEOUT,
            max_tokens=LOCAL_MAX_TOKENS,
        ))

    extra_key = _env("ARIADNE_EXTRA_API_KEY")
    extra_url = _env("ARIADNE_EXTRA_BASE_URL")
    extra_model = _env("ARIADNE_EXTRA_MODEL")
    if extra_key and extra_url and extra_model:
        chain.append(OpenAICompatibleProvider(
            name=_env("ARIADNE_EXTRA_NAME") or "extra",
            base_url=extra_url,
            api_key=extra_key,
            model=extra_model,
        ))

    return chain


def log_attempt(attempt: int, result: ProviderResult) -> None:
    """Structured, prompt-free record of one provider attempt.

    Failures log at WARNING so fallbacks stay visible in journald (uvicorn's
    root logger drops INFO by default); successes log at INFO.
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
