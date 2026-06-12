-- Per-train per-station timestamps from official operator PDFs.
-- Replaces band-based projection for lines where we have ground truth.
--
-- One row per (train_no, station) call. Times are local Athens HH:MM strings;
-- we keep them as TEXT to preserve the source representation. Day-of-week
-- semantics live in `day_type` (mon_fri / sat / sun / weekend / all).

CREATE TABLE IF NOT EXISTS train_timestamps (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    line_id TEXT NOT NULL,                  -- A1, A2, A3, A4, etc.
    direction TEXT NOT NULL,                -- outbound, inbound, both
    day_type TEXT NOT NULL,                 -- mon_fri, sat, sun, weekend, all
    train_no TEXT NOT NULL,
    station_id TEXT,                        -- our station id (A1_PIR, etc.) - nullable while mapping evolves
    station_name_en TEXT NOT NULL,          -- raw station label from PDF
    station_name_el TEXT NOT NULL,
    time TEXT NOT NULL,                     -- HH:MM
    stop_sequence INTEGER NOT NULL,         -- 0-based position in the train's stopping pattern
    source_pdf TEXT NOT NULL,
    valid_from TEXT NOT NULL DEFAULT '2025-11-22',
    fetched_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE INDEX IF NOT EXISTS idx_train_timestamps_line_day
    ON train_timestamps(line_id, day_type, direction);

CREATE INDEX IF NOT EXISTS idx_train_timestamps_station
    ON train_timestamps(station_name_en, line_id, day_type);

CREATE INDEX IF NOT EXISTS idx_train_timestamps_train
    ON train_timestamps(train_no, line_id);
