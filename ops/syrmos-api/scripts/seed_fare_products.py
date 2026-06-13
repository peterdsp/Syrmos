"""Seed OASA fare products into the fare_products table.

Why this exists: the apps' Tickets screen renders from
`/api/fares` → `products`. Until OASA publishes a machine-readable
prices feed (their site currently ships prices via images and JS), we
ship a stable manual seed that the admin UI can override per-row.

The seed is idempotent: each row is keyed by `(section, sort_order)`
so re-running upserts without duplicating.

Source: https://www.oasa.gr/en/tickets/prices-of-products/ — verify on
that page before bumping a price. The UI already links there with the
disclaimer "Prices are provided by OASA. For the authoritative figure,
check the official page."

Run: cd ~/syrmos-api && .venv/bin/python -m scripts.seed_fare_products
"""
from __future__ import annotations

from syrmos_admin import db as dbmod

OASA_URL = "https://www.oasa.gr/en/tickets/prices-of-products/"
SOURCE_URL = OASA_URL

# (section, sort_order, title_en, title_el, full_price_eur,
#  discounted_price_eur, validity, notes, tags)
PRODUCTS = [
    # SINGLE
    ("single", 1,
     "90-minute single ticket",
     "Εισιτήριο 90 λεπτών",
     1.20, 0.60,
     "90 minutes",
     "Valid on metro, tram and bus. Excludes Airport routes and X80 express.",
     "excludes_airport"),
    ("single", 2,
     "Daily ticket",
     "Ημερήσιο εισιτήριο",
     4.10, 2.05,
     "24 hours",
     "Unlimited travel for 24 hours from validation. Excludes Airport routes.",
     "excludes_airport"),
    ("single", 3,
     "5-day ticket",
     "Εισιτήριο 5 ημερών",
     8.20, 4.10,
     "5 days",
     "Unlimited travel for 5 days from validation. Excludes Airport routes.",
     "excludes_airport"),
    ("single", 4,
     "3-day tourist ticket",
     "Τουριστικό 3 ημερών",
     20.00, None,
     "3 days, all routes",
     "Unlimited travel for 3 days including Airport metro, X95 Airport Express bus and all urban lines.",
     "tourist,airport_included"),

    # AIRPORT
    ("airport", 1,
     "Airport single (metro M3)",
     "Εισιτήριο Αεροδρομίου (Μετρό Γρ. 3)",
     9.00, 4.50,
     "Single, M3 Airport",
     "Single ticket to or from Athens International Airport via metro M3.",
     "airport"),
    ("airport", 2,
     "Airport metro from Pallini / Kantza / Koropi",
     "Μετρό Αεροδρομίου από Παλλήνη / Κάντζα / Κορωπί",
     6.00, 3.00,
     "Single, from M3 outer",
     "Reduced airport-zone single from the three outer M3 stations before the airport.",
     "airport"),
    ("airport", 3,
     "Airport round trip",
     "Εισιτήριο μετ' επιστροφής Αεροδρομίου",
     16.00, None,
     "Round trip, 48h",
     "Return airport ticket valid within 48 hours of issue.",
     "airport,return"),
    ("airport", 4,
     "Airport 3-day tourist ticket",
     "Τουριστικό 3 ημερών Αεροδρομίου",
     22.00, None,
     "3 days incl. Airport",
     "Same as the 3-day tourist ticket plus unlimited Airport metro and X95 Express bus.",
     "tourist,airport_included"),

    # OFFERS
    ("offers", 1,
     "Pack of 10 × 90-minute tickets",
     "Πακέτο 10 × 90 λεπτών",
     12.00, 6.00,
     "10 tickets",
     "Ten 90-minute tickets at €1.20 each, paid up front. Reduced version €0.60 each.",
     "pack"),
    ("offers", 2,
     "X95 Express bus single",
     "X95 Express εισιτήριο",
     5.50, None,
     "X95 only",
     "Single ticket valid only on the X95 Athens Airport Express bus from Syntagma.",
     "airport_bus"),

    # PASSES (monthly + annual)
    ("passes", 1,
     "Monthly urban card",
     "Μηνιαία κάρτα αστικών",
     30.00, 15.00,
     "30 days, urban",
     "Unlimited urban travel for 30 days. Excludes Airport routes.",
     "monthly,excludes_airport"),
    ("passes", 2,
     "Monthly card with Airport",
     "Μηνιαία κάρτα με Αεροδρόμιο",
     60.50, 30.25,
     "30 days, all",
     "Unlimited urban plus Airport metro and X95 Express for 30 days.",
     "monthly,airport_included"),
    ("passes", 3,
     "Annual urban card",
     "Ετήσια κάρτα αστικών",
     270.00, None,
     "365 days, urban",
     "Unlimited urban travel for 365 days from purchase. Excludes Airport routes.",
     "annual,excludes_airport"),
    ("passes", 4,
     "Annual card with Airport",
     "Ετήσια κάρτα με Αεροδρόμιο",
     590.00, None,
     "365 days, all",
     "Unlimited urban plus Airport metro and X95 Express for 365 days from purchase.",
     "annual,airport_included"),
]


def main() -> None:
    with dbmod.connect() as conn:
        dbmod.migrate(conn)
        # Wipe and re-seed so dropped products go away too. Admin UI overrides
        # belong on a separate row; this table is OASA-canonical only.
        conn.execute("DELETE FROM fare_products")
        for section, sort_order, t_en, t_el, full, disc, validity, notes, tags in PRODUCTS:
            conn.execute(
                "INSERT INTO fare_products(section, sort_order, title_en, title_el,"
                " full_price_eur, discounted_price_eur, validity, notes, tags, source_url)"
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (section, sort_order, t_en, t_el, full, disc, validity, notes, tags, SOURCE_URL),
            )
        count = conn.execute("SELECT COUNT(*) FROM fare_products").fetchone()[0]
        print(f"seeded {count} OASA fare products")


if __name__ == "__main__":
    main()
