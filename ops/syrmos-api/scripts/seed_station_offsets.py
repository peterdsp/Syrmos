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
    ("M3", "outbound", 44),  # Dim. Theatro -> Doukissis Plakentias (city only)
    ("M3", "inbound",  44),
    ("T6", "outbound", 33),  # Syntagma -> Pikrodafni
    ("T6", "inbound",  35),
    ("T7", "outbound", 54),  # Akti Posidonos -> Asklipiio Voulas
    ("T7", "inbound",  59),  # via Piraeus loop
]

# Exact STASY trip-duration tables, station_id -> minutes_from_origin.
# When a (line, direction) pair has an entry here, it overrides the
# haversine-prorated value computed by distribute() so the projector
# matches the published minute-by-minute timetable verbatim.
STASY_OFFSETS: dict[tuple[str, str], dict[str, int]] = {
    ("M1", "outbound"): {
        "M1_PIR": 0, "M1_FAL": 4, "M1_MOS": 7, "M1_KAL": 9, "M1_TAV": 11,
        "M1_PET": 13, "M1_THI": 15, "M1_MON": 17, "M1_OMO": 19, "M1_VIC": 21,
        "M1_ATT": 24, "M1_AGI": 26, "M1_KAT": 28, "M1_AG2": 30, "M1_ANO": 31,
        "M1_PER": 34, "M1_PEF": 35, "M1_NEA": 37, "M1_IRA": 39, "M1_EIR": 42,
        "M1_NER": 44, "M1_MAR": 47, "M1_KA2": 49, "M1_KIF": 51,
    },
    ("M1", "inbound"): {
        "M1_KIF": 0, "M1_KA2": 2, "M1_MAR": 5, "M1_NER": 8, "M1_EIR": 10,
        "M1_IRA": 12, "M1_NEA": 15, "M1_PEF": 16, "M1_PER": 18, "M1_ANO": 20,
        "M1_AG2": 22, "M1_KAT": 24, "M1_AGI": 25, "M1_ATT": 27, "M1_VIC": 30,
        "M1_OMO": 32, "M1_MON": 35, "M1_THI": 36, "M1_PET": 39, "M1_TAV": 41,
        "M1_KAL": 42, "M1_MOS": 45, "M1_FAL": 48, "M1_PIR": 51,
    },
    ("M2", "outbound"): {
        "M2_ANT": 0, "M2_PER": 1, "M2_AGI": 2, "M2_SEP": 4, "M2_ATT": 6,
        "M2_STA": 8, "M2_MET": 9, "M2_OMO": 10, "M2_PAN": 12, "M2_SYN": 14,
        "M2_AKR": 16, "M2_SY2": 17, "M2_NEO": 19, "M2_AG2": 20, "M2_DAF": 22,
        "M2_AG3": 24, "M2_ILI": 26, "M2_ALI": 28, "M2_ARG": 30, "M2_ELL": 32,
    },
    ("M2", "inbound"): {
        "M2_ELL": 0, "M2_ARG": 1, "M2_ALI": 4, "M2_ILI": 6, "M2_AG3": 7,
        "M2_DAF": 9, "M2_AG2": 11, "M2_NEO": 12, "M2_SY2": 14, "M2_AKR": 15,
        "M2_SYN": 17, "M2_PAN": 19, "M2_OMO": 20, "M2_MET": 22, "M2_STA": 23,
        "M2_ATT": 25, "M2_SEP": 26, "M2_AGI": 29, "M2_PER": 31, "M2_ANT": 32,
    },
    # M3 city service: Dim. Theatro ↔ Doukissis Plakentias (44 min). Out
    # offsets identical to M3_AIR outbound up to DPL; inbound origin is
    # DPL so SYN sits at minute 20, not 40.
    ("M3", "outbound"): {
        "M3_DIM": 0, "M3_PIR": 2, "M3_MAN": 4, "M3_NIK": 6, "M3_KOR": 8,
        "M3_AGI": 10, "M3_AG2": 12, "M3_EGA": 14, "M3_ELE": 17, "M3_KER": 20,
        "M3_MON": 22, "M3_SYN": 24, "M3_EVA": 26, "M3_MEG": 27, "M3_AMB": 29,
        "M3_PAN": 31, "M3_KAT": 33, "M3_ETH": 35, "M3_CHO": 37, "M3_NOM": 39,
        "M3_AG3": 40, "M3_CHA": 42, "M3_DOY": 44,
    },
    ("M3", "inbound"): {
        "M3_DOY": 0, "M3_CHA": 2, "M3_AG3": 4, "M3_NOM": 5, "M3_CHO": 7,
        "M3_ETH": 9, "M3_KAT": 11, "M3_PAN": 13, "M3_AMB": 15, "M3_MEG": 17,
        "M3_EVA": 18, "M3_SYN": 20, "M3_MON": 22, "M3_KER": 24, "M3_ELE": 27,
        "M3_EGA": 30, "M3_AG2": 32, "M3_AGI": 34, "M3_KOR": 36, "M3_NIK": 38,
        "M3_MAN": 40, "M3_PIR": 42, "M3_DIM": 44,
    },
    # M3_AIR is the full airport-route train. Physically each airport train
    # IS one of the M3 city trains continuing past Doukissis Plakentias all
    # the way to the Airport (1 physical train per slot, not 2). To render
    # the airport-bound train at every station it passes through (not only
    # past DPL), offsets cover the whole route. The projector's _dedupe()
    # collapses the simultaneous M3 city row at minute X in favor of the
    # M3_AIR row at the same minute, so users at e.g. Syntagma see "Airport"
    # on the right minutes and "Doukissis Plakentias" on the rest.
    # Outbound: Dim. Theatro -> Airport (65 min). Inbound: Airport ->
    # Dim. Theatro (62 min). Numbers traced from the STASY PDFs
    # AIRPORT-TRAIN-SCHEDULES-from-Dim_Theatro-to-Airport_valid-from-24-6-24
    # and ...-from-Airport-to-Dimotiko-Theatro_valid-from-24-6-24 (train 332
    # / train 331 respectively, the earliest run of the day so headway-drift
    # to later trips is bounded by ±1 minute).
    ("M3_AIR", "outbound"): {
        "M3_DIM": 0, "M3_PIR": 1, "M3_MAN": 3, "M3_NIK": 5, "M3_KOR": 7,
        "M3_AGI": 9, "M3_AG2": 11, "M3_EGA": 13, "M3_ELE": 16, "M3_KER": 19,
        "M3_MON": 21, "M3_SYN": 23, "M3_EVA": 25, "M3_MEG": 26, "M3_AMB": 28,
        "M3_PAN": 30, "M3_KAT": 32, "M3_ETH": 34, "M3_CHO": 36, "M3_NOM": 38,
        "M3_AG3": 39, "M3_CHA": 41, "M3_DOY": 44, "M3_PAL": 50, "M3_PEA": 53,
        "M3_KO2": 59, "M3_AER": 65,
    },
    ("M3_AIR", "inbound"): {
        "M3_AER": 0, "M3_KO2": 5, "M3_PEA": 11, "M3_PAL": 13, "M3_DOY": 20,
        "M3_CHA": 21, "M3_AG3": 23, "M3_NOM": 25, "M3_CHO": 26, "M3_ETH": 28,
        "M3_KAT": 30, "M3_PAN": 32, "M3_AMB": 34, "M3_MEG": 36, "M3_EVA": 37,
        "M3_SYN": 39, "M3_MON": 41, "M3_KER": 43, "M3_ELE": 46, "M3_EGA": 48,
        "M3_AG2": 51, "M3_AGI": 53, "M3_KOR": 55, "M3_NIK": 57, "M3_MAN": 59,
        "M3_PIR": 61, "M3_DIM": 62,
    },
    ("T6", "outbound"): {
        "T6_SYN": 0, "T6_ZAP": 2, "T6_VOU": 5, "T6_FIX": 7, "T6_KAS": 9,
        "T6_NEO": 11, "T6_BAK": 13, "T6_AEG": 15, "T6_AGI": 17, "T6_ALE": 19,
        "T6_AGH": 21, "T6_MED": 22, "T6_EVA": 23, "T6_ACH": 24, "T6_AMF": 26,
        "T6_PAN": 27, "T6_MOU": 29, "T6_EDE": 32, "T6_PIK": 33,
    },
    ("T6", "inbound"): {
        "T6_PIK": 0, "T6_EDE": 2, "T6_MOU": 5, "T6_PAN": 7, "T6_AMF": 9,
        "T6_ACH": 10, "T6_EVA": 12, "T6_MED": 13, "T6_AGH": 15, "T6_ALE": 16,
        "T6_AGI": 18, "T6_AEG": 21, "T6_BAK": 23, "T6_NEO": 25, "T6_KAS": 27,
        "T6_FIX": 29, "T6_VOU": 30, "T6_ZAP": 32, "T6_SYN": 35,
    },
    ("T7", "outbound"): {
        "T7_AKT": 0, "T7_AGI": 2, "T7_PL2": 4, "T7_SYN": 5, "T7_AND": 7,
        "T7_OMI": 8, "T7_PEA": 9, "T7_NEO": 11, "T7_MOS": 12, "T7_KAL": 13,
        "T7_TZI": 14, "T7_DEL": 15, "T7_AGH": 17, "T7_TRO": 18, "T7_PAR": 20,
        "T7_FLI": 21, "T7_BAT": 23, "T7_EDE": 25, "T7_PIK": 26, "T7_MAR": 28,
        "T7_KA2": 29, "T7_ZEF": 31, "T7_LOU": 32, "T7_ELL": 32, "T7_STA": 33,
        "T7_NDA": 35, "T7_AG2": 36, "T7_EL2": 37, "T7_KEN": 39, "T7_PL3": 40,
        "T7_PA2": 42, "T7_PAL": 44, "T7_PL4": 46, "T7_AG3": 47, "T7_PL5": 49,
        "T7_KOL": 51, "T7_ASK": 54,
    },
    ("T7", "inbound"): {
        "T7_ASK": 0, "T7_KOL": 2, "T7_PL5": 4, "T7_AG3": 5, "T7_PL4": 7,
        "T7_PAL": 9, "T7_PA2": 11, "T7_PL3": 12, "T7_KEN": 13, "T7_EL2": 14,
        "T7_AG2": 15, "T7_NDA": 17, "T7_STA": 18, "T7_ELL": 18, "T7_LOU": 19,
        "T7_ZEF": 21, "T7_KA2": 22, "T7_MAR": 24, "T7_PIK": 25, "T7_EDE": 27,
        "T7_BAT": 29, "T7_FLI": 30, "T7_PAR": 31, "T7_TRO": 33, "T7_AGH": 34,
        "T7_DEL": 35, "T7_TZI": 36, "T7_KAL": 37, "T7_MOS": 38, "T7_NEO": 40,
        "T7_GIP": 42, "T7_MIK": 44, "T7_GRI": 48, "T7_EVA": 51, "T7_PLA": 53,
        "T7_DIM": 55, "T7_AKT": 59,
    },
}


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


