-- Explicit scheduled trips for fixed-timetable lines (suburban Proastiakos
-- A1 / A2 / A3 / A4). Unlike the metro/tram, suburban trains don't run on
-- a regular headway grid — Hellenic Train publishes a fixed list of named
-- trips (train numbers like 1200, 2202, 3530) with specific HH:MM at each
-- station. The projector uses these rows directly for those lines; the
-- legacy frequency_bands path keeps working for the metro/tram lines.

CREATE TABLE IF NOT EXISTS scheduled_trips (
    train_no      TEXT NOT NULL,
    line_id       TEXT NOT NULL,
    direction     TEXT NOT NULL,   -- outbound | inbound
    day_type      TEXT NOT NULL,   -- mon_thu | fri | sat | sun
    service_label TEXT,            -- optional, e.g. "weekday_only" or notes
    PRIMARY KEY (train_no, line_id, day_type, direction)
);

CREATE TABLE IF NOT EXISTS scheduled_trip_stops (
    train_no       TEXT NOT NULL,
    line_id        TEXT NOT NULL,
    direction      TEXT NOT NULL,
    day_type       TEXT NOT NULL,
    station_id     TEXT NOT NULL,
    stop_sequence  INTEGER NOT NULL,
    departure_time TEXT NOT NULL,  -- "HH:MM", may roll past 24:00 as 24+
    PRIMARY KEY (train_no, line_id, day_type, direction, station_id)
);

CREATE INDEX IF NOT EXISTS idx_trip_stops_line_day
    ON scheduled_trip_stops (line_id, day_type, direction);

CREATE INDEX IF NOT EXISTS idx_trip_stops_station
    ON scheduled_trip_stops (station_id, day_type);
