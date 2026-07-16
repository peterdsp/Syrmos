-- Region + line status, for the first second region (Thessaloniki).
--
-- Design: docs/plans/2026-07-15-thessaloniki-metro-design.md
--
-- `region` groups a line/station into a network. It is deliberately NOT called
-- `city`: the Thessaloniki suburban corridors run to Larisa (~150 km) and
-- Florina (~160 km), so a line legitimately spans several cities. Hellenic Train
-- brands all of them as "Thessaloniki Regional lines / Προαστιακός
-- Θεσσαλονίκης", so the network, not the municipality, is the unit.
--
-- Region drives exactly five things on-device: the default map camera, the
-- no-GPS home hero, track-picker grouping, announcement scoping (a Thessaloniki
-- user must never be shown Athens STASY alerts), and the weather coordinates.
-- Nearest-station deliberately stays global: if you are physically in
-- Thessaloniki the nearest station already is a Thessaloniki one, so geometry
-- handles it and a region filter would only add a way to be wrong.
--
-- `status` exists so a line that does not run can still be drawn. Thessaloniki
-- metro Line 2 (Kalamaria extension) is due to open at the end of July 2026; it
-- ships as under_construction and renders greyed, and every prediction path
-- (projector, simulator, home hero, track-picker, last-train) skips a
-- non-operational line so nothing can invent a departure or a train for track
-- that carries neither. Opening it later is then a data change, not a code
-- change: flip status to 'operational' and seed its bands + offsets.
--
-- Both columns are NOT NULL with a default, so every existing Athens row
-- backfills to ('athens', 'operational') and nothing regresses.

ALTER TABLE lines ADD COLUMN region TEXT NOT NULL DEFAULT 'athens';
ALTER TABLE stations ADD COLUMN region TEXT NOT NULL DEFAULT 'athens';
ALTER TABLE lines ADD COLUMN status TEXT NOT NULL DEFAULT 'operational';

CREATE INDEX IF NOT EXISTS idx_lines_region ON lines (region);
CREATE INDEX IF NOT EXISTS idx_stations_region ON stations (region);

INSERT OR IGNORE INTO schema_version(version) VALUES (18);
