import sqlite3
import unittest
from datetime import datetime

from syrmos_admin.projector import ATHENS, active_trains, project_next_departures


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
            label TEXT,
            direction TEXT DEFAULT 'both'
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
            ("M2", "sat", "05:30", "05:28", 0),
            ("M2", "sun", "05:30", "00:30", 0),
            ("M3", "sat", "05:30", "05:28", 0),
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


def make_overnight_conn() -> sqlite3.Connection:
    """Fixture that mirrors the official Saturday overnight truth: M2 / M3
    (city) / T6 / T7 run 24h, so their Saturday service continues past midnight
    into Sunday. M1 is NOT 24h (last train ~01:00). The M3 airport branch is
    excluded from the 24h service, so M3_AIR has no overnight band."""
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    conn.executescript(
        """
        CREATE TABLE lines (id TEXT PRIMARY KEY, terminal_a TEXT NOT NULL, terminal_b TEXT NOT NULL);
        CREATE TABLE schedule_rules (line_id TEXT, day_type TEXT, open_time TEXT, close_time TEXT, is_24_7 INTEGER DEFAULT 0);
        CREATE TABLE frequency_bands (line_id TEXT, day_type TEXT, time_start TEXT, time_end TEXT, headway_minutes REAL, label TEXT, direction TEXT DEFAULT 'both');
        CREATE TABLE station_offsets (line_id TEXT, direction TEXT, station_id TEXT, minutes_from_origin INTEGER);
        """
    )
    conn.executemany(
        "INSERT INTO lines(id, terminal_a, terminal_b) VALUES (?,?,?)",
        [("M2", "Anthoupoli", "Elliniko"), ("M3", "Dimotiko Theatro", "Airport"),
         ("T6", "Syntagma", "Pikrodafni"), ("T7", "Syntagma", "SEF"), ("M1", "Piraeus", "Kifisia")],
    )
    conn.executemany(
        "INSERT INTO schedule_rules(line_id, day_type, open_time, close_time, is_24_7) VALUES (?,?,?,?,?)",
        [
            ("M2", "sat", "05:30", "05:28", 0), ("M2", "sun", "05:30", "00:30", 0),
            ("M3", "sat", "05:30", "05:28", 0), ("M3", "sun", "05:30", "00:30", 0),
            ("M3_AIR", "sat", "05:30", "00:30", 0), ("M3_AIR", "sun", "05:30", "23:00", 0),
            ("T6", "sat", "05:30", "05:28", 0), ("T6", "sun", "05:30", "01:00", 0),
            ("T7", "sat", "05:30", "05:28", 0),
            ("M1", "sat", "05:00", "01:00", 0),
        ],
    )
    conn.executemany(
        "INSERT INTO frequency_bands(line_id, day_type, time_start, time_end, headway_minutes, label) VALUES (?,?,?,?,?,?)",
        [
            # 24h Saturday service as three continuous bands: overnight tail
            # (00:xx -> 05:30, flows into Sunday), daytime, and the evening wrap
            # (22:00 -> 00:20 next day). Together they cover a full 24 hours.
            ("M2", "sat", "00:20", "05:30", 15.0, "sat_24mmm"),
            ("M2", "sat", "05:30", "22:00", 10.0, "saturday_day"),
            ("M2", "sat", "22:00", "00:20", 12.0, "sat_24mmm"),
            ("M2", "sun", "05:30", "00:30", 12.5, "sunday_all_day"),
            ("M3", "sat", "00:20", "05:30", 15.0, "sat_24mmm"),
            ("M3", "sat", "05:30", "22:00", 9.0, "saturday_day"),
            ("M3", "sat", "22:00", "00:20", 10.0, "sat_24mmm"),
            ("T6", "sat", "00:30", "05:30", 25.0, "sat_24mmm"),
            ("T6", "sat", "05:30", "22:00", 12.0, "saturday_day"),
            ("T6", "sat", "22:00", "00:30", 15.0, "sat_24mmm"),
            ("T7", "sat", "00:30", "05:30", 25.0, "sat_24mmm"),
            ("T7", "sat", "05:30", "22:00", 12.0, "saturday_day"),
            ("T7", "sat", "22:00", "00:30", 15.0, "sat_24mmm"),
            # Airport branch is NOT 24h: last overnight run ends 00:30, nothing after.
            ("M3_AIR", "sat", "22:00", "00:30", 36.0, "airport"),
            # M1 is NOT 24h: last saturday_late train ~01:00, nothing overnight.
            ("M1", "sat", "23:30", "01:00", 15.0, "saturday_late"),
        ],
    )
    conn.executemany(
        "INSERT INTO station_offsets(line_id, direction, station_id, minutes_from_origin) VALUES (?,?,?,?)",
        [
            ("M2", "outbound", "M2_END", 40), ("M2", "inbound", "M2_END", 40),
            ("M3", "outbound", "M3_END", 33), ("M3", "inbound", "M3_END", 33),
            ("M3_AIR", "outbound", "M3_END", 55), ("M3_AIR", "inbound", "M3_END", 55),
            ("T6", "outbound", "T6_END", 45), ("T6", "inbound", "T6_END", 45),
            ("T7", "outbound", "T7_END", 30), ("T7", "inbound", "T7_END", 30),
            ("M1", "outbound", "M1_END", 44), ("M1", "inbound", "M1_END", 44),
        ],
    )
    return conn


