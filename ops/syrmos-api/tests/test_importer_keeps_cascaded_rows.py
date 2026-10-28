"""The nightly Athens importer deletes and re-inserts its lines. ON DELETE
CASCADE must not take admin line styling or scraped date overrides with it."""
import scripts.import_athens_package as importer
from syrmos_admin import db as dbmod


def test_reimport_keeps_line_display_and_date_overrides(tmp_path):
    path = str(tmp_path / "syrmos.db")
    conn = dbmod.connect(path)
    dbmod.migrate(conn)
    importer.apply(conn, dry_run=False)

    conn.execute(
        "INSERT OR REPLACE INTO line_display(line_id, stroke_color, stroke_weight, stroke_dash, glow)"
        " VALUES('M1', '#ABCDEF', 7, '3 3', 1)"
    )
    conn.execute(
        "INSERT INTO date_overrides(override_date, line_id, source, payload_json)"
        " VALUES('2026-10-28', 'M1', 'manual', '{\"closed\": true}')"
    )

    importer.apply(conn, dry_run=False)

    assert tuple(conn.execute(
        "SELECT stroke_color, stroke_weight, stroke_dash, glow FROM line_display WHERE line_id='M1'"
    ).fetchone()) == ("#ABCDEF", 7, "3 3", 1)
    assert conn.execute(
        "SELECT payload_json FROM date_overrides WHERE line_id='M1' AND override_date='2026-10-28'"
    ).fetchone()[0] == '{"closed": true}'
    conn.close()


def test_import_gives_every_line_a_display_row(tmp_path):
    conn = dbmod.connect(str(tmp_path / "syrmos.db"))
    dbmod.migrate(conn)
    conn.execute("DELETE FROM line_display")
    importer.apply(conn, dry_run=False)
    missing = conn.execute(
        "SELECT id FROM lines WHERE id NOT IN (SELECT line_id FROM line_display)"
    ).fetchall()
    assert missing == []
    conn.close()
