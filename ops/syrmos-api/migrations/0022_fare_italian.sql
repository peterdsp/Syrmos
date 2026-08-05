-- Complete the four-language fare payload used by Android, Web, and iOS.
ALTER TABLE fare_products ADD COLUMN title_it TEXT;
ALTER TABLE fare_products ADD COLUMN validity_it TEXT;
ALTER TABLE fare_products ADD COLUMN notes_el TEXT;
ALTER TABLE fare_products ADD COLUMN notes_it TEXT;

INSERT OR IGNORE INTO schema_version(version) VALUES (22);