def _apply_stasy_overrides(
    stops: list[dict], line_id: str, direction: str
) -> list[dict]:
    """Replace haversine-prorated minutes with STASY's published per-station
    minutes whenever we have them. For M3 specifically the city service
    terminates at Doukissis Plakentias, so we DROP any stop past the table
    (Pallini / Kantza / Koropi / Airport) rather than letting the haversine
    prorate them — those stops are served only by M3_AIR trains."""
    overrides = STASY_OFFSETS.get((line_id, direction))
    if not overrides:
        return stops
    result: list[dict] = []
    for stop in stops:
        sid = stop["station_id"]
        if sid in overrides:
            result.append({**stop, "minutes_from_origin": overrides[sid]})
        elif line_id == "M3":
            # M3 city service: skip airport-extension stops.
            continue
        else:
            result.append(stop)
    return result


def _stasy_only_stops(
    conn: sqlite3.Connection, table: dict[str, int]
) -> list[dict]:
    """Build a stop list directly from a STASY override table. Used for
    synthetic lines (M3_AIR) that don't have their own line_stations
    rows — they ride on another line's track."""
    name_lookup = {r["id"]: r["name_en"] for r in conn.execute(
        "SELECT id, name_en FROM stations"
    ).fetchall()}
    ordered = sorted(table.items(), key=lambda kv: kv[1])
    return [
        {
            "station_id": sid,
            "stop_sequence": i,
            "minutes_from_origin": minutes,
            "station_en": name_lookup.get(sid, ""),
        }
        for i, (sid, minutes) in enumerate(ordered)
    ]


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
            stops = _apply_stasy_overrides(stops, line_id, direction)
            upsert(conn, line_id, direction, stops)
            total += len(stops)
            print(f"  {line_id}/{direction}: {len(stops)} stops over {runtime} min")
        # Synthetic / extension lines (M3_AIR) that share track but need
        # their own origin reference. Seeded purely from STASY_OFFSETS.
        for (line_id, direction), table in STASY_OFFSETS.items():
            if (line_id, direction, ...) in [(r[0], r[1], ...) for r in RUNTIMES]:
                continue
            if any(r[0] == line_id and r[1] == direction for r in RUNTIMES):
                continue
            stops = _stasy_only_stops(conn, table)
            if not stops:
                continue
            upsert(conn, line_id, direction, stops)
            total += len(stops)
            print(f"  {line_id}/{direction} (STASY-only): {len(stops)} stops")
        print(f"seeded {total} station offsets")


if __name__ == "__main__":
    main()
