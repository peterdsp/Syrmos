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
OSETH_URL = "https://oseth.com.gr/en/tickets"
HT_PATRAS_URL = "https://www.hellenictrain.gr/en/patras-suburban-railway"
HT_BOOK_URL = "https://newtickets.hellenictrain.gr/"
SOURCE_URL = OASA_URL

# (section, sort_order, title_en, title_el, full_price_eur,
#  discounted_price_eur, validity, notes, tags)
PRODUCTS = [
    # SINGLE
    ("single", 1,
     "90-minute single ticket",
     "Εισιτήριο 90 λεπτών",
     1.20, 0.50,
     "90 minutes",
     "Valid on metro, tram and bus. Excludes Airport routes and X80 express.",
     "excludes_airport"),
    ("single", 2,
     "Daily ticket",
     "Ημερήσιο εισιτήριο",
     4.10, None,
     "24 hours",
     "Unlimited travel for 24 hours from validation. Excludes Airport routes.",
     "excludes_airport"),
    ("single", 3,
     "5-day ticket",
     "Εισιτήριο 5 ημερών",
     8.20, None,
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
     5.50, 2.70,
     "Single, from M3 outer",
     "Airport metro single from or to Pallini, Kantza, or Koropi.",
     "airport"),
    ("airport", 3,
     "Airport round trip",
     "Εισιτήριο μετ' επιστροφής Αεροδρομίου",
     16.00, None,
     "Round trip, 30 days",
     "Return airport ticket valid within 30 days of issue.",
     "airport,return"),
    ("airport", 4,
     "Airport 3-day tourist ticket",
     "Τουριστικό 3 ημερών Αεροδρομίου",
     20.00, None,
     "3 days incl. Airport",
     "Unlimited urban travel plus two trips from or to the Airport by metro or Airport Express bus.",
     "tourist,airport_included"),

    # OFFERS
    ("offers", 1, "2-ticket pack", "Πακέτο 2 εισιτηρίων",
     2.30, 1.00, "2 tickets", "Two 90-minute tickets. Reduced pack requires a personalized ATH.ENA Card.", "pack"),
    ("offers", 2, "5-ticket pack", "Πακέτο 5 εισιτηρίων",
     5.70, 2.50, "5 tickets", "Five 90-minute tickets. Reduced pack requires a personalized ATH.ENA Card.", "pack"),
    ("offers", 3, "10+1 ticket pack", "Πακέτο 10+1 εισιτηρίων",
     12.00, 5.00, "11 journeys", "Ten 90-minute tickets plus one bonus journey.", "pack"),
    ("offers", 4, "Airport Express bus single", "Εισιτήριο Airport Express λεωφορείου",
     5.50, 2.70, "one journey", "One journey from or to the Airport on an Airport Express bus line.", "airport_bus"),

    # PASSES
    ("passes", 1, "30-day urban pass", "Κάρτα 30 ημερών αστικών",
     27.00, 13.50, "30 days, urban", "Unlimited urban travel. Excludes Airport routes and X80.", "monthly,excludes_airport"),
    ("passes", 2, "90-day urban pass", "Κάρτα 90 ημερών αστικών",
     78.00, 39.00, "90 days, urban", "Unlimited urban travel. Excludes Airport routes and X80.", "excludes_airport"),
    ("passes", 3, "180-day urban pass", "Κάρτα 180 ημερών αστικών",
     155.00, 77.50, "180 days, urban", "Unlimited urban travel. Excludes Airport routes and X80.", "excludes_airport"),
    ("passes", 4, "365-day urban pass", "Κάρτα 365 ημερών αστικών",
     300.00, 150.00, "365 days, urban", "Unlimited urban travel. Excludes Airport routes and X80.", "annual,excludes_airport"),
    ("passes", 5, "30-day pass with Airport", "Κάρτα 30 ημερών με Αεροδρόμιο",
     45.00, 22.50, "30 days, Airport included", "Unlimited urban travel including Airport metro and Airport Express buses. Excludes X80.", "monthly,airport_included"),
    ("passes", 6, "90-day pass with Airport", "Κάρτα 90 ημερών με Αεροδρόμιο",
     129.00, 64.50, "90 days, Airport included", "Unlimited urban travel including Airport metro and Airport Express buses. Excludes X80.", "airport_included"),
    ("passes", 7, "180-day pass with Airport", "Κάρτα 180 ημερών με Αεροδρόμιο",
     228.00, 114.00, "180 days, Airport included", "Unlimited urban travel including Airport metro and Airport Express buses. Excludes X80.", "airport_included"),
    ("passes", 8, "365-day pass with Airport", "Κάρτα 365 ημερών με Αεροδρόμιο",
     446.00, 223.00, "365 days, Airport included", "Unlimited urban travel including Airport metro, Airport Express buses, and X80.", "annual,airport_included"),

    # ---- Thessaloniki (OSETH). Metro + bus not yet interoperable; single-mode.
    # Source: https://oseth.com.gr (fetched 2026-07-27). 10th field = source_url.
    ("thessaloniki", 1, "Urban single ticket", "Αστικό εισιτήριο",
     0.60, 0.30, "70 minutes", "Thessaloniki metro or city bus (separate products - not yet interoperable).",
     "thessaloniki", OSETH_URL),
    ("thessaloniki", 2, "Suburban (peri-urban) single", "Υπεραστικό εισιτήριο",
     0.80, 0.40, "70 minutes", "Thessaloniki peri-urban zone.", "thessaloniki,suburban", OSETH_URL),
    ("thessaloniki", 3, "Special bus (X1/X2 Airport, Route 50)", "Ειδική γραμμή (X1/X2 Αεροδρ., 50)",
     2.00, 1.00, "single", "Airport express X1/X2 and cultural Route 50.", "thessaloniki,airport", OSETH_URL),
    ("thessaloniki", 4, "24-hour ticket", "Ημερήσιο 24 ωρών",
     2.50, None, "24 hours", "Unlimited single-mode travel for 24 hours.", "thessaloniki", OSETH_URL),
    ("thessaloniki", 5, "Monthly card", "Μηνιαία κάρτα",
     16.00, 8.00, "30 days", "Unlimited bus travel for 30 days, including Airport, Cultural, and Express routes.", "thessaloniki,monthly", OSETH_URL),
    ("thessaloniki", 6, "10+1 urban ticket pack", "Πακέτο 10+1 αστικών εισιτηρίων",
     5.80, 2.90, "11 journeys", "Each ticket allows urban-zone bus travel for 70 minutes.", "thessaloniki,pack", OSETH_URL),
    ("thessaloniki", 7, "10+1 peri-urban ticket pack", "Πακέτο 10+1 περιαστικών εισιτηρίων",
     7.80, 3.90, "11 journeys", "Each ticket allows urban and peri-urban bus travel for 70 minutes.", "thessaloniki,pack,suburban", OSETH_URL),
    ("thessaloniki", 8, "90-day bus card", "Κάρτα λεωφορείων 90 ημερών",
     45.00, 22.50, "90 days", "Unlimited bus travel, including Airport, Cultural, and Express routes.", "thessaloniki,pass", OSETH_URL),
    ("thessaloniki", 9, "180-day bus card", "Κάρτα λεωφορείων 180 ημερών",
     85.00, 42.50, "180 days", "Unlimited bus travel, including Airport, Cultural, and Express routes.", "thessaloniki,pass", OSETH_URL),

    # ---- Patras suburban (Hellenic Train) zone grid A1/A/B/C.
    # Source: HT Patras Suburban map PDF (2025-07). 10th field = source_url.
    ("patras", 1, "Suburban ticket (single zone A1/A/B/C)", "Εισιτήριο προαστιακού (μία ζώνη)",
     1.40, 1.00, "single zone", "Patras suburban within one zone (A1, A, B or C).", "patras", HT_PATRAS_URL),
    ("patras", 2, "Suburban ticket (two zones)", "Εισιτήριο προαστιακού (δύο ζώνες)",
     2.00, 1.00, "A+B or B+C", "Patras suburban across two adjacent zones.", "patras", HT_PATRAS_URL),
    ("patras", 3, "Suburban ticket (all zones A+B+C)", "Εισιτήριο προαστιακού (όλες οι ζώνες)",
     3.00, 1.40, "A+B+C", "Patras suburban across the full network (e.g. to Kato Achaia).", "patras", HT_PATRAS_URL),
    ("patras", 4, "Monthly card (single zone)", "Μηνιαία κάρτα (μία ζώνη)",
     25.00, 15.00, "30 days, zone A1", "Patras suburban monthly, single zone (A1 shown; A/B/C €30/€20).", "patras,monthly", HT_PATRAS_URL),

    # ---- Intercity / regional (Hellenic Train). Current standard one-way
    # references observed in the official booking search on 2026-08-05.
    # Source: https://www.hellenictrain.gr/en/tickets-ticket-discounts-offers.
    ("intercity", 1, "Intercity / regional ticket", "Εισιτήριο υπεραστικό / περιφερειακό",
     None, None, "price set at booking",
     "Booking-time price (route, class, date). Discounts: early-booking up to 15%, return 20%, "
     "students 25-50%, youth <24 25%, children 4-12 50%, reduced mobility 50% (total capped at 40%). "
     "Rail-replacement buses use the same ticket as the segment they cover.",
     "intercity,dynamic", HT_BOOK_URL),
    ("intercity", 2, "Athens - Thessaloniki", "Αθήνα - Θεσσαλονίκη",
     43.00, None, "one-way reference, checked 2026-08-05", "Approximate standard one-way fare observed in official booking. Verify the exact train, date, class, and discount before purchase.", "intercity,reference", HT_BOOK_URL),
    ("intercity", 3, "Athens - Larisa", "Αθήνα - Λάρισα",
     32.50, None, "one-way reference, checked 2026-08-05", "Approximate standard one-way fare observed in official booking. Verify before purchase.", "intercity,reference", HT_BOOK_URL),
    ("intercity", 4, "Athens - Trikala", "Αθήνα - Τρίκαλα",
     29.50, None, "one-way reference, checked 2026-08-05", "Approximate standard one-way fare observed in official booking. Includes the offered connection and must be verified before purchase.", "intercity,reference", HT_BOOK_URL),
    ("intercity", 5, "Athens - Kalambaka", "Αθήνα - Καλαμπάκα",
     30.90, None, "one-way reference, checked 2026-08-05", "Approximate standard one-way fare observed in official booking. Includes the offered connection and must be verified before purchase.", "intercity,reference", HT_BOOK_URL),
    ("intercity", 6, "Thessaloniki - Larisa", "Θεσσαλονίκη - Λάρισα",
     14.00, None, "one-way reference, checked 2026-08-05", "Approximate standard one-way fare observed in official booking. Verify before purchase.", "intercity,reference", HT_BOOK_URL),
    ("intercity", 7, "Trikala - Kalambaka", "Τρίκαλα - Καλαμπάκα",
     1.80, None, "one-way reference, checked 2026-08-05", "Approximate standard one-way fare observed in official booking. The current service may be a rail-replacement bus. Verify before purchase.", "intercity,reference", HT_BOOK_URL),
]

