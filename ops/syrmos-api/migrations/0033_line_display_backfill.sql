-- Give every line a line_display row so the admin "Line drawing" page can edit
-- it and /api/line-display carries it to the apps.
--
-- Values match what the clients draw when no override exists (lines.color,
-- weight 5 for metro/tram and 4 otherwise, no dash: buses keep their own dash
-- when stroke_dash is NULL), so backfilling changes nothing on the map until a
-- row is edited. Existing rows are left untouched.
INSERT OR IGNORE INTO line_display(line_id, stroke_color, stroke_weight, stroke_dash, label_color, glow, notes)
SELECT id,
       color,
       CASE WHEN mode IN ('metro', 'tram') THEN 5 ELSE 4 END,
       NULL,
       NULL,
       0,
       name_en
FROM lines;

INSERT OR IGNORE INTO schema_version(version) VALUES (33);
