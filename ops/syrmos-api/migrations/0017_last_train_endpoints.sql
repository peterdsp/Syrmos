-- Per-station last-train data scraped from stasy.gr/en/timetables/line-N.
-- STASY publishes a "FIRST / LAST / LAST (UP TO X STATION)" table for every
-- line and direction; the extra column documents short-turn services that
-- terminate at an intermediate station instead of running to the full
-- terminal. The projector consumes this to override the displayed
-- destination on the very last departures of the night so users see
-- "towards Omonia" instead of "towards Kifissia" for trains that don't
-- actually go all the way.
--
-- One row per (line, direction, from_station, time). end_station_id is
-- the short-turn terminal (e.g. "M1_OMO"). When the table has no
-- short-turn row at that time, end_station_id is the line's normal
-- terminal and the row is still useful because it pins the actual
-- last-train clock time.

CREATE TABLE IF NOT EXISTS last_train_endpoints (
    line_id TEXT NOT NULL,
    day_type TEXT NOT NULL,
    direction TEXT NOT NULL,
    from_station_id TEXT NOT NULL,
    time TEXT NOT NULL,
    end_station_id TEXT NOT NULL,
    label TEXT,
    source TEXT,
    fetched_at TEXT,
    PRIMARY KEY (line_id, day_type, direction, from_station_id, time)
);

CREATE INDEX IF NOT EXISTS idx_last_train_endpoints_line_dir
    ON last_train_endpoints (line_id, direction);
