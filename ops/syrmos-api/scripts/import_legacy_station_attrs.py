"""Backfill station accessibility + zone from the legacy bundled seed.

Design: docs/plans/2026-07-17-server-as-single-source-for-lines.md

Why this exists: accessibility and zone live ONLY in the legacy seed
(core/data/.../files/seed/stations.json), which is generated from hardcoded Swift
by a sync script that has been broken since the June 2026 iOS restructure. They
are consumed by StationRepositoryImpl and DataSeeder, so if the clients switch to
schedules-v2 before these values are in the DB, the data is silently lost with no
other copy anywhere.

This is a ONE-WAY, ONE-TIME rescue: read the legacy JSON, write the values into
the server, and from then on the DB owns them. Once the clients are switched and
the legacy seed is retired, this script has no further purpose and should be
deleted along with it.

Idempotent: re-running writes the same values.

Run: cd ~/syrmos-api && .venv/bin/python -m scripts.import_legacy_station_attrs
     (or locally with --seed pointing at the repo copy)
"""
from __future__ import annotations

import argparse
import json
import sqlite3
from pathlib import Path

from syrmos_admin import db as dbmod

# scripts/ -> syrmos-api/ -> ops/ -> repo root
DEFAULT_SEED = (
    Path(__file__).resolve().parents[3]
    / "core/data/src/commonMain/composeResources/files/seed/stations.json"
)


def load_legacy(path: Path) -> list[dict]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(data, dict):  # tolerate a wrapped shape
        data = data.get("stations", [])
    return data


def apply(conn: sqlite3.Connection, stations: list[dict]) -> tuple[int, int, list[str]]:
    """Backfill, but REFUSE to run on an incomplete id match.

    The legacy seed and the server have diverged: as of 2026-07-17, 86 of 201
    station ids differ, and they are the same physical stations filed under
    different ids (the Airport is A1_AER in the legacy seed and A2_AIR on the
    server; Irakleio is A1_IRK vs M1_IRA). The two disagree about which line owns
    a station.

    Skipping unmatched ids would leave those stations on the column default
    zone=1. Athens is a zoned fare network, so that is not a missing value, it is
    a WRONG fare, written silently into rows that already look fine. Refuse
    instead: a human has to map the ids first (see --id-map).
    """
    known = {r["id"] for r in conn.execute("SELECT id FROM stations")}
    unmatched = [s.get("id") for s in stations if s.get("id") not in known]
    if unmatched:
        raise SystemExit(
            f"REFUSING: {len(unmatched)} of {len(stations)} legacy station ids do not "
            f"exist on the server, e.g. {', '.join(filter(None, unmatched[:6]))}.\n"
            "The legacy seed and the server have diverged and these are the same\n"
            "stations under different ids. Backfilling anyway would leave them on\n"
            "the default zone=1, i.e. a wrong fare zone, not a blank one.\n"
            "Resolve the ids first: see\n"
            "docs/plans/2026-07-17-server-as-single-source-for-lines.md"
        )

    updated = 0
    for s in stations:
        sid = s["id"]
        accessibility = 1 if s.get("accessibility", True) else 0
        zone = int(s.get("zone", 1))
        conn.execute(
            "UPDATE stations SET accessibility=?, zone=? WHERE id=?",
            (accessibility, zone, sid),
        )
        updated += 1
    return updated, len(stations), []


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--seed", type=Path, default=DEFAULT_SEED)
    args = ap.parse_args()

    if not args.seed.exists():
        raise SystemExit(f"legacy seed not found: {args.seed}")

    conn = dbmod.connect()
    dbmod.migrate(conn)
    stations = load_legacy(args.seed)

    cur = conn.cursor()
    cur.execute("BEGIN")
    try:
        updated, total, skipped = apply(conn, stations)
        cur.execute("COMMIT")
    except Exception:
        cur.execute("ROLLBACK")
        raise

    print(f"legacy stations read: {total}")
    print(f"server rows updated:  {updated}")
    if skipped:
        print(f"not in server ({len(skipped)}), left alone: {', '.join(skipped[:8])}"
              + (" ..." if len(skipped) > 8 else ""))
    dist = conn.execute(
        "SELECT zone, COUNT(*) n FROM stations GROUP BY zone ORDER BY zone"
    ).fetchall()
    print("zone distribution:", {r["zone"]: r["n"] for r in dist})
    inacc = conn.execute(
        "SELECT COUNT(*) n FROM stations WHERE accessibility=0").fetchone()["n"]
    print(f"stations marked not accessible: {inacc}")


if __name__ == "__main__":
    main()