TITLE_SQ = {
    "90-minute single ticket": "Biletë e vetme 90-minutëshe",
    "Daily ticket": "Biletë ditore",
    "5-day ticket": "Biletë 5-ditore",
    "3-day tourist ticket": "Biletë turistike 3-ditore",
    "Airport single (metro M3)": "Biletë e vetme për aeroportin (metro M3)",
    "Airport metro from Pallini / Kantza / Koropi": "Metro për aeroportin nga Pallini / Kantza / Koropi",
    "Airport round trip": "Biletë vajtje-ardhje për aeroportin",
    "Airport 3-day tourist ticket": "Biletë turistike 3-ditore me aeroport",
    "2-ticket pack": "Paketë me 2 bileta",
    "5-ticket pack": "Paketë me 5 bileta",
    "10+1 ticket pack": "Paketë me 10+1 bileta",
    "Airport Express bus single": "Biletë e vetme për autobusin Airport Express",
    "30-day urban pass": "Abonim urban 30-ditor",
    "90-day urban pass": "Abonim urban 90-ditor",
    "180-day urban pass": "Abonim urban 180-ditor",
    "365-day urban pass": "Abonim urban 365-ditor",
    "30-day pass with Airport": "Abonim 30-ditor me aeroport",
    "90-day pass with Airport": "Abonim 90-ditor me aeroport",
    "180-day pass with Airport": "Abonim 180-ditor me aeroport",
    "365-day pass with Airport": "Abonim 365-ditor me aeroport",
    "Urban single ticket": "Biletë e vetme urbane",
    "Suburban (peri-urban) single": "Biletë e vetme periferike",
    "Special bus (X1/X2 Airport, Route 50)": "Autobus special (X1/X2 Aeroport, linja 50)",
    "24-hour ticket": "Biletë 24-orëshe",
    "Monthly card": "Kartë mujore",
    "10+1 urban ticket pack": "Paketë me 10+1 bileta urbane",
    "10+1 peri-urban ticket pack": "Paketë me 10+1 bileta periferike",
    "90-day bus card": "Kartë autobusi 90-ditore",
    "180-day bus card": "Kartë autobusi 180-ditore",
    "Suburban ticket (single zone A1/A/B/C)": "Biletë periferike (një zonë A1/A/B/C)",
    "Suburban ticket (two zones)": "Biletë periferike (dy zona)",
    "Suburban ticket (all zones A+B+C)": "Biletë periferike (të gjitha zonat A+B+C)",
    "Monthly card (single zone)": "Kartë mujore (një zonë)",
    "Intercity / regional ticket": "Biletë ndërqytetëse / rajonale",
    "Athens - Thessaloniki": "Athinë - Selanik",
    "Athens - Larisa": "Athinë - Larisa",
    "Athens - Trikala": "Athinë - Trikala",
    "Athens - Kalambaka": "Athinë - Kalambaka",
    "Thessaloniki - Larisa": "Selanik - Larisa",
    "Trikala - Kalambaka": "Trikala - Kalambaka",
}

