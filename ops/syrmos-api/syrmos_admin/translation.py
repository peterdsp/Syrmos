"""Shared, defensive translation helpers for scraped Greek content."""

from __future__ import annotations

import re


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


def has_greek(text: str) -> bool:
    return bool(_GREEK_LETTER_RE.search(text))


def is_valid_translation(text: str) -> bool:
    lowered = text.lower()
    return bool(text) and not has_greek(text) and not any(
        marker in lowered for marker in _ERROR_MARKERS
    )


def translate_from_greek(text: str, target: str) -> str:
    """Translate Greek text and never mislabel unchanged Greek as translated."""
    text = text.strip()
    if not text or not has_greek(text):
        return text

    try:
        from deep_translator import GoogleTranslator

        translated = (
            GoogleTranslator(source="el", target=target).translate(text) or ""
        ).strip()
        if is_valid_translation(translated):
            return translated
    except Exception:
        pass

    try:
        from deep_translator import MyMemoryTranslator

        translated = (
            MyMemoryTranslator(
                source="greek",
                target=_MYMEMORY_TARGETS.get(target, target),
            ).translate(text)
            or ""
        ).strip()
        return translated if is_valid_translation(translated) else ""
    except Exception:
        return ""
