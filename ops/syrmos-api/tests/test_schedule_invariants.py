"""Tests for the self-healing 24h Saturday overnight invariant.

These cover the enforcer in isolation (fast, in-memory), complementing the
projector tests and the end-to-end seed integration test.
"""
import sqlite3
import unittest

from syrmos_admin.schedule_invariants import (
    NOT_24H_LINES,
    OVERNIGHT_END,
    OVERNIGHT_LABEL,
    SATURDAY_24H_OVERNIGHT,
    ensure_saturday_overnight,
    provenance,
    saturday_overnight_gap,
    verify_saturday_continuity,
)


def _conn(with_direction: bool = True) -> sqlite3.Connection:
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    direction = ", direction TEXT DEFAULT 'both'" if with_direction else ""
    conn.execute(
        "CREATE TABLE frequency_bands (line_id TEXT, day_type TEXT, time_start TEXT,"
        f" time_end TEXT, headway_minutes REAL, label TEXT{direction})"
    )
    return conn


def _bands(conn, line_id):
    return sorted(
        (r["time_start"], r["time_end"], r["headway_minutes"], r["label"])
        for r in conn.execute(
            "SELECT time_start, time_end, headway_minutes, label FROM frequency_bands"
            " WHERE line_id=? AND day_type='sat'",
            (line_id,),
        )
    )


class EnsureOvernightTest(unittest.TestCase):
    def test_fills_missing_overnight_band(self):
        conn = _conn()
        # No Saturday bands at all -> enforcer must create the overnight band.
        changed = ensure_saturday_overnight(conn)
        self.assertEqual(set(changed), set(SATURDAY_24H_OVERNIGHT))
        for lid, (start, hw) in SATURDAY_24H_OVERNIGHT.items():
            self.assertIn((start, OVERNIGHT_END, hw, OVERNIGHT_LABEL), _bands(conn, lid))

    def test_repairs_truncated_scraper_band(self):
        conn = _conn()
        # The exact live defect: truncated sat_24mmm overnight fragments.
        conn.executemany(
            "INSERT INTO frequency_bands(line_id,day_type,time_start,time_end,headway_minutes,label,direction)"
            " VALUES(?,?,?,?,?,?, 'both')",
            [
                # Truncated scraper overnight fragments (the live defect) ...
                ("M3", "sat", "00:20", "02:00", 15.0, "sat_24mmm"),
                ("T6", "sat", "00:30", "01:40", 25.0, "sat_24mmm"),
                ("T7", "sat", "00:30", "01:40", 25.0, "sat_24mmm"),
                # ... plus the evening wrap band that owns 00:00 -> overnight_start.
                ("M3", "sat", "22:00", "00:20", 9.0, "saturday_evening"),
                ("T6", "sat", "21:00", "00:30", 15.0, "saturday_evening"),
                ("T7", "sat", "21:00", "00:30", 15.0, "saturday_evening"),
            ],
        )
        self.assertTrue(saturday_overnight_gap(conn, "M3"))  # gap exists before
        ensure_saturday_overnight(conn)
        for lid in ("M3", "T6", "T7"):
            self.assertEqual(saturday_overnight_gap(conn, lid), [], f"{lid} still has a gap")

    def test_idempotent(self):
        conn = _conn()
        ensure_saturday_overnight(conn)
        first = {lid: _bands(conn, lid) for lid in SATURDAY_24H_OVERNIGHT}
        changed = ensure_saturday_overnight(conn)
        self.assertEqual(changed, [], "second run should be a no-op")
        second = {lid: _bands(conn, lid) for lid in SATURDAY_24H_OVERNIGHT}
        self.assertEqual(first, second)

    def test_no_duplicate_overnight_band(self):
        conn = _conn()
        ensure_saturday_overnight(conn)
        ensure_saturday_overnight(conn)
        for lid in SATURDAY_24H_OVERNIGHT:
            rows = conn.execute(
                "SELECT COUNT(*) AS n FROM frequency_bands"
                " WHERE line_id=? AND day_type='sat' AND label=?",
                (lid, OVERNIGHT_LABEL),
            ).fetchone()
            self.assertEqual(rows["n"], 1, f"{lid} must have exactly one overnight band")

    def test_works_without_direction_column(self):
        # Older DBs (pre-migration 0012) have no direction column.
        conn = _conn(with_direction=False)
        conn.execute(
            "INSERT INTO frequency_bands(line_id,day_type,time_start,time_end,headway_minutes,label)"
            " VALUES('M2','sat','22:00','00:20',10.0,'saturday_evening')"
        )
        ensure_saturday_overnight(conn)
        self.assertIn(("00:20", OVERNIGHT_END, 15.0, OVERNIGHT_LABEL), _bands(conn, "M2"))
        self.assertEqual(saturday_overnight_gap(conn, "M2"), [])

    def test_does_not_touch_non_24h_lines(self):
        conn = _conn()
        ensure_saturday_overnight(conn)
        for lid in NOT_24H_LINES:
            self.assertEqual(
                _bands(conn, lid), [], f"{lid} must not get an overnight band"
            )

    def test_gap_detection_counts_wraparound_evening_band(self):
        conn = _conn()
        # Evening band wraps past midnight (22:00 -> 00:20) and the overnight
        # band continues to 05:30: together, no gap before the handover.
        conn.executemany(
            "INSERT INTO frequency_bands(line_id,day_type,time_start,time_end,headway_minutes,label,direction)"
            " VALUES(?,?,?,?,?,?, 'both')",
            [
                ("M2", "sat", "22:00", "00:20", 10.0, "saturday_evening"),
                ("M2", "sat", "00:20", "05:30", 15.0, OVERNIGHT_LABEL),
            ],
        )
        self.assertEqual(saturday_overnight_gap(conn, "M2"), [])

    def test_verify_reports_all_lines(self):
        conn = _conn()
        report = verify_saturday_continuity(conn)
        self.assertEqual(set(report), set(SATURDAY_24H_OVERNIGHT))

    def test_provenance_shape(self):
        p = provenance()
        self.assertEqual(p["source"], "https://www.oasa.gr/en/24mmm/")
        self.assertEqual(p["verifiedOn"], "2026-08-30")
        self.assertEqual(set(p["lines"]), {"M2", "M3", "T6", "T7"})
        self.assertNotIn("M1", p["lines"])
        self.assertNotIn("M3_AIR", p["lines"])


