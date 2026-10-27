"""Every line gets an editable line_display row whose defaults match what the
apps draw without an override, so backfilling never changes the map."""
from pathlib import Path

from syrmos_admin import app as appmod
from syrmos_admin import db as dbmod

MIGRATION = Path(__file__).resolve().parent.parent / "migrations" / "0033_line_display_backfill.sql"


def _insert_line(conn, line_id, mode, color, name):
    conn.execute("PRAGMA foreign_keys = OFF")
    conn.execute(
        "INSERT OR REPLACE INTO lines(id, mode, name_en, name_el, color, terminal_a, terminal_b, sort_order)"
        " VALUES(?,?,?,?,?,?,?,?)",
        (line_id, mode, name, name, color, "A", "B", 99),
    )


def _row(conn, line_id):
    return conn.execute(
        "SELECT stroke_color, stroke_weight, stroke_dash, glow FROM line_display WHERE line_id=?",
        (line_id,),
    ).fetchone()


def test_backfill_defaults_match_client_rendering(temp_db):
    conn = dbmod.connect()
    conn.execute("DELETE FROM line_display")
    _insert_line(conn, "M9", "metro", "#123456", "Metro 9")
    _insert_line(conn, "T9", "tram", "#654321", "Tram 9")
    _insert_line(conn, "S9", "suburban", "#111111", "Suburban 9")
    _insert_line(conn, "B9", "bus", "#222222", "Bus 9")
    conn.executescript(MIGRATION.read_text(encoding="utf-8"))

    assert tuple(_row(conn, "M9")) == ("#123456", 5, None, 0)
    assert tuple(_row(conn, "T9")) == ("#654321", 5, None, 0)
    assert tuple(_row(conn, "S9")) == ("#111111", 4, None, 0)
    # NULL dash lets the bus keep its own client-side dash.
    assert tuple(_row(conn, "B9")) == ("#222222", 4, None, 0)
    conn.close()


def test_backfill_never_overwrites_an_edited_row(temp_db):
    conn = dbmod.connect()
    _insert_line(conn, "M9", "metro", "#123456", "Metro 9")
    conn.execute(
        "INSERT OR REPLACE INTO line_display(line_id, stroke_color, stroke_weight, stroke_dash, glow)"
        " VALUES('M9', '#FF00FF', 7, '3 3', 1)"
    )
    conn.executescript(MIGRATION.read_text(encoding="utf-8"))
    assert tuple(_row(conn, "M9")) == ("#FF00FF", 7, "3 3", 1)
    conn.close()


def test_new_line_appears_on_the_line_drawing_page(admin_client):
    client, headers, _ = admin_client
    conn = dbmod.connect()
    _insert_line(conn, "Z9", "suburban", "#ABCDEF", "Brand new line")
    conn.execute("DELETE FROM line_display WHERE line_id='Z9'")
    conn.close()

    r = client.get("/line-display", headers=headers)
    assert r.status_code == 200
    assert "Z9" in r.text

    conn = dbmod.connect()
    assert tuple(_row(conn, "Z9")) == ("#ABCDEF", 4, None, 0)
    assert appmod.ensure_line_display_rows(conn) == 0  # idempotent
    conn.close()