TITLE_IT = {
    "90-minute single ticket": "Biglietto singolo da 90 minuti",
    "Daily ticket": "Biglietto giornaliero",
    "5-day ticket": "Biglietto da 5 giorni",
    "3-day tourist ticket": "Biglietto turistico da 3 giorni",
    "Airport single (metro M3)": "Biglietto singolo aeroporto (metro M3)",
    "Airport metro from Pallini / Kantza / Koropi": "Metro aeroporto da Pallini / Kantza / Koropi",
    "Airport round trip": "Andata e ritorno aeroporto",
    "Airport 3-day tourist ticket": "Biglietto turistico 3 giorni con aeroporto",
    "2-ticket pack": "Pacchetto da 2 biglietti",
    "5-ticket pack": "Pacchetto da 5 biglietti",
    "10+1 ticket pack": "Pacchetto da 10+1 biglietti",
    "Airport Express bus single": "Biglietto singolo autobus Airport Express",
    "30-day urban pass": "Abbonamento urbano 30 giorni",
    "90-day urban pass": "Abbonamento urbano 90 giorni",
    "180-day urban pass": "Abbonamento urbano 180 giorni",
    "365-day urban pass": "Abbonamento urbano 365 giorni",
    "30-day pass with Airport": "Abbonamento 30 giorni con aeroporto",
    "90-day pass with Airport": "Abbonamento 90 giorni con aeroporto",
    "180-day pass with Airport": "Abbonamento 180 giorni con aeroporto",
    "365-day pass with Airport": "Abbonamento 365 giorni con aeroporto",
    "Urban single ticket": "Biglietto singolo urbano",
    "Suburban (peri-urban) single": "Biglietto singolo suburbano",
    "Special bus (X1/X2 Airport, Route 50)": "Autobus speciale (X1/X2 aeroporto, linea 50)",
    "24-hour ticket": "Biglietto 24 ore",
    "Monthly card": "Abbonamento mensile",
    "10+1 urban ticket pack": "Pacchetto da 10+1 biglietti urbani",
    "10+1 peri-urban ticket pack": "Pacchetto da 10+1 biglietti suburbani",
    "90-day bus card": "Abbonamento autobus 90 giorni",
    "180-day bus card": "Abbonamento autobus 180 giorni",
    "Suburban ticket (single zone A1/A/B/C)": "Biglietto suburbano (una zona A1/A/B/C)",
    "Suburban ticket (two zones)": "Biglietto suburbano (due zone)",
    "Suburban ticket (all zones A+B+C)": "Biglietto suburbano (tutte le zone A+B+C)",
    "Monthly card (single zone)": "Abbonamento mensile (una zona)",
    "Intercity / regional ticket": "Biglietto intercity / regionale",
    "Athens - Thessaloniki": "Atene - Salonicco",
    "Athens - Larisa": "Atene - Larissa",
    "Athens - Trikala": "Atene - Trikala",
    "Athens - Kalambaka": "Atene - Kalambaka",
    "Thessaloniki - Larisa": "Salonicco - Larissa",
    "Trikala - Kalambaka": "Trikala - Kalambaka",
}


