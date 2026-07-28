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

SAMBANOVA_API_KEY = os.environ.get("SAMBANOVA_API_KEY", "")
SAMBANOVA_MODEL = "Meta-Llama-3.3-70B-Instruct"
SAMBANOVA_URL = "https://api.sambanova.ai/v1/chat/completions"

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
    """Pull live transit data from the DB so the LLM answers from real info."""
    try:
        conn = dbmod.connect()
        dbmod.migrate(conn)
        parts = []

        # Active lines with terminals
        try:
            rows = conn.execute(
                "SELECT id, name_en, terminal_a, terminal_b, mode, status"
                " FROM lines WHERE status != 'inactive' ORDER BY sort_order"
            ).fetchall()
            if rows:
                lines = [f"- {r[0]}: {r[1]} ({r[4]}, {r[2]} to {r[3]}, {r[5]})" for r in rows]
                parts.append("Active lines:\n" + "\n".join(lines))
        except sqlite3.OperationalError:
            pass

        # STASY service status
        try:
            row = conn.execute(
                "SELECT status, raw_message_en FROM stasy_status LIMIT 1"
            ).fetchone()
            if row:
                parts.append(f"STASY service: {row[0]}. {row[1] or ''}")
        except sqlite3.OperationalError:
            pass

        # Announcements (alerts, disruptions)
        try:
            rows = conn.execute(
                "SELECT title_en, summary_en, severity, affected_lines"
                " FROM announcements ORDER BY date DESC LIMIT 5"
            ).fetchall()
            if rows:
                alerts = []
                for r in rows:
                    line = f"- {r[0]}"
                    if r[2]:
                        line += f" (severity: {r[2]})"
                    if r[3]:
                        line += f" [lines: {r[3]}]"
                    alerts.append(line)
                parts.append("Active announcements:\n" + "\n".join(alerts))
        except sqlite3.OperationalError:
            pass

        # Fare products
        try:
            rows = conn.execute(
                "SELECT title_en, full_price_eur, validity FROM fare_products"
                " ORDER BY sort_order LIMIT 12"
            ).fetchall()
            if rows:
                fares = [f"- {r[0]}: EUR {r[1]}" + (f" ({r[2]})" if r[2] else "") for r in rows]
                parts.append("Current fares:\n" + "\n".join(fares))
        except sqlite3.OperationalError:
            pass

        # Operating hours (schedule rules)
        try:
            rows = conn.execute(
                "SELECT line_id, day_type, open_time, close_time, notes"
                " FROM schedule_rules ORDER BY line_id"
            ).fetchall()
            if rows:
                hours = [f"- {r[0]} ({r[1]}): {r[2]}-{r[3]}" + (f" ({r[4]})" if r[4] else "") for r in rows]
                parts.append("Operating hours:\n" + "\n".join(hours))
        except sqlite3.OperationalError:
            pass

        # Current frequency bands
        try:
            from datetime import datetime, timezone, timedelta
            athens_tz = timezone(timedelta(hours=3))
            now_hour = datetime.now(athens_tz).strftime("%H:%M")
            rows = conn.execute(
                "SELECT line_id, day_type, headway_minutes, label"
                " FROM frequency_bands"
                " WHERE time_start <= ? AND time_end > ?"
                " ORDER BY line_id",
                (now_hour, now_hour),
            ).fetchall()
            if rows:
                freqs = [f"- {r[0]} ({r[1]}): every {r[2]} min" + (f" ({r[3]})" if r[3] else "") for r in rows]
                parts.append(f"Current frequencies (Athens time {now_hour}):\n" + "\n".join(freqs))
        except (sqlite3.OperationalError, Exception):
            pass

        # Recent rail news
        try:
            rows = conn.execute(
                "SELECT title_en, summary_en FROM rail_news"
                " ORDER BY published_at DESC LIMIT 3"
            ).fetchall()
            if rows:
                news = [f"- {r[0]}" + (f": {r[1][:120]}" if r[1] else "") for r in rows]
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


def _call_sambanova(
    messages: list[dict[str, str]], transit_ctx: str | None,
) -> str | None:
    """Try SambaNova (Llama 3.3 70B). Returns reply text or None on failure."""
    if not SAMBANOVA_API_KEY:
        return None
    system_text = SYSTEM_PROMPT
    if transit_ctx:
        system_text += f"\n\nCurrent transit context:\n{transit_ctx}"
    oai_messages = [{"role": "system", "content": system_text}]
    for msg in messages:
        role = "user" if msg.get("role") == "user" else "assistant"
        oai_messages.append({"role": role, "content": msg["text"]})
    body = {
        "model": SAMBANOVA_MODEL,
        "messages": oai_messages,
        "temperature": 0.7,
        "max_tokens": 300,
    }
    req = Request(
        SAMBANOVA_URL,
        data=json.dumps(body).encode(),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {SAMBANOVA_API_KEY}",
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


def chat(messages: list[dict[str, str]]) -> dict:
    """Try Gemini, fall back to Groq, then Cerebras, then offline."""
    transit_ctx = _get_transit_context()
    reply = _call_gemini(messages, transit_ctx)
    if reply:
        return {"reply": reply, "provider": "gemini"}
    reply = _call_groq(messages, transit_ctx)
    if reply:
        return {"reply": reply, "provider": "groq"}
    reply = _call_sambanova(messages, transit_ctx)
    if reply:
        return {"reply": reply, "provider": "sambanova"}
    return {"reply": _offline_fallback(messages), "provider": "offline"}


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
