"""Load the official metro + tram operating schedule into the syrmos-api DB.

Source of truth: athens_fixed_rail_station_coordinates.md (the master
reference the user maintains). This script translates the "Operating
Schedule Reference" section into schedule_rules + frequency_bands + holiday
rows so the projector serves correct minute-by-minute output.

Lines covered: M1, M2, M3 (city), M3_AIR (airport branch), T6, T7.

Run on the Pi against the live DB:
    SYRMOS_DB_PATH=/home/peterdsp/syrmos-api/db/syrmos.db \\
    python3 scripts/ingest-master-schedule.py
"""
from __future__ import annotations

import os
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB_PATH = os.environ.get(
    "SYRMOS_DB_PATH",
    str(ROOT / "ops" / "syrmos-api" / "data" / "syrmos.db"),
)

# (line_id, day_type, open_time, close_time, is_24_7, notes)
SCHEDULE_RULES = [
    # M1 Green: 05:00-00:30 every day. Last from terminals 00:15, from Omonia 00:30.
    ("M1", "mon_thu", "05:00", "00:30", 0, "Last from terminals 00:15"),
    ("M1", "fri",     "05:00", "00:30", 0, "Last from terminals 00:15"),
    ("M1", "sat",     "05:00", "00:30", 0, "Last from terminals 00:15"),
    ("M1", "sun",     "05:00", "00:30", 0, "Last from terminals 00:15"),
    # M2 Red: mon-thu 05:30-00:30, fri extended to 02:00, sat 24/7, sun 05:30-00:30.
    ("M2", "mon_thu", "05:30", "00:30", 0, "Last departures 00:03 from Elliniko, 00:06 from Anthoupoli"),
    ("M2", "fri",     "05:30", "02:00", 0, "Friday late: last 01:40 Elliniko, 01:43 Anthoupoli"),
    ("M2", "sat",     "00:00", "23:59", 1, "Saturday 24/7 service"),
    ("M2", "sun",     "05:30", "00:30", 0, "Last 00:03 from Elliniko, 00:06 from Anthoupoli"),
    # M3 City (Dimotiko Theatro - Doukissis Plakentias): same pattern as M2.
    ("M3", "mon_thu", "05:30", "00:30", 0, "Last 00:03 from Piraeus and Doukissis Plakentias"),
    ("M3", "fri",     "05:30", "02:00", 0, "Last 01:40 from Piraeus and Doukissis Plakentias"),
    ("M3", "sat",     "00:00", "23:59", 1, "Saturday 24/7 service, city route only"),
    ("M3", "sun",     "05:30", "00:30", 0, "Last 00:03 from Piraeus and Doukissis Plakentias"),
    # M3 Airport branch: 05:30-23:00 strict every day. No late extensions, no 24/7.
    ("M3_AIR", "mon_thu", "05:30", "23:00", 0, "Strict 23:00 cutoff, no late extensions"),
    ("M3_AIR", "fri",     "05:30", "23:00", 0, "Strict 23:00 cutoff, no Friday extension"),
    ("M3_AIR", "sat",     "05:30", "23:00", 0, "Strict 23:00 cutoff, no 24/7 overnight"),
    ("M3_AIR", "sun",     "05:30", "23:00", 0, "Strict 23:00 cutoff"),
    # Tram T6/T7: mon-thu 05:30-01:00, fri 05:00-01:30, sat 24/7, sun 05:30-01:00.
    ("T6", "mon_thu", "05:30", "01:00", 0, "Last arrival exactly at 01:00"),
    ("T6", "fri",     "05:00", "01:30", 0, "Friday extension to 01:30"),
    ("T6", "sat",     "00:00", "23:59", 1, "Saturday 24/7 service"),
    ("T6", "sun",     "05:30", "01:00", 0, "Last arrival at 01:00"),
    ("T7", "mon_thu", "05:30", "01:00", 0, "Last arrival exactly at 01:00"),
    ("T7", "fri",     "05:00", "01:30", 0, "Friday extension to 01:30"),
    ("T7", "sat",     "00:00", "23:59", 1, "Saturday 24/7 service"),
    ("T7", "sun",     "05:30", "01:00", 0, "Last arrival at 01:00"),
]

# Per-line headway maps. Each entry is (day_type, time_start, time_end, headway_minutes, label).
# Numbers come straight from the "Minute-by-Minute Frequencies" section of the master.
FREQUENCY_BANDS = []

