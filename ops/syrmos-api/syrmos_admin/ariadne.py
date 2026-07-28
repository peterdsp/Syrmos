"""Ariadne cloud LLM backend.

Proxies user messages to Google Gemini Flash for the Ariadne assistant,
unified across iOS, Android, and web. The system prompt includes Athens
transit knowledge so answers are grounded in real data.

Requires GEMINI_API_KEY env var. Falls back to a polite "offline only"
response when the key is missing or the API is unreachable.
"""
from __future__ import annotations

import json
import os
import sqlite3
from urllib.error import URLError
from urllib.request import Request, urlopen

from . import db as dbmod

GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
GEMINI_MODEL = "gemini-2.0-flash-lite"
GEMINI_URL = (
    f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent"
)

GROQ_API_KEY = os.environ.get("GROQ_API_KEY", "")
GROQ_MODEL = "llama-3.1-8b-instant"
GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"

TIMEOUT_SECONDS = 15

SYSTEM_PROMPT = """\
You are Ariadne, the built-in transit assistant for Syrmos, an Athens public \
transport app. You help riders with metro (M1, M2, M3), tram (T6, T7), \
suburban rail (Proastiakos lines A1-A4), and intercity (Hellenic Train) in Greece.

Core knowledge:
- Athens metro: M1 (Piraeus-Kifisia, green), M2 (Anthoupoli-Elliniko, red), \
M3 (Nikaia-Airport, blue). Airport single ticket: 9 EUR, 90-min ticket: 1.20 EUR.
- Tram: T6 (Syntagma-SEF), T7 (Syntagma-Voula, merged to T6 at Mouson).
- Suburban/Proastiakos: A1 (Piraeus-Airport), A2 (Ano Liosia-Corinth/Kiato), \
A3 (Piraeus-Corinth via Megara), A4 (Ano Liosia-Airport).
- Operators: STASY (metro/tram), OASA (buses, integrated tickets), \
Hellenic Train (suburban + intercity).
- Operating hours: metro roughly 05:30-00:30 (later on weekends), \
tram 05:30-01:00, suburban varies by line.
- Key interchanges: Syntagma (M2/M3), Monastiraki (M1/M3), Attiki (M1/M2), \
Doukissis Plakentias (M3/A1-A4), Piraeus (M1/A1/A3).
- Thessaloniki metro is under construction (2 lines planned).
- Patras suburban: Patras-Rio-Kiato corridor.

Rules:
- Be concise, warm, and helpful. 2-3 sentences max per answer unless the \
user asks for detail.
- Respond in the SAME LANGUAGE the user writes in (Greek, English, or Albanian).
- Never invent schedules, times, or prices you are not sure about. Say \
"check hellenictrain.gr" or "check OASA" for live/exact data.
- For route planning, suggest lines and transfers but note that the app's \
built-in planner gives exact times.
- You can discuss weather impact on transit (prefer metro in rain/heat), \
accessibility (most metro stations have lifts), fares, and general Athens \
transit tips.
- If the question is completely unrelated to transit or Athens, politely \
redirect: "I specialize in Athens transit. Ask me about trains, routes, \
or stations."
- Keep a friendly tone. Your mascot is an owl.
"""


def _build_request_body(
    messages: list[dict[str, str]],
    transit_context: str | None = None,
) -> dict:
    system_text = SYSTEM_PROMPT
    if transit_context:
        system_text += f"\n\nCurrent transit context:\n{transit_context}"

    contents = []
    for msg in messages:
        role = "user" if msg.get("role") == "user" else "model"
        contents.append({"role": role, "parts": [{"text": msg["text"]}]})

    return {
        "system_instruction": {"parts": [{"text": system_text}]},
        "contents": contents,
        "generationConfig": {
            "temperature": 0.7,
            "maxOutputTokens": 300,
            "topP": 0.9,
        },
    }


