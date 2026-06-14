-- frequency_bands gains a per-direction column so STASY's asymmetric
-- timetables can be represented properly (e.g. M3_AIR runs outbound
-- 05:30-23:21 from Dim. Theatro and inbound 06:10-23:34 from Airport).
--
-- Existing rows get 'both' so projector behaviour is unchanged for any
-- line whose direction isn't being differentiated yet. The new
-- (line_id, day_type, direction) composite is the natural lookup key.

-- Recreate the table to widen the primary key. SQLite can't ALTER a PK,
-- so we copy through a temp table. Direction defaults to 'both' which
-- preserves the projector's old behaviour for any row not yet migrated
-- to per-direction data.
CREATE TABLE IF NOT EXISTS frequency_bands_v2 (
    line_id          TEXT NOT NULL REFERENCES lines(id) ON DELETE CASCADE,
    day_type         TEXT NOT NULL,
    time_start       TEXT NOT NULL,
    time_end         TEXT NOT NULL,
    headway_minutes  REAL NOT NULL,
    label            TEXT,
    direction        TEXT NOT NULL DEFAULT 'both',  -- 'both' | 'outbound' | 'inbound'
    PRIMARY KEY (line_id, day_type, direction, time_start)
);

INSERT INTO frequency_bands_v2
    (line_id, day_type, time_start, time_end, headway_minutes, label, direction)
    SELECT line_id, day_type, time_start, time_end, headway_minutes, label, 'both'
    FROM frequency_bands;

DROP TABLE frequency_bands;
ALTER TABLE frequency_bands_v2 RENAME TO frequency_bands;

CREATE INDEX IF NOT EXISTS idx_frequency_bands_line_day_dir
    ON frequency_bands(line_id, day_type, direction, time_start);

INSERT OR IGNORE INTO schema_version(version) VALUES (12);
