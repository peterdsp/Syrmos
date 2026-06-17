"""Populate fare_products.title_sq + notes_sq with hand translations.

Why this exists: migration 0015 added the Sq columns but doesn't seed
them. This script matches rows by (section, title_en) and writes the
Albanian translation in place. Rows that already have title_sq set are
skipped so an admin override survives a re-run.

Run:
    cd ~/syrmos-api && \\
    SYRMOS_DB_PATH=db/syrmos.db PYTHONPATH=. \\
    .venv/bin/python3 scripts/seed_fare_products_albanian.py
"""
from __future__ import annotations

import sqlite3

from syrmos_admin import db as dbmod

# (section, title_en) -> (title_sq, notes_sq, validity_sq)
# Covers every product the OASA scraper currently produces. New products
# get an empty translation until added here — the client falls back to
# the English field when the Sq variant is blank, so no breakage either way.
TRANSLATIONS: dict[tuple[str, str], tuple[str, str, str]] = {
    ("single", "90-minute single ticket"): (
        "Biletë e vetme 90-minutëshe",
        "E vlefshme në metro, tramvaj dhe autobus. Përjashton linjat e Aeroportit dhe X80 ekspres.",
        "90 minuta",
    ),
    ("single", "Daily ticket"): (
        "Biletë ditore",
        "Udhëtime të pakufizuara për 24 orë nga validimi. Përjashton linjat e Aeroportit.",
        "24 orë",
    ),
    ("single", "5-day ticket"): (
        "Biletë 5-ditore",
        "Udhëtime të pakufizuara për 5 ditë nga validimi. Përjashton linjat e Aeroportit.",
        "5 ditë",
    ),
    ("single", "3-day tourist ticket"): (
        "Biletë turistike 3-ditore",
        "Udhëtime të pakufizuara për 3 ditë, përfshirë metron e Aeroportit, autobusin X95 Express dhe një udhëtim vajtje-ardhje në Aeroport.",
        "3 ditë, të gjitha linjat",
    ),
    ("offers", "Pack of 10 × 90-minute tickets"): (
        "Paketë 10 × bileta 90-minutëshe",
        "Dhjetë bileta 90-minutëshe me €1.20 secila, të paguara paraprakisht. Versioni me zbritje €0.60 secila.",
        "10 bileta",
    ),
    ("offers", "X95 Express bus single"): (
        "Biletë e vetme për autobusin X95 Express",
        "Biletë e vetme e vlefshme vetëm në autobusin X95 Athens Airport Express nga Syntagma.",
        "Vetëm X95",
    ),
    ("airport", "Airport single (metro M3)"): (
        "Biletë e vetme për Aeroportin (metro M3)",
        "Biletë e vetme për ose nga Aeroporti Ndërkombëtar i Athinës me metron M3.",
        "Vetëm Aeroport, M3",
    ),
    ("airport", "Airport metro from Pallini / Kantza / Koropi"): (
        "Metro për Aeroportin nga Pallini / Kantza / Koropi",
        "Biletë e reduktuar e zonës së Aeroportit nga tri stacionet e jashtme të M3 para Aeroportit.",
        "Vetëm, nga stacionet e jashtme M3",
    ),
    ("airport", "Airport round trip"): (
        "Vajtje-ardhje Aeroporti",
        "Biletë vajtje-ardhje për Aeroportin, e vlefshme brenda 48 orësh nga lëshimi.",
        "Vajtje-ardhje, 48 orë",
    ),
    ("airport", "Airport 3-day tourist ticket"): (
        "Biletë turistike 3-ditore për Aeroportin",
        "Njësoj si bileta turistike 3-ditore plus metroja e pakufizuar për Aeroportin dhe autobusi X95 Express.",
        "3 ditë, përfshirë Aeroportin",
    ),
    ("passes", "Monthly urban card"): (
        "Kartë mujore urbane",
        "Udhëtime urbane të pakufizuara për 30 ditë. Përjashton linjat e Aeroportit.",
        "30 ditë, urbane",
    ),
    ("passes", "Monthly card with Airport"): (
        "Kartë mujore me Aeroportin",
        "Udhëtime urbane të pakufizuara plus metroja e Aeroportit dhe X95 Express për 30 ditë.",
        "30 ditë, të gjitha",
    ),
    ("passes", "Annual urban card"): (
        "Kartë vjetore urbane",
        "Udhëtime urbane të pakufizuara për 365 ditë nga blerja. Përjashton linjat e Aeroportit.",
        "365 ditë, urbane",
    ),
    ("passes", "Annual card with Airport"): (
        "Kartë vjetore me Aeroportin",
        "Udhëtime urbane të pakufizuara plus metroja e Aeroportit dhe X95 Express për 365 ditë nga blerja.",
        "365 ditë, të gjitha",
    ),
}

