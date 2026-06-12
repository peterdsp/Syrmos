"""Load assets/oasa-fares/parsed/fares.jsonl into the syrmos-api DB.

Idempotent: wipes fare_products and re-inserts. Designed to be re-run by
the daily OASA watcher whenever the page hash changes.
"""
from __future__ import annotations

import json
import os
import sqlite3
import sys
from pathlib import Path

ROOT = Path(os.environ.get("PIPELINE_ROOT", str(Path(__file__).resolve().parent.parent)))
JSONL = ROOT / "assets" / "oasa-fares" / "parsed" / "fares.jsonl"
_MIG_DEV = ROOT / "ops" / "syrmos-api" / "migrations" / "0008_fare_products.sql"
_MIG_PI  = ROOT / "migrations" / "0008_fare_products.sql"
MIGRATION = _MIG_DEV if _MIG_DEV.exists() else _MIG_PI
DB_PATH = os.environ.get(
    "SYRMOS_DB_PATH",
    str(ROOT / "ops" / "syrmos-api" / "data" / "syrmos.db"),
)


def main() -> None:
    if not JSONL.exists():
        print(f"missing: {JSONL}", file=sys.stderr)
        sys.exit(1)
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA foreign_keys = ON")
    conn.executescript(MIGRATION.read_text())
    conn.execute("DELETE FROM fare_products")

    n = 0
    with JSONL.open() as f:
        for sort_order, line in enumerate(f):
            rec = json.loads(line)
            conn.execute(
                "INSERT INTO fare_products"
                " (section, title_en, full_price_eur, discounted_price_eur,"
                "  validity, notes, tags, sort_order)"
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                (
                    rec.get("section") or "single",
                    rec["title_en"],
                    rec.get("full_price_eur"),
                    rec.get("discounted_price_eur"),
                    rec.get("validity") or "",
                    rec.get("notes") or "",
                    ",".join(rec.get("tags") or []),
                    sort_order,
                ),
            )
            n += 1
    conn.commit()
    print(f"loaded {n} fare_products rows into {DB_PATH}")


if __name__ == "__main__":
    main()
