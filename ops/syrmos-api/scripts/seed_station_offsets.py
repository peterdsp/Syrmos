"""Seed station_offsets from official STASY run times + station coordinates.

Why: STASY's per-station timetable pages render times via JavaScript, so an
HTML scraper returns nothing. Until they publish a structured feed (or we
ship a headless-browser scraper), distribute the published run times along
the line proportionally to inter-station great-circle distance. The result
is correct at terminals (0 and total runtime) and good enough for the
middle stops to be visibly different from 0 in station-detail screens.

When STASY adds a JSON feed or we add a Playwright scraper, replace this
seed with the real data without changing the table shape.

Source for runtimes: assets/athens-transit-package/RULES.md and
ops/syrmos-api/pkg/athens_fixed_rail_station_coordinates.md.

Run: cd ~/syrmos-api && .venv/bin/python -m scripts.seed_station_offsets
"""
from __future__ import annotations

import math
import sqlite3

from syrmos_admin import db as dbmod

# (line_id, direction_key, runtime_minutes)
# `direction_key` matches the apps' SyrmosStationOffsetsStore convention:
#   outbound = terminal_a -> terminal_b
#   inbound  = terminal_b -> terminal_a
RUNTIMES = [
    ("M1", "outbound", 51),  # Piraeus -> Kifissia
    ("M1", "inbound",  51),  # Kifissia -> Piraeus
    ("M2", "outbound", 32),  # Anthoupoli -> Elliniko
    ("M2", "inbound",  32),
    ("M3", "outbound", 63),  # Dimotiko Theatro -> Airport (full)
    ("M3", "inbound",  63),
    ("T6", "outbound", 33),  # Syntagma -> Pikrodafni
    ("T6", "inbound",  35),
    ("T7", "outbound", 54),  # Akti Posidonos -> Asklipiio Voulas
    ("T7", "inbound",  59),  # via Piraeus loop
]


def haversine_meters(a_lat, a_lng, b_lat, b_lng) -> float:
    """Great-circle distance in meters. Decent approximation for inter-station
    spacing within a city — stations are close enough that we don't need a
    full geodesic."""
    R = 6_371_000.0
    p1, p2 = math.radians(a_lat), math.radians(b_lat)
    dp = math.radians(b_lat - a_lat)
    dl = math.radians(b_lng - a_lng)
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))


def line_stations(conn: sqlite3.Connection, line_id: str) -> list[dict]:
    """Stations in outbound (terminal_a -> terminal_b) order."""
    rows = conn.execute(
        "SELECT ls.station_id, ls.seq, s.lat, s.lng, s.name_en"
        " FROM line_stations ls"
        " JOIN stations s ON s.id = ls.station_id"
        " WHERE ls.line_id = ?"
        " ORDER BY ls.seq",
        (line_id,),
    ).fetchall()
    return [
        {
            "station_id": r["station_id"],
            "seq": r["seq"],
            "lat": r["lat"],
            "lng": r["lng"],
            "name": r["name_en"],
        }
        for r in rows
    ]


def distribute(stations: list[dict], runtime_min: int, reverse: bool) -> list[dict]:
    """Spread runtime_min minutes across the stops in proportion to distance.
    Returns ordered list of {station_id, stop_sequence, minutes_from_origin}.

    First stop is always 0 minutes; last stop is the published runtime; middle
    stops are distance-weighted. Result is monotonically non-decreasing.
    """
    if not stations:
        return []
    seq = list(reversed(stations)) if reverse else list(stations)
    distances = [0.0]
    total = 0.0
    for prev, cur in zip(seq, seq[1:]):
        d = haversine_meters(prev["lat"], prev["lng"], cur["lat"], cur["lng"])
        total += d
        distances.append(total)
    if total <= 0:
        # Fallback to even spacing when coordinates are degenerate.
        per = runtime_min / max(len(seq) - 1, 1)
        return [
            {
                "station_id": s["station_id"],
                "stop_sequence": i,
                "minutes_from_origin": round(i * per),
            }
            for i, s in enumerate(seq)
        ]
    out = []
    for i, s in enumerate(seq):
        offset = (distances[i] / total) * runtime_min
        out.append(
            {
                "station_id": s["station_id"],
                "stop_sequence": i,
                "minutes_from_origin": round(offset),
            }
        )
    return out


def upsert(conn: sqlite3.Connection, line_id: str, direction: str, stops: list[dict]) -> None:
    if not stops:
        return
    terminals = stops[0]["station_id"], stops[-1]["station_id"]
    name_lookup = {r["id"]: (r["name_en"], r["name_el"]) for r in conn.execute(
        "SELECT id, name_en, name_el FROM stations"
    ).fetchall()}
    origin = name_lookup.get(terminals[0], ("", ""))[0]
    destination = name_lookup.get(terminals[1], ("", ""))[0]
    conn.execute(
        "DELETE FROM station_offsets WHERE line_id = ? AND direction = ?",
        (line_id, direction),
    )
    for stop in stops:
        en, _ = name_lookup.get(stop["station_id"], ("", ""))
        conn.execute(
            "INSERT INTO station_offsets("
            " line_id, direction, origin, destination, station_id, station_en,"
            " stop_sequence, minutes_from_origin"
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (line_id, direction, origin, destination,
             stop["station_id"], en, stop["stop_sequence"], stop["minutes_from_origin"]),
        )


def main() -> None:
    with dbmod.connect() as conn:
        dbmod.migrate(conn)
        total = 0
        for line_id, direction, runtime in RUNTIMES:
            stations = line_stations(conn, line_id)
            if not stations:
                print(f"  {line_id}/{direction}: no line_stations rows, skipped")
                continue
            stops = distribute(stations, runtime, reverse=(direction == "inbound"))
            upsert(conn, line_id, direction, stops)
            total += len(stops)
            print(f"  {line_id}/{direction}: {len(stops)} stops over {runtime} min")
        print(f"seeded {total} station offsets")


if __name__ == "__main__":
    main()
