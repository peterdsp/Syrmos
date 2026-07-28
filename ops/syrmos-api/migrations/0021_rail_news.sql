-- Rail news scraped from sidirodromikanea.blogspot.com (Greek rail blog).
-- The daily scraper fetches the Atom feed, filters by rail-relevant
-- keywords (Hellenic Train, delays, disruptions, closures), and stores
-- translated titles/summaries for the Home news carousel.

CREATE TABLE IF NOT EXISTS rail_news (
    id              TEXT PRIMARY KEY,
    title           TEXT NOT NULL,
    title_en        TEXT NOT NULL DEFAULT '',
    title_sq        TEXT NOT NULL DEFAULT '',
    summary         TEXT NOT NULL DEFAULT '',
    summary_en      TEXT NOT NULL DEFAULT '',
    summary_sq      TEXT NOT NULL DEFAULT '',
    url             TEXT NOT NULL,
    published_at    TEXT NOT NULL DEFAULT '',
    thumbnail_url   TEXT NOT NULL DEFAULT '',
    categories      TEXT NOT NULL DEFAULT '[]',
    fetched_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE INDEX IF NOT EXISTS idx_rail_news_published
    ON rail_news(published_at DESC);

INSERT OR IGNORE INTO schema_version(version) VALUES (21);
