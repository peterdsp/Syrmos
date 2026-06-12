"""Load assets/stasy-announcements/parsed/announcements.jsonl into the DB.

Idempotent: rewrites stasy_status (singleton) and announcements wholesale.
"""
from __future__ import annotations

import json
import os
import sqlite3
import sys
from pathlib import Path

ROOT = Path(os.environ.get("PIPELINE_ROOT", str(Path(__file__).resolve().parent.parent)))
JSONL = ROOT / "assets" / "stasy-announcements" / "parsed" / "announcements.jsonl"
_MIG_DEV = ROOT / "ops" / "syrmos-api" / "migrations" / "0009_announcements.sql"
_MIG_PI = ROOT / "migrations" / "0009_announcements.sql"
MIGRATION = _MIG_DEV if _MIG_DEV.exists() else _MIG_PI
DB_PATH = os.environ.get(
    "SYRMOS_DB_PATH",
    str(ROOT / "ops" / "syrmos-api" / "data" / "syrmos.db"),
)


def main() -> None:
    if not JSONL.exists():
        print(f"missing: {JSONL}", file=sys.stderr)
        sys.exit(1)

    payload = json.loads(JSONL.read_text().splitlines()[0])

    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA foreign_keys = ON")
    conn.executescript(MIGRATION.read_text())

    status = payload.get("status") or {}
    conn.execute("DELETE FROM stasy_status")
    conn.execute(
        "INSERT INTO stasy_status (id, status, raw_message, service_until) VALUES (1, ?, ?, ?)",
        (
            status.get("status") or "unknown",
            status.get("raw_message") or "",
            status.get("service_until"),
        ),
    )

    conn.execute("DELETE FROM announcements")
    for sort_order, item in enumerate(payload.get("announcements") or []):
        conn.execute(
            "INSERT OR REPLACE INTO announcements"
            " (id, title, summary, url, date, category, sort_order)"
            " VALUES (?, ?, ?, ?, ?, ?, ?)",
            (
                item.get("id") or str(sort_order),
                item.get("title") or "",
                item.get("summary") or "",
                item.get("url") or "",
                item.get("date") or "",
                item.get("category") or "general",
                sort_order,
            ),
        )
    conn.commit()
    n = conn.execute("SELECT COUNT(*) FROM announcements").fetchone()[0]
    print(f"loaded status={status.get('status')} + {n} announcements into {DB_PATH}")


if __name__ == "__main__":
    main()
