"""Load parsed Hellenic Train PDFs into the syrmos-api train_timestamps table.

Reads JSONL from assets/hellenic-train-pdfs/parsed/, maps each PDF to a line_id
(A1 / A2 / A3 / A4) based on the source filename and the train's stopping
pattern, and inserts one row per (train_no, station) call.

Run locally against the same DB the API uses:
    SYRMOS_DB_PATH=/path/to/syrmos.db python3 scripts/ingest-train-timestamps.py
"""
from __future__ import annotations

import json
import os
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PARSED_DIR = ROOT / "assets" / "hellenic-train-pdfs" / "parsed"
DB_PATH = os.environ.get(
    "SYRMOS_DB_PATH",
    str(ROOT / "ops" / "syrmos-api" / "data" / "syrmos.db"),
)
MIGRATION = ROOT / "ops" / "syrmos-api" / "migrations" / "0005_train_timestamps.sql"

# Pattern → line mapping. Trains that include the Athens-Liosia-Airport
# corridor are A1 if they reach Piraeus, A2 if they start at Ano Liosia or
# Athens. A3 = Chalkida, A4 = Kiato. Everything else (Aigio, Patras) is A4
# extension and lives in the data but isn't surfaced in the apps yet.
LINE_BY_PDF = {
    "PIRAEUS-ATHENS-LIOSIA-AIRPORT_MON-FRI_from_221125_0.jsonl":  ("A1", "outbound", "mon_fri"),
    "PIRAEUS-ATHENS-LIOSIA-AIRPORT_WEEKENDS_from_221125_0.jsonl": ("A1", "outbound", "weekend"),
    "AIRPORT-LIOSIA-ATHENS-PIRAEUS_MON-FRI_from_221125_0.jsonl":  ("A1", "inbound",  "mon_fri"),
    "AIRPORT-LIOSIA-ATHENS-PIRAEUS_WEEKENDS_from_221125_0.jsonl": ("A1", "inbound",  "weekend"),
    "ATHENS-CHALKIDA-ATHENS_from_221125_0.jsonl":                 ("A3", "both",     "all"),
    "PIRAEUS-KIATO-PIRAEUS_from_221125_0.jsonl":                  ("A4", "both",     "all"),
    "KIATO-AIGIO-KIATO_from_221125_0.jsonl":                      ("A4", "extension", "all"),
}

# Trains that DO call at the AIR-LIN stops (Ano Liosia + SKA + Pyrgos Vasilissis)
# but NOT at Piraeus / Lefka / Rentis -> classify as A2 instead of A1.
A2_TERMINAL_STATIONS = {"Ano Liosia", "Athens"}
A1_REQUIRED_STATIONS = {"Piraeus"}

