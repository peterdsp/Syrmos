-- Adds the fares table. We only store *metadata* (provider, currency,
-- the link to the canonical OASA fares page, the list of contactless
-- payment methods) — we deliberately do NOT store specific ticket prices.
-- Prices change without notice; the source of truth must be OASA itself.
-- The app surfaces the link so users always see the current price.

CREATE TABLE IF NOT EXISTS fares (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    operator_id          TEXT NOT NULL,                  -- "oasa" | "hellenic_train"
    region               TEXT NOT NULL DEFAULT 'athens', -- room for regional fares later
    prices_url           TEXT NOT NULL,                  -- canonical fares page
    prices_url_el        TEXT,                           -- optional Greek-language URL
    currency             TEXT NOT NULL DEFAULT 'EUR',
    contactless_methods  TEXT NOT NULL,                  -- JSON array of strings
    contactless_locations TEXT NOT NULL,                 -- JSON array of strings
    notes_en             TEXT,
    notes_el             TEXT,
    updated_at           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    UNIQUE(operator_id, region)
);

-- Seed: OASA (Metro + Tram + Bus + Trolley) contactless tap-and-go rules.
INSERT OR IGNORE INTO fares
    (operator_id, region, prices_url, prices_url_el, currency, contactless_methods, contactless_locations, notes_en, notes_el)
VALUES (
    'oasa',
    'athens',
    'https://www.oasa.gr/en/tickets/prices-of-products/',
    'https://www.oasa.gr/eisitiria/timokatalogos-proionton/',
    'EUR',
    '["apple_pay","google_wallet","contactless_credit_card","contactless_debit_card"]',
    '["metro_station_gates","tram_stop_gates","tram_onboard_validators","train_onboard_validators","bus_onboard_validators"]',
    'Tap and go works at metro and tram station gates with Apple Pay, Google Wallet, or any contactless Visa/Mastercard. On trams and trains you can also tap on the validators inside the vehicle. Syrmos only links to OASA''s official fare list; price changes are managed by OASA.',
    'Η ανέπαφη πληρωμή λειτουργεί στις πύλες σταθμών μετρό και τραμ με Apple Pay, Google Wallet ή οποιαδήποτε ανέπαφη κάρτα Visa/Mastercard. Στα τραμ και τα τρένα μπορείτε επίσης να σαρώσετε στα τερματικά μέσα στο όχημα. Το Syrmos απλώς παραπέμπει στον επίσημο τιμοκατάλογο του ΟΑΣΑ· οι τιμές διαχειρίζονται από τον ΟΑΣΑ.'
);

INSERT OR IGNORE INTO fares
    (operator_id, region, prices_url, prices_url_el, currency, contactless_methods, contactless_locations, notes_en, notes_el)
VALUES (
    'hellenic_train',
    'athens',
    'https://www.hellenictrain.gr/en',
    'https://www.hellenictrain.gr/',
    'EUR',
    '["online_ticket","ticket_vending_machine","ticket_office"]',
    '["station_ticket_office","station_vending_machine","online"]',
    'Suburban railway tickets are purchased on Hellenic Train''s site or at stations. Tap-and-go is not currently supported on suburban gates as of this writing.',
    'Τα εισιτήρια του προαστιακού αγοράζονται από τον ιστότοπο της Hellenic Train ή στους σταθμούς. Η ανέπαφη πληρωμή στις πύλες δεν υποστηρίζεται από τον προαστιακό σιδηρόδρομο μέχρι στιγμής.'
);

INSERT OR IGNORE INTO schema_version(version) VALUES (2);
