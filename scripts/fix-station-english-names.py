"""Backfill English names for stations whose name_en still holds Greek.

The seed data populated name_el for every station but a number of rows
ended up with the Greek string copied into name_en too. The master
reference athens_fixed_rail_station_coordinates.md gives the canonical
English transliteration for every station, so this script writes those
back.

Run on the Pi:
    SYRMOS_DB_PATH=/home/peterdsp/syrmos-api/db/syrmos.db \\
    python3 scripts/fix-station-english-names.py
"""
from __future__ import annotations

import os
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB_PATH = os.environ.get(
    "SYRMOS_DB_PATH",
    str(ROOT / "ops" / "syrmos-api" / "data" / "syrmos.db"),
)

# Greek (Greek-letter) -> canonical English transliteration. Sourced from the
# per-line tables in athens_fixed_rail_station_coordinates.md (lines 122-386).
# Every entry below maps the lowercased Greek form to the English form the
# user-facing apps already use elsewhere.
GREEK_TO_EN: dict[str, str] = {
    # M1
    "νεραντζιώτισσα": "Neratziotissa",
    "νερατζιώτισσα":  "Neratziotissa",  # alternate spelling used on A1/A2
    # M2
    "σταθμός λαρίσης": "Larissa Station",
    # M3 city
    "δημοτικό θέατρο": "Dimotiko Theatro",
    "μανιάτικα":        "Maniatika",
    "νίκαια":           "Nikaia",
    "δουκίσσης πλακεντίας": "Doukissis Plakentias",
    "παλλήνη":          "Pallini",
    "κορωπί":           "Koropi",
    "αεροδρόμιο":       "Airport",
    # T6
    "νέος κόσμος":               "Neos Kosmos",
    "αγίας φωτεινής-πλατεία":    "Aghias Foteinis Plateia",
    # T7
    "δημαρχείο":        "Dimarheio",
    "δημαρχείο / δημοτικό θέατρο": "Dimarheio / Dimotiko Theatro",
    "πλατεία δεληγιάννη": "Plateia Deligianni",
    "πλατεία ιπποδαμείας": "Plateia Ippodameias",
    "πλατεία βεργωτή":  "Platia Vergoti",
    "πλατεία βάσω κατράκη": "Platia Vaso Katraki",
    "πλατεία εσπερίδων": "Platia Esperidon",
    "παλαιό δημαρχείο": "Paleo Demarhio",
    # Suburban A1 / A4 shared corridor (Piraeus -> Athens)
    "λεύκα":            "Lefka",
    "ρέντης":           "Rentis",
    "ταύρος":           "Tavros",
    "ρουφ":             "Rouf",
    "αθήνα":            "Athens",
    "άγιοι ανάργυροι":  "Agioi Anargyroi",
    "πύργος βασιλίσσης":"Pyrgos Vasilissis",
    "κάτω αχαρναί":     "Kato Acharnai",
    "μεταμόρφωση":      "Metamorfosi",
    "ηράκλειο":         "Irakleio",
    "κηφισίας":         "Kifisias",
    "πεντέλης":         "Pentelis",
    "παιανία-κάντζα":   "Paiania-Kantza",
    # A2 connector
    "άνω λιόσια":             "Ano Liosia",
    "σιδηροδρομικό κέντρο αχαρνών": "Acharnai Railway Center",
    # A3 corridor
    "αχαρνές":          "Acharnes",
    "δεκέλεια":         "Dekeleia",
    "άγιος στέφανος":   "Agios Stefanos",
    "αφίδνες":          "Afidnes",
    "σφενδάλη":         "Sfendali",
    "αυλώνας":          "Avlonas",
    "άγιος θωμάς":      "Agios Thomas",
    "οινόφυτα":         "Oinofyta",
    "οινόη":            "Oinoi",
    "δήλεσι":           "Dilesi",
    "άγιος γεώργιος":   "Agios Georgios",
    "καλοχώρι-παντείχι":"Kalochori-Panteichi",
    "αυλίδα":           "Avlida",
    "χαλκίδα":          "Chalkida",
    # A4 corridor
    "ζεφύρι":           "Zefiri",
    "ασπρόπυργος":      "Aspropyrgos",
    "μαγούλα":          "Magoula",
    "νέα πέραμος":      "Nea Peramos",
    "μέγαρα":           "Megara",
    "κινέτα":           "Kineta",
    "άγιοι θεοδώροι":   "Agioi Theodoroi",
    "κόρινθος":         "Korinthos",
    "ζευγολατιό":       "Zevgolatio",
    "κιάτο":            "Kiato",
}


_GREEK_RANGE = re.compile(r"[Ͱ-Ͽἀ-῿]")


def has_greek(s: str | None) -> bool:
    return bool(s and _GREEK_RANGE.search(s))


def main() -> None:
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA foreign_keys = ON")

    rows = conn.execute("SELECT id, name_en, name_el FROM stations").fetchall()
    fixed = 0
    unmapped: list[tuple[str, str]] = []
    for sid, name_en, name_el in rows:
        if not has_greek(name_en):
            continue
        key = (name_en or "").strip().lower()
        replacement = GREEK_TO_EN.get(key)
        if not replacement:
            # Try the Greek-language name_el if name_en wasn't recognized
            key2 = (name_el or "").strip().lower()
            replacement = GREEK_TO_EN.get(key2)
        if not replacement:
            unmapped.append((sid, name_en))
            continue
        conn.execute(
            "UPDATE stations SET name_en=? WHERE id=?",
            (replacement, sid),
        )
        fixed += 1
    conn.commit()

    print(f"fixed name_en for {fixed} stations")
    if unmapped:
        print(f"\n{len(unmapped)} stations still need an English name:")
        for sid, raw in unmapped:
            print(f"  {sid}: {raw!r}")


if __name__ == "__main__":
    main()