# Map PDF station label -> internal station_id. This is the bridge between
# the operator's name and our app's id namespace. Resolved from the master
# reference athens_fixed_rail_station_coordinates.md so every station the
# Hellenic Train PDFs touch has a stable internal id (and therefore a map
# marker + station icon path in the apps).
STATION_ID_MAP = {
    # A1 + A4 shared southern corridor (Piraeus to Athens)
    "Piraeus":             "A1_PIR",
    "Pireas":              "A1_PIR",
    "Lefka":               "A1_LEF",
    "Rentis":              "A1_REN",
    "Tavros":              "A1_TAV",
    "Rouf":                "A1_ROU",
    "Athens":              "A1_ATH",
    # A1 northern Athens corridor
    "Ag. Anargyroi":       "A1_AGA",
    "Agioi Anargyroi":     "A1_AGA",
    "Pyrgos Vasilissis":   "A1_PYR",
    "Kato Acharnai":       "A1_KAA",
    "Metamorfosi":         "A1_MET",
    "Irakleio":            "A1_IRA",
    "Neratziotissa":       "A1_NER",
    "Kifisias":            "A1_KIF",
    "Pentelis":            "A1_PEN",
    "Doukissis Plakentias":"A1_DPL",
    "Pallini":             "A1_PAL",
    "Paiania-Kantza":      "A1_PAI",
    "Koropi":              "A1_KOR",
    "Airport":             "A1_AER",
    "SKA Airport Line":    "A1_SKA",
    "SKA":                 "A1_SKA",
    # A2 (Ano Liosia branch)
    "Ano Liosia":          "A2_LIO",
    "A. Liosia":           "A2_LIO",
    "Acharnai Railway Center": "A2_AKR",
    # A3 corridor (Athens to Chalkida) -- coords from master file
    "Acharnes":            "A3_ACH",
    "Dekeleia":            "A3_DEK",
    "Agios Stefanos":      "A3_AGS",
    "Afidnes":             "A3_AFI",
    "Sfendali":            "A3_SFE",
    "Avlonas":             "A3_AVL",
    "Agios Thomas":        "A3_AGT",
    "Oinofyta":            "A3_OIF",
    "Oinoi":               "A3_OIN",
    "Dilesi":              "A3_DIL",
    "Agios Georgios":      "A3_AGG",
    "Kalochori-Panteichi": "A3_KAL",
    "Kalochori- Panteichi":"A3_KAL",  # PDF spacing variant
    "Avlida":              "A3_AVI",
    "Chalkida":            "A3_CHA",
    # A4 corridor (Piraeus to Kiato) -- coords from master file
    "Zefiri":              "A4_ZEF",
    "Aspropyrgos":         "A4_ASP",
    "Magoula":             "A4_MAG",
    "Nea Peramos":         "A4_NEA",
    "Megara":              "A4_MEG",
    "Kinetta":             "A4_KIN",
    "Kineta":              "A4_KIN",
    "Ag. Theodoroi":       "A4_AGT",
    "Agioi Theodoroi":     "A4_AGT",
    "Corinth":             "A4_KOR",
    "Korinthos":           "A4_KOR",
    "Zevgolatio":          "A4_ZEU",
    "Kiato":               "A4_KIA",
    # A4 long-distance extensions on the Kiato-Aigio table
    "Diakopto":            "A4_DIA",
    "Aigio":               "A4_AIG",
    "Akrata":              "A4_AKR",
    "Xylokastro":          "A4_XYL",
    "Lygia":               "A4_LYG",
    "Lykoporia":           "A4_LYK",
    "Eliki":               "A4_ELI",
    "Platanos":            "A4_PLA",
    "Diminio":             "A4_DIM",
    "Kiato Άφιξη":         "A4_KIA",  # arrival marker, same node
    # Variants with footnote sequence already stripped by parser but kept here for safety
}


def classify_line(default_line: str, stops: list[dict]) -> str:
    """If a train uses the airport corridor but starts at Athens/Ano Liosia
    (not Piraeus), call it A2; otherwise keep the default line from the PDF."""
    if default_line != "A1":
        return default_line
    station_set = {s["station_en"] for s in stops}
    if "Piraeus" in station_set:
        return "A1"
    if "Athens" in station_set or "Ano Liosia" in station_set:
        return "A2"
    return "A1"


def main() -> None:
    if not PARSED_DIR.exists():
        print(f"parsed dir missing: {PARSED_DIR}", file=sys.stderr)
        sys.exit(1)

    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA foreign_keys = ON")
    conn.executescript(MIGRATION.read_text())

    # Clean slate for this batch; we treat the latest PDFs as authoritative.
    conn.execute("DELETE FROM train_timestamps")

    total = 0
    unmapped: set[str] = set()
    for filename, (default_line, direction, day_type) in LINE_BY_PDF.items():
        path = PARSED_DIR / filename
        if not path.exists():
            print(f"  missing {filename}", file=sys.stderr)
            continue
        with path.open() as f:
            for line in f:
                train = json.loads(line)
                line_id = classify_line(default_line, train["stops"])
                for seq, stop in enumerate(train["stops"]):
                    name_en = stop["station_en"]
                    station_id = STATION_ID_MAP.get(name_en)
                    if station_id is None:
                        unmapped.add(name_en)
                    conn.execute(
                        "INSERT INTO train_timestamps "
                        "(line_id, direction, day_type, train_no, station_id, "
                        " station_name_en, station_name_el, time, stop_sequence, source_pdf) "
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        (
                            line_id, direction, day_type,
                            train["train_no"], station_id,
                            name_en, stop["station_el"],
                            stop["time"], seq, train["source_pdf"],
                        ),
                    )
                    total += 1
    conn.commit()
    print(f"inserted {total} timestamp rows into {DB_PATH}")
    if unmapped:
        print(f"\nWARN: {len(unmapped)} station names had no internal ID:")
        for n in sorted(unmapped):
            print(f"  - {n}")


if __name__ == "__main__":
    main()
