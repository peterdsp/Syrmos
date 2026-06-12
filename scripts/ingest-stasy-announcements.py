"""Load assets/stasy-announcements/parsed/announcements.jsonl into the DB.

Idempotent: rewrites stasy_status (singleton) and announcements wholesale.
Translates Greek titles/summaries/status messages to English via the free
GoogleTranslator backend (deep-translator). Stores both columns so the
generator can emit the right one for the app's active language.
"""
from __future__ import annotations

import json
import os
import sqlite3
import sys
from pathlib import Path

ROOT = Path(os.environ.get("PIPELINE_ROOT", str(Path(__file__).resolve().parent.parent)))
JSONL = ROOT / "assets" / "stasy-announcements" / "parsed" / "announcements.jsonl"
_MIG_DEV = ROOT / "ops" / "syrmos-api" / "migrations"
_MIG_PI = ROOT / "migrations"
MIG_DIR = _MIG_DEV if _MIG_DEV.exists() else _MIG_PI
DB_PATH = os.environ.get(
    "SYRMOS_DB_PATH",
    str(ROOT / "ops" / "syrmos-api" / "data" / "syrmos.db"),
)


def _translate_factory():
    """Return a function el->en that degrades to identity if the network
    or the package is unavailable. Translation is best-effort; an outage
    must never break the ingest."""
    try:
        from deep_translator import GoogleTranslator
        translator = GoogleTranslator(source="el", target="en")

        def tr(text: str) -> str:
            text = (text or "").strip()
            if not text:
                return ""
            try:
                out = translator.translate(text[:4500])  # API cap is ~5k chars
                return out or text
            except Exception as e:
                print(f"  translation failed ({e!r}); falling back to GR", file=sys.stderr)
                return text
        return tr
    except ImportError:
        print("  deep-translator not installed; storing GR text in *_en columns", file=sys.stderr)
        return lambda s: s or ""


def _ensure_translation_columns(conn: sqlite3.Connection) -> None:
    """SQLite has no ADD COLUMN IF NOT EXISTS; check sqlite_master instead."""
    def has_col(table: str, col: str) -> bool:
        rows = conn.execute(f"PRAGMA table_info({table})").fetchall()
        return any(r[1] == col for r in rows)

    for table, col in (
        ("announcements", "title_en"),
        ("announcements", "summary_en"),
        ("stasy_status", "raw_message_en"),
    ):
        if not has_col(table, col):
            conn.execute(f"ALTER TABLE {table} ADD COLUMN {col} TEXT NOT NULL DEFAULT ''")


def main() -> None:
    if not JSONL.exists():
        print(f"missing: {JSONL}", file=sys.stderr)
        sys.exit(1)

    payload = json.loads(JSONL.read_text().splitlines()[0])

    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA foreign_keys = ON")
    # Run base announcement schema first so the columns we ALTER exist.
    base = MIG_DIR / "0009_announcements.sql"
    if base.exists():
        conn.executescript(base.read_text())
    _ensure_translation_columns(conn)

    tr = _translate_factory()

    status = payload.get("status") or {}
    raw_message = status.get("raw_message") or ""
    raw_message_en = tr(raw_message) if status.get("status") != "normal" else "Normal Operation" if raw_message else ""
    conn.execute("DELETE FROM stasy_status")
    conn.execute(
        "INSERT INTO stasy_status (id, status, raw_message, raw_message_en, service_until)"
        " VALUES (1, ?, ?, ?, ?)",
        (
            status.get("status") or "unknown",
            raw_message,
            raw_message_en,
            status.get("service_until"),
        ),
    )

    conn.execute("DELETE FROM announcements")
    items = payload.get("announcements") or []
    for sort_order, item in enumerate(items):
        title = item.get("title") or ""
        summary = item.get("summary") or ""
        conn.execute(
            "INSERT OR REPLACE INTO announcements"
            " (id, title, title_en, summary, summary_en, url, date, category, sort_order)"
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                item.get("id") or str(sort_order),
                title,
                tr(title),
                summary,
                tr(summary),
                item.get("url") or "",
                item.get("date") or "",
                item.get("category") or "general",
                sort_order,
            ),
        )
    conn.commit()
    n = conn.execute("SELECT COUNT(*) FROM announcements").fetchone()[0]
    print(f"loaded status={status.get('status')} + {n} announcements (translated)")


if __name__ == "__main__":
    main()
