-- Admin audit log (3.0.2): one row per mutating admin request.
--
-- actor is the verified Cloudflare Access email (or 'token-admin' for the
-- header token fallback). payload_sha256 hashes the submitted form fields
-- (minus the CSRF token) so the log proves what was sent without storing
-- free text twice.
CREATE TABLE IF NOT EXISTS admin_audit (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at     TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    actor          TEXT NOT NULL,
    method         TEXT NOT NULL,
    path           TEXT NOT NULL,
    action         TEXT,
    payload_sha256 TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_created
    ON admin_audit(created_at DESC);

INSERT OR IGNORE INTO schema_version(version) VALUES (32);
