"""Seed the Thessaloniki metro (TM1, TM2) into the schedules DB.

Design: docs/plans/2026-07-15-thessaloniki-metro-design.md

Why this lives on the server and not in the bundled seeds: scripts/snapshot-api-to-seed.py
rmtree's and rewrites every bundled schedules-v2 directory from the live API, so
hand-authored bundle files are deleted on the next Athens refresh. The DB is the
source of truth; the bundles are a snapshot of it.

Data provenance:
  Stations, order, coordinates and colours come from the OSM route relations
  (TM1 outbound 6152448, route_master 7885089; TM2 7898294, route_master
  7898299), read from a Geofabrik Greece extract. NOT from prose station lists:
  the marketing copy we started from had Sintrivani and Panepistimio the wrong
  way round, and the coordinates plus the official line map both disagreed with
  it. Do not "correct" the order back.

  Frequency bands come from the Thessmetro FAQ. The metro is driverless with
  dynamically adjusted headways and publishes no per-train timetable, so it runs
  on the frequency-band path like the Athens metro and tram, not the
  scheduled-trips path used for suburban lines.

TM2 (Kalamaria extension) is seeded status='operational': it opened to the public
on 27 August 2026 (Nomarchia, Kalamaria, Aretsou, Nea Krini, Mikra). It renders
solid in its official Line 2 blue (#0070FF) and carries bands + offsets like TM1.

Suburban (TP1 Larisa, TP2 Edessa/Florina, TP3 Sindos, TP4 Serres-Drama) is NOT
here yet. It uses the scheduled-trips path and its per-stop times come from
docs/plans/greece_passenger_rail_timetables_2026-07-16.pdf.

Idempotent: re-running replaces the TM rows and leaves Athens untouched.

Run: cd ~/syrmos-api && .venv/bin/python -m scripts.seed_thessaloniki
"""
from __future__ import annotations

import math
import sqlite3

from syrmos_admin import db as dbmod

REGION = "thessaloniki"

# --- stations -------------------------------------------------------------
# (id, name_en, name_el, lat, lng). Order is the OSM relation's order.
TM1_STATIONS = [
    ("TM1_NSS", "New Railway Station", "Νέος Σιδηροδρομικός Σταθμός", 40.643918, 22.928711),
    ("TM1_DIM", "Dimokratias", "Δημοκρατίας", 40.640941, 22.934419),
    ("TM1_VEN", "Venizelou", "Βενιζέλου", 40.636941, 22.942086),
    ("TM1_AGS", "Agia Sofia", "Αγίας Σοφίας", 40.634616, 22.946429),
    ("TM1_SYN", "Sintrivani", "Συντριβάνι", 40.630756, 22.954161),
    ("TM1_PAN", "Panepistimio", "Πανεπιστήμιο", 40.626254, 22.960527),
    ("TM1_PAP", "Papafi", "Παπάφη", 40.619708, 22.962974),
    ("TM1_EFK", "Efkleidis", "Ευκλείδης", 40.615873, 22.960412),
    ("TM1_FLE", "Fleming", "Φλέμινγκ", 40.611983, 22.957280),
    ("TM1_ANA", "Analipsi", "Ανάληψη", 40.606199, 22.957800),
    ("TM1_25M", "25is Martiou", "25ης Μαρτίου", 40.601083, 22.958473),
    ("TM1_VOU", "Voulgari", "Βούλγαρη", 40.595024, 22.960612),
    ("TM1_NEL", "Nea Elvetia", "Νέα Ελβετία", 40.593231, 22.968863),
]

# TM2's own stations. It shares TM1_NSS..TM1_25M with TM1 and branches after
# 25is Martiou, serving neither Voulgari nor Nea Elvetia. The shared stops keep
# their TM1_* ids: there is one physical Syntrivani, not two.
TM2_OWN_STATIONS = [
    ("TM2_NOM", "Nomarchia", "Νομαρχία", 40.591653, 22.957086),
    ("TM2_KAL", "Kalamaria", "Καλαμαριά", 40.584718, 22.953047),
    ("TM2_ARE", "Aretsou", "Αρετσού", 40.578375, 22.954404),
    ("TM2_NKR", "Nea Krini", "Νέα Κρήνη", 40.571903, 22.961363),
    ("TM2_MIK", "Mikra", "Μίκρα", 40.567590, 22.966672),
]

TM2_SHARED_PREFIX = [s[0] for s in TM1_STATIONS[:11]]  # NSS .. 25is Martiou

# --- lines ----------------------------------------------------------------
# (id, mode, name_en, name_el, color, terminal_a, terminal_b, sort_order, status)
LINES = [
    ("TM1", "metro", "Line 1", "Γραμμή 1", "#FF0000",
     "New Railway Station", "Nea Elvetia", 20, "operational"),
    ("TM2", "metro", "Line 2", "Γραμμή 2", "#0070FF",
     "New Railway Station", "Mikra", 21, "operational"),
]

