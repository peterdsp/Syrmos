-- Canonical alias table: maps (source, raw_name) pairs from imported
-- timetable feeds onto our internal stations.id namespace. Lets the apps
-- and the offset/timestamp ingesters resolve spelling variants
-- (Moshato/Moschato, Sygrou-Fix/Syngrou-Fix, etc.) at write time instead
-- of falling back to fuzzy matching at query time.
--
-- raw_name is preserved as-published so a future audit can see what the
-- operator wrote. normalized_name is the lowercased / whitespace-collapsed
-- form used by the resolver - we index on it.

CREATE TABLE IF NOT EXISTS station_name_aliases (
    source                 TEXT NOT NULL,        -- 'stasy' | 'hellenic_train' | 'oasa'
    raw_name               TEXT NOT NULL,        -- exact string from the feed
    normalized_name        TEXT NOT NULL,
    canonical_station_id   TEXT NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
    notes                  TEXT,
    PRIMARY KEY (source, raw_name)
);

CREATE INDEX IF NOT EXISTS idx_station_name_aliases_norm
    ON station_name_aliases(source, normalized_name);

CREATE INDEX IF NOT EXISTS idx_station_name_aliases_id
    ON station_name_aliases(canonical_station_id);
