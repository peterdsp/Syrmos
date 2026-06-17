-- Albanian (Shqip) translations for OASA ticket products.
-- The Syrmos app surfaces a Greek/English/Albanian UI; until this point
-- the fare_products table only stored title_en + title_el so Albanian
-- users saw English content under the (Albanian-labelled) panel header.
-- This migration adds the Sq fields. The generator emits titleSq / notesSq;
-- a separate seed step writes the actual translations. Nullable columns
-- so existing rows continue to validate without backfill.

ALTER TABLE fare_products ADD COLUMN title_sq TEXT;
ALTER TABLE fare_products ADD COLUMN notes_sq TEXT;

-- Operator-level metadata. notes_en already exists; add Sq sibling so the
-- "Suburban tickets are bought on Hellenic Train..." disclaimer renders
-- in Albanian where the iOS / Android Tickets screen surfaces it.
ALTER TABLE fares ADD COLUMN notes_sq TEXT;

INSERT OR IGNORE INTO schema_version(version) VALUES (15);
