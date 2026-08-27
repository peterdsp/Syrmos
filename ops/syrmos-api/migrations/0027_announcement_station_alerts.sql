-- Station-level service alerts.
--
-- The STASY announcements scraper writes two columns the earlier migrations
-- never created: affected_station_ids (JSON list of station IDs a disruption
-- touches) and service_until_time ("normal service restored by" time). These
-- had been added on the production Pi ad hoc, so a rebuild from migrations
-- alone produced a table the scraper could not upsert into (discovered during
-- the 2026-08-28 SD-card rebuild). This migration makes fresh bootstraps match.
ALTER TABLE announcements ADD COLUMN affected_station_ids TEXT;
ALTER TABLE announcements ADD COLUMN service_until_time TEXT;

INSERT OR IGNORE INTO schema_version(version) VALUES (27);
