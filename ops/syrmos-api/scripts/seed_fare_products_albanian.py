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

from syrmos_admin import db as dbmod

# (section, title_en) -> (title_sq, notes_sq)
# Covers every product the OASA scraper currently produces. New products
# will simply get an empty title_sq until added here — the client falls
# back to title_en when title_sq is blank, so no breakage either way.
TRANSLATIONS: dict[tuple[str, str], tuple[str, str]] = {
    ("single", "90-minute single ticket"): (
        "Biletë e vetme 90-minutëshe",
        "E vlefshme në metro, tramvaj dhe autobus. Përjashton linjat e Aeroportit dhe X80 ekspres.",
    ),
    ("single", "Daily ticket"): (
        "Biletë ditore",
        "Udhëtime të pakufizuara për 24 orë nga validimi. Përjashton linjat e Aeroportit.",
    ),
    ("single", "5-day ticket"): (
        "Biletë 5-ditore",
        "Udhëtime të pakufizuara për 5 ditë nga validimi. Përjashton linjat e Aeroportit.",
    ),
    ("single", "3-day tourist ticket"): (
        "Biletë turistike 3-ditore",
        "Udhëtime të pakufizuara për 3 ditë, përfshirë metron e Aeroportit, autobusin X95 Express dhe një udhëtim vajtje-ardhje në Aeroport.",
    ),
    ("offers", "Pack of 10 × 90-minute tickets"): (
        "Paketë 10 × bileta 90-minutëshe",
        "Dhjetë bileta 90-minutëshe me €1.20 secila, të paguara paraprakisht. Versioni me zbritje €0.60 secila.",
    ),
    ("offers", "X95 Express bus single"): (
        "Biletë e vetme për autobusin X95 Express",
        "Biletë e vetme e vlefshme vetëm në autobusin X95 Athens Airport Express nga Syntagma.",
    ),
    ("airport", "Airport single (metro M3)"): (
        "Biletë e vetme për Aeroportin (metro M3)",
        "Biletë e vetme për ose nga Aeroporti Ndërkombëtar i Athinës me metron M3.",
    ),
    ("airport", "Airport metro from Pallini / Kantza / Koropi"): (
        "Metro për Aeroportin nga Pallini / Kantza / Koropi",
        "Biletë e reduktuar e zonës së Aeroportit nga tri stacionet e jashtme të M3 para Aeroportit.",
    ),
    ("airport", "Airport round trip"): (
        "Vajtje-ardhje Aeroporti",
        "Biletë vajtje-ardhje për Aeroportin, e vlefshme brenda 48 orësh nga lëshimi.",
    ),
    ("airport", "Airport 3-day tourist ticket"): (
        "Biletë turistike 3-ditore për Aeroportin",
        "Njësoj si bileta turistike 3-ditore plus metroja e pakufizuar për Aeroportin dhe autobusi X95 Express.",
    ),
    ("passes", "Monthly urban card"): (
        "Kartë mujore urbane",
        "Udhëtime urbane të pakufizuara për 30 ditë. Përjashton linjat e Aeroportit.",
    ),
    ("passes", "Monthly card with Airport"): (
        "Kartë mujore me Aeroportin",
        "Udhëtime urbane të pakufizuara plus metroja e Aeroportit dhe X95 Express për 30 ditë.",
    ),
    ("passes", "Annual urban card"): (
        "Kartë vjetore urbane",
        "Udhëtime urbane të pakufizuara për 365 ditë nga blerja. Përjashton linjat e Aeroportit.",
    ),
    ("passes", "Annual card with Airport"): (
        "Kartë vjetore me Aeroportin",
        "Udhëtime urbane të pakufizuara plus metroja e Aeroportit dhe X95 Express për 365 ditë nga blerja.",
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


def main() -> None:
    written = 0
    with dbmod.connect() as conn:
        # fare_products
        rows = conn.execute(
            "SELECT id, section, title_en, title_sq FROM fare_products"
        ).fetchall()
        for r in rows:
            key = (r["section"], r["title_en"])
            tr = TRANSLATIONS.get(key)
            if not tr:
                print(f"  skip: no translation for {key}")
                continue
            if r["title_sq"]:
                continue  # respect admin override
            title_sq, notes_sq = tr
            conn.execute(
                "UPDATE fare_products SET title_sq = ?, notes_sq = ? WHERE id = ?",
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