def localized_validity(validity: str) -> tuple[str, str]:
    sq = validity.replace("days", "ditë").replace("hours", "orë").replace("minutes", "minuta")
    it = validity.replace("days", "giorni").replace("hours", "ore").replace("minutes", "minuti")
    replacements = {
        "tickets": ("bileta", "biglietti"),
        "journeys": ("udhëtime", "viaggi"),
        "one journey": ("një udhëtim", "un viaggio"),
        "single zone": ("një zonë", "una zona"),
        "price set at booking": ("çmimi në rezervim", "prezzo alla prenotazione"),
        "one-way reference, checked 2026-08-05": (
            "referencë vetëm vajtje, kontrolluar më 2026-08-05",
            "riferimento sola andata, verificato il 2026-08-05",
        ),
    }
    if validity in replacements:
        return replacements[validity]
    for source, (sq_value, it_value) in replacements.items():
        sq = sq.replace(source, sq_value)
        it = it.replace(source, it_value)
    sq = sq.replace("urban", "urban").replace("Airport included", "aeroporti i përfshirë")
    it = it.replace("urban", "urbano").replace("Airport included", "aeroporto incluso")
    return sq, it


def localized_notes(section: str, tags: str) -> tuple[str, str, str]:
    if section == "intercity" and "reference" in tags:
        return (
            "Ενδεικτική τιμή απλής διαδρομής από την επίσημη κράτηση. Επιβεβαίωσε τρένο, ημερομηνία, θέση και έκπτωση πριν την αγορά.",
            "Çmim orientues vetëm vajtje nga rezervimi zyrtar. Verifiko trenin, datën, klasën dhe zbritjen para blerjes.",
            "Tariffa indicativa di sola andata dalla prenotazione ufficiale. Verifica treno, data, classe e sconto prima dell'acquisto.",
        )
    if section == "intercity":
        return (
            "Η τελική τιμή ορίζεται στην κράτηση ανά διαδρομή, ημερομηνία, θέση και έκπτωση.",
            "Çmimi përfundimtar caktohet gjatë rezervimit sipas rrugës, datës, klasës dhe zbritjes.",
            "Il prezzo finale viene calcolato alla prenotazione in base a tratta, data, classe e sconto.",
        )
    if section == "thessaloniki":
        return (
            "Επίσημο προϊόν κομίστρου OSETH. Έλεγξε τη ζώνη, τη διάρκεια και τις ειδικές γραμμές πριν τη χρήση.",
            "Produkt zyrtar tarife OSETH. Kontrollo zonën, kohëzgjatjen dhe linjat speciale para përdorimit.",
            "Prodotto tariffario ufficiale OSETH. Verifica zona, durata e linee speciali prima dell'uso.",
        )
    if section == "patras":
        return (
            "Ζωνικό κόμιστρο Προαστιακού Πάτρας. Η τελική τιμή εξαρτάται από τις ζώνες της διαδρομής.",
            "Tarifë zonale e trenit periferik të Patrës. Çmimi përfundimtar varet nga zonat e udhëtimit.",
            "Tariffa zonale del suburbano di Patrasso. Il prezzo finale dipende dalle zone attraversate.",
        )
    if section == "airport":
        return (
            "Προϊόν μετακίνησης από ή προς το Αεροδρόμιο Αθηνών. Έλεγξε τα μέσα και τη διάρκεια ισχύος.",
            "Produkt udhëtimi nga ose drejt Aeroportit të Athinës. Kontrollo mjetet dhe vlefshmërinë.",
            "Prodotto per viaggi da o verso l'aeroporto di Atene. Verifica mezzi inclusi e validità.",
        )
    if section == "passes":
        return (
            "Απεριόριστες διαδρομές για την αναγραφόμενη περίοδο, με τους περιορισμούς του προϊόντος.",
            "Udhëtime të pakufizuara për periudhën e treguar, sipas kufizimeve të produktit.",
            "Viaggi illimitati per il periodo indicato, secondo le limitazioni del prodotto.",
        )
    if section == "offers":
        return (
            "Πακέτο εισιτηρίων OASA. Το μειωμένο προϊόν απαιτεί προσωποποιημένη ATH.ENA Card.",
            "Paketë biletash OASA. Produkti me ulje kërkon ATH.ENA Card të personalizuar.",
            "Pacchetto di biglietti OASA. Il prodotto ridotto richiede una ATH.ENA Card personalizzata.",
        )
    return (
        "Επίσημο προϊόν OASA. Δεν ισχύει στις διαδρομές Αεροδρομίου εκτός αν αναφέρεται ρητά.",
        "Produkt zyrtar OASA. Nuk vlen për rrugët e aeroportit, përveç rasteve kur thuhet shprehimisht.",
        "Prodotto ufficiale OASA. Non valido sulle tratte aeroportuali salvo indicazione esplicita.",
    )