# Operator-level notes (fares table).
FARES_NOTES_SQ: dict[str, str] = {
    "hellenic_train": (
        "Biletat e trenit periferik blihen në faqen e Hellenic Train ose në stacione. "
        "Pagesa pa kontakt nuk mbështetet ende në portat e trenit periferik."
    ),
    "oasa": (
        "Biletat e OASA mund të blihen në aparatet automatike, sportele, kioska "
        "të autorizuara dhe online në athenacard.gr."
    ),
}


def _lookup_translation(section: str, title_en: str) -> tuple[str, str, str] | None:
    """Match the OASA-scraped title against our hand-curated translation
    table. Tries (1) the exact (section, title) key, (2) a case-insensitive
    match — the parser flips between Title Case and ALL CAPS across
    revisions — and (3) a substring match against the canonical English
    titles, so a scraper that wrote 'METRO AIRPORT TICKET' instead of
    'Airport single (metro M3)' still gets translated."""
    exact = TRANSLATIONS.get((section, title_en))
    if exact:
        return exact
    needle = title_en.lower().strip()
    # Pass A: case-insensitive equality on (section, title)
    for (sec, t), tr in TRANSLATIONS.items():
        if sec == section and t.lower() == needle:
            return tr
    # Pass B: substring match keyed by salient tokens. We pin the
    # tokens once here rather than re-deriving so a parser that
    # capitalises differently from run to run still lands on the
    # right translation. Order matters — longer / more specific
    # phrases must precede their substrings.
    HEURISTIC: list[tuple[str, tuple[str, str]]] = [
        ("3-day tourist",            ("single", "3-day tourist ticket")),
        ("airport metro from",       ("airport", "Airport metro from Pallini / Kantza / Koropi")),
        ("airport round trip",       ("airport", "Airport round trip")),
        ("metro airport ticket",     ("airport", "Airport single (metro M3)")),
        ("airport 3-day tourist",    ("airport", "Airport 3-day tourist ticket")),
        ("90-minute",                ("single", "90-minute single ticket")),
        ("daily ticket",             ("single", "Daily ticket")),
        ("5-day",                    ("single", "5-day ticket")),
        ("x95 express",              ("offers", "X95 Express bus single")),
        ("pack of 10",               ("offers", "Pack of 10 × 90-minute tickets")),
        ("monthly urban",            ("passes", "Monthly urban card")),
        ("monthly card with airport",("passes", "Monthly card with Airport")),
        ("annual urban",             ("passes", "Annual urban card")),
        ("annual card with airport", ("passes", "Annual card with Airport")),
    ]
    for token, key in HEURISTIC:
        if token in needle:
            tr = TRANSLATIONS.get(key)
            if tr:
                return tr
    return None


def main() -> None:
    written = 0
    with dbmod.connect() as conn:
        # fare_products
        rows = conn.execute(
            "SELECT id, section, title_en, title_sq FROM fare_products"
        ).fetchall()
        for r in rows:
            tr = _lookup_translation(r["section"], r["title_en"])
            if not tr:
                print(f"  skip: no translation for {(r['section'], r['title_en'])}")
                continue
            if r["title_sq"]:
                continue  # respect admin override
            title_sq, notes_sq, validity_sq = tr
            try:
                conn.execute(
                    "UPDATE fare_products SET title_sq = ?, notes_sq = ?,"
                    " validity_sq = ? WHERE id = ?",
                    (title_sq, notes_sq, validity_sq, r["id"]),
                )
            except sqlite3.OperationalError:
                # Migration 0016 (validity_sq) not yet applied; degrade
                # to the 0015 column set so this still seeds title + notes.
                conn.execute(
                    "UPDATE fare_products SET title_sq = ?, notes_sq = ?"
                    " WHERE id = ?",
                    (title_sq, notes_sq, r["id"]),
                )
            written += 1
        # fares
        for r in conn.execute("SELECT operator_id, notes_sq FROM fares").fetchall():
            if r["notes_sq"]:
                continue
            tr = FARES_NOTES_SQ.get(r["operator_id"])
            if not tr:
                continue
            conn.execute(
                "UPDATE fares SET notes_sq = ? WHERE operator_id = ?",
                (tr, r["operator_id"]),
            )
            written += 1
    print(f"wrote {written} Albanian translations")


if __name__ == "__main__":
    main()