if __name__ == "__main__":
    unittest.main()


class ClosureNeverEmptiesLineTest(unittest.TestCase):
    """A station closure must never remove every station of a line."""

    def _conn(self):
        conn = sqlite3.connect(":memory:")
        conn.row_factory = sqlite3.Row
        conn.execute("CREATE TABLE line_stations (line_id TEXT, station_id TEXT, seq INTEGER, direction TEXT)")
        rows = [("T6", "T6_SYN", 0), ("T6", "T6_FIX", 1), ("M2", "M2_SYN", 0), ("M2", "M2_PAN", 1),
                ("M2", "M2_AKR", 2)]
        conn.executemany("INSERT INTO line_stations VALUES (?, ?, ?, 'both')", rows)
        return conn

    @staticmethod
    def _closure(cid, stations, lines=(), start=100, end=None, status="scheduled"):
        return {"id": cid, "stationIds": list(stations), "lineIds": list(lines), "mode": "closed",
                "startsAtEpochSeconds": start, "endsAtEpochSeconds": end, "status": status}

    def test_partial_closure_is_fine(self):
        from syrmos_admin.schedule_invariants import closure_emptied_lines

        self.assertEqual(closure_emptied_lines(self._conn(), self._closure("a", ["M2_SYN", "T6_SYN"])), [])

    def test_whole_line_closure_is_flagged(self):
        from syrmos_admin.schedule_invariants import closure_emptied_lines

        conn = self._conn()
        self.assertEqual(closure_emptied_lines(conn, self._closure("a", ["T6_SYN", "T6_FIX"])), ["T6"])

    def test_line_scope_limits_the_check(self):
        from syrmos_admin.schedule_invariants import closure_emptied_lines

        # Scoped to M2: closing T6's two stations for M2 only empties nothing.
        closure = self._closure("a", ["T6_SYN", "T6_FIX", "M2_SYN"], lines=["M2"])
        self.assertEqual(closure_emptied_lines(self._conn(), closure), [])

    def test_overlapping_closures_add_up(self):
        from syrmos_admin.schedule_invariants import closure_emptied_lines

        conn = self._conn()
        other = self._closure("b", ["T6_FIX"], start=50, end=500)
        self.assertEqual(closure_emptied_lines(conn, self._closure("a", ["T6_SYN"]), [other]), ["T6"])
        # A non-overlapping, draft or cancelled peer does not count.
        for peer in (self._closure("c", ["T6_FIX"], start=0, end=100),
                     self._closure("d", ["T6_FIX"], status="draft"),
                     self._closure("e", ["T6_FIX"], status="cancelled")):
            self.assertEqual(closure_emptied_lines(conn, self._closure("a", ["T6_SYN"]), [peer]), [])

    def test_editing_a_closure_does_not_count_itself_twice(self):
        from syrmos_admin.schedule_invariants import closure_emptied_lines

        conn = self._conn()
        stored = self._closure("a", ["T6_FIX"])
        self.assertEqual(closure_emptied_lines(conn, self._closure("a", ["T6_SYN"]), [stored]), [])