class ActiveTrainsOvernightTest(unittest.TestCase):
    """Saturday 24h service (M2/M3/T6/T7) must stay visible on the map after
    midnight into Sunday; M1 and the M3 airport branch must not."""

    def _lines(self, now):
        conn = make_overnight_conn()
        trains = active_trains(conn, ["M1", "M2", "M3", "M3_AIR", "T6", "T7"], now=now)
        return trains, {t["lineId"] for t in trains}

    def test_saturday_2359_all_night_lines_running(self):
        trains, lines = self._lines(datetime(2026, 8, 29, 23, 59, tzinfo=ATHENS))
        for lid in ("M2", "M3", "T6", "T7"):
            self.assertIn(lid, lines, f"{lid} should run at Sat 23:59")

    def test_sunday_0001_continues_saturday_service(self):
        # 00:01 Sunday belongs to the Saturday operating day.
        trains, lines = self._lines(datetime(2026, 8, 30, 0, 1, tzinfo=ATHENS))
        for lid in ("M2", "M3", "T6", "T7"):
            self.assertIn(lid, lines, f"{lid} should still run at Sun 00:01")

    def test_sunday_0153_regression_map_not_empty(self):
        # The exact defect: 01:53 Sunday returned zero metro/tram vehicles.
        trains, lines = self._lines(datetime(2026, 8, 30, 1, 53, tzinfo=ATHENS))
        self.assertTrue(trains, "map must not be empty at Sun 01:53")
        for lid in ("M2", "M3", "T6", "T7"):
            self.assertIn(lid, lines, f"{lid} 24h service must show at Sun 01:53")
        # Route identity + direction + provenance, not just a count.
        m2 = [t for t in trains if t["lineId"] == "M2"]
        self.assertTrue(m2)
        self.assertIn(m2[0]["directionKey"], {"outbound", "inbound"})
        self.assertIn(m2[0]["serviceType"], {"regular", "late_night"})
        self.assertIn("originDepartureMinute", m2[0])
        self.assertIn("totalTravelMinutes", m2[0])

    def test_sunday_0153_m1_not_24h(self):
        # M1 is not 24h: its last saturday_late train (~01:00) has finished by 01:53.
        _trains, lines = self._lines(datetime(2026, 8, 30, 1, 53, tzinfo=ATHENS))
        self.assertNotIn("M1", lines, "M1 must not be treated as 24h")

    def test_sunday_0153_airport_branch_excluded(self):
        # M3 city runs 24h but the airport branch does not.
        trains, _lines = self._lines(datetime(2026, 8, 30, 1, 53, tzinfo=ATHENS))
        airport = [t for t in trains if t["serviceType"] == "airport"]
        self.assertEqual(airport, [], "airport branch must not run overnight")

    def test_sunday_0459_last_overnight_window(self):
        _trains, lines = self._lines(datetime(2026, 8, 30, 4, 59, tzinfo=ATHENS))
        for lid in ("M2", "M3", "T6", "T7"):
            self.assertIn(lid, lines, f"{lid} should still run at Sun 04:59")

    def test_sunday_0530_daytime_service_starts(self):
        _trains, lines = self._lines(datetime(2026, 8, 30, 5, 30, tzinfo=ATHENS))
        self.assertIn("M2", lines)

    def test_missing_timetable_data_is_safe(self):
        # A line with no rules/bands must not crash and must contribute nothing.
        conn = make_overnight_conn()
        trains = active_trains(conn, ["ZZ_UNKNOWN"], now=datetime(2026, 8, 30, 1, 53, tzinfo=ATHENS))
        self.assertEqual(trains, [])

    def test_dst_spring_forward_boundary(self):
        # Last Sunday of March 2026 (29th), 03:xx local: zoneinfo handles the
        # skipped hour; the projector must not raise.
        trains, _lines = self._lines(datetime(2026, 3, 29, 3, 30, tzinfo=ATHENS))
        self.assertIsInstance(trains, list)


if __name__ == "__main__":
    unittest.main()
