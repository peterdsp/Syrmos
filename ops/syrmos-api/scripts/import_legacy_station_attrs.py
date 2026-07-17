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
import math
import json
import sqlite3
from pathlib import Path

from syrmos_admin import db as dbmod

# scripts/ -> syrmos-api/ -> ops/ -> repo root
DEFAULT_SEED = (
    Path(__file__).resolve().parents[3]
    / "core/data/src/commonMain/composeResources/files/seed/stations.json"
)

MAX_MATCH_METRES = 80

# Explicit overrides for cases coordinates alone cannot settle.
#
# The two seeds assigned M2_AGI to DIFFERENT stations, 5.5 km apart. Both are
# real M2 stations and both exist on the server, which disambiguated the second
# "Agios" with a 2 suffix (as it does for M2_SY2 and M2_AG3). So the legacy pair
# swaps:
#     legacy M2_AGI  Agios Ioannis  (37.9564) -> server M2_AG2
#     legacy M2_AGA  Agios Antonios (38.0061) -> server M2_AGI
#
# This is the one an id-equality backfill cannot catch: M2_AGI exists on both
# sides, so it looks like a match, and Agios Ioannis's fare zone would be written
# onto Agios Antonios with nothing in the counts to notice. Confirmed by Petros
# 2026-07-17. See docs/plans/2026-07-17-station-id-map.md.
ID_OVERRIDES = {
    "M2_AGI": "M2_AG2",   # Agios Ioannis
    "M2_AGA": "M2_AGI",   # Agios Antonios
}


def haversine_m(a: tuple[float, float], b: tuple[float, float]) -> float:
    r = 6371000.0
    dp = math.radians(b[0] - a[0])
    dl = math.radians(b[1] - a[1])
    x = (math.sin(dp / 2) ** 2
         + math.cos(math.radians(a[0])) * math.cos(math.radians(b[0])) * math.sin(dl / 2) ** 2)
    return 2 * r * math.asin(math.sqrt(x))


def resolve_ids(conn: sqlite3.Connection, stations: list[dict]) -> tuple[dict[str, str], list[str]]:
    """Map every legacy station id to a server id.

    Never match on id equality: M2_AGI proves ids are not stable across the two
    seeds. Never match on name alone either: several stations share a name across
    lines (Piraeus is on M1, M3, A1 and A4).

    Order matters:

      1. Explicit overrides (the M2_AGI swap).
      2. The id exists on the server -> use it. 115 of 201 ids already agree, and
         for those the id IS the answer. Do NOT second-guess them by coordinate:
         the two seeds' coordinates are noisy (legacy T7_GRI sits 12 m from server
         T7_AND) and their transliterations differ ("Ag. Kosma" vs "Aghiou
         Kosma"), so coordinate or name matching invents disagreements where the
         ids are already correct.
      3. Only the 86 diverged ids need matching, and both seeds keep one record
         per (line, station) and preserve the line prefix, so match within the
         same prefix by nearest coordinate.
    """
    server = {
        r["id"]: (r["lat"], r["lng"])
        for r in conn.execute("SELECT id, lat, lng FROM stations")
    }
    override_targets = set(ID_OVERRIDES.values())
    mapping: dict[str, str] = {}
    unresolved: list[str] = []

    for s in stations:
        lid = s["id"]
        if lid in ID_OVERRIDES:
            mapping[lid] = ID_OVERRIDES[lid]
            continue
        # An id that already exists on the server is the answer, unless an
        # override has claimed it for a different station.
        if lid in server and lid not in override_targets:
            mapping[lid] = lid
            continue
        prefix = lid.split("_")[0]
        pt = (s["latitude"], s["longitude"])
        cands = sorted(
            (haversine_m(pt, server[sid]), sid)
            for sid in server
            if sid.split("_")[0] == prefix and sid not in override_targets
        )
        if cands and cands[0][0] <= MAX_MATCH_METRES:
            mapping[lid] = cands[0][1]
        else:
            unresolved.append(lid)

    # A server station must not be claimed twice, or one legacy row silently
    # overwrites another's zone.
    claimed: dict[str, str] = {}
    for lid, sid in mapping.items():
        if sid in claimed:
            unresolved.append(f"{lid} and {claimed[sid]} both -> {sid}")
        claimed[sid] = lid
    return mapping, unresolved


def load_legacy(path: Path) -> list[dict]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(data, dict):  # tolerate a wrapped shape
        data = data.get("stations", [])
    return data


def apply(conn: sqlite3.Connection, stations: list[dict]) -> tuple[int, int, list[str]]:
    """Backfill accessibility + zone, refusing on anything unresolved.

    Athens is a zoned fare network, so an unmapped station is not left blank, it
    is left on the column default zone=1: a WRONG fare silently written into a row
    that looks fine. Refuse rather than skip.
    """
    mapping, unresolved = resolve_ids(conn, stations)
    if unresolved:
        raise SystemExit(
            f"REFUSING: {len(unresolved)} legacy stations did not resolve to a "
            f"unique server station:\n  " + "\n  ".join(unresolved[:8])
            + "\nAn unresolved station keeps the default zone=1, which in a zoned "
            "fare network is a wrong fare, not a blank one.\n"
            "See docs/plans/2026-07-17-station-id-map.md"
        )

    updated = 0
    for s in stations:
        sid = mapping[s["id"]]
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