# --- schedule -------------------------------------------------------------
# Thessmetro FAQ: daily 05:15-23:00. Closes before midnight, so unlike the
# Athens lines there is no past-midnight wrap to model.
OPEN_TIME, CLOSE_TIME = "05:15", "23:00"

# Mon-Sat share one profile; Sunday differs. The app's day types are
# mon_thu | fri | sat | sun, so Mon-Sat maps onto the first three.
MON_SAT_BANDS = [
    ("05:15", "07:30", 5.0, "early"),
    ("07:30", "21:30", 3.5, "daytime"),
    ("21:30", "23:00", 4.5, "late"),
]
SUN_BANDS = [
    ("05:15", "12:30", 5.0, "early"),
    ("12:30", "21:30", 4.0, "daytime"),
    ("21:30", "23:00", 4.5, "late"),
]
DAY_TYPES_MON_SAT = ("mon_thu", "fri", "sat")

# End-to-end run time. The official figure is ~18.5 min over 9.5 km for the
# 13 stations. Offsets below are distributed along real inter-station distance
# rather than evenly, so central stops (which are close together) do not get the
# same spacing as the wider gaps toward Nea Elvetia.
TM1_RUNTIME_MIN = 18.5
DWELL_SECONDS = 20

# TM2's ordered stations: the shared TM1 trunk (NSS..25is Martiou) then the
# Kalamaria branch. Used for distance-derived offsets; TM2's runtime is scaled
# from TM1's pace over TM2's own length (see seed_offsets), since the operator
# publishes no per-train timetable for the driverless metro.
TM2_STATIONS = TM1_STATIONS[:11] + TM2_OWN_STATIONS


def haversine_m(a: tuple[float, float], b: tuple[float, float]) -> float:
    r = 6371000.0
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dp = math.radians(b[0] - a[0])
    dl = math.radians(b[1] - a[1])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def distance_offsets(stations, runtime_min: float) -> list[int]:
    """Cumulative minutes from the origin, distributed by inter-station distance.

    Dwell is charged per intermediate stop and the remaining time is split in
    proportion to distance travelled, so the terminals are exact (0 and
    runtime) and the middle stops are plausible rather than uniform.
    """
    pts = [(s[3], s[4]) for s in stations]
    legs = [haversine_m(pts[i], pts[i + 1]) for i in range(len(pts) - 1)]
    total_m = sum(legs)
    dwell_total_min = (len(stations) - 2) * DWELL_SECONDS / 60.0
    moving_min = max(runtime_min - dwell_total_min, 0.1)

    out = [0]
    acc_m = 0.0
    for i, leg in enumerate(legs):
        acc_m += leg
        moving = moving_min * (acc_m / total_m)
        dwell = min(i + 1, len(stations) - 2) * DWELL_SECONDS / 60.0
        out.append(int(round(moving + dwell)))
    out[-1] = int(round(runtime_min))
    return out


def seed_stations(conn: sqlite3.Connection) -> int:
    rows = [(sid, en, el, lat, lng, REGION)
            for (sid, en, el, lat, lng) in TM1_STATIONS + TM2_OWN_STATIONS]
    conn.executemany(
        "INSERT INTO stations(id, name_en, name_el, lat, lng, region)"
        " VALUES(?,?,?,?,?,?)"
        " ON CONFLICT(id) DO UPDATE SET"
        " name_en=excluded.name_en, name_el=excluded.name_el,"
        " lat=excluded.lat, lng=excluded.lng, region=excluded.region",
        rows,
    )
    return len(rows)


def seed_lines(conn: sqlite3.Connection) -> int:
    rows = [(lid, mode, en, el, color, ta, tb, order_, REGION, status)
            for (lid, mode, en, el, color, ta, tb, order_, status) in LINES]
    conn.executemany(
        "INSERT INTO lines(id, mode, name_en, name_el, color, terminal_a,"
        " terminal_b, sort_order, region, status) VALUES(?,?,?,?,?,?,?,?,?,?)"
        " ON CONFLICT(id) DO UPDATE SET"
        " mode=excluded.mode, name_en=excluded.name_en, name_el=excluded.name_el,"
        " color=excluded.color, terminal_a=excluded.terminal_a,"
        " terminal_b=excluded.terminal_b, sort_order=excluded.sort_order,"
        " region=excluded.region, status=excluded.status",
        rows,
    )
    return len(rows)


def seed_line_stations(conn: sqlite3.Connection) -> int:
    conn.execute("DELETE FROM line_stations WHERE line_id IN ('TM1','TM2')")
    rows = []
    for seq, s in enumerate(TM1_STATIONS, start=1):
        rows.append(("TM1", s[0], seq, "both"))
    tm2_ids = TM2_SHARED_PREFIX + [s[0] for s in TM2_OWN_STATIONS]
    for seq, sid in enumerate(tm2_ids, start=1):
        rows.append(("TM2", sid, seq, "both"))
    conn.executemany(
        "INSERT INTO line_stations(line_id, station_id, seq, direction)"
        " VALUES(?,?,?,?)",
        rows,
    )
    return len(rows)