def main() -> None:
    with dbmod.connect() as conn:
        dbmod.migrate(conn)
        # Wipe and re-seed so dropped products go away too. Admin UI overrides
        # belong on a separate row; this table is OASA-canonical only.
        conn.execute("DELETE FROM fare_products")
        for p in PRODUCTS:
            section, sort_order, t_en, t_el, full, disc, validity, notes, tags = p[:9]
            # OASA rows are 9-tuples (default to the OASA source); the other
            # networks carry their own operator source_url as a 10th field.
            source_url = p[9] if len(p) > 9 else SOURCE_URL
            title_sq = TITLE_SQ[t_en]
            title_it = TITLE_IT[t_en]
            validity_sq, validity_it = localized_validity(validity)
            notes_el, notes_sq, notes_it = localized_notes(section, tags)
            conn.execute(
                "INSERT INTO fare_products(section, sort_order, title_en, title_el, title_sq, title_it,"
                " full_price_eur, discounted_price_eur, validity, validity_sq, validity_it, notes,"
                " notes_el, notes_sq, notes_it, tags, source_url)"
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (section, sort_order, t_en, t_el, title_sq, title_it, full, disc, validity,
                 validity_sq, validity_it, notes, notes_el, notes_sq, notes_it, tags, source_url),
            )
        count = conn.execute("SELECT COUNT(*) FROM fare_products").fetchone()[0]
        print(f"seeded {count} fare products (OASA + OSETH + Patras + intercity)")


if __name__ == "__main__":
    main()
