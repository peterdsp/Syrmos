-- Compatibility migration for Pi databases that already recorded schema
-- version 24 before anonymous community reporting was introduced.

CREATE TABLE IF NOT EXISTS community_reports (
    report_id TEXT PRIMARY KEY,
    scope_id TEXT NOT NULL,
    scope_label TEXT NOT NULL,
    signal TEXT NOT NULL,
    detail TEXT,
    platform TEXT NOT NULL,
    locale TEXT,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_community_reports_scope_expiry
    ON community_reports(scope_id, expires_at DESC);

CREATE INDEX IF NOT EXISTS idx_community_reports_expiry
    ON community_reports(expires_at DESC);

INSERT OR IGNORE INTO schema_version(version) VALUES (25);
