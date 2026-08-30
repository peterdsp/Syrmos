"""Parse the Athens transit data package and seed the SQLite DB.

Source: /Users/p.dhespollari/Desktop/athens_transit_icons_and_rules_package.zip
        (RULES.md + athens_fixed_rail_station_coordinates.md)

Usage:
    python3 -m scripts.import_athens_package --dry-run
    python3 -m scripts.import_athens_package --apply

The script is idempotent: re-running it overwrites the seed rows but leaves
admin-edited rows alone (none yet on first run). For now we wipe + insert in a
transaction so dry-runs and applies show the same result.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
from syrmos_admin import db as dbmod  # noqa: E402
from syrmos_admin.schedule_invariants import ensure_saturday_overnight  # noqa: E402

PACKAGE_DIR = Path(os.environ.get(
    "SYRMOS_PACKAGE_DIR",
    str(ROOT / "pkg"),
))
COORD_MD = PACKAGE_DIR / "athens_fixed_rail_station_coordinates.md"

# Brand colors. M1/M2/M3 match the existing lines.json. Others are the
# operator's standard palette for trams/suburban as documented by STASY/Hellenic Train.
LINE_COLORS = {
    "M1": "#00843D",
    "M2": "#E61E2A",
    "M3": "#0083C9",
    "T6": "#F39800",
    "T7": "#F39800",
    "A1": "#EE2625",
    "A2": "#EE2625",
    "A3": "#EE2625",
    "A4": "#EE2625",
}
LINE_NAMES_EL = {
    "M1": "Γραμμή 1",
    "M2": "Γραμμή 2",
    "M3": "Γραμμή 3",
    "T6": "Τραμ T6",
    "T7": "Τραμ T7",
    "A1": "Προαστιακός Α1",
    "A2": "Προαστιακός Α2",
    "A3": "Προαστιακός Α3",
    "A4": "Προαστιακός Α4",
}
MODE_LABEL = {"Metro": "metro", "Tram": "tram", "Suburban railway": "suburban"}


# Schedule reference, transcribed from the embedded master schedule in the package.
# Keeps the importer self-contained and reviewable.

WEEKLY_HOURS = [
    # (line_id, day_type, open, close, is_24_7, notes)
    # close = last origin departure (the later of the two directions), so
    # the projector never truncates an in-flight train; per-direction band
    # ends inside FREQUENCY_BANDS already enforce the real last departure
    # for each direction.
    #
    # M1 — Piraeus / Kifissia. STASY publishes a single LAST DEPARTURE
    # column for all days: 00:15 full route, 00:30 short-turn to Omonia.
    # close = 00:30 (latest train still leaving an origin).
    ("M1", "mon_thu", "05:00", "00:30", 0, None),
    ("M1", "fri",     "05:00", "00:30", 0, None),
    ("M1", "sat",     "05:00", "00:30", 0, None),
    ("M1", "sun",     "05:00", "00:30", 0, None),

    # M2 — Anthoupoli / Elliniko. STASY last origin Mon-Thu/Sun:
    # Anthoupoli 00:06, Elliniko 00:03 → close 00:06. Fri late extension:
    # Anthoupoli 01:43, Elliniko 01:40 → close 01:43. Sat 24-hour.
    ("M2", "mon_thu", "05:30", "00:06", 0, None),
    ("M2", "fri",     "05:30", "01:43", 0, "Friday late extension: last Anthoupoli 01:43"),
    ("M2", "sat",     "05:30", "05:28", 0, "Saturday 24h: city service extends overnight into Sunday 05:30 (OASA 24mmm https://www.oasa.gr/en/24mmm/, verified 2026-08-30)"),
    ("M2", "sun",     "05:30", "00:06", 0, None),

    # M3 — city service (Dim. Theatro / Doukissis Plakentias). Last origin
    # Mon-Thu/Sun: DT 23:57, DPL 00:01 → close 00:01. Fri: DT 01:34,
    # DPL 01:38 → close 01:38. Sat 24-hour (city only).
    ("M3", "mon_thu", "05:30", "00:01", 0, "City service; airport branch closes earlier (M3_AIR)"),
    ("M3", "fri",     "05:30", "01:38", 0, "Friday late extension: last DPL→DT 01:38"),
    ("M3", "sat",     "05:30", "05:28", 0, "Saturday 24h: city service extends overnight into Sunday 05:30; airport branch excluded (OASA 24mmm https://www.oasa.gr/en/24mmm/, verified 2026-08-30)"),
    ("M3", "sun",     "05:30", "00:01", 0, None),

    # M3_AIR — full airport route (Dim Theatro <-> Airport, 65 min). Excluded
    # from 24-hour Saturday and from Fri late-night extensions per STASY note.
    # First/last per the STASY PDFs (valid from 24-6-24):
    #   outbound (DT origin): first 05:30, last 22:54
    #   inbound  (AER origin): first 06:10, last 23:34
    # openTime is the earliest origin departure of the day across both
    # directions (05:30); closeTime is the latest origin departure (23:34).
    ("M3_AIR", "mon_thu", "05:30", "23:34", 0, None),
    ("M3_AIR", "fri",     "05:30", "23:34", 0, None),
    ("M3_AIR", "sat",     "05:30", "23:34", 0, None),
    ("M3_AIR", "sun",     "05:30", "23:34", 0, None),

    # T6 / T7. Last origin per STASY First/Last table (later of two
    # directions). Mon-Thu/Sun: T6 Syntagma 00:50, T7 Akti Posidonos
    # 00:40. Fri extension: T6 Syntagma 01:40, T7 Akti Posidonos 01:50.
    # Sat 24-hour.
    ("T6", "mon_thu", "05:30", "00:50", 0, None),
    ("T6", "fri",     "05:30", "01:40", 0, None),
    ("T6", "sat",     "05:30", "05:28", 0, "Saturday 24h: tram extends overnight into Sunday 05:30 (OASA 24mmm https://www.oasa.gr/en/24mmm/, verified 2026-08-30)"),
    ("T6", "sun",     "05:30", "00:50", 0, None),

    ("T7", "mon_thu", "05:30", "00:40", 0, None),
    ("T7", "fri",     "05:30", "01:50", 0, None),
    ("T7", "sat",     "05:30", "05:28", 0, "Saturday 24h: tram extends overnight into Sunday 05:30 (OASA 24mmm https://www.oasa.gr/en/24mmm/, verified 2026-08-30)"),
    ("T7", "sun",     "05:30", "00:40", 0, None),

    # Suburban / Hellenic Train. Source PDFs in assets/hellenic-train-timetables/
    # Effective 2025-11-22. A1, A2 share the Airport corridor; from 2025-11-22
    # the Airport-Metamorfosi section runs every 20 min Mon-Fri (operator note).
    ("A1", "mon_thu", "04:00", "23:00", 0, "Piraeus-Airport through trains, ~hourly"),
    ("A1", "fri",     "04:00", "23:00", 0, None),
    ("A1", "sat",     "05:00", "22:00", 0, "Reduced weekend service"),
    ("A1", "sun",     "05:00", "22:00", 0, "Reduced weekend service"),

    ("A2", "mon_thu", "05:30", "22:00", 0, "Ano Liosia-Airport branch, interleaved with A1"),
    ("A2", "fri",     "05:30", "22:00", 0, None),
    ("A2", "sat",     "06:00", "22:00", 0, "Reduced weekend service"),
    ("A2", "sun",     "06:00", "22:00", 0, "Reduced weekend service"),

    ("A3", "mon_thu", "05:00", "23:00", 0, "Athens-Chalkida regional"),
    ("A3", "fri",     "05:00", "23:00", 0, None),
    ("A3", "sat",     "06:00", "22:00", 0, None),
    ("A3", "sun",     "06:00", "22:00", 0, None),

    ("A4", "mon_thu", "04:30", "23:30", 0, "Piraeus-Kiato regional"),
    ("A4", "fri",     "04:30", "23:30", 0, None),
    ("A4", "sat",     "05:30", "22:30", 0, None),
    ("A4", "sun",     "05:30", "22:30", 0, None),
]

# Frequency bands. (line_id, day_type, time_start, time_end, headway_min, label, direction)
# direction ∈ both | outbound | inbound. day_type ∈ mon_thu | fri | sat | sun | aug_15 | dec_24_31.
# All numbers transcribed from the STASY "Frequency of Routes" tables on
# stasy.gr (Lines 1, 2, 3, Tram). Sub-30-minute bands are preserved
# verbatim rather than collapsed so the projector matches the published
# minute-by-minute timetable.
FREQUENCY_BANDS = [
    # ---------------- M1 (Piraeus ↔ Kifissia) ----------------
    # STASY publishes a single Mon-Fri schedule; we duplicate it into
    # mon_thu + fri so the projector picks the right day_type.
    ("M1", "mon_thu", "05:00", "05:30", 15.0,    "early_morning",   "both"),
    ("M1", "mon_thu", "05:30", "07:00", 7.5,     "early_morning",   "both"),
    ("M1", "mon_thu", "07:00", "10:00", 6.0,     "morning_peak",    "both"),
    ("M1", "mon_thu", "10:00", "15:00", 7.5,     "midday_offpeak",  "both"),
    ("M1", "mon_thu", "15:00", "18:00", 6.0,     "afternoon_peak",  "both"),
    ("M1", "mon_thu", "18:00", "22:30", 7.5,     "evening_offpeak", "both"),
    ("M1", "mon_thu", "22:30", "23:00", 11.5,    "wind_down",       "both"),
    ("M1", "mon_thu", "23:00", "23:30", 12.5,    "wind_down",       "both"),
    ("M1", "mon_thu", "23:30", "01:30", 15.0,    "late_night",      "both"),
    ("M1", "fri", "05:00", "05:30", 15.0,    "early_morning",   "both"),
    ("M1", "fri", "05:30", "07:00", 7.5,     "early_morning",   "both"),
    ("M1", "fri", "07:00", "10:00", 6.0,     "morning_peak",    "both"),
    ("M1", "fri", "10:00", "15:00", 7.5,     "midday_offpeak",  "both"),
    ("M1", "fri", "15:00", "18:00", 6.0,     "afternoon_peak",  "both"),
    ("M1", "fri", "18:00", "22:30", 7.5,     "evening_offpeak", "both"),
    ("M1", "fri", "22:30", "23:00", 11.5,    "wind_down",       "both"),
    ("M1", "fri", "23:00", "23:30", 12.5,    "wind_down",       "both"),
    ("M1", "fri", "23:30", "01:30", 15.0,    "late_night",      "both"),
    ("M1", "sat", "05:00", "05:30", 15.0,    "early_morning",   "both"),
    ("M1", "sat", "05:30", "23:30", 10.5,    "saturday_day",    "both"),
    ("M1", "sat", "23:30", "01:00", 15.0,    "saturday_late",   "both"),
    ("M1", "sun", "05:00", "05:30", 15.0,    "early_morning",   "both"),
    ("M1", "sun", "05:30", "23:30", 10.5,    "sunday_day",      "both"),
    ("M1", "sun", "23:30", "01:00", 15.0,    "sunday_late",     "both"),

    # ---------------- M2 (Anthoupoli ↔ Elliniko) ----------------
    ("M2", "mon_thu", "05:30", "06:00", 10.0,    "early_morning",   "both"),
    ("M2", "mon_thu", "06:00", "06:30", 7.5,     "early_morning",   "both"),
    ("M2", "mon_thu", "06:30", "07:00", 5.5,     "early_morning",   "both"),
    ("M2", "mon_thu", "07:00", "10:00", 4.5,     "morning_peak",    "both"),
    ("M2", "mon_thu", "10:00", "10:30", 5.0,     "midday_offpeak",  "both"),
    ("M2", "mon_thu", "10:30", "14:00", 6.333,   "midday_offpeak",  "both"),  # 6'20"
    ("M2", "mon_thu", "14:00", "19:00", 5.0,     "afternoon_peak",  "both"),
    ("M2", "mon_thu", "19:00", "20:00", 5.5,     "evening_offpeak", "both"),
    ("M2", "mon_thu", "20:00", "20:30", 6.333,   "evening_offpeak", "both"),
    ("M2", "mon_thu", "20:30", "21:00", 7.0,     "evening_offpeak", "both"),
    ("M2", "mon_thu", "21:00", "21:30", 7.5,     "wind_down",       "both"),
    ("M2", "mon_thu", "21:30", "22:00", 8.5,     "wind_down",       "both"),
    ("M2", "mon_thu", "22:00", "22:30", 9.5,     "wind_down",       "both"),
    ("M2", "mon_thu", "22:30", "00:20", 10.0,    "late_night",      "both"),
    ("M2", "fri", "05:30", "06:00", 10.0,    "early_morning",   "both"),
    ("M2", "fri", "06:00", "06:30", 7.5,     "early_morning",   "both"),
    ("M2", "fri", "06:30", "07:00", 5.5,     "early_morning",   "both"),
    ("M2", "fri", "07:00", "10:00", 4.5,     "morning_peak",    "both"),
    ("M2", "fri", "10:00", "10:30", 5.0,     "midday_offpeak",  "both"),
    ("M2", "fri", "10:30", "14:00", 6.333,   "midday_offpeak",  "both"),
    ("M2", "fri", "14:00", "19:00", 5.0,     "afternoon_peak",  "both"),
    ("M2", "fri", "19:00", "20:00", 5.5,     "evening_offpeak", "both"),
    ("M2", "fri", "20:00", "20:30", 6.333,   "evening_offpeak", "both"),
    ("M2", "fri", "20:30", "21:00", 7.0,     "evening_offpeak", "both"),
    ("M2", "fri", "21:00", "21:30", 7.5,     "wind_down",       "both"),
    ("M2", "fri", "21:30", "22:00", 8.5,     "wind_down",       "both"),
    ("M2", "fri", "22:00", "22:30", 9.5,     "wind_down",       "both"),
    ("M2", "fri", "22:30", "00:20", 10.0,    "late_night",      "both"),
    ("M2", "fri", "00:20", "02:00", 15.0,    "fri_late_extension", "both"),
    ("M2", "sat", "05:30", "09:00", 12.5,    "saturday_morning","both"),
    ("M2", "sat", "09:00", "12:00", 10.833,  "saturday_day",    "both"),  # 10'50"
    ("M2", "sat", "12:00", "12:30", 9.5,     "saturday_day",    "both"),
    ("M2", "sat", "12:30", "13:00", 8.5,     "saturday_day",    "both"),
    ("M2", "sat", "13:00", "21:00", 7.5,     "saturday_day",    "both"),
    ("M2", "sat", "21:00", "21:30", 8.5,     "saturday_evening","both"),
    ("M2", "sat", "21:30", "22:00", 9.5,     "saturday_evening","both"),
    ("M2", "sat", "22:00", "00:20", 10.833,  "saturday_evening","both"),
    ("M2", "sat", "00:20", "05:30", 15.0,    "saturday_overnight_24_7", "both"),
    ("M2", "sun", "05:30", "07:00", 12.5,    "sunday_morning",  "both"),
    ("M2", "sun", "07:00", "10:00", 10.833,  "sunday_day",      "both"),
    ("M2", "sun", "10:00", "11:00", 9.5,     "sunday_day",      "both"),
    ("M2", "sun", "11:00", "19:00", 8.5,     "sunday_day",      "both"),
    ("M2", "sun", "19:00", "20:00", 9.5,     "sunday_evening",  "both"),
    ("M2", "sun", "20:00", "23:30", 10.833,  "sunday_evening",  "both"),
    ("M2", "sun", "23:30", "00:20", 12.5,    "sunday_late",     "both"),

    # ---------------- M3 city (Dim. Theatro ↔ Doukissis Plakentias) ----------------
    ("M3", "mon_thu", "05:30", "06:00", 10.0,    "early_morning",   "both"),
    ("M3", "mon_thu", "06:00", "06:30", 7.0,     "early_morning",   "both"),
    ("M3", "mon_thu", "06:30", "07:00", 4.5,     "early_morning",   "both"),
    ("M3", "mon_thu", "07:00", "10:00", 4.0,     "morning_peak",    "both"),
    ("M3", "mon_thu", "10:00", "10:30", 5.0,     "midday_offpeak",  "both"),
    ("M3", "mon_thu", "10:30", "13:30", 6.0,     "midday_offpeak",  "both"),
    ("M3", "mon_thu", "13:30", "14:00", 5.5,     "midday_offpeak",  "both"),
    ("M3", "mon_thu", "14:00", "18:00", 4.25,    "afternoon_peak",  "both"),  # 4'15"
    ("M3", "mon_thu", "18:00", "19:30", 4.583,   "evening_peak",    "both"),  # 4'35"
    ("M3", "mon_thu", "19:30", "20:30", 5.5,     "evening_offpeak", "both"),
    ("M3", "mon_thu", "20:30", "21:00", 6.25,    "evening_offpeak", "both"),  # 6'15"
    ("M3", "mon_thu", "21:00", "21:30", 7.0,     "wind_down",       "both"),
    ("M3", "mon_thu", "21:30", "22:00", 8.0,     "wind_down",       "both"),
    ("M3", "mon_thu", "22:00", "00:20", 9.0,     "late_night",      "both"),
    ("M3", "fri", "05:30", "06:00", 10.0,    "early_morning",   "both"),
    ("M3", "fri", "06:00", "06:30", 7.0,     "early_morning",   "both"),
    ("M3", "fri", "06:30", "07:00", 4.5,     "early_morning",   "both"),
    ("M3", "fri", "07:00", "10:00", 4.0,     "morning_peak",    "both"),
    ("M3", "fri", "10:00", "10:30", 5.0,     "midday_offpeak",  "both"),
    ("M3", "fri", "10:30", "13:30", 6.0,     "midday_offpeak",  "both"),
    ("M3", "fri", "13:30", "14:00", 5.5,     "midday_offpeak",  "both"),
    ("M3", "fri", "14:00", "18:00", 4.25,    "afternoon_peak",  "both"),
    ("M3", "fri", "18:00", "19:30", 4.583,   "evening_peak",    "both"),
    ("M3", "fri", "19:30", "20:30", 5.5,     "evening_offpeak", "both"),
    ("M3", "fri", "20:30", "21:00", 6.25,    "evening_offpeak", "both"),
    ("M3", "fri", "21:00", "21:30", 7.0,     "wind_down",       "both"),
    ("M3", "fri", "21:30", "22:00", 8.0,     "wind_down",       "both"),
    ("M3", "fri", "22:00", "00:20", 9.0,     "late_night",      "both"),
    ("M3", "fri", "00:20", "02:00", 15.0,    "fri_late_extension", "both"),
    ("M3", "sat", "05:30", "09:00", 10.0,    "saturday_morning","both"),
    ("M3", "sat", "09:00", "12:00", 9.0,     "saturday_day",    "both"),
    ("M3", "sat", "12:00", "12:30", 8.5,     "saturday_day",    "both"),
    ("M3", "sat", "12:30", "13:00", 7.5,     "saturday_day",    "both"),
    ("M3", "sat", "13:00", "21:00", 7.0,     "saturday_day",    "both"),
    ("M3", "sat", "21:00", "21:30", 7.5,     "saturday_evening","both"),
    ("M3", "sat", "21:30", "22:00", 8.0,     "saturday_evening","both"),
    ("M3", "sat", "22:00", "00:20", 9.0,     "saturday_evening","both"),
    ("M3", "sat", "00:20", "05:30", 15.0,    "saturday_overnight_24_7", "both"),
    ("M3", "sun", "05:30", "07:00", 10.0,    "sunday_morning",  "both"),
    ("M3", "sun", "07:00", "10:00", 9.0,     "sunday_day",      "both"),
    ("M3", "sun", "10:00", "11:00", 8.5,     "sunday_day",      "both"),
    ("M3", "sun", "11:00", "19:00", 7.5,     "sunday_day",      "both"),
    ("M3", "sun", "19:00", "20:30", 9.0,     "sunday_evening",  "both"),
    ("M3", "sun", "20:30", "21:30", 8.5,     "sunday_evening",  "both"),
    ("M3", "sun", "21:30", "00:20", 10.0,    "sunday_late",     "both"),

    # ---------------- M3_AIR (full airport route, per-direction) ----------------
    # Bands now anchored at the line origin for each direction so the
    # projector renders the airport train at every station it passes
    # through (Dim. Theatro included), not only past DPL. Per STASY PDFs:
    #   outbound: Dim. Theatro 05:30 -> Airport 06:35, 36-min headway,
    #             last origin departure 22:54
    #   inbound : Airport 06:10 -> Dim. Theatro 07:12, 36-min headway,
    #             last origin departure 23:34
    # The dedupe pass in the projector collapses the simultaneous M3 city
    # row at each minute so users see exactly one entry per train.
    ("M3_AIR", "mon_thu", "05:30", "22:54", 36.0, "airport_daily", "outbound"),
    ("M3_AIR", "mon_thu", "06:10", "23:34", 36.0, "airport_daily", "inbound"),
    ("M3_AIR", "fri",     "05:30", "22:54", 36.0, "airport_daily", "outbound"),
    ("M3_AIR", "fri",     "06:10", "23:34", 36.0, "airport_daily", "inbound"),
    ("M3_AIR", "sat",     "05:30", "22:54", 36.0, "airport_daily", "outbound"),
    ("M3_AIR", "sat",     "06:10", "23:34", 36.0, "airport_daily", "inbound"),
    ("M3_AIR", "sun",     "05:30", "22:54", 36.0, "airport_daily", "outbound"),
    ("M3_AIR", "sun",     "06:10", "23:34", 36.0, "airport_daily", "inbound"),

    # ---------------- T6 (Syntagma ↔ Pikrodafni) ----------------
    ("T6", "mon_thu", "05:30", "07:00", 12.0, "early_morning",  "both"),
    ("T6", "mon_thu", "07:00", "19:00", 9.0,  "day",            "both"),
    ("T6", "mon_thu", "19:00", "22:00", 12.0, "evening",        "both"),
    ("T6", "mon_thu", "22:00", "00:50", 15.0, "late_night",     "both"),
    ("T6", "fri", "05:30", "07:00", 12.0, "early_morning",  "both"),
    ("T6", "fri", "07:00", "19:00", 9.0,  "day",            "both"),
    ("T6", "fri", "19:00", "22:00", 12.0, "evening",        "both"),
    ("T6", "fri", "22:00", "00:30", 15.0, "late_night",     "both"),
    ("T6", "fri", "00:30", "01:40", 25.0, "fri_late_extension", "both"),
    ("T6", "sat", "05:30", "09:00", 15.0, "saturday_morning", "both"),
    ("T6", "sat", "09:00", "21:00", 12.0, "saturday_day",     "both"),
    ("T6", "sat", "21:00", "00:30", 15.0, "saturday_evening", "both"),
    ("T6", "sat", "00:30", "05:30", 25.0, "saturday_overnight_24_7", "both"),
    ("T6", "sun", "05:30", "09:00", 15.0, "sunday_morning",   "both"),
    ("T6", "sun", "09:00", "21:00", 15.0, "sunday_day",       "both"),
    ("T6", "sun", "21:00", "00:50", 15.0, "sunday_evening",   "both"),

    # ---------------- T7 (Akti Posidonos ↔ Asklipiio Voulas) ----------------
    ("T7", "mon_thu", "05:30", "07:00", 12.0, "early_morning",  "both"),
    ("T7", "mon_thu", "07:00", "19:00", 12.0, "day",            "both"),
    ("T7", "mon_thu", "19:00", "22:00", 12.0, "evening",        "both"),
    ("T7", "mon_thu", "22:00", "00:40", 15.0, "late_night",     "both"),
    ("T7", "fri", "05:30", "07:00", 12.0, "early_morning",  "both"),
    ("T7", "fri", "07:00", "19:00", 12.0, "day",            "both"),
    ("T7", "fri", "19:00", "22:00", 12.0, "evening",        "both"),
    ("T7", "fri", "22:00", "00:30", 15.0, "late_night",     "both"),
    ("T7", "fri", "00:30", "01:50", 25.0, "fri_late_extension", "both"),
    ("T7", "sat", "05:30", "09:00", 15.0, "saturday_morning", "both"),
    ("T7", "sat", "09:00", "21:00", 15.0, "saturday_day",     "both"),
    ("T7", "sat", "21:00", "00:30", 15.0, "saturday_evening", "both"),
    ("T7", "sat", "00:30", "05:30", 25.0, "saturday_overnight_24_7", "both"),
    ("T7", "sun", "05:30", "09:00", 15.0, "sunday_morning",   "both"),
    ("T7", "sun", "09:00", "21:00", 15.0, "sunday_day",       "both"),
    ("T7", "sun", "21:00", "00:40", 15.0, "sunday_evening",   "both"),

    # ---------------- Suburban (Hellenic Train, placeholder cadence) ----------------
    ("A1", "mon_thu", "04:00", "23:00", 60.0, "weekday",  "both"),
    ("A1", "fri",     "04:00", "23:00", 60.0, "weekday",  "both"),
    ("A1", "sat",     "05:00", "22:00", 90.0, "weekend",  "both"),
    ("A1", "sun",     "05:00", "22:00", 90.0, "weekend",  "both"),
    ("A2", "mon_thu", "05:30", "22:00", 60.0, "weekday",  "both"),
    ("A2", "fri",     "05:30", "22:00", 60.0, "weekday",  "both"),
    ("A2", "sat",     "06:00", "22:00", 90.0, "weekend",  "both"),
    ("A2", "sun",     "06:00", "22:00", 90.0, "weekend",  "both"),
    ("A3", "mon_thu", "05:00", "23:00", 90.0, "weekday",  "both"),
    ("A3", "fri",     "05:00", "23:00", 90.0, "weekday",  "both"),
    ("A3", "sat",     "06:00", "22:00", 120.0,"weekend",  "both"),
    ("A3", "sun",     "06:00", "22:00", 120.0,"weekend",  "both"),
    ("A4", "mon_thu", "04:30", "23:30", 60.0, "weekday",  "both"),
    ("A4", "fri",     "04:30", "23:30", 60.0, "weekday",  "both"),
    ("A4", "sat",     "05:30", "22:30", 90.0, "weekend",  "both"),
    ("A4", "sun",     "05:30", "22:30", 90.0, "weekend",  "both"),
]

# Holiday calendar. date_pattern + day_type to apply.
HOLIDAY_RULES = [
    ("New Year",            "01-01",        "sun",         None),
    ("Epiphany",            "01-06",        "sat",         "Bank holiday: Saturday-style"),
    ("Clean Monday",        "clean_monday", "sun",         "Movable: 48 days before Orthodox Easter"),
    ("Good Friday",         "easter-2",     "sun",         None),
    ("Easter Monday",       "easter+1",     "sun",         None),
    ("Labour Day",          "05-01",        "sun",         None),
    ("Assumption of Mary",  "08-15",        "aug_15",      "Flat 12-min interval all day"),
    ("Ohi Day",             "10-28",        "sun",         None),
    ("Christmas Eve",       "12-24",        "dec_24_31",   "Early shutdown by 23:00"),
    ("Christmas Day",       "12-25",        "sun",         None),
    ("Boxing Day",          "12-26",        "sun",         None),
    ("New Year's Eve",      "12-31",        "dec_24_31",   "Early shutdown by 23:00"),
    ("School Holiday Nov 17", "11-17",      "sat",         "Bank-holiday rule: Saturday-style"),
    ("Epiphany Bank Day",   "01-02",        "sat",         "Bank-holiday rule: Saturday-style"),
]


# Markdown parsing helpers

LINE_HEADER_RE = re.compile(
    r"^## (Metro|Tram|Suburban railway):\s+([A-Z][0-9]+)\s+\(([^)]+)\)$",
    re.MULTILINE,
)
TABLE_ROW_RE = re.compile(r"^\|\s*\d+\s*\|(.+)$")
SUMMARY_ROW_RE = re.compile(
    r"^\|\s*(Metro|Tram|Suburban railway)\s*\|\s*([A-Z][0-9]+)\s*\|\s*([^|]+?)\s*\|\s*\d+\s*\|\s*(\d+)\s*\|$",
    re.MULTILINE,
)


@dataclass
class LineSummary:
    line_id: str
    mode: str
    direction_label: str
    expected_stops: int


@dataclass
class StationRow:
    seq: int
    name_en: str
    name_el: str
    lat: float
    lng: float


def _strip(s: str) -> str:
    return s.strip().strip("|").strip()


_GREEK_TO_LATIN = {
    "Α": "A", "Β": "V", "Γ": "G", "Δ": "D", "Ε": "E", "Ζ": "Z", "Η": "I",
    "Θ": "TH", "Ι": "I", "Κ": "K", "Λ": "L", "Μ": "M", "Ν": "N", "Ξ": "X",
    "Ο": "O", "Π": "P", "Ρ": "R", "Σ": "S", "Τ": "T", "Υ": "Y", "Φ": "F",
    "Χ": "CH", "Ψ": "PS", "Ω": "O",
    "Ά": "A", "Έ": "E", "Ή": "I", "Ί": "I", "Ό": "O", "Ύ": "Y", "Ώ": "O",
    "Ϊ": "I", "Ϋ": "Y",
}


def _ascii_letters_only(text: str) -> str:
    """Strip accents, romanize Greek, keep [A-Z]."""
    out: list[str] = []
    for ch in text:
        up = ch.upper()
        if up in _GREEK_TO_LATIN:
            out.append(_GREEK_TO_LATIN[up])
        else:
            decomposed = unicodedata.normalize("NFKD", up)
            for d in decomposed:
                if "A" <= d <= "Z":
                    out.append(d)
    return "".join(out)


def _slug3(name_en: str, fallback: str) -> str:
    """Three-letter station code; falls back to Greek transliteration."""
    primary = _ascii_letters_only(name_en or "")
    if len(primary) >= 3:
        return primary[:3]
    secondary = _ascii_letters_only(fallback or "")
    if len(secondary) >= 3:
        return secondary[:3]
    src = primary or secondary
    return (src + "XXX")[:3]


def parse_line_summary(md: str) -> list[LineSummary]:
    summaries: list[LineSummary] = []
    for m in SUMMARY_ROW_RE.finditer(md):
        mode_label, line_id, direction, stops = m.groups()
        summaries.append(
            LineSummary(
                line_id=line_id,
                mode=MODE_LABEL[mode_label],
                direction_label=direction.strip(),
                expected_stops=int(stops),
            )
        )
    return summaries


def parse_line_stations(md: str, line_id: str) -> list[StationRow]:
    """Pull the per-line station table from the markdown."""
    header_pat = re.compile(
        rf"^## (?:Metro|Tram|Suburban railway):\s+{re.escape(line_id)}\s*\(",
        re.MULTILINE,
    )
    m = header_pat.search(md)
    if not m:
        return []
    # Slice from this header to the next ## (any depth-2)
    start = m.end()
    next_h = re.search(r"^## ", md[start:], re.MULTILINE)
    section = md[start : start + (next_h.start() if next_h else len(md) - start)]

    rows: list[StationRow] = []
    for line in section.splitlines():
        if not line.startswith("| "):
            continue
        cols = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cols) < 7:
            continue
        if not cols[0].isdigit():
            continue
        seq = int(cols[0])
        name_en = cols[1]
        name_el = cols[2]
        try:
            lat = float(cols[3])
            lng = float(cols[4])
        except ValueError:
            continue
        # When the markdown puts the Greek name in col 1 and leaves col 2 empty,
        # swap so name_en stays a Latin string when possible.
        if not name_en or any(ord(ch) > 127 for ch in name_en):
            if name_el and not any(ord(ch) > 127 for ch in name_el):
                name_en, name_el = name_el, name_en
            else:
                # Both Greek; keep order, leave romanization to a later pass.
                pass
        rows.append(StationRow(seq=seq, name_en=name_en, name_el=name_el, lat=lat, lng=lng))
    return rows


def terminals_from_direction(direction: str) -> tuple[str, str]:
    if " to " in direction:
        a, b = direction.split(" to ", 1)
        return a.strip(), b.strip()
    if " loop to " in direction:
        a, b = direction.split(" loop to ", 1)
        return a.strip(), b.strip()
    return direction, direction


# DB writes

def apply(conn, dry_run: bool) -> dict:
    dbmod.migrate(conn)
    md = COORD_MD.read_text(encoding="utf-8")
    summaries = parse_line_summary(md)
    if not summaries:
        raise RuntimeError("No line summary rows parsed")

    line_rows: list[tuple] = []
    station_rows: dict[str, tuple] = {}
    line_station_rows: list[tuple] = []

    sort_idx = 0
    for s in summaries:
        ta, tb = terminals_from_direction(s.direction_label)
        # Source MD summary conflates M3 city + airport into "Dimotiko Theatro
        # to Airport". The regular M3 city terminus is Doukissis Plakentias;
        # only the M3_AIR variant reaches the Airport. Override so the
        # projector labels regular outbound trains as "towards Doukissis
        # Plakentias" and only the M3_AIR rows display "Airport".
        if s.line_id == "M3":
            tb = "Doukissis Plakentias"
        line_rows.append(
            (
                s.line_id,
                s.mode,
                s.direction_label.split(" to ")[0]
                if " to " in s.direction_label
                else s.line_id,
                LINE_NAMES_EL.get(s.line_id, s.line_id),
                LINE_COLORS.get(s.line_id, "#666666"),
                ta,
                tb,
                sort_idx,
            )
        )
        sort_idx += 1

        stations = parse_line_stations(md, s.line_id)
        if len(stations) != s.expected_stops:
            print(
                f"WARN: {s.line_id} parsed {len(stations)} stations, expected {s.expected_stops}",
                file=sys.stderr,
            )
        used_codes: set[str] = set()
        for st in stations:
            code = _slug3(st.name_en, st.name_el)
            base = code
            n = 1
            # Ensure uniqueness inside this line. When a collision happens,
            # replace the last char with a digit; only 9 collisions per prefix
            # are realistic (none observed in current data).
            while code in used_codes:
                n += 1
                if n > 9:
                    raise RuntimeError(
                        f"Too many station-code collisions for {s.line_id}/{base}"
                    )
                code = base[:2] + str(n)
            used_codes.add(code)
            station_id = f"{s.line_id}_{code}"

            # Friendlier line name "Line 1", "Tram T6", "Suburban A1"
            station_rows.setdefault(
                station_id,
                (station_id, st.name_en, st.name_el or st.name_en, st.lat, st.lng),
            )
            line_station_rows.append((s.line_id, station_id, st.seq, "both"))

    # Patch line name_en to a clean human label
    line_rows_final = []
    for r in line_rows:
        lid = r[0]
        name_en = {
            "M1": "Line 1",
            "M2": "Line 2",
            "M3": "Line 3",
            "T6": "Tram T6",
            "T7": "Tram T7",
            "A1": "Suburban A1",
            "A2": "Suburban A2",
            "A3": "Suburban A3",
            "A4": "Suburban A4",
        }.get(lid, lid)
        line_rows_final.append((lid, r[1], name_en, r[3], r[4], r[5], r[6], r[7]))

    # M3 airport is a virtual schedule-only line (no separate stations)
    line_rows_final.append((
        "M3_AIR",
        "metro",
        "Line 3 Airport",
        "Γραμμή 3 Αεροδρόμιο",
        "#0083C9",
        "Doukissis Plakentias",
        "Athens Airport",
        99,
    ))

    summary = {
        "lines": len(line_rows_final),
        "stations": len(station_rows),
        "line_stations": len(line_station_rows),
        "schedule_rules": len(WEEKLY_HOURS),
        "frequency_bands": len(FREQUENCY_BANDS),
        "holiday_rules": len(HOLIDAY_RULES),
    }

    if dry_run:
        print(json.dumps(summary, indent=2))
        return summary

    cur = conn.cursor()
    cur.execute("BEGIN")
    try:
        # This importer owns ONLY the Athens package. It must not delete lines or
        # stations from other regions (thessaloniki, national, patras) -- those
        # are seeded by seed_thessaloniki / seed_greek_corridors and would
        # otherwise be wiped on every nightly run, taking the whole map with them.
        athens_line_ids = [r[0] for r in line_rows_final]
        ph = ",".join("?" for _ in athens_line_ids)
        cur.execute(f"DELETE FROM line_stations WHERE line_id IN ({ph})", athens_line_ids)
        cur.execute(f"DELETE FROM schedule_rules WHERE line_id IN ({ph})", athens_line_ids)
        cur.execute(f"DELETE FROM frequency_bands WHERE line_id IN ({ph})", athens_line_ids)
        cur.execute("DELETE FROM holiday_rules")  # global calendar, safe to rebuild
        cur.execute(f"DELETE FROM lines WHERE id IN ({ph})", athens_line_ids)
        # Athens stations only. region defaults to 'athens' on re-insert below.
        cur.execute("DELETE FROM stations WHERE region='athens'")

        cur.executemany(
            "INSERT INTO lines(id,mode,name_en,name_el,color,terminal_a,terminal_b,sort_order)"
            " VALUES(?,?,?,?,?,?,?,?)",
            line_rows_final,
        )
        cur.executemany(
            "INSERT INTO stations(id,name_en,name_el,lat,lng) VALUES(?,?,?,?,?)",
            list(station_rows.values()),
        )
        cur.executemany(
            "INSERT INTO line_stations(line_id,station_id,seq,direction) VALUES(?,?,?,?)",
            line_station_rows,
        )
        cur.executemany(
            "INSERT INTO schedule_rules(line_id,day_type,open_time,close_time,is_24_7,notes)"
            " VALUES(?,?,?,?,?,?)",
            WEEKLY_HOURS,
        )
        cur.executemany(
            "INSERT INTO frequency_bands"
            "(line_id,day_type,time_start,time_end,headway_minutes,label,direction)"
            " VALUES(?,?,?,?,?,?,?)",
            FREQUENCY_BANDS,
        )
        cur.executemany(
            "INSERT INTO holiday_rules(name,date_pattern,day_type,notes) VALUES(?,?,?,?)",
            HOLIDAY_RULES,
        )

        # Belt-and-suspenders: guarantee the continuous 24h Saturday overnight
        # bands even if the FREQUENCY_BANDS table above is ever edited to drop
        # them. Normally a no-op because the seed already ships them.
        ensure_saturday_overnight(conn)

        cur.execute(
            "UPDATE meta SET value=strftime('%Y-%m-%dT%H:%M:%SZ','now') WHERE key='updated_at'"
        )
        cur.execute("COMMIT")
    except Exception:
        cur.execute("ROLLBACK")
        raise

    print(json.dumps(summary, indent=2))
    return summary


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--apply", action="store_true", help="Write to DB (default is dry-run)")
    args = p.parse_args()
    with dbmod.connect() as conn:
        apply(conn, dry_run=not args.apply)


if __name__ == "__main__":
    main()