def _get_transit_context() -> str | None:
    """Pull current alerts and news for the LLM context."""
    try:
        conn = dbmod.connect()
        dbmod.migrate(conn)
        parts = []

        # Recent STASY announcements
        try:
            rows = conn.execute(
                "SELECT title_en, severity FROM stasy_announcements"
                " ORDER BY published_at DESC LIMIT 3"
            ).fetchall()
            if rows:
                alerts = [f"- {r[0]} (severity: {r[1]})" for r in rows]
                parts.append("Active STASY alerts:\n" + "\n".join(alerts))
        except sqlite3.OperationalError:
            pass

        # Recent rail news
        try:
            rows = conn.execute(
                "SELECT title_en FROM rail_news"
                " ORDER BY published_at DESC LIMIT 3"
            ).fetchall()
            if rows:
                news = [f"- {r[0]}" for r in rows]
                parts.append("Recent rail news:\n" + "\n".join(news))
        except sqlite3.OperationalError:
            pass

        conn.close()
        return "\n\n".join(parts) if parts else None
    except Exception:
        return None


def _call_gemini(
    messages: list[dict[str, str]], transit_ctx: str | None,
) -> str | None:
    """Try Gemini. Returns reply text or None on failure."""
    if not GEMINI_API_KEY:
        return None
    body = _build_request_body(messages, transit_ctx)
    url = f"{GEMINI_URL}?key={GEMINI_API_KEY}"
    req = Request(
        url,
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json", "User-Agent": "Syrmos-Ariadne/1.0"},
        method="POST",
    )
    try:
        with urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
            result = json.loads(resp.read())
        candidates = result.get("candidates", [])
        if candidates:
            parts = candidates[0].get("content", {}).get("parts", [])
            if parts:
                return parts[0].get("text", "").strip()
    except (URLError, TimeoutError, json.JSONDecodeError, KeyError):
        pass
    return None


def _call_groq(
    messages: list[dict[str, str]], transit_ctx: str | None,
) -> str | None:
    """Try Groq (Llama). Returns reply text or None on failure."""
    if not GROQ_API_KEY:
        return None
    system_text = SYSTEM_PROMPT
    if transit_ctx:
        system_text += f"\n\nCurrent transit context:\n{transit_ctx}"
    oai_messages = [{"role": "system", "content": system_text}]
    for msg in messages:
        role = "user" if msg.get("role") == "user" else "assistant"
        oai_messages.append({"role": role, "content": msg["text"]})
    body = {
        "model": GROQ_MODEL,
        "messages": oai_messages,
        "temperature": 0.7,
        "max_tokens": 300,
    }
    req = Request(
        GROQ_URL,
        data=json.dumps(body).encode(),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {GROQ_API_KEY}",
            "User-Agent": "Syrmos-Ariadne/1.0",
        },
        method="POST",
    )
    try:
        with urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
            result = json.loads(resp.read())
        choices = result.get("choices", [])
        if choices:
            return choices[0].get("message", {}).get("content", "").strip()
    except (URLError, TimeoutError, json.JSONDecodeError, KeyError):
        pass
    return None


def chat(messages: list[dict[str, str]]) -> str:
    """Try Gemini, fall back to Groq, then offline fallback."""
    transit_ctx = _get_transit_context()
    reply = _call_gemini(messages, transit_ctx)
    if reply:
        return reply
    reply = _call_groq(messages, transit_ctx)
    if reply:
        return reply
    return _offline_fallback(messages)


def _offline_fallback(messages: list[dict[str, str]]) -> str:
    """When the LLM is unavailable, give a helpful static response."""
    last = (messages[-1].get("text", "") if messages else "").lower()
    if any(w in last for w in ["γεια", "γειά", "hi", "hello", "hey", "pershendetje"]):
        return (
            "Hi, I'm Ariadne. I'm running in offline mode right now. "
            "Use the app's built-in planner for routes and departures."
        )
    return (
        "I can't reach my brain right now. "
        "Use the app's built-in features for routes, departures, and alerts."
    )
