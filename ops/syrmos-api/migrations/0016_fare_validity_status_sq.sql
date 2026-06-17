-- Round 2 of Albanian localisation for the fares + announcements API.
-- Migration 0015 covered title + notes; this one covers the remaining
-- user-visible English strings:
--
--   1. fare_products.validity_sq — the short badge text under each
--      ticket card ("90 minutes" / "10 tickets" / "X95 only"). Was
--      shipped only in English; Albanian users saw English chips
--      under an otherwise-Albanian fares screen.
--
--   2. stasy_status.raw_message_sq — the inline alert banner text on
--      the home screen ("Traffic arrangements on Metro Line 3..."). The
--      scraper writes raw_message (Greek source) + raw_message_en
--      (Google translation); this column adds the Albanian translation
--      so the alert card honours the picked language on every platform.

ALTER TABLE fare_products ADD COLUMN validity_sq TEXT;
ALTER TABLE stasy_status  ADD COLUMN raw_message_sq TEXT NOT NULL DEFAULT '';

-- Per-announcement Albanian fields. The scraper writes the Albanian
-- translation of the title + summary so the iOS / Android / Web alert
-- cards (which render announcement.title / .summary, not the status
-- pill) honour the picked language.
ALTER TABLE announcements ADD COLUMN title_sq   TEXT NOT NULL DEFAULT '';
ALTER TABLE announcements ADD COLUMN summary_sq TEXT NOT NULL DEFAULT '';

INSERT OR IGNORE INTO schema_version(version) VALUES (16);
