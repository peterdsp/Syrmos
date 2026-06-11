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
    ("M1", "mon_thu", "05:00", "00:30", 0, None),
    ("M1", "fri",     "05:00", "00:30", 0, None),
    ("M1", "sat",     "05:00", "00:30", 0, None),
    ("M1", "sun",     "05:00", "00:30", 0, None),

    ("M2", "mon_thu", "05:30", "00:30", 0, None),
    ("M2", "fri",     "05:30", "02:00", 0, "Friday late extension to 02:00"),
    ("M2", "sat",     "00:00", "23:59", 1, "Saturday 24/7"),
    ("M2", "sun",     "05:30", "00:30", 0, None),

    ("M3", "mon_thu", "05:30", "00:30", 0, "City segment; airport segment closes 23:00"),
    ("M3", "fri",     "05:30", "02:00", 0, "Friday late extension to 02:00 (city only)"),
    ("M3", "sat",     "00:00", "23:59", 1, "Saturday 24/7 (city only)"),
    ("M3", "sun",     "05:30", "00:30", 0, None),

    # Airport branch: hard 23:00 cutoff every day, no 24/7, no Fri extension.
    ("M3_AIR", "mon_thu", "05:30", "23:00", 0, None),
    ("M3_AIR", "fri",     "05:30", "23:00", 0, None),
    ("M3_AIR", "sat",     "05:30", "23:00", 0, None),
    ("M3_AIR", "sun",     "05:30", "23:00", 0, None),

    ("T6", "mon_thu", "05:30", "01:00", 0, None),
    ("T6", "fri",     "05:00", "01:30", 0, None),
    ("T6", "sat",     "00:00", "23:59", 1, "Saturday 24/7"),
    ("T6", "sun",     "05:30", "01:00", 0, None),

    ("T7", "mon_thu", "05:30", "01:00", 0, None),
    ("T7", "fri",     "05:00", "01:30", 0, None),
    ("T7", "sat",     "00:00", "23:59", 1, "Saturday 24/7"),
    ("T7", "sun",     "05:30", "01:00", 0, None),

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

