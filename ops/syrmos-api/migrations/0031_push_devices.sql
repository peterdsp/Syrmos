-- Anonymous push registrations (3.0.2).
--
-- No account, no location, no identifier other than the push token itself.
-- token_hash (sha256 hex of the token) is the key so an upsert is idempotent
-- and lookups never need the raw token in a WHERE clause. topics is a JSON
-- list such as ["all", "region.athens", "line.M3", "station.M3_NIK"] chosen
-- by the app from the rider's favourites. Rows are deleted on unregister and
-- when APNs answers 410 / BadDeviceToken or FCM answers UNREGISTERED.
CREATE TABLE IF NOT EXISTS push_devices (
    token_hash  TEXT PRIMARY KEY,
    token       TEXT NOT NULL,
    platform    TEXT NOT NULL CHECK (platform IN ('ios', 'android')),
    environment TEXT NOT NULL DEFAULT 'production' CHECK (environment IN ('sandbox', 'production')),
    lang        TEXT NOT NULL DEFAULT 'en',
    topics      TEXT NOT NULL DEFAULT '[]',
    app_version TEXT,
    created_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    last_seen   TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_push_devices_platform
    ON push_devices(platform);

INSERT OR IGNORE INTO schema_version(version) VALUES (31);
