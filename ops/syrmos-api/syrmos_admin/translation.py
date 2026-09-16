"""Shared, defensive translation helpers for scraped Greek content.

Order of preference: the Ariadne provider chain (keyed, already deployed, and
the only backend here that answers reliably), then the free deep-translator
endpoints as a fallback. The free endpoints rate limit persistently, which is
why every announcement in the live feed had an empty translation.
"""

from __future__ import annotations

import asyncio
import atexit
import logging
import os
import re
import time


_GREEK_LETTER_RE = re.compile(r"[Ͱ-Ͽἀ-῿]")
_ERROR_MARKERS = (
    "error 500",
    "that's an error",
    "there was an error",
    "server error",
)
_MYMEMORY_TARGETS = {
    "en": "english",
    "sq": "albanian",
    "it": "italian",
}
_LANGUAGE_NAMES = {
    "en": "English",
    "sq": "Albanian",
    "it": "Italian",
}

logger = logging.getLogger("syrmos.translation")

# The free Google endpoint documents ~5 requests/second and answers a burst with
# TooManyRequests. A scraper run translates every item into every language, so
# without pacing a single run trips the limit and every string comes back empty.
_MIN_INTERVAL_SECONDS = 0.25
_MAX_ATTEMPTS = 4
_BACKOFF_BASE_SECONDS = 1.0
_RATE_LIMIT_MARKERS = ("too many requests", "toomanyrequests", "429", "quota")

# Injected in tests so the retry path costs no wall-clock time.
_sleep = time.sleep
_monotonic = time.monotonic
_last_call_at = 0.0


def has_greek(text: str) -> bool:
    return bool(_GREEK_LETTER_RE.search(text))


def is_valid_translation(text: str) -> bool:
    lowered = text.lower()
    return bool(text) and not has_greek(text) and not any(
        marker in lowered for marker in _ERROR_MARKERS
    )


def is_rate_limited(exc: BaseException) -> bool:
    """Whether an exception is the provider asking us to slow down, as opposed to
    a real failure. Matched on text because deep-translator raises its own
    exception types that we do not want to import just to catch."""
    text = f"{type(exc).__name__} {exc}".lower()
    return any(marker in text for marker in _RATE_LIMIT_MARKERS)


def _pace() -> None:
    """Hold the call rate under the provider's documented ceiling."""
    global _last_call_at
    wait = _MIN_INTERVAL_SECONDS - (_monotonic() - _last_call_at)
    if wait > 0:
        _sleep(wait)
    _last_call_at = _monotonic()


def _translate_with(make_translator, text: str) -> str:
    """One provider, paced, with backoff while it is rate limiting us. Returns
    "" for anything we could not turn into a usable translation, so the caller
    can try the next provider."""
    for attempt in range(_MAX_ATTEMPTS):
        _pace()
        try:
            translated = (make_translator().translate(text) or "").strip()
        except Exception as exc:  # provider-specific types; treated uniformly
            if not is_rate_limited(exc) or attempt == _MAX_ATTEMPTS - 1:
                return ""
            _sleep(_BACKOFF_BASE_SECONDS * (2 ** attempt))
            continue
        return translated if is_valid_translation(translated) else ""
    return ""


# --- Ariadne provider chain (primary backend) --------------------------------
# The same keyed OpenAI-compatible chain the assistant uses (Groq, Cloudflare
# Workers AI, a local llama.cpp brain, an optional extra endpoint), reused here
# so translation depends on a backend we actually control rather than on a free
# scraped endpoint that answers a burst with TooManyRequests.

USE_ARIADNE = os.environ.get("SYRMOS_TRANSLATION_USE_ARIADNE", "1") != "0"

_SYSTEM_PROMPT = (
    "You are a translation engine inside a public transport app. Translate the "
    "user's Greek text into {language}.\n"
    "Reply with the translation and nothing else: no quotation marks, no notes, "
    "no alternatives, no explanation of what you did.\n"
    "Leave line and service codes (M1, M3, T6, X95, A1, IC), numbers, times and "
    "dates exactly as they appear.\n"
    "If you cannot translate the text, reply with exactly NO_TRANSLATION."
)
_REFUSAL = "NO_TRANSLATION"
# A chatty model answers a short title with a paragraph. Anything this much
# longer than the source is commentary, not a translation.
_MAX_GROWTH_FACTOR = 4
_MIN_ALLOWED_LENGTH = 80
_LEADING_LABEL_RE = re.compile(r"^(translation|përkthim|traduzione)\s*:\s*", re.IGNORECASE)
_QUOTE_CHARS = "\"'«»“”‘’"

# One event loop and one HTTP client for the whole process, so a scraper run
# reuses TCP/TLS connections instead of paying a handshake per string. The
# scrapers are synchronous scripts; this is the bridge to the async chain.
_loop: asyncio.AbstractEventLoop | None = None
_http_client = None
_chain_cache: list | None = None
_memo: dict[tuple[str, str], str] = {}


