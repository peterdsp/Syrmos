"""Ariadne assistant backend.

Turns user messages into grounded, natural-language transit answers by
calling a chain of OpenAI-compatible LLM providers (see ariadne_providers).
The providers are the language layer only: schedules, departures, fares,
station names, and disruptions come from the Syrmos database and are passed
to the model as authoritative context. The model must not invent those
facts and must abstain when the supplied context does not answer the
question. If no provider is reachable, a deterministic offline reply is
returned so the endpoint always responds.
"""
from __future__ import annotations

import asyncio
import os
import sqlite3

from . import ariadne_providers as providers
from . import db as dbmod

SYSTEM_PROMPT = """\
You are Ariadne, the built-in transit assistant for Syrmos, an Athens public \
transport app. You help riders with metro (M1, M2, M3), tram (T6, T7), \
suburban rail (Proastiakos lines A1-A4), and intercity (Hellenic Train) in Greece.

Core knowledge:
- Athens metro: M1 (Piraeus-Kifisia, green), M2 (Anthoupoli-Elliniko, red), \
M3 (Nikaia-Airport, blue). Airport single ticket: 9 EUR, 90-min ticket: 1.20 EUR.
- Tram: T6 (Syntagma-SEF), T7 (Syntagma-Voula, merged to T6 at Mouson).
- Suburban/Proastiakos: A1 (Piraeus-Airport), A2 (Ano Liosia-Airport), \
A3 (Athens-Chalcis), A4 (Piraeus-Kiato).
- Operators: STASY (metro/tram), OASA (buses, integrated tickets), \
Hellenic Train (suburban + intercity).
- Operating hours: metro roughly 05:30-00:30 (later on weekends), \
tram 05:30-01:00, suburban varies by line.
- Key interchanges: Syntagma (M2/M3), Monastiraki (M1/M3), Attiki (M1/M2), \
Doukissis Plakentias (M3, airport suburban A1/A2), Piraeus (M1/A1/A4).
- Thessaloniki metro is under construction (2 lines planned).
- Patras suburban: Patras-Rio-Kiato corridor.
- Scenic railways: the Pelion railway (Ano Lechonia-Ano Gatzea-Milies) is a \
seasonal tourist service, weekends and holidays April to October. The \
Diakopto-Kalavryta rack railway (Odontotos) is temporarily suspended since \
March 2026 after rockfalls; do not present it as running.

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

# Prepended to the live DB context. Makes the grounding contract explicit so
# the model treats the supplied facts as the only source of truth for
# schedules/fares/stations/disruptions and abstains rather than guessing.
GROUNDING = (
    "\n\nAuthoritative Syrmos transit facts follow. Use ONLY these for any "
    "claim about schedules, departure times, fares, station names, routes, "
    "or service disruptions, and do not invent or guess such facts. If the "
    "facts below do not answer the question, say that Syrmos does not "
    "currently have enough information and point the rider to the app's "
    "planner, rather than making something up.\n\n"
)

# Used when the live DB context cannot be loaded. Keeps the abstention
# contract in force so the model does not present the prompt's baseline
# figures (fares, hours) as if they were current.
GROUNDING_NO_CONTEXT = (
    "\n\nLive Syrmos transit data is unavailable right now. Do not state "
    "specific current departure times, fares, or service disruptions, and do "
    "not present any figures above as if they were live. For anything "
    "time-sensitive, tell the rider to check the app's planner or the "
    "official operator (hellenictrain.gr, OASA). General guidance about which "
    "lines exist and how the network connects is still fine.\n"
)

# Grounded context is trimmed to the sections relevant to the question and
# capped, so the full DB dump never blows a provider's per-minute token budget
# (Groq free is 8K TPM). Lines, service status, and announcements are always
# included (routing and disruptions matter for any question); other sections
# are added only when the question mentions them.
MAX_CONTEXT_CHARS = int(os.environ.get("ARIADNE_MAX_CONTEXT_CHARS", "2500"))
_ALWAYS_CONTEXT = ("lines", "stasy", "announcements")
_CONTEXT_KEYWORDS = {
    "fares": ["fare", "ticket", "price", "cost", "euro", "€", "τιμ", "εισιτ",
              "κοστ", "κόστ", "çmim", "bilet"],
    "hours": ["hour", "open", "clos", "time", "when", "schedule", "depart",
              "ωρα", "ώρα", "ωράρ", "πότε", "δρομολ", "orar"],
    "frequencies": ["how often", "frequen", "every", "headway", "wait",
                    "συχνότ", "κάθε", "αναμον"],
    "news": ["news", "νέα", "ειδήσ", "lajm"],
}


def _get_transit_sections() -> list[tuple[str, str]]:
    """Pull live transit data from the DB as labelled sections so the caller
    can include only the relevant ones. Returns ``[(name, text), ...]``."""
    sections: list[tuple[str, str]] = []
    try:
        conn = dbmod.connect()
        dbmod.migrate(conn)

        try:
            rows = conn.execute(
                "SELECT id, name_en, terminal_a, terminal_b, mode, status"
                " FROM lines WHERE status != 'inactive' ORDER BY sort_order"
            ).fetchall()
            if rows:
                lines = [f"- {r[0]}: {r[1]} ({r[4]}, {r[2]} to {r[3]}, {r[5]})" for r in rows]
                sections.append(("lines", "Active lines:\n" + "\n".join(lines)))
        except sqlite3.OperationalError:
            pass

        try:
            row = conn.execute(
                "SELECT status, raw_message_en FROM stasy_status LIMIT 1"
            ).fetchone()
            if row:
                sections.append(("stasy", f"STASY service: {row[0]}. {row[1] or ''}"))
        except sqlite3.OperationalError:
            pass

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
                sections.append(("announcements", "Active announcements:\n" + "\n".join(alerts)))
        except sqlite3.OperationalError:
            pass

        try:
            rows = conn.execute(
                "SELECT title_en, full_price_eur, validity FROM fare_products"
                " ORDER BY sort_order LIMIT 12"
            ).fetchall()
            if rows:
                fares = [f"- {r[0]}: EUR {r[1]}" + (f" ({r[2]})" if r[2] else "") for r in rows]
                sections.append(("fares", "Current fares:\n" + "\n".join(fares)))
        except sqlite3.OperationalError:
            pass

        try:
            rows = conn.execute(
                "SELECT line_id, day_type, open_time, close_time, notes"
                " FROM schedule_rules ORDER BY line_id"
            ).fetchall()
            if rows:
                hours = [f"- {r[0]} ({r[1]}): {r[2]}-{r[3]}" + (f" ({r[4]})" if r[4] else "") for r in rows]
                sections.append(("hours", "Operating hours:\n" + "\n".join(hours)))
        except sqlite3.OperationalError:
            pass

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
                sections.append(("frequencies", f"Current frequencies (Athens time {now_hour}):\n" + "\n".join(freqs)))
        except (sqlite3.OperationalError, Exception):
            pass

        try:
            rows = conn.execute(
                "SELECT title_en, summary_en FROM rail_news"
                " ORDER BY published_at DESC LIMIT 3"
            ).fetchall()
            if rows:
                news = [f"- {r[0]}" + (f": {r[1][:120]}" if r[1] else "") for r in rows]
                sections.append(("news", "Recent rail news:\n" + "\n".join(news)))
        except sqlite3.OperationalError:
            pass

        conn.close()
    except Exception:
        return []
    return sections


def _select_context(
    sections: list[tuple[str, str]],
    query: str,
    max_chars: int = MAX_CONTEXT_CHARS,
) -> str | None:
    """Pick the always-included sections plus any the question asks about, then
    cap the total size so the grounded context never blows a per-minute token
    budget."""
    ql = (query or "").lower()
    wanted = set(_ALWAYS_CONTEXT)
    for name, keywords in _CONTEXT_KEYWORDS.items():
        if any(k in ql for k in keywords):
            wanted.add(name)
    chosen = [text for name, text in sections if name in wanted]
    if not chosen:
        return None
    out: list[str] = []
    total = 0
    for text in chosen:
        if total >= max_chars:
            break
        if total + len(text) > max_chars:
            text = text[: max_chars - total]
        out.append(text)
        total += len(text)
    return "\n\n".join(out) if out else None


def _system_text(query: str) -> str:
    """System prompt plus the grounding contract.

    The abstention contract is always present. When live DB facts relevant to
    the question are available they are appended under it; when they are not,
    an explicit "no live data" note keeps the model from presenting the
    prompt's baseline figures as current.
    """
    transit_ctx = _select_context(_get_transit_sections(), query)
    if transit_ctx:
        return SYSTEM_PROMPT + GROUNDING + transit_ctx
    return SYSTEM_PROMPT + GROUNDING_NO_CONTEXT


def _to_openai_messages(messages: list[dict[str, str]]) -> list[dict[str, str]]:
    """Map Syrmos {role, text} turns to OpenAI {role, content} messages."""
    out: list[dict[str, str]] = []
    for msg in messages:
        role = "user" if msg.get("role") == "user" else "assistant"
        out.append({"role": role, "content": msg.get("text", "")})
    return out


def _latest_user_text(messages: list[dict[str, str]]) -> str:
    """The most recent user turn, used to pick the relevant grounded context."""
    for msg in reversed(messages):
        if msg.get("role") == "user":
            return msg.get("text", "") or ""
    return messages[-1].get("text", "") if messages else ""


async def _build_system_text(
    deadline: float, loop: asyncio.AbstractEventLoop, query: str,
) -> str:
    """Assemble the grounded system prompt without blocking the event loop.

    _system_text() does synchronous SQLite connect/migrate/query work, so run
    it in a worker thread and bound it by the remaining chain budget. If it is
    slow (DB lock, migration) or errors, fall back to the no-context
    abstention prompt so grounding can never blow the deadline or stall the
    loop.
    """
    remaining = deadline - loop.time()
    try:
        return await asyncio.wait_for(
            asyncio.to_thread(_system_text, query), timeout=max(0.1, remaining),
        )
    except asyncio.TimeoutError:
        return SYSTEM_PROMPT + GROUNDING_NO_CONTEXT
    except Exception:  # noqa: BLE001 - grounding must never break the reply
        return SYSTEM_PROMPT + GROUNDING_NO_CONTEXT


async def chat_async(messages: list[dict[str, str]]) -> dict:
    """Run the provider chain, returning the first grounded reply.

    Providers are tried in configured order (Groq, Cloudflare, local
    llama.cpp, optional extra). A per-provider circuit breaker skips a
    provider that is mid-outage so the chain fails over fast instead of
    paying its timeout on every request. The whole chain is bounded by
    CHAIN_DEADLINE, and grounding runs off the event loop within that budget.
    Each attempt is logged with provider, status, latency, and token counts,
    never the prompt text. If every provider fails, fall back to a
    deterministic offline reply so the endpoint always answers.
    """
    loop = asyncio.get_event_loop()
    deadline = loop.time() + providers.CHAIN_DEADLINE
    oai_messages = _to_openai_messages(messages)
    system_text = await _build_system_text(deadline, loop, _latest_user_text(messages))
    client = providers.get_client()
    for attempt, provider in enumerate(providers.build_chain(), 1):
        remaining = deadline - loop.time()
        if remaining < providers.MIN_ATTEMPT_BUDGET:
            break
        if not providers.breaker_allows(provider.name, loop.time()):
            providers.log_attempt(attempt, providers.ProviderResult(
                provider.name, provider.model, False,
                error_kind="circuit_open", error_detail="breaker open",
            ))
            continue
        try:
            result = await asyncio.wait_for(
                provider.complete(client, system_text, oai_messages, remaining),
                timeout=remaining,
            )
        except asyncio.TimeoutError:
            result = providers.ProviderResult(
                provider.name, provider.model, False,
                error_kind="timeout", error_detail="chain deadline reached",
            )
        except Exception as exc:  # noqa: BLE001 - contain, never abort the chain
            result = providers.ProviderResult(
                provider.name, provider.model, False,
                error_kind="network", error_detail=repr(exc)[:200],
            )
        providers.breaker_record(provider.name, result.ok, result.error_kind, loop.time())
        providers.log_attempt(attempt, result)
        if result.ok:
            return {
                "reply": result.text,
                "provider": provider.name,
                "model": provider.model,
            }
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
