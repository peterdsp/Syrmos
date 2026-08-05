-- Italian translations for live status, announcements, and rail news.
ALTER TABLE stasy_status ADD COLUMN raw_message_it TEXT NOT NULL DEFAULT '';
ALTER TABLE announcements ADD COLUMN title_it TEXT NOT NULL DEFAULT '';
ALTER TABLE announcements ADD COLUMN summary_it TEXT NOT NULL DEFAULT '';
ALTER TABLE rail_news ADD COLUMN title_it TEXT NOT NULL DEFAULT '';
ALTER TABLE rail_news ADD COLUMN summary_it TEXT NOT NULL DEFAULT '';

INSERT OR IGNORE INTO schema_version(version) VALUES (23);
