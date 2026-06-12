-- STASY homepage status + announcements scraped by the daily watcher.
-- The iOS Home tab consumes this through /api/announcements; without it
-- the live-trains panel falls back to a "Could not reach stasy.gr" error.

CREATE TABLE IF NOT EXISTS stasy_status (
    id              INTEGER PRIMARY KEY CHECK (id = 1),  -- singleton
    status          TEXT NOT NULL,                       -- normal | alert | unknown
    raw_message     TEXT NOT NULL DEFAULT '',
    service_until   TEXT,                                -- "HH:MM" when an alert sets a cutoff
    scraped_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE TABLE IF NOT EXISTS announcements (
    id              TEXT PRIMARY KEY,                    -- url slug, deduped
    title           TEXT NOT NULL,
    summary         TEXT NOT NULL DEFAULT '',
    url             TEXT NOT NULL,
    date            TEXT NOT NULL DEFAULT '',
    category        TEXT NOT NULL DEFAULT 'general',     -- general | serviceAlert
    sort_order      INTEGER NOT NULL DEFAULT 0,
    fetched_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE INDEX IF NOT EXISTS idx_announcements_category
    ON announcements(category, sort_order);
