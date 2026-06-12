"""Load STASY station offsets into the syrmos-api DB.

Reads assets/stasy-html/parsed/station-offsets.jsonl produced by
parse-stasy-html.py and replaces the contents of station_offsets in one
transaction. Idempotent: re-run any time after the parser to refresh.

Run on the Pi:
    SYRMOS_DB_PATH=/home/peterdsp/syrmos-api/db/syrmos.db \\
    python3 scripts/ingest-stasy-html.py
"""
from __future__ import annotations

import json
import os
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JSONL = ROOT / "assets" / "stasy-html" / "parsed" / "station-offsets.jsonl"
MIGRATION = ROOT / "ops" / "syrmos-api" / "migrations" / "0006_station_offsets.sql"
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
    conn.execute("DELETE FROM station_offsets")

    # Best-effort station_id lookup by exact English name. Lets the apps
    # join offsets onto map markers without a second query.
    stations_by_name = {
        r[0]: r[1]
        for r in conn.execute("SELECT name_en, id FROM stations").fetchall()
    }

    total = 0
    unmatched: set[str] = set()
    with JSONL.open() as f:
        for line in f:
            rec = json.loads(line)
            station_en = rec["station_en"]
            station_id = stations_by_name.get(station_en)
            if station_id is None:
                unmatched.add(station_en)
            conn.execute(
                "INSERT INTO station_offsets "
                "(line_id, direction, origin, destination, stop_sequence,"
                " station_en, station_id, minutes_from_origin)"
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                (
                    rec["line_id"], rec["direction"], rec["origin"], rec["destination"],
                    rec["stop_sequence"], station_en, station_id, rec["minutes_from_origin"],
                ),
            )
            total += 1
    conn.commit()
    print(f"loaded {total} station_offsets rows into {DB_PATH}")
    if unmatched:
        print(f"\nWARN: {len(unmatched)} station names did not match the stations table:")
        for n in sorted(unmatched):
            print(f"  - {n}")


if __name__ == "__main__":
    main()