# Weekday template: morning peak, midday off-peak, evening peak, night, late wind-down.
def add_weekday_bands(line_id: str, headways: dict[str, float]) -> None:
    """`headways` keys: peak_am, midday, peak_pm, night, late."""
    for day_type in ("mon_thu", "fri"):
        FREQUENCY_BANDS.extend([
            (line_id, day_type, "05:00", "07:00", headways.get("early", headways["night"]), "early_morning"),
            (line_id, day_type, "07:00", "10:00", headways["peak_am"], "morning_peak"),
            (line_id, day_type, "10:00", "17:00", headways["midday"],  "midday_offpeak"),
            (line_id, day_type, "17:00", "20:00", headways["peak_pm"], "evening_peak"),
            (line_id, day_type, "20:00", "22:30", headways["night"],   "night_offpeak"),
            (line_id, day_type, "22:30", "00:30", headways["late"],    "late_night"),
        ])
    # Friday late extension 00:30 - 02:00 fixed at 15 min for city lines.
    if line_id in ("M1", "M2", "M3", "T6", "T7"):
        FREQUENCY_BANDS.append((line_id, "fri", "00:30", "02:00", 15.0, "friday_extension"))


# M1: peak 6, midday 8, evening 6, night 10, late 15.
add_weekday_bands("M1", {"early": 8, "peak_am": 6, "midday": 8, "peak_pm": 6, "night": 10, "late": 15})
# M2: peak 4, midday 6, evening 4.5, night 10, late 15.
add_weekday_bands("M2", {"early": 6, "peak_am": 4, "midday": 6, "peak_pm": 4.5, "night": 10, "late": 15})
# M3 city: peak 4, midday 6, evening 4.5, night 10, late 15.
add_weekday_bands("M3", {"early": 6, "peak_am": 4, "midday": 6, "peak_pm": 4.5, "night": 10, "late": 15})
# T6/T7: peak 10, midday 12, evening 10, night 15.
add_weekday_bands("T6", {"early": 12, "peak_am": 10, "midday": 12, "peak_pm": 10, "night": 15, "late": 15})
add_weekday_bands("T7", {"early": 12, "peak_am": 10, "midday": 12, "peak_pm": 10, "night": 15, "late": 15})

# M3 Airport: fixed 36 min interval every day, 05:30-23:00.
for day_type in ("mon_thu", "fri", "sat", "sun"):
    FREQUENCY_BANDS.append(("M3_AIR", day_type, "05:30", "23:00", 36.0, "airport_fixed"))

# Saturday daytime bands (city lines): 7-10.5 min, we model as 9 min.
for line_id, head in [("M1", 9.0), ("M2", 7.5), ("M3", 7.5), ("T6", 12.0), ("T7", 12.0)]:
    FREQUENCY_BANDS.append((line_id, "sat", "05:00", "00:30", head, "saturday_daytime"))
# Saturday 24/7 overnight band 00:30 - 05:30 for M2, M3, T6, T7 (NOT M1, NOT M3_AIR).
for line_id in ("M2", "M3", "T6", "T7"):
    FREQUENCY_BANDS.append((line_id, "sat", "00:30", "05:30", 15.0, "saturday_overnight"))

# Sunday: 10.5-15 min, modeled as 12 min for city lines, 15 for tram.
for line_id, head in [("M1", 12.0), ("M2", 12.0), ("M3", 12.0), ("T6", 15.0), ("T7", 15.0)]:
    FREQUENCY_BANDS.append((line_id, "sun", "05:00", "00:30", head, "sunday_flat"))

# Holiday day types: Sunday-style for default holidays; Saturday-style for bank holidays.
# Aug 15 = flat 12 min all day. Dec 24/31 = 22:00-22:20 cutoff.
HOLIDAY_DAY_TYPES = ["holiday", "bank_holiday", "aug_15", "dec_24_31"]
# holiday (Sunday-style) - copy Sun bands
for line_id, head in [("M1", 12.0), ("M2", 12.0), ("M3", 12.0), ("T6", 15.0), ("T7", 15.0)]:
    FREQUENCY_BANDS.append((line_id, "holiday", "05:30", "00:00", head, "holiday_sunday_style"))
FREQUENCY_BANDS.append(("M3_AIR", "holiday", "05:30", "23:00", 36.0, "airport_fixed"))
# bank_holiday (Saturday-style)
for line_id, head in [("M1", 9.0), ("M2", 7.5), ("M3", 7.5), ("T6", 12.0), ("T7", 12.0)]:
    FREQUENCY_BANDS.append((line_id, "bank_holiday", "05:30", "00:00", head, "bank_holiday_saturday_style"))
FREQUENCY_BANDS.append(("M3_AIR", "bank_holiday", "05:30", "23:00", 36.0, "airport_fixed"))
# aug_15 (flat 12 min all day except airport)
for line_id in ("M1", "M2", "M3", "T6", "T7"):
    FREQUENCY_BANDS.append((line_id, "aug_15", "05:30", "00:00", 12.0, "aug_15_flat"))
