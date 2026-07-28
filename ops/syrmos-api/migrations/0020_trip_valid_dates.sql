-- Allow specific-date trips: a nullable comma-separated list of ISO dates
-- (e.g. "2026-08-17" or "2026-08-07,2026-08-14"). When NULL the trip runs
-- on every matching day_type. When set, it runs ONLY on those calendar dates
-- (provided the day_type also matches).
ALTER TABLE scheduled_trips ADD COLUMN valid_dates TEXT;

INSERT INTO schema_version(version) VALUES(20);