# Frequency bands. headway_minutes is a float (e.g. 4.5, 10.5).
# day_type ∈ mon_thu | fri | sat | sun | holiday | bank_holiday | aug_15 | dec_24_31
FREQUENCY_BANDS = [
    # Weekday morning peak 07:00-10:00
    ("M1", "mon_thu", "07:00", "10:00", 6.0,  "morning_peak"),
    ("M2", "mon_thu", "07:00", "10:00", 4.0,  "morning_peak"),
    ("M3", "mon_thu", "07:00", "10:00", 4.0,  "morning_peak"),
    ("M3_AIR", "mon_thu", "07:00", "10:00", 36.0, "morning_peak"),
    ("T6", "mon_thu", "07:00", "10:00", 10.0, "morning_peak"),
    ("T7", "mon_thu", "07:00", "10:00", 10.0, "morning_peak"),

    # Weekday midday 10:00-17:00 (ranges -> midpoint)
    ("M1", "mon_thu", "10:00", "17:00", 8.0,  "midday_offpeak"),
    ("M2", "mon_thu", "10:00", "17:00", 6.0,  "midday_offpeak"),
    ("M3", "mon_thu", "10:00", "17:00", 6.0,  "midday_offpeak"),
    ("M3_AIR", "mon_thu", "10:00", "17:00", 36.0, "midday_offpeak"),
    ("T6", "mon_thu", "10:00", "17:00", 12.0, "midday_offpeak"),
    ("T7", "mon_thu", "10:00", "17:00", 12.0, "midday_offpeak"),

    # Weekday evening peak 17:00-20:00
    ("M1", "mon_thu", "17:00", "20:00", 6.0,  "evening_peak"),
    ("M2", "mon_thu", "17:00", "20:00", 4.5,  "evening_peak"),
    ("M3", "mon_thu", "17:00", "20:00", 4.5,  "evening_peak"),
    ("M3_AIR", "mon_thu", "17:00", "20:00", 36.0, "evening_peak"),
    ("T6", "mon_thu", "17:00", "20:00", 10.0, "evening_peak"),
    ("T7", "mon_thu", "17:00", "20:00", 10.0, "evening_peak"),

    # Weekday night off-peak 20:00-22:30
    ("M1", "mon_thu", "20:00", "22:30", 10.0, "night_offpeak"),
    ("M2", "mon_thu", "20:00", "22:30", 10.0, "night_offpeak"),
    ("M3", "mon_thu", "20:00", "22:30", 10.0, "night_offpeak"),
    ("M3_AIR", "mon_thu", "20:00", "23:00", 36.0, "night_offpeak"),
    ("T6", "mon_thu", "20:00", "22:30", 15.0, "night_offpeak"),
    ("T7", "mon_thu", "20:00", "22:30", 15.0, "night_offpeak"),

    # Weekday late-night wind-down 22:30-00:30
    ("M1", "mon_thu", "22:30", "24:30", 15.0, "late_night"),
    ("M2", "mon_thu", "22:30", "24:30", 15.0, "late_night"),
    ("M3", "mon_thu", "22:30", "24:30", 15.0, "late_night"),
    ("T6", "mon_thu", "22:30", "25:00", 15.0, "late_night"),
    ("T7", "mon_thu", "22:30", "25:00", 15.0, "late_night"),

    # Friday clones weekday daytime, then late extension 00:30-02:00 @ 15 min
    ("M1", "fri", "05:00", "07:00", 8.0,  "early_morning"),
    ("M1", "fri", "07:00", "10:00", 6.0,  "morning_peak"),
    ("M1", "fri", "10:00", "17:00", 8.0,  "midday_offpeak"),
    ("M1", "fri", "17:00", "20:00", 6.0,  "evening_peak"),
    ("M1", "fri", "20:00", "22:30", 10.0, "night_offpeak"),
    ("M1", "fri", "22:30", "24:30", 15.0, "late_night"),

    ("M2", "fri", "05:30", "07:00", 6.0,  "early_morning"),
    ("M2", "fri", "07:00", "10:00", 4.0,  "morning_peak"),
    ("M2", "fri", "10:00", "17:00", 6.0,  "midday_offpeak"),
    ("M2", "fri", "17:00", "20:00", 4.5,  "evening_peak"),
    ("M2", "fri", "20:00", "22:30", 10.0, "night_offpeak"),
    ("M2", "fri", "22:30", "00:30", 15.0, "late_night"),
    ("M2", "fri", "00:30", "02:00", 15.0, "fri_late_extension"),

    ("M3", "fri", "05:30", "07:00", 6.0,  "early_morning"),
    ("M3", "fri", "07:00", "10:00", 4.0,  "morning_peak"),
    ("M3", "fri", "10:00", "17:00", 6.0,  "midday_offpeak"),
    ("M3", "fri", "17:00", "20:00", 4.5,  "evening_peak"),
    ("M3", "fri", "20:00", "22:30", 10.0, "night_offpeak"),
    ("M3", "fri", "22:30", "00:30", 15.0, "late_night"),
    ("M3", "fri", "00:30", "02:00", 15.0, "fri_late_extension"),

    ("M3_AIR", "fri", "05:30", "23:00", 36.0, "all_day_fixed"),

    ("T6", "fri", "05:00", "07:00", 12.0, "early_morning"),
    ("T6", "fri", "07:00", "10:00", 10.0, "morning_peak"),
    ("T6", "fri", "10:00", "17:00", 12.0, "midday_offpeak"),
    ("T6", "fri", "17:00", "20:00", 10.0, "evening_peak"),
    ("T6", "fri", "20:00", "22:30", 15.0, "night_offpeak"),
    ("T6", "fri", "22:30", "25:30", 15.0, "late_night"),

    ("T7", "fri", "05:00", "07:00", 12.0, "early_morning"),
    ("T7", "fri", "07:00", "10:00", 10.0, "morning_peak"),
    ("T7", "fri", "10:00", "17:00", 12.0, "midday_offpeak"),
    ("T7", "fri", "17:00", "20:00", 10.0, "evening_peak"),
    ("T7", "fri", "20:00", "22:30", 15.0, "night_offpeak"),
    ("T7", "fri", "22:30", "25:30", 15.0, "late_night"),

    # Saturday daytime
    ("M1", "sat", "05:00", "20:00", 9.0,  "saturday_day"),
    ("M2", "sat", "05:30", "20:00", 8.5,  "saturday_day"),
    ("M3", "sat", "05:30", "20:00", 8.5,  "saturday_day"),
    ("M3_AIR", "sat", "05:30", "23:00", 36.0, "saturday_day_fixed"),
    ("T6", "sat", "05:30", "20:00", 12.0, "saturday_day"),
    ("T7", "sat", "05:30", "20:00", 12.0, "saturday_day"),

    # Saturday evening + 24/7 overnight (M2/M3/Tram only, M1 closes normally)
    ("M1", "sat", "20:00", "24:30", 15.0, "saturday_evening"),
    ("M2", "sat", "20:00", "00:30", 15.0, "saturday_evening"),
    ("M2", "sat", "00:30", "05:30", 15.0, "saturday_overnight_24_7"),
    ("M3", "sat", "20:00", "00:30", 15.0, "saturday_evening"),
    ("M3", "sat", "00:30", "05:30", 15.0, "saturday_overnight_24_7"),
    ("T6", "sat", "20:00", "00:30", 15.0, "saturday_evening"),
    ("T6", "sat", "00:30", "05:30", 15.0, "saturday_overnight_24_7"),
    ("T7", "sat", "20:00", "00:30", 15.0, "saturday_evening"),
    ("T7", "sat", "00:30", "05:30", 15.0, "saturday_overnight_24_7"),

    # Sunday holiday-style
    ("M1", "sun", "05:00", "24:30", 12.5, "sunday_all_day"),
    ("M2", "sun", "05:30", "00:30", 12.5, "sunday_all_day"),
    ("M3", "sun", "05:30", "00:30", 12.5, "sunday_all_day"),
    ("M3_AIR", "sun", "05:30", "23:00", 36.0, "sunday_all_day_fixed"),
    ("T6", "sun", "05:30", "01:00", 15.0, "sunday_all_day"),
    ("T7", "sun", "05:30", "01:00", 15.0, "sunday_all_day"),

    # August 15 — flat 12-min interval all metro, airport remains 36 min
    ("M1", "aug_15", "05:00", "24:30", 12.0, "aug15_flat"),
    ("M2", "aug_15", "05:30", "00:30", 12.0, "aug15_flat"),
    ("M3", "aug_15", "05:30", "00:30", 12.0, "aug15_flat"),
    ("M3_AIR", "aug_15", "05:30", "23:00", 36.0, "aug15_flat_fixed"),
    ("T6", "aug_15", "05:30", "01:00", 15.0, "aug15_tram"),
    ("T7", "aug_15", "05:30", "01:00", 15.0, "aug15_tram"),

    # Dec 24/31 — last trains 22:00–22:20, full shutdown by 23:00
    ("M1", "dec_24_31", "05:00", "22:20", 10.0, "early_shutdown"),
    ("M2", "dec_24_31", "05:30", "22:20", 10.0, "early_shutdown"),
    ("M3", "dec_24_31", "05:30", "22:20", 10.0, "early_shutdown"),
    ("M3_AIR", "dec_24_31", "05:30", "22:00", 36.0, "early_shutdown"),
    ("T6", "dec_24_31", "05:30", "22:20", 12.0, "early_shutdown"),
    ("T7", "dec_24_31", "05:30", "22:20", 12.0, "early_shutdown"),

    # === Suburban (Hellenic Train). Effective 2025-11-22 ===
    # A1 Piraeus-Athens-Airport, ~hourly through-trains. Interleaved with A2 at Airport-Metamorfosi.
    ("A1", "mon_thu", "04:00", "06:30", 60.0, "early_morning"),
    ("A1", "mon_thu", "06:30", "21:30", 60.0, "weekday_all_day"),
    ("A1", "mon_thu", "21:30", "23:00", 60.0, "evening"),
    ("A1", "fri",     "04:00", "23:00", 60.0, "weekday_all_day"),
    ("A1", "sat",     "05:00", "22:00", 75.0, "weekend"),
    ("A1", "sun",     "05:00", "22:00", 75.0, "weekend"),

    # A2 Ano Liosia-Airport branch. Interleaved with A1 at Airport corridor for combined 20-min.
    ("A2", "mon_thu", "05:30", "22:00", 30.0, "weekday_all_day"),
    ("A2", "fri",     "05:30", "22:00", 30.0, "weekday_all_day"),
    ("A2", "sat",     "06:00", "22:00", 60.0, "weekend"),
    ("A2", "sun",     "06:00", "22:00", 60.0, "weekend"),

    # A3 Athens-Chalkida regional. Roughly hourly off-peak, denser at peak.
    ("A3", "mon_thu", "05:00", "08:00", 60.0, "early_morning"),
    ("A3", "mon_thu", "08:00", "20:00", 60.0, "weekday_all_day"),
    ("A3", "mon_thu", "20:00", "23:00", 90.0, "evening"),
    ("A3", "fri",     "05:00", "23:00", 60.0, "weekday_all_day"),
    ("A3", "sat",     "06:00", "22:00", 90.0, "weekend"),
    ("A3", "sun",     "06:00", "22:00", 90.0, "weekend"),

    # A4 Piraeus-Kiato regional. Hourly through-trains.
    ("A4", "mon_thu", "04:30", "23:30", 60.0, "weekday_all_day"),
    ("A4", "fri",     "04:30", "23:30", 60.0, "weekday_all_day"),
    ("A4", "sat",     "05:30", "22:30", 90.0, "weekend"),
    ("A4", "sun",     "05:30", "22:30", 90.0, "weekend"),
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
        cur.execute("DELETE FROM line_stations")
        cur.execute("DELETE FROM schedule_rules")
        cur.execute("DELETE FROM frequency_bands")
        cur.execute("DELETE FROM holiday_rules")
        cur.execute("DELETE FROM lines")
        cur.execute("DELETE FROM stations")

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
            "INSERT INTO frequency_bands(line_id,day_type,time_start,time_end,headway_minutes,label)"
            " VALUES(?,?,?,?,?,?)",
            FREQUENCY_BANDS,
        )
        cur.executemany(
            "INSERT INTO holiday_rules(name,date_pattern,day_type,notes) VALUES(?,?,?,?)",
            HOLIDAY_RULES,
        )

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
