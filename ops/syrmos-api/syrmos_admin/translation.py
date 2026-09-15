"""Shared, defensive translation helpers for scraped Greek content."""

from __future__ import annotations

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
