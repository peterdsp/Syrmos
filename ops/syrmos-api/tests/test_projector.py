import sqlite3
import unittest
from datetime import datetime

from syrmos_admin.projector import ATHENS, project_next_departures


def make_conn() -> sqlite3.Connection:
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    conn.executescript(
        """
        CREATE TABLE lines (
            id TEXT PRIMARY KEY,
            terminal_a TEXT NOT NULL,
            terminal_b TEXT NOT NULL
        );
        CREATE TABLE schedule_rules (
            line_id TEXT NOT NULL,
            day_type TEXT NOT NULL,
            open_time TEXT NOT NULL,
            close_time TEXT NOT NULL,
            is_24_7 INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE frequency_bands (
            line_id TEXT NOT NULL,
            day_type TEXT NOT NULL,
            time_start TEXT NOT NULL,
            time_end TEXT NOT NULL,
            headway_minutes REAL NOT NULL,
            label TEXT
        );
        CREATE TABLE station_offsets (
            line_id TEXT NOT NULL,
            direction TEXT NOT NULL,
            station_id TEXT NOT NULL,
            minutes_from_origin INTEGER NOT NULL
        );
        """
    )
    conn.executemany(
        "INSERT INTO lines(id, terminal_a, terminal_b) VALUES (?, ?, ?)",
        [
            ("M2", "Anthoupoli", "Elliniko"),
            ("M3", "Dimotiko Theatro", "Airport"),
        ],
    )
    conn.executemany(
        "INSERT INTO schedule_rules(line_id, day_type, open_time, close_time, is_24_7)"
        " VALUES (?, ?, ?, ?, ?)",
        [
            ("M2", "sat", "00:00", "23:59", 1),
            ("M2", "sun", "05:30", "00:30", 0),
            ("M3", "sat", "00:00", "23:59", 1),
            ("M3", "sun", "05:30", "00:30", 0),
            ("M3_AIR", "sun", "05:30", "23:00", 0),
        ],
    )
    conn.executemany(
        "INSERT INTO frequency_bands(line_id, day_type, time_start, time_end,"
        " headway_minutes, label) VALUES (?, ?, ?, ?, ?, ?)",
        [
            ("M2", "sat", "00:30", "05:30", 15.0, "saturday_overnight_24_7"),
            ("M2", "sun", "05:30", "00:30", 12.5, "sunday_all_day"),
            ("M3", "sat", "00:30", "05:30", 15.0, "saturday_overnight_24_7"),
            ("M3_AIR", "sun", "05:30", "23:00", 36.0, "airport"),
        ],
    )
    conn.executemany(
        "INSERT INTO station_offsets(line_id, direction, station_id, minutes_from_origin)"
        " VALUES (?, ?, ?, ?)",
        [
            ("M2", "outbound", "M2_SYN", 14),
            ("M2", "inbound", "M2_SYN", 17),
            ("M3", "outbound", "M3_SYN", 24),
            ("M3", "inbound", "M3_SYN", 40),
        ],
    )
    return conn


class ProjectorTest(unittest.TestCase):
    def test_sunday_early_uses_saturday_overnight_extension_after_station_offset(self):
        conn = make_conn()
        rows = project_next_departures(
            conn,
            "M2_SYN",
            ["M2"],
            now=datetime(2026, 6, 14, 3, 43, tzinfo=ATHENS),
            limit=4,
        )

        self.assertEqual(rows[0]["lineId"], "M2")
        self.assertEqual(rows[0]["direction"], "Elliniko")
        self.assertEqual(rows[0]["time"], "03:44")
        self.assertEqual(rows[0]["minutesAway"], 1)
        self.assertEqual(rows[1]["direction"], "Anthoupoli")
        self.assertEqual(rows[1]["time"], "03:47")

    def test_direction_filter_returns_only_requested_stream(self):
        conn = make_conn()
        rows = project_next_departures(
            conn,
            "M2_SYN",
            ["M2"],
            direction="inbound",
            now=datetime(2026, 6, 14, 3, 43, tzinfo=ATHENS),
            limit=3,
        )

        self.assertEqual({row["directionKey"] for row in rows}, {"inbound"})
        self.assertEqual({row["direction"] for row in rows}, {"Anthoupoli"})

    def test_multiple_lines_compete_before_limit_is_applied(self):
        conn = make_conn()
        rows = project_next_departures(
            conn,
            "M2_SYN",
            ["M2", "M3"],
            now=datetime(2026, 6, 14, 3, 43, tzinfo=ATHENS),
            limit=4,
        )

        self.assertIn("M2", {row["lineId"] for row in rows})
        self.assertIn("M3", {row["lineId"] for row in rows})

    def test_airport_branch_uses_m3_station_offsets(self):
        conn = make_conn()
        rows = project_next_departures(
            conn,
            "M3_SYN",
            ["M3_AIR"],
            now=datetime(2026, 6, 14, 5, 40, tzinfo=ATHENS),
            limit=1,
        )

        self.assertEqual(rows[0]["lineId"], "M3")
        self.assertEqual(rows[0]["serviceType"], "airport")
        self.assertEqual(rows[0]["time"], "05:54")
        self.assertEqual(rows[0]["minutesAway"], 14)


if __name__ == "__main__":
    unittest.main()
