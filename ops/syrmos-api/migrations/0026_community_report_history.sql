-- Permanent, privacy-safe daily Ichnos aggregates.
--
-- Individual reports remain short lived in community_reports. This table keeps
-- only public rail scope, day, signal, and count so Greece can build a useful
-- operational history without retaining a person, device, or journey trail.
CREATE TABLE IF NOT EXISTS community_report_daily (
    report_day  TEXT NOT NULL,
    scope_id    TEXT NOT NULL,
    scope_label TEXT NOT NULL,
    signal      TEXT NOT NULL,
    report_count INTEGER NOT NULL DEFAULT 0 CHECK (report_count >= 0),
    updated_at  TEXT NOT NULL,
    PRIMARY KEY (report_day, scope_id, signal)
);

CREATE INDEX IF NOT EXISTS idx_community_report_daily_day
    ON community_report_daily(report_day DESC);

CREATE INDEX IF NOT EXISTS idx_community_report_daily_scope_day
    ON community_report_daily(scope_id, report_day DESC);

INSERT OR IGNORE INTO schema_version(version) VALUES (26);