def _clean_llm_translation(raw: str, source: str) -> str:
    """Reduce a model reply to a usable translation, or "" if it is not one."""
    text = (raw or "").strip()
    text = _LEADING_LABEL_RE.sub("", text).strip()
    text = text.strip(_QUOTE_CHARS).strip()
    if not text or text.upper() == _REFUSAL:
        return ""
    if len(text) > max(_MIN_ALLOWED_LENGTH, len(source) * _MAX_GROWTH_FACTOR):
        return ""
    return text if is_valid_translation(text) else ""


def _get_chain():
    """The configured chain, built once. Empty when nothing is configured, in
    which case Ariadne translation is skipped without costing a round trip."""
    global _chain_cache
    if _chain_cache is None:
        try:
            from .ariadne_providers import build_chain
            _chain_cache = build_chain()
        except Exception as exc:  # httpx missing, bad config: fall back quietly
            logger.warning("ariadne chain unavailable for translation: %r", exc)
            _chain_cache = []
    return _chain_cache


def _get_runner():
    """A private loop plus HTTP client, created on first use and closed at exit."""
    global _loop, _http_client
    if _loop is None:
        import httpx

        _loop = asyncio.new_event_loop()
        _http_client = httpx.AsyncClient(
            limits=httpx.Limits(max_keepalive_connections=4, max_connections=8),
            headers={"User-Agent": "Syrmos-Translation/1.0"},
            trust_env=False,
        )
        atexit.register(_shutdown_runner)
    return _loop, _http_client


def _shutdown_runner() -> None:
    global _loop, _http_client
    if _loop is None:
        return
    try:
        if _http_client is not None:
            _loop.run_until_complete(_http_client.aclose())
    except Exception:  # noqa: BLE001 - shutdown must never raise
        pass
    finally:
        _loop.close()
        _loop, _http_client = None, None


async def _complete_via_chain(text: str, target: str) -> str:
    """Walk the chain in order, returning the first usable translation."""
    from .ariadne_providers import breaker_allows, breaker_record

    chain = _get_chain()
    if not chain:
        return ""
    _, client = _get_runner()
    system_text = _SYSTEM_PROMPT.format(
        language=_LANGUAGE_NAMES.get(target, target)
    )
    for provider in chain:
        now = time.monotonic()
        if not breaker_allows(provider.name, now):
            continue
        result = await provider.complete(
            client, system_text, [{"role": "user", "content": text}]
        )
        breaker_record(provider.name, result.ok, result.error_kind, time.monotonic())
        if not result.ok:
            logger.info(
                "translation provider=%s failed kind=%s status=%s",
                provider.name, result.error_kind, result.status,
            )
            continue
        cleaned = _clean_llm_translation(result.text, text)
        if cleaned:
            return cleaned
        logger.info(
            "translation provider=%s returned no usable translation", provider.name
        )
    return ""


def _translate_via_ariadne(text: str, target: str) -> str:
    """Synchronous bridge to the async chain. Returns "" when it cannot help."""
    if not USE_ARIADNE or not _get_chain():
        return ""
    try:
        asyncio.get_running_loop()
    except RuntimeError:
        pass
    else:
        # Already inside an event loop (e.g. called from the API process);
        # the sync bridge would deadlock, so leave it to the fallback.
        return ""
    try:
        loop, _ = _get_runner()
        return loop.run_until_complete(_complete_via_chain(text, target))
    except Exception as exc:  # noqa: BLE001 - translation must never break a scrape
        logger.warning("ariadne translation failed: %r", exc)
        return ""


def translate_from_greek(text: str, target: str) -> str:
    """Translate Greek text and never mislabel unchanged Greek as translated.

    Returns "" when no usable translation could be obtained. Callers must treat
    that as "leave whatever is already stored alone", never as "the translation
    is now empty": a provider outage would otherwise wipe good translations for
    every item on the next run.
    """
    text = text.strip()
    if not text or not has_greek(text):
        return text

    key = (text, target)
    if key in _memo:
        return _memo[key]

    translated = _translate_via_ariadne(text, target)
    if translated:
        _memo[key] = translated
        return translated

    translated = _translate_via_deep_translator(text, target)
    _memo[key] = translated
    return translated


def _translate_via_deep_translator(text: str, target: str) -> str:
    """The free fallback: Google first, then MyMemory. Both rate limit heavily,
    so this is a backstop for when the Ariadne chain is unconfigured, not the
    backend the pipeline should be relying on."""
    try:
        from deep_translator import GoogleTranslator
    except Exception:
        pass
    else:
        translated = _translate_with(
            lambda: GoogleTranslator(source="el", target=target), text
        )
        if translated:
            return translated

    try:
        from deep_translator import MyMemoryTranslator
    except Exception:
        return ""
    return _translate_with(
        lambda: MyMemoryTranslator(
            source="greek",
            target=_MYMEMORY_TARGETS.get(target, target),
        ),
        text,
    )
