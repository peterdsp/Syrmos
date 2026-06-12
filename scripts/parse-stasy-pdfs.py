"""Parse STASY metro/tram timetable PDFs into structured per-train timestamps.

STASY publishes most of its timetable data inline as HTML tables on
www.stasy.gr/en/timetables/{line-1,line-2,line-3,tram}. The few PDFs
they DO publish are the M3 airport-extension train schedules in each
direction. This script parses those PDFs into the same JSONL format that
parse-hellenic-train-pdfs.py emits, so ingest-train-timestamps.py can
load them into train_timestamps too.

Output: assets/stasy-pdfs/parsed/{slug}.jsonl
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

import pdfplumber

ROOT = Path(__file__).resolve().parent.parent
PDF_DIR = ROOT / "assets" / "stasy-pdfs"
OUT_DIR = PDF_DIR / "parsed"
OUT_DIR.mkdir(parents=True, exist_ok=True)

# STASY PDF manifest: (filename, line_id, direction, day_type)
# day_type is "all" because STASY publishes a single weekday-style table
# that's then modulated by Saturday/Sunday operating-hours rules elsewhere.
PDF_MANIFEST = [
    ("AIRPORT-TRAIN-SCHEDULES-from-Dim_Theatro-to-Airport_valid-from-24-6-24.pdf",
     "M3_AIR", "outbound", "all"),
    ("AIRPORT-TRAIN-SCHEDULES-from-Airport-to-Dimotiko-Theatro_valid-from-24-6-24.pdf",
     "M3_AIR", "inbound", "all"),
]

TIME_RE = re.compile(r"^\s*(\d{1,2}):(\d{2})\s*$")
TRAIN_NO_RE = re.compile(r"^\s*(\d{3,5})\s*$")


def normalize_time(cell: str | None) -> str | None:
    if not cell:
        return None
    m = TIME_RE.match(cell.replace("\n", " "))
    if not m:
        return None
    h, mn = int(m.group(1)), int(m.group(2))
    if not (0 <= h < 30 and 0 <= mn < 60):
        return None
    return f"{h:02d}:{mn:02d}"


def clean(cell: str | None) -> str:
    if not cell:
        return ""
    return re.sub(r"\s+", " ", cell.replace("\n", " ")).strip()


# Canonical M3 station ordering for the airport-corridor table. The PDF
# column header text varies slightly across editions; we use this list as the
# expected output schema and match PDF columns positionally.
M3_OUTBOUND_STATIONS = [
    ("Piraeus",            "Πειραιάς"),
    ("Dimotiko Theatro",   "Δημοτικό Θέατρο"),
    ("Maniatika",          "Μανιάτικα"),
    ("Nikaia",             "Νίκαια"),
    ("Korydallos",         "Κορυδαλλός"),
    ("Agia Varvara",       "Αγία Βαρβάρα"),
    ("Agia Marina",        "Αγία Μαρίνα"),
    ("Egaleo",             "Αιγάλεω"),
    ("Eleonas",            "Ελαιώνας"),
    ("Kerameikos",         "Κεραμεικός"),
    ("Monastiraki",        "Μοναστηράκι"),
    ("Syntagma",           "Σύνταγμα"),
    ("Evangelismos",       "Ευαγγελισμός"),
    ("Megaro Mousikis",    "Μέγαρο Μουσικής"),
    ("Ambelokipi",         "Αμπελόκηποι"),
    ("Panormou",           "Πανόρμου"),
    ("Katechaki",          "Κατεχάκη"),
    ("Ethniki Amyna",      "Εθνική Άμυνα"),
    ("Cholargos",          "Χολαργός"),
    ("Nomismatokopio",     "Νομισματοκοπείο"),
    ("Agia Paraskevi",     "Αγία Παρασκευή"),
    ("Chalandri",          "Χαλάνδρι"),
    ("Doukissis Plakentias","Δουκίσσης Πλακεντίας"),
    ("Pallini",            "Παλλήνη"),
    ("Paiania-Kantza",     "Παιανία-Κάντζα"),
    ("Koropi",             "Κορωπί"),
    ("Airport",            "Αεροδρόμιο"),
]


def parse_pdf(path: Path, line_id: str, direction: str, day_type: str) -> list[dict]:
    """Walk every page, find rows whose first cell is a train number, and
    emit one record per train with all (station, time) pairs."""
    expected_stations = M3_OUTBOUND_STATIONS if direction == "outbound" else list(reversed(M3_OUTBOUND_STATIONS))
    trains: list[dict] = []
    with pdfplumber.open(str(path)) as pdf:
        for page in pdf.pages:
            for table in page.extract_tables() or []:
                for row in table:
                    if not row or not row[0]:
                        continue
                    tno_m = TRAIN_NO_RE.match((row[0] or "").strip())
                    if not tno_m:
                        continue
                    train_no = tno_m.group(1)
                    stops: list[dict] = []
                    for c in range(1, min(len(row), len(expected_stations) + 1)):
                        t = normalize_time(row[c])
                        if t is None:
                            continue
                        if c - 1 >= len(expected_stations):
                            continue
                        en, el = expected_stations[c - 1]
                        stops.append({"station_en": en, "station_el": el, "time": t})
                    if len(stops) >= 2:
                        trains.append({
                            "train_no": train_no,
                            "line_id": line_id,
                            "direction": direction,
                            "day_type": day_type,
                            "source_pdf": path.name,
                            "stops": stops,
                        })
    return trains


def main() -> None:
    total = 0
    for filename, line_id, direction, day_type in PDF_MANIFEST:
        path = PDF_DIR / filename
        if not path.exists():
            print(f"missing: {filename}", file=sys.stderr)
            continue
        trains = parse_pdf(path, line_id, direction, day_type)
        out_path = OUT_DIR / (path.stem + ".jsonl")
        with out_path.open("w") as f:
            for t in trains:
                f.write(json.dumps(t, ensure_ascii=False) + "\n")
        total += len(trains)
        print(f"  {filename}: {len(trains)} trains -> {out_path.name}")
    print(f"\nTOTAL: {total} STASY trains across {len(PDF_MANIFEST)} PDFs")


if __name__ == "__main__":
    main()
