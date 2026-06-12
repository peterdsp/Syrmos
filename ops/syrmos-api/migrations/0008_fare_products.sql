-- Structured OASA ticket products scraped from
-- https://www.oasa.gr/en/tickets/prices-of-products/. Replaces the old
-- "we don't store prices" stance: apps now render the catalogue natively
-- in the design language, with auto-refresh via the daily OASA watcher.
--
-- One row per product. The legacy `fares` table (operator-level metadata)
-- stays; this is the per-ticket catalogue the new FaresView consumes.

CREATE TABLE IF NOT EXISTS fare_products (
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    section                TEXT NOT NULL,          -- single | offers | airport | passes
    title_en               TEXT NOT NULL,
    title_el               TEXT,                   -- filled in by a parallel Greek-page scrape; nullable for now
    full_price_eur         REAL,
    discounted_price_eur   REAL,
    validity               TEXT,                   -- short badge string ('Exclude Airport & X80', etc.)
    notes                  TEXT,                   -- free-form description from OASA
    tags                   TEXT,                   -- comma-separated: airport_excluded, pack, tourist, ...
    source_url             TEXT NOT NULL DEFAULT 'https://www.oasa.gr/en/tickets/prices-of-products/',
    sort_order             INTEGER NOT NULL DEFAULT 0,
    fetched_at             TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE INDEX IF NOT EXISTS idx_fare_products_section
    ON fare_products(section, sort_order);
