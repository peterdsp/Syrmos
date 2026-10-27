-- Station closures (3.0.2).
--
-- One row per closure decided by the owner in /admin/closures, or drafted by
-- the STASY announcements scraper (source 'stasy', status 'draft'; a draft is
-- never served publicly and never goes live without an explicit approval).
--
-- station_ids / line_ids are JSON lists of canonical ids (M2_SYN, M3). An empty
-- line_ids list means every line at those stations. starts_at / ends_at are
-- UTC epoch seconds; ends_at NULL means "until further notice". Localised
-- reason text uses one column per language, the clients pick the app language
-- and fall back to en, then el. replacement_bus is JSON text in the public
-- contract shape: {label, fromStop, toStop, headwayMinutes, stops[]}.
--
-- status: draft | scheduled | active | ended | cancelled. The dispatcher
-- (syrmos_admin.dispatch) flips scheduled -> active -> ended by time, but the
-- clients always evaluate the window themselves, so status is informational.
CREATE TABLE IF NOT EXISTS station_closures (
    id              TEXT PRIMARY KEY,
    station_ids     TEXT NOT NULL DEFAULT '[]',
    line_ids        TEXT NOT NULL DEFAULT '[]',
    mode            TEXT NOT NULL CHECK (mode IN ('closed', 'pass_through', 'exit_only', 'entry_only')),
    starts_at       INTEGER NOT NULL,
    ends_at         INTEGER,
    reason_en       TEXT NOT NULL DEFAULT '',
    reason_el       TEXT NOT NULL DEFAULT '',
    reason_sq       TEXT NOT NULL DEFAULT '',
    reason_it       TEXT NOT NULL DEFAULT '',
    replacement_bus TEXT,
    source          TEXT NOT NULL DEFAULT 'admin' CHECK (source IN ('admin', 'stasy')),
    source_ref      TEXT,
    status          TEXT NOT NULL DEFAULT 'draft'
                    CHECK (status IN ('draft', 'scheduled', 'active', 'ended', 'cancelled')),
    created_by      TEXT NOT NULL DEFAULT '',
    created_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    updated_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_station_closures_status_window
    ON station_closures(status, starts_at, ends_at);

-- One draft per scraped announcement, so re-running the scraper is a no-op.
CREATE UNIQUE INDEX IF NOT EXISTS idx_station_closures_source_ref
    ON station_closures(source, source_ref) WHERE source_ref IS NOT NULL;

INSERT OR IGNORE INTO schema_version(version) VALUES (29);
