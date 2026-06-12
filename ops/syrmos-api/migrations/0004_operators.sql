-- operator_partners table.
--
-- Today no Athens operator (STASY metro/tram, OASA telematics, Hellenic Train
-- suburban) publishes a public real-time arrivals feed. We ship this table
-- and the corresponding admin page now so that whenever an operator opens a
-- feed, registering it is a row-insert in the admin UI rather than a code
-- change.
--
-- Each row holds the bare minimum needed to start consuming a feed: the
-- endpoint URL, the auth method, optional credentials (token or basic auth),
-- a refresh interval, and a status field the maintainer flips to "enabled"
-- once they have validated the feed shape end-to-end.

CREATE TABLE IF NOT EXISTS operator_partners (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    operator_id         TEXT NOT NULL,           -- 'stasy' | 'oasa' | 'hellenic_train' | future
    operator_name       TEXT NOT NULL,
    contact_email       TEXT,
    contact_url         TEXT,
    feed_kind           TEXT NOT NULL,           -- 'live_arrivals' | 'live_positions' | 'service_alerts' | 'gtfs_realtime'
    feed_url            TEXT,                    -- The endpoint to poll/subscribe
    auth_method         TEXT NOT NULL DEFAULT 'none',  -- 'none' | 'bearer' | 'basic' | 'header_key'
    auth_credential     TEXT,                    -- Encrypted at rest in production; for now plain TEXT
    refresh_seconds     INTEGER NOT NULL DEFAULT 30,
    status              TEXT NOT NULL DEFAULT 'awaiting_partnership',
                        -- 'awaiting_partnership' | 'in_discussion' | 'enabled' | 'disabled' | 'broken'
    notes               TEXT,
    last_seen_at        TEXT,
    enabled_at          TEXT,
    created_at          TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    updated_at          TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_operator_partners_status ON operator_partners(status);
CREATE INDEX IF NOT EXISTS idx_operator_partners_operator ON operator_partners(operator_id);

-- Seed entries documenting the current "no feed yet" state. Each is a real
-- conversation we want to have with the listed operator. Updating these rows
-- in the admin UI is how a future partnership goes live.
INSERT OR IGNORE INTO operator_partners
    (operator_id, operator_name, contact_url, feed_kind, status, notes)
VALUES
    ('stasy', 'STASY (Athens Metro + Tram)', 'https://www.stasy.gr',
     'live_arrivals', 'awaiting_partnership',
     'No public real-time arrivals feed as of 2026-06. Service alerts are scraped daily from stasy.gr. If STASY publishes a JSON feed, paste its URL here, pick an auth method, set status=enabled. The StasyLiveArrivalsProvider in core/domain will pick it up.'),
    ('oasa', 'OASA Telematics', 'https://telematics.oasa.gr',
     'live_arrivals', 'awaiting_partnership',
     'OASA exposes bus arrivals via an HTML telematics page. Metro/tram coverage unconfirmed. Switch to enabled once feed shape is documented.'),
    ('hellenic_train', 'Hellenic Train', 'https://www.hellenictrain.gr',
     'live_positions', 'enabled',
     'Train positions via SSE at railway.gov.gr. Per-stop ETAs not yet available; this row covers the position feed only.'),
    ('hellenic_train_etas', 'Hellenic Train (per-stop ETAs)', 'https://www.hellenictrain.gr',
     'live_arrivals', 'awaiting_partnership',
     'No public per-stop arrival feed. When available, this row holds its endpoint.');

INSERT OR IGNORE INTO schema_version(version) VALUES (4);
