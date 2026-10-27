-- Owner broadcasts (3.0.2): one row per announcement pushed to riders.
--
-- Title and body are localised per language (Greek is written by the owner,
-- the rest come from the translation pipeline and stay editable). audience is
-- 'all' | 'line:<lineId>' | 'station:<stationId>' | 'region:athens' |
-- 'region:thessaloniki'. send_at / sent_at / expires_at are UTC epoch seconds.
--
-- status: draft | scheduled | sending | sent | cancelled | expired. The
-- dispatcher claims a due row by flipping scheduled -> sending (with sent_at)
-- in one UPDATE before any push goes out, so a broadcast is sent at most once
-- even if two dispatchers race or one crashes mid-send.
CREATE TABLE IF NOT EXISTS broadcasts (
    id           TEXT PRIMARY KEY,
    title_en     TEXT NOT NULL DEFAULT '',
    title_el     TEXT NOT NULL DEFAULT '',
    title_sq     TEXT NOT NULL DEFAULT '',
    title_it     TEXT NOT NULL DEFAULT '',
    body_en      TEXT NOT NULL DEFAULT '',
    body_el      TEXT NOT NULL DEFAULT '',
    body_sq      TEXT NOT NULL DEFAULT '',
    body_it      TEXT NOT NULL DEFAULT '',
    audience     TEXT NOT NULL DEFAULT 'all',
    send_at      INTEGER,
    sent_at      INTEGER,
    expires_at   INTEGER,
    deep_link    TEXT,
    closure_id   TEXT REFERENCES station_closures(id) ON DELETE SET NULL,
    status       TEXT NOT NULL DEFAULT 'draft'
                 CHECK (status IN ('draft', 'scheduled', 'sending', 'sent', 'cancelled', 'expired')),
    sent_count   INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    created_by   TEXT NOT NULL DEFAULT '',
    created_at   TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_broadcasts_status_send_at
    ON broadcasts(status, send_at);

CREATE INDEX IF NOT EXISTS idx_broadcasts_sent_at
    ON broadcasts(sent_at DESC);

INSERT OR IGNORE INTO schema_version(version) VALUES (30);
