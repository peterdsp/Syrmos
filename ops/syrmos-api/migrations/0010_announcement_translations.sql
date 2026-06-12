-- Greek-to-English translations for STASY announcements + status banner.
-- Populated at ingest time via Google Translate (deep-translator). The apps
-- read title_en / summary_en / raw_message_en when the active app language
-- is English, falling back to the Greek originals if the columns are NULL.

ALTER TABLE announcements ADD COLUMN title_en TEXT NOT NULL DEFAULT '';
ALTER TABLE announcements ADD COLUMN summary_en TEXT NOT NULL DEFAULT '';
ALTER TABLE stasy_status  ADD COLUMN raw_message_en TEXT NOT NULL DEFAULT '';