def seed_schedule(conn: sqlite3.Connection) -> tuple[int, int]:
    """Rules + frequency bands for both driverless metro lines.

    TM1 and TM2 are the same driverless system on the same Thessmetro FAQ, so
    they share the open/close window and headway bands (each line's own trains
    run on that headway; the shared trunk simply carries both).
    """
    conn.execute("DELETE FROM schedule_rules WHERE line_id IN ('TM1','TM2')")
    conn.execute("DELETE FROM frequency_bands WHERE line_id IN ('TM1','TM2')")

    rules, bands = [], []
    for line_id in ("TM1", "TM2"):
        for dt in DAY_TYPES_MON_SAT + ("sun",):
            rules.append((line_id, dt, OPEN_TIME, CLOSE_TIME, 0,
                          "Thessmetro FAQ; driverless, dynamic headway"))
        for dt in DAY_TYPES_MON_SAT:
            for (start, end, headway, label) in MON_SAT_BANDS:
                bands.append((line_id, dt, start, end, headway, label, "both"))
        for (start, end, headway, label) in SUN_BANDS:
            bands.append((line_id, "sun", start, end, headway, label, "both"))
    conn.executemany(
        "INSERT INTO schedule_rules(line_id, day_type, open_time, close_time,"
        " is_24_7, notes) VALUES(?,?,?,?,?,?)",
        rules,
    )
    conn.executemany(
        "INSERT INTO frequency_bands(line_id, day_type, time_start, time_end,"
        " headway_minutes, label, direction) VALUES(?,?,?,?,?,?,?)",
        bands,
    )
    return len(rules), len(bands)


def seed_offsets(conn: sqlite3.Connection) -> int:
    """Per-station offsets for both metro lines, both directions, distance-derived.

    Symmetric by direction: a driverless metro runs the same profile each way,
    unlike the Athens suburban lines whose inbound and outbound differ. TM2's
    end-to-end runtime is scaled from TM1's pace (min per metre) over TM2's own
    length, since the operator publishes no per-train timetable.
    """
    conn.execute("DELETE FROM station_offsets WHERE line_id IN ('TM1','TM2')")

    def route_length_m(stns) -> float:
        pts = [(s[3], s[4]) for s in stns]
        return sum(haversine_m(pts[i], pts[i + 1]) for i in range(len(pts) - 1))

    pace_min_per_m = TM1_RUNTIME_MIN / route_length_m(TM1_STATIONS)
    tm2_runtime = round(pace_min_per_m * route_length_m(TM2_STATIONS), 1)
    specs = [
        ("TM1", TM1_STATIONS, TM1_RUNTIME_MIN),
        ("TM2", TM2_STATIONS, tm2_runtime),
    ]

    rows = []
    for line_id, line_stations_list, runtime in specs:
        origin_en = line_stations_list[0][1]
        dest_en = line_stations_list[-1][1]
        for direction in ("outbound", "inbound"):
            stations = line_stations_list if direction == "outbound" else list(reversed(line_stations_list))
            o_en = origin_en if direction == "outbound" else dest_en
            d_en = dest_en if direction == "outbound" else origin_en
            offsets = distance_offsets(stations, runtime)
            for seq, (station, mins) in enumerate(zip(stations, offsets)):
                rows.append((line_id, direction, o_en, d_en, seq, station[1], station[0],
                             mins, "thessmetro-distance"))
    conn.executemany(
        "INSERT INTO station_offsets(line_id, direction, origin, destination,"
        " stop_sequence, station_en, station_id, minutes_from_origin, source)"
        " VALUES(?,?,?,?,?,?,?,?,?)",
        rows,
    )
    return len(rows)


def main() -> None:
    conn = dbmod.connect()
    dbmod.migrate(conn)
    cur = conn.cursor()
    cur.execute("BEGIN")
    try:
        n_st = seed_stations(conn)
        n_ln = seed_lines(conn)
        n_ls = seed_line_stations(conn)
        n_rules, n_bands = seed_schedule(conn)
        n_off = seed_offsets(conn)
        cur.execute("COMMIT")
    except Exception:
        cur.execute("ROLLBACK")
        raise

    print(f"stations:      {n_st}")
    print(f"lines:         {n_ln} (TM1 + TM2 operational)")
    print(f"line_stations: {n_ls}")
    print(f"schedule_rules:{n_rules}")
    print(f"bands:         {n_bands} (TM1 + TM2, driverless FAQ headways)")
    print(f"offsets:       {n_off} (TM1 + TM2, distance-derived)")

    athens = conn.execute(
        "SELECT COUNT(*) FROM lines WHERE region='athens'").fetchone()[0]
    thess = conn.execute(
        "SELECT COUNT(*) FROM lines WHERE region='thessaloniki'").fetchone()[0]
    print(f"lines by region: athens={athens} thessaloniki={thess}")


if __name__ == "__main__":
    main()
