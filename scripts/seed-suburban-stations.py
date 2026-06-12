"""Seed every suburban intermediate station from the master reference.

Resolves the 26 stations the train-timestamps ingest had to report as
unmapped, by inserting their (id, name_en, name_el, lat, lng) into the
stations table plus a line_stations row for each line they belong to.
Coordinates are copied verbatim from athens_fixed_rail_station_coordinates.md
so the apps' map markers, icons and search index pick them up the moment
the next generator publish runs.

Idempotent: uses INSERT OR REPLACE.
"""
from __future__ import annotations

import os
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB_PATH = os.environ.get(
    "SYRMOS_DB_PATH",
    str(ROOT / "ops" / "syrmos-api" / "data" / "syrmos.db"),
)

# (station_id, name_en, name_el, lat, lng)
STATIONS = [
    # A2 connector
    ("A2_AKR", "Acharnai Railway Center", "Σιδηροδρομικό Κέντρο Αχαρνών", 38.0656438, 23.7376508),
    # A3 (Athens - Chalkida)
    ("A3_ACH", "Acharnes",            "Αχαρνές",              38.0802534, 23.7440766),
    ("A3_DEK", "Dekeleia",            "Δεκέλεια",             38.0997540, 23.7801125),
    ("A3_AGS", "Agios Stefanos",      "Άγιος Στέφανος",       38.1403696, 23.8591873),
    ("A3_AFI", "Afidnes",             "Αφίδνες",              38.1883264, 23.8444658),
    ("A3_SFE", "Sfendali",            "Σφενδάλη",             38.2354158, 23.7844141),
    ("A3_AVL", "Avlonas",             "Αυλώνας",              38.2504724, 23.6955986),
    ("A3_AGT", "Agios Thomas",        "Άγιος Θωμάς",          38.2816770, 23.6672270),
    ("A3_OIF", "Oinofyta",            "Οινόφυτα",             38.3069654, 23.6338955),
    ("A3_OIN", "Oinoi",               "Οινόη",                38.3230172, 23.6090770),
    ("A3_DIL", "Dilesi",              "Δήλεσι",               38.3376364, 23.6094499),
    ("A3_AGG", "Agios Georgios",      "Άγιος Γεώργιος",       38.3548928, 23.6074074),
    ("A3_KAL", "Kalochori-Panteichi", "Καλοχώρι-Παντείχι",    38.3893073, 23.5931559),
    ("A3_AVI", "Avlida",              "Αυλίδα",               38.4044464, 23.6033835),
    ("A3_CHA", "Chalkida",            "Χαλκίδα",              38.4625271, 23.5861659),
    # A4 (Piraeus - Kiato + extension to Aigio)
    ("A4_ZEF", "Zefiri",              "Ζεφύρι",               38.0699579, 23.7163427),
    ("A4_ASP", "Aspropyrgos",         "Ασπρόπυργος",          38.0810388, 23.6042595),
    ("A4_MAG", "Magoula",             "Μαγούλα",              38.0730827, 23.5291665),
    ("A4_NEA", "Nea Peramos",         "Νέα Πέραμος",          38.0127986, 23.4132616),
    ("A4_MEG", "Megara",              "Μέγαρα",               37.9910006, 23.3610190),
    ("A4_KIN", "Kineta",              "Κινέτα",               37.9654426, 23.2010371),
    ("A4_AGT", "Agioi Theodoroi",     "Άγιοι Θεοδώροι",       37.9332405, 23.1369832),
    ("A4_KOR", "Korinthos",           "Κόρινθος",             37.9209680, 22.9323960),
    ("A4_ZEU", "Zevgolatio",          "Ζευγολατιό",           37.9263503, 22.8046326),
    ("A4_KIA", "Kiato",               "Κιάτο",                38.0139838, 22.7348102),
]

# (line_id, station_id, seq) -- master file lists stations in operational order.
LINE_STATIONS = [
    # A3 sequence per master file: 1 Athens, 2 Ag. Anargyroi, 3 SKA, 4 Acharnes,
    # 5 Dekeleia, 6 Agios Stefanos, 7 Afidnes, 8 Sfendali, 9 Avlonas,
    # 10 Agios Thomas, 11 Oinofyta, 12 Oinoi, 13 Dilesi, 14 Agios Georgios,
    # 15 Kalochori-Panteichi, 16 Avlida, 17 Chalkida.
    ("A3", "A3_ACH",  4), ("A3", "A3_DEK",  5),
    ("A3", "A3_AGS",  6), ("A3", "A3_AFI",  7), ("A3", "A3_SFE",  8),
    ("A3", "A3_AVL",  9), ("A3", "A3_AGT", 10), ("A3", "A3_OIF", 11),
    ("A3", "A3_OIN", 12), ("A3", "A3_DIL", 13), ("A3", "A3_AGG", 14),
    ("A3", "A3_KAL", 15), ("A3", "A3_AVI", 16), ("A3", "A3_CHA", 17),
    # A4 sequence per master file: 1 Piraeus ... 10 Zefiri, 11 Ano Liosia,
    # 12 Aspropyrgos, 13 Magoula, 14 Nea Peramos, 15 Megara, 16 Kineta,
    # 17 Agioi Theodoroi, 18 Korinthos, 19 Zevgolatio, 20 Kiato.
    ("A4", "A4_ZEF", 10),
    ("A4", "A4_ASP", 12), ("A4", "A4_MAG", 13), ("A4", "A4_NEA", 14),
    ("A4", "A4_MEG", 15), ("A4", "A4_KIN", 16), ("A4", "A4_AGT", 17),
    ("A4", "A4_KOR", 18), ("A4", "A4_ZEU", 19), ("A4", "A4_KIA", 20),
    # A2 connector
    ("A2", "A2_AKR", 2),
]


def main() -> None:
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA foreign_keys = ON")

    inserted = 0
    for sid, en, el, lat, lng in STATIONS:
        conn.execute(
            "INSERT OR REPLACE INTO stations (id, name_en, name_el, lat, lng) VALUES (?, ?, ?, ?, ?)",
            (sid, en, el, lat, lng),
        )
        inserted += 1

    for line_id, station_id, seq in LINE_STATIONS:
        conn.execute(
            "INSERT OR REPLACE INTO line_stations (line_id, station_id, seq, direction) VALUES (?, ?, ?, 'both')",
            (line_id, station_id, seq),
        )

    conn.commit()
    print(f"seeded {inserted} stations, {len(LINE_STATIONS)} line_station rows")


if __name__ == "__main__":
    main()
