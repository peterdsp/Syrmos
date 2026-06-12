-- Icon registry + line-display settings.
--
-- icons table: every known station icon, served from the Pi at /icons/...
-- The same record carries both the default URL (from the package) and any
-- admin override. The /api/icons/manifest endpoint composes a station-id ->
-- effective-url map that the apps consume on cold start.
--
-- line_display table: per-line polyline rendering parameters that the apps
-- can read at runtime. Lets a maintainer change a line's color or stroke
-- weight without an app release.

CREATE TABLE IF NOT EXISTS icons (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    -- Identifies what this icon is for.
    scope           TEXT NOT NULL,     -- 'station' | 'interchange' | 'vehicle'
    station_id      TEXT,              -- For scope='station' (M3_NIK, T7_AKT, etc.) or 'interchange' (M2_SYN, M1_MON, M3_DIM, ...)
    line_id         TEXT,              -- For scope='vehicle' (M1, M2, M3, T6, T7, M3_AIR)
    direction       TEXT,              -- For scope='vehicle' ('inbound' | 'outbound' | 'airport')
    -- Source of the SVG asset.
    default_url     TEXT NOT NULL,     -- Canonical URL from the package layout
    override_url    TEXT,              -- Admin-set override URL (cdn or self-hosted)
    description     TEXT,
    updated_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_icons_scope ON icons(scope);
CREATE INDEX IF NOT EXISTS idx_icons_station ON icons(station_id);
CREATE INDEX IF NOT EXISTS idx_icons_vehicle ON icons(line_id, direction);

-- Polyline / line-rendering parameters editable from admin.
CREATE TABLE IF NOT EXISTS line_display (
    line_id          TEXT PRIMARY KEY REFERENCES lines(id) ON DELETE CASCADE,
    stroke_color     TEXT NOT NULL,     -- "#0083C9"
    stroke_weight    INTEGER NOT NULL DEFAULT 4,
    stroke_dash      TEXT,              -- e.g. "4 2" for dashed lines (suburban)
    label_color      TEXT,              -- For station labels on the map
    glow             INTEGER NOT NULL DEFAULT 0,  -- 0|1, whether to render a soft glow
    notes            TEXT,
    updated_at       TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

-- Seed line_display with current defaults so the admin doesn't start empty.
INSERT OR IGNORE INTO line_display(line_id, stroke_color, stroke_weight, stroke_dash, label_color, glow, notes) VALUES
    ('M1',     '#00843D', 4, NULL, NULL, 0, 'Metro Line 1 (Green)'),
    ('M2',     '#E61E2A', 4, NULL, NULL, 0, 'Metro Line 2 (Red)'),
    ('M3',     '#0083C9', 4, NULL, NULL, 0, 'Metro Line 3 (Blue), city segment'),
    ('M3_AIR', '#0083C9', 4, '6 4', NULL, 0, 'Metro Line 3 airport extension (dashed)'),
    ('T6',     '#F39800', 4, NULL, NULL, 0, 'Tram T6 (Syntagma-Pikrodafni)'),
    ('T7',     '#F39800', 4, NULL, NULL, 0, 'Tram T7 (Akti Posidonos-Voula)'),
    ('A1',     '#EE2625', 3, '8 4', NULL, 0, 'Suburban A1 Piraeus-Airport (dashed)'),
    ('A2',     '#EE2625', 3, '8 4', NULL, 0, 'Suburban A2 Ano Liosia-Airport (dashed)'),
    ('A3',     '#EE2625', 3, '8 4', NULL, 0, 'Suburban A3 Athens-Chalcis (dashed)'),
    ('A4',     '#EE2625', 3, '8 4', NULL, 0, 'Suburban A4 Piraeus-Kiato (dashed)');

INSERT OR IGNORE INTO schema_version(version) VALUES (3);
