-- Per-station cumulative minutes from origin, scraped from STASY's per-line
-- timetable pages (https://www.stasy.gr/en/timetables/{line-1,line-2,line-3,tram}/).
-- Lets the projector compute exact HH:MM at every station for every synthesized
-- train, instead of just origin + destination with a hardcoded runtime.

CREATE TABLE IF NOT EXISTS station_offsets (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    line_id              TEXT NOT NULL,            -- M1, M2, M3, T6, T7
    direction            TEXT NOT NULL,            -- outbound, inbound
    origin               TEXT NOT NULL,            -- Anthoupoli, Elliniko, etc.
    destination          TEXT NOT NULL,
    stop_sequence        INTEGER NOT NULL,         -- 0-indexed position in the direction
    station_en           TEXT NOT NULL,
    station_id           TEXT,                     -- best-effort lookup against stations table
    minutes_from_origin  INTEGER NOT NULL,         -- 0 at the origin terminal
    source               TEXT NOT NULL DEFAULT 'stasy-html',
    fetched_at           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    UNIQUE(line_id, direction, stop_sequence)
);

CREATE INDEX IF NOT EXISTS idx_station_offsets_line_dir
    ON station_offsets(line_id, direction);

CREATE INDEX IF NOT EXISTS idx_station_offsets_station
    ON station_offsets(station_en, line_id);
