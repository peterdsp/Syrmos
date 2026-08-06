-- Anonymous, short-lived Ichnos reports submitted by app users.
--
-- report_id is generated independently for every report. No account, device id,
-- advertising id, precise location, or source IP is stored. Reports expire so
-- transient conditions cannot linger in the app after they stop being useful.
CREATE TABLE IF NOT EXISTS community_reports (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    report_id   TEXT NOT NULL UNIQUE,
    scope_id    TEXT NOT NULL,
    scope_label TEXT NOT NULL,
    signal      TEXT NOT NULL,
    detail      TEXT,
    platform    TEXT NOT NULL,
    locale      TEXT,
    created_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    expires_at  TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_community_reports_scope_expiry
    ON community_reports(scope_id, expires_at DESC);

CREATE INDEX IF NOT EXISTS idx_community_reports_expiry
    ON community_reports(expires_at DESC);

INSERT OR IGNORE INTO schema_version(version) VALUES (24);
