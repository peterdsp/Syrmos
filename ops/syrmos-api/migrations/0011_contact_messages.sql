-- User-submitted feedback / bug reports / feature requests from the app.
--
-- Each row is one message. The platform column lets the admin see whether
-- the report came from iOS, Android or the web. attachment_path is a
-- relative path under /home/peterdsp/syrmos-api/uploads/contact when the
-- user attached a screenshot / video; null otherwise. Email is optional
-- so users can stay anonymous, but if they include one the admin can
-- reply directly.
CREATE TABLE IF NOT EXISTS contact_messages (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    platform        TEXT NOT NULL,           -- 'ios' | 'android' | 'web' | 'other'
    app_version     TEXT,
    locale          TEXT,
    user_agent      TEXT,
    category        TEXT NOT NULL DEFAULT 'other',  -- 'bug' | 'feature' | 'question' | 'other'
    subject         TEXT,
    message         TEXT NOT NULL,
    contact_email   TEXT,
    attachment_path TEXT,
    attachment_mime TEXT,
    attachment_size INTEGER,
    status          TEXT NOT NULL DEFAULT 'new',    -- 'new' | 'read' | 'replied' | 'spam'
    admin_notes     TEXT,
    replied_at      TEXT
);

CREATE INDEX IF NOT EXISTS idx_contact_messages_status
    ON contact_messages(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_contact_messages_platform
    ON contact_messages(platform, created_at DESC);

INSERT OR IGNORE INTO schema_version(version) VALUES (11);
