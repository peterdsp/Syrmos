"""Seed Thessaloniki airport <-> metro shuttle buses into the schedules DB.

Makedonia Airport (SKG) connects to the metro network via OASTH/OSETH buses:
  X3  Airport <-> Mikra metro  -- NEW, launched 2026-08-27 alongside the Line 2
      (TM2) Kalamaria extension; ~10 min, EUR 2, year-round shuttle. Mikra is
      the new TM2 terminus and the airport interchange.
  2X  Airport <-> Nea Elvetia metro transfer station.
(1X/1N to the city centre + railway station + KTEL, and 79 to the IKEA eastern
bus station, also serve the airport but are not metro connectors, so they are
left out of the rail companion for now.)

These carry mode='bus' with no per-stop timetable -- like the KP1/PU2 connector
buses -- so the app surfaces the airport<->metro link and defers the schedule to
the operator rather than inventing times. Idempotent.

Run: cd ~/syrmos-api && .venv/bin/python -m scripts.seed_thessaloniki_airport_buses
"""
from __future__ import annotations

import sqlite3

from syrmos_admin import db as dbmod

REGION = "thessaloniki"
BUS_COLOR = "#0E7490"

# Makedonia Airport (SKG) terminal-front bus stop.
AIRPORT = ("THS_AIR", "Makedonia Airport", "Αεροδρόμιο «Μακεδονία»", 40.51972, 22.97083)

# Metro interchange stations already seeded by scripts.seed_thessaloniki.
MIKRA = "TM2_MIK"        # Mikra (TM2 terminus)
NEA_ELVETIA = "TM1_NEL"  # Nea Elvetia (TM1 terminus)

# (id, name_en, name_el, terminal_a, terminal_b, metro_station_id)
LINES = [
    ("X3", "X3 Airport – Mikra Metro", "Χ3 Αεροδρόμιο – Μετρό Μίκρα",
     "Makedonia Airport", "Mikra", MIKRA),
    ("2X", "2X Airport – Nea Elvetia Metro", "2Χ Αεροδρόμιο – Μετρό Νέα Ελβετία",
     "Makedonia Airport", "Nea Elvetia", NEA_ELVETIA),
]


def main() -> None:
    conn = dbmod.connect()
    dbmod.migrate(conn)
    cur = conn.cursor()
    cur.execute("BEGIN")
    try:
        conn.execute(
            "INSERT INTO stations(id, name_en, name_el, lat, lng, region)"
            " VALUES(?,?,?,?,?,?)"
            " ON CONFLICT(id) DO UPDATE SET name_en=excluded.name_en,"
            " name_el=excluded.name_el, lat=excluded.lat, lng=excluded.lng,"
            " region=excluded.region",
            (AIRPORT[0], AIRPORT[1], AIRPORT[2], AIRPORT[3], AIRPORT[4], REGION),
        )
        for i, (lid, en, el, ta, tb, metro_id) in enumerate(LINES):
            conn.execute(
                "INSERT INTO lines(id, mode, name_en, name_el, color, terminal_a,"
                " terminal_b, sort_order, region, status) VALUES(?,?,?,?,?,?,?,?,?,?)"
                " ON CONFLICT(id) DO UPDATE SET mode=excluded.mode,"
                " name_en=excluded.name_en, name_el=excluded.name_el,"
                " color=excluded.color, terminal_a=excluded.terminal_a,"
                " terminal_b=excluded.terminal_b, sort_order=excluded.sort_order,"
                " region=excluded.region, status=excluded.status",
                (lid, "bus", en, el, BUS_COLOR, ta, tb, 60 + i, REGION, "operational"),
            )
            conn.execute("DELETE FROM line_stations WHERE line_id=?", (lid,))
            conn.executemany(
                "INSERT INTO line_stations(line_id, station_id, seq, direction)"
                " VALUES(?,?,?,?)",
                [(lid, AIRPORT[0], 1, "both"), (lid, metro_id, 2, "both")],
            )
        cur.execute("COMMIT")
    except Exception:
        cur.execute("ROLLBACK")
        raise

    print("seeded airport buses:", [l[0] for l in LINES])
    n = conn.execute(
        "SELECT COUNT(*) FROM lines WHERE mode='bus' AND region='thessaloniki'"
    ).fetchone()[0]
    print("thessaloniki bus lines:", n)


if __name__ == "__main__":
    main()
