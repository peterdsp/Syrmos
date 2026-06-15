-- Structured fields for STASY announcements so the apps can colour-code
-- alerts, filter per-station, and surface closure days without inferring
-- anything from the Greek title. Populated by syrmos_admin.scraper_stasy_announcements
-- on its hourly run.
ALTER TABLE announcements ADD COLUMN affected_lines TEXT NOT NULL DEFAULT '[]';
ALTER TABLE announcements ADD COLUMN severity TEXT NOT NULL DEFAULT 'info';
ALTER TABLE announcements ADD COLUMN valid_from TEXT;
ALTER TABLE announcements ADD COLUMN valid_until TEXT;

CREATE INDEX IF NOT EXISTS idx_announcements_severity
    ON announcements(severity, sort_order);
CREATE INDEX IF NOT EXISTS idx_announcements_validity
    ON announcements(valid_from, valid_until);

INSERT OR IGNORE INTO schema_version(version) VALUES (13);
