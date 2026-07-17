-- Move accessibility + zone into the server, so the DB can become the single
-- source of truth for stations.
--
-- Design: docs/plans/2026-07-17-server-as-single-source-for-lines.md
--
-- Today the apps read their line and station list from a LEGACY bundled seed
-- (files/seed/lines.json, stations.json) generated from hardcoded Swift by
-- scripts/sync-ios-transit-seed.mjs -- a script that has been broken since the
-- June 2026 iOS restructure moved TransitData.swift. The server only ever
-- generates schedules-v2/, which nothing reads for the line list. So lines live
-- in Swift, schedules live in the DB, and nothing joins them.
--
-- The fix is for the DB to own lines and stations. Most of the legacy station
-- payload survives that move: line_ids and is_interchange are derivable from
-- line_stations (a station on more than one line is an interchange).
--
-- accessibility and zone are not derivable. They exist ONLY in the legacy seed,
-- which means only in hardcoded Swift, and they are consumed by
-- StationRepositoryImpl and DataSeeder. Switching the clients to schedules-v2
-- without these columns would silently drop them, so they have to land here
-- first, be backfilled from the legacy seed, and be emitted by the generator.
--
-- Defaults match the legacy seed's own defaults (accessibility true, zone 1), so
-- a station we have no explicit data for behaves exactly as it does today.
-- accessibility is an INTEGER because SQLite has no boolean: 1 = accessible.

ALTER TABLE stations ADD COLUMN accessibility INTEGER NOT NULL DEFAULT 1;
ALTER TABLE stations ADD COLUMN zone INTEGER NOT NULL DEFAULT 1;

INSERT OR IGNORE INTO schema_version(version) VALUES (19);
