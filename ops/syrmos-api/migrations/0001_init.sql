-- Syrmos schedules DB schema, v1.
-- All times are HH:MM (24h), Athens local.
-- Coordinates are WGS84 decimal degrees.

PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;

CREATE TABLE IF NOT EXISTS schema_version (
    version     INTEGER NOT NULL PRIMARY KEY,
    applied_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE TABLE IF NOT EXISTS lines (
    id            TEXT PRIMARY KEY,           -- M1, M2, M3_CITY, M3_AIR, T6, T7, P1, A2, A3, A4
    mode          TEXT NOT NULL,              -- metro | tram | suburban
    name_en       TEXT NOT NULL,
    name_el       TEXT NOT NULL,
    color         TEXT NOT NULL,              -- hex
    terminal_a    TEXT NOT NULL,
    terminal_b    TEXT NOT NULL,
    sort_order    INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS stations (
    id        TEXT PRIMARY KEY,               -- M1_PIR, T7_AKT, ...
    name_en   TEXT NOT NULL,
    name_el   TEXT NOT NULL,
    lat       REAL NOT NULL,
    lng       REAL NOT NULL
);

CREATE TABLE IF NOT EXISTS line_stations (
    line_id    TEXT NOT NULL REFERENCES lines(id) ON DELETE CASCADE,
    station_id TEXT NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
    seq        INTEGER NOT NULL,              -- order along the line
    direction  TEXT NOT NULL DEFAULT 'both',  -- inbound | outbound | both
    PRIMARY KEY (line_id, station_id, direction)
);

CREATE INDEX IF NOT EXISTS idx_line_stations_seq ON line_stations(line_id, seq);

-- Daily window the line operates. One row per (line, day_type).
-- day_type ∈ mon_thu | fri | sat | sun | holiday | bank_holiday | aug_15 | dec_24_31
CREATE TABLE IF NOT EXISTS schedule_rules (
    line_id     TEXT NOT NULL REFERENCES lines(id) ON DELETE CASCADE,
    day_type    TEXT NOT NULL,
    open_time   TEXT NOT NULL,                -- "05:30"
    close_time  TEXT NOT NULL,                -- "00:30" (next-day allowed; > 24:00 is fine semantically)
    is_24_7     INTEGER NOT NULL DEFAULT 0,   -- 1 for Saturday 24/7 service
    notes       TEXT,
    PRIMARY KEY (line_id, day_type)
);

-- Frequency by daypart. Stack rows to cover the full operating window.
CREATE TABLE IF NOT EXISTS frequency_bands (
    line_id          TEXT NOT NULL REFERENCES lines(id) ON DELETE CASCADE,
    day_type         TEXT NOT NULL,
    time_start       TEXT NOT NULL,           -- "07:00"
    time_end         TEXT NOT NULL,           -- "10:00"
    headway_minutes  REAL NOT NULL,           -- 4.0, 5.5, 10.5, etc.
    label            TEXT,                    -- "morning_peak" | "midday_offpeak" | "evening_peak" | ...
    PRIMARY KEY (line_id, day_type, time_start)
);

-- Holiday calendar rules. Resolves to a day_type for schedule_rules + frequency_bands.
-- date_pattern: fixed (MM-DD), easter-relative (easter+N / easter-N), or named (clean_monday).
CREATE TABLE IF NOT EXISTS holiday_rules (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT NOT NULL,              -- "Christmas Day"
    date_pattern  TEXT NOT NULL,              -- "12-25" | "easter-2" | "clean_monday" | "08-15" | "dec_24_31"
    day_type      TEXT NOT NULL,              -- which day_type to apply
    notes         TEXT,
    UNIQUE(date_pattern)
);

-- Per-date overrides for specific (line, date) where 24mmm differs from the rule.
-- payload_json holds either a list of frequency_bands or a full schedule_rules block.
CREATE TABLE IF NOT EXISTS date_overrides (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    override_date TEXT NOT NULL,              -- YYYY-MM-DD
    line_id     TEXT NOT NULL REFERENCES lines(id) ON DELETE CASCADE,
    source      TEXT NOT NULL,                -- "oasa_24mmm" | "manual"
    payload_json TEXT NOT NULL,
    fetched_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    UNIQUE(override_date, line_id)
);

CREATE INDEX IF NOT EXISTS idx_overrides_date ON date_overrides(override_date);

CREATE TABLE IF NOT EXISTS scrape_log (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    run_at       TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    source       TEXT NOT NULL,
    ok           INTEGER NOT NULL,
    rows_written INTEGER NOT NULL DEFAULT 0,
    error        TEXT
);

CREATE TABLE IF NOT EXISTS meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

INSERT OR IGNORE INTO meta(key, value) VALUES
    ('version', '1'),
    ('updated_at', strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    ('etag', ''),
    ('client_min_version', '0');

INSERT OR IGNORE INTO schema_version(version) VALUES (1);