FREQUENCY_BANDS.append(("M3_AIR", "aug_15", "05:30", "23:00", 36.0, "airport_fixed"))
# dec_24_31 (final 22:00-22:20 from terminals; we cap with a short band)
for line_id in ("M1", "M2", "M3", "T6", "T7"):
    FREQUENCY_BANDS.append((line_id, "dec_24_31", "05:30", "22:00", 12.0, "dec_24_31_daytime"))
FREQUENCY_BANDS.append(("M3_AIR", "dec_24_31", "05:30", "22:00", 36.0, "airport_fixed"))

# Holiday rules (date_pattern -> day_type)
HOLIDAY_RULES = [
    ("Christmas Day",         "12-25",      "holiday"),
    ("Boxing Day",            "12-26",      "holiday"),
    ("New Year",              "01-01",      "holiday"),
    ("Labor Day",             "05-01",      "holiday"),
    ("Ohi Day",               "10-28",      "holiday"),
    ("Clean Monday",          "clean_monday", "holiday"),
    ("Good Friday",           "easter-2",   "holiday"),
    ("Easter Monday",         "easter+1",   "holiday"),
    ("Assumption of Mary",    "08-15",      "aug_15"),
    ("Christmas Eve",         "12-24",      "dec_24_31"),
    ("New Year's Eve",        "12-31",      "dec_24_31"),
    ("Jan 2 Bank Holiday",    "01-02",      "bank_holiday"),
    ("Epiphany",              "01-06",      "bank_holiday"),
    ("School Holiday Nov 17", "11-17",      "bank_holiday"),
]

# schedule_rules also need an entry per holiday day_type per line, otherwise
# the projector falls back to mon_thu.
HOLIDAY_RULE_HOURS = {
    "holiday":      ("05:30", "00:30"),
    "bank_holiday": ("05:30", "00:30"),
    "aug_15":       ("05:30", "00:30"),
    "dec_24_31":    ("05:30", "22:00"),
}


def insert_holiday_rules(conn: sqlite3.Connection) -> None:
    for line_id in ("M1", "M2", "M3", "T6", "T7"):
        for day_type, (open_t, close_t) in HOLIDAY_RULE_HOURS.items():
            SCHEDULE_RULES.append((line_id, day_type, open_t, close_t, 0, f"{day_type} rule"))
    for day_type in HOLIDAY_RULE_HOURS:
        SCHEDULE_RULES.append(("M3_AIR", day_type, "05:30", "23:00", 0, "Airport strict cutoff regardless of holiday"))


def main() -> None:
    insert_holiday_rules(None)

    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA foreign_keys = ON")

    # Wipe and rewrite the metro + tram rules. We deliberately keep suburban
    # rules untouched because A1-A4 are PDF-driven via train_timestamps.
    metro_tram = ("M1", "M2", "M3", "M3_AIR", "T6", "T7")
    placeholders = ",".join("?" * len(metro_tram))
    conn.execute(f"DELETE FROM schedule_rules WHERE line_id IN ({placeholders})", metro_tram)
    conn.execute(f"DELETE FROM frequency_bands WHERE line_id IN ({placeholders})", metro_tram)
    conn.execute("DELETE FROM holiday_rules")

    for line_id, day_type, open_t, close_t, is_247, notes in SCHEDULE_RULES:
        conn.execute(
            "INSERT INTO schedule_rules (line_id, day_type, open_time, close_time, is_24_7, notes)"
            " VALUES (?, ?, ?, ?, ?, ?)",
            (line_id, day_type, open_t, close_t, is_247, notes),
        )

    for line_id, day_type, time_start, time_end, headway, label in FREQUENCY_BANDS:
        conn.execute(
            "INSERT INTO frequency_bands (line_id, day_type, time_start, time_end, headway_minutes, label)"
            " VALUES (?, ?, ?, ?, ?, ?)",
            (line_id, day_type, time_start, time_end, float(headway), label),
        )

    for name, pattern, day_type in HOLIDAY_RULES:
        conn.execute(
            "INSERT INTO holiday_rules (name, date_pattern, day_type) VALUES (?, ?, ?)",
            (name, pattern, day_type),
        )

    conn.commit()
    n_rules = conn.execute("SELECT COUNT(*) FROM schedule_rules WHERE line_id IN ({})".format(placeholders), metro_tram).fetchone()[0]
    n_bands = conn.execute("SELECT COUNT(*) FROM frequency_bands WHERE line_id IN ({})".format(placeholders), metro_tram).fetchone()[0]
    n_holidays = conn.execute("SELECT COUNT(*) FROM holiday_rules").fetchone()[0]
    print(f"schedule_rules: {n_rules} rows for metro + tram")
    print(f"frequency_bands: {n_bands} rows for metro + tram")
    print(f"holiday_rules: {n_holidays} rows")


if __name__ == "__main__":
    main()
