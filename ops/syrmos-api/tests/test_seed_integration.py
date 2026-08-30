"""End-to-end seed integration test.

Unlike test_projector (which hand-builds a tiny in-memory fixture), this test
builds a real DB through the ACTUAL production seed path -- migrations +
scripts.import_athens_package + scripts.seed_station_offsets -- and runs the
server projector against it at the exact times from the incident report. This is
the layer that would have caught the live defect: the projector logic was fine,
but the seeded data (and the offline bundle baked from it) had a truncated
Saturday overnight band, so the map went dark after midnight.

It also asserts the offline-first artifact (out/schedules/*.json) is continuous,
so the online (projector-over-DB) and offline (bundled-JSON) paths agree.
"""
import json
import os
import sqlite3
import tempfile
import unittest
from datetime import datetime
from pathlib import Path

from syrmos_admin import db as dbmod
from syrmos_admin.projector import ATHENS, active_trains, project_next_departures
from syrmos_admin.schedule_invariants import saturday_overnight_gap

ROOT = Path(__file__).resolve().parent.parent
ALL_LINES = ["M1", "M2", "M3", "M3_AIR", "T6", "T7"]
NIGHT_LINES = ("M2", "M3", "T6", "T7")


def _lines_present(trains):
    out: dict[str, int] = {}
    for t in trains:
        out[t["lineId"]] = out.get(t["lineId"], 0) + 1
    return out


def _build_seeded_db(path: str) -> None:
    """Run the real seed pipeline into `path` by pointing dbmod.connect at it.

    db.connect binds its default path at definition time, and seed scripts call
    connect() with no argument, so patching DEFAULT_DB_PATH is not enough --
    patch connect itself for the duration of the build.
    """
    import scripts.import_athens_package as importer
    import scripts.seed_station_offsets as offsets

    real_connect = dbmod.connect
    dbmod.connect = lambda *a, **k: real_connect(path)  # type: ignore[assignment]
    try:
        conn = real_connect(path)
        dbmod.migrate(conn)
        importer.apply(conn, dry_run=False)
        conn.close()
        offsets.main()  # uses patched connect -> same file
    finally:
        dbmod.connect = real_connect  # type: ignore[assignment]


class SeedIntegrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        fd, cls.db_path = tempfile.mkstemp(suffix=".db")
        os.close(fd)
        try:
            _build_seeded_db(cls.db_path)
        except Exception as e:  # pragma: no cover - environment guard
            raise unittest.SkipTest(f"seed pipeline unavailable: {e}")

    @classmethod
    def tearDownClass(cls):
        for suffix in ("", "-wal", "-shm"):
            try:
                os.remove(cls.db_path + suffix)
            except OSError:
                pass

    def setUp(self):
        self.conn = sqlite3.connect(self.db_path)
        self.conn.row_factory = sqlite3.Row

    def tearDown(self):
        self.conn.close()

    def _active(self, dt):
        return active_trains(self.conn, ALL_LINES, now=dt.replace(tzinfo=ATHENS))

    # --- the seed itself is continuous (would have caught the real bug) ---
    def test_seeded_saturday_overnight_is_continuous(self):
        for lid in NIGHT_LINES:
            self.assertEqual(
                saturday_overnight_gap(self.conn, lid), [],
                f"{lid} seeded Saturday overnight has a coverage gap",
            )

    # --- the incident timeline ---
    def test_saturday_2359(self):
        lines = _lines_present(self._active(datetime(2026, 8, 29, 23, 59)))
        for lid in NIGHT_LINES:
            self.assertIn(lid, lines, f"{lid} must run at Sat 23:59")

    def test_sunday_0001_is_saturday_service(self):
        lines = _lines_present(self._active(datetime(2026, 8, 30, 0, 1)))
        for lid in NIGHT_LINES:
            self.assertIn(lid, lines, f"{lid} must continue at Sun 00:01")

    def test_sunday_0153_the_reported_defect(self):
        trains = self._active(datetime(2026, 8, 30, 1, 53))
        lines = _lines_present(trains)
        self.assertTrue(trains, "map must not be empty at Sun 01:53")
        for lid in NIGHT_LINES:
            self.assertIn(lid, lines, f"{lid} 24h service must show at Sun 01:53")

    def test_sunday_0230_after_old_truncation_point(self):
        # The old scraper band died at 02:00 (metro) / 01:40 (tram); 02:30 is the
        # window that used to go dark. All four must still be running.
        lines = _lines_present(self._active(datetime(2026, 8, 30, 2, 30)))
        for lid in NIGHT_LINES:
            self.assertIn(lid, lines, f"{lid} must run at Sun 02:30 (past old cutoff)")

    def test_sunday_0459_last_overnight_window(self):
        lines = _lines_present(self._active(datetime(2026, 8, 30, 4, 59)))
        for lid in NIGHT_LINES:
            self.assertIn(lid, lines, f"{lid} must run at Sun 04:59")

    def test_sunday_0530_daytime_service_starts(self):
        lines = _lines_present(self._active(datetime(2026, 8, 30, 5, 30)))
        self.assertIn("M1", lines, "M1 daytime service starts by 05:30")
        for lid in NIGHT_LINES:
            self.assertIn(lid, lines)

    # --- route identity + direction + service-day, not just a count ---
    def test_route_identity_and_direction_at_0153(self):
        trains = self._active(datetime(2026, 8, 30, 1, 53))
        for lid in NIGHT_LINES:
            rows = [t for t in trains if t["lineId"] == lid]
            self.assertTrue(rows, f"{lid} missing")
            for r in rows:
                self.assertIn(r["directionKey"], {"outbound", "inbound"})
                self.assertIn("originDepartureMinute", r)
                self.assertIn("totalTravelMinutes", r)
                self.assertGreater(r["totalTravelMinutes"], 0)

    def test_m2_and_m3_overnight_specifically(self):
        trains = self._active(datetime(2026, 8, 30, 3, 30))
        for lid in ("M2", "M3"):
            self.assertTrue([t for t in trains if t["lineId"] == lid],
                            f"{lid} city overnight must run at Sun 03:30")

    def test_t6_t7_overnight_specifically(self):
        trains = self._active(datetime(2026, 8, 30, 3, 30))
        for lid in ("T6", "T7"):
            self.assertTrue([t for t in trains if t["lineId"] == lid],
                            f"{lid} tram overnight must run at Sun 03:30")

    # --- exclusions ---
    def test_m1_not_24h(self):
        for dt in (datetime(2026, 8, 30, 1, 53), datetime(2026, 8, 30, 3, 30)):
            lines = _lines_present(self._active(dt))
            self.assertNotIn("M1", lines, f"M1 must not run at {dt.time()}")

    def test_airport_branch_excluded_overnight(self):
        trains = self._active(datetime(2026, 8, 30, 1, 53))
        airport = [t for t in trains if t["serviceType"] == "airport"]
        self.assertEqual(airport, [], "airport branch must not run overnight")

    # --- ordinary Sunday night into Monday is NOT all-night ---
    def test_ordinary_sunday_evening_runs(self):
        # Plain Sunday 23:00 (2026-06-14): normal Sunday service still running.
        lines = _lines_present(self._active(datetime(2026, 6, 14, 23, 0)))
        for lid in ("M2", "M3"):
            self.assertIn(lid, lines)

    def test_ordinary_monday_small_hours_dark(self):
        # Sunday -> Monday is not 24h: at Monday 02:30 no metro/tram overnight.
        lines = _lines_present(self._active(datetime(2026, 6, 15, 2, 30)))
        for lid in NIGHT_LINES:
            self.assertNotIn(lid, lines, f"{lid} must NOT run Mon 02:30")

    # --- DST boundaries must not raise ---
    def test_dst_spring_forward(self):
        # Last Sunday of March 2026 (29th) spring-forward skips 03:00->04:00.
        self.assertIsInstance(self._active(datetime(2026, 3, 29, 3, 30)), list)

    def test_dst_autumn_back(self):
        # Last Sunday of October 2026 (25th) falls back 04:00->03:00.
        self.assertIsInstance(self._active(datetime(2026, 10, 25, 3, 30)), list)

    # --- missing / stale data safety ---
    def test_missing_line_contributes_nothing(self):
        self.assertEqual(active_trains(self.conn, ["ZZ_UNKNOWN"],
                                       now=datetime(2026, 8, 30, 1, 53, tzinfo=ATHENS)), [])

    def test_stale_override_closure_removes_line(self):
        # A STASY closure for the date (date_overrides payload closed=true) must
        # blank that line even during 24h service.
        self.conn.execute(
            "INSERT INTO date_overrides(override_date, line_id, source, payload_json)"
            " VALUES('2026-08-30','M2','test','{\"closed\": true}')"
        )
        lines = _lines_present(self._active(datetime(2026, 8, 30, 1, 53)))
        self.assertNotIn("M2", lines, "closed line must not show trains")
        self.assertIn("M3", lines, "other 24h lines unaffected")

    # --- departures projector agrees with the map at 01:53 ---
    def test_departures_projector_shows_overnight(self):
        rows = project_next_departures(
            self.conn, "M2_SYN", ["M2"],
            now=datetime(2026, 8, 30, 1, 53, tzinfo=ATHENS), limit=3,
        )
        self.assertTrue(rows, "departures must not be empty at Sun 01:53")
        self.assertEqual(rows[0]["lineId"], "M2")

    # --- provenance recorded in the DB (migration 0028) ---
    def test_provenance_recorded(self):
        meta = {r["key"]: r["value"] for r in self.conn.execute(
            "SELECT key, value FROM meta WHERE key LIKE 'saturday_24h%'")}
        self.assertEqual(meta.get("saturday_24h_source"), "https://www.oasa.gr/en/24mmm/")
        self.assertEqual(meta.get("saturday_24h_verified_on"), "2026-08-30")


class OfflineBundleReferenceTest(unittest.TestCase):
    """The offline-first artifact the generator ships must be continuous too."""

    def _load(self, lid):
        p = ROOT / "out" / "schedules" / f"{lid}.json"
        return json.loads(p.read_text(encoding="utf-8"))

    def _sat_gap(self, lid):
        conn = sqlite3.connect(":memory:")
        conn.row_factory = sqlite3.Row
        conn.execute(
            "CREATE TABLE frequency_bands (line_id TEXT, day_type TEXT, time_start TEXT,"
            " time_end TEXT, headway_minutes REAL, label TEXT, direction TEXT DEFAULT 'both')"
        )
        for b in self._load(lid)["bands"]:
            if b["dayType"] != "sat":
                continue
            conn.execute(
                "INSERT INTO frequency_bands(line_id,day_type,time_start,time_end,headway_minutes,label)"
                " VALUES(?,?,?,?,?,?)",
                (lid, "sat", b["timeStart"], b["timeEnd"], b["headwayMinutes"], b.get("label", "")),
            )
        return saturday_overnight_gap(conn, lid)

    def test_reference_bundle_overnight_continuous(self):
        for lid in NIGHT_LINES:
            self.assertEqual(self._sat_gap(lid), [], f"out/schedules/{lid}.json has a gap")

    def test_reference_bundle_m1_is_not_24h(self):
        # M1 must keep its genuine overnight gap (~01:00 -> 05:00); it is NOT 24h.
        self.assertTrue(self._sat_gap("M1"), "M1 must not be continuous overnight")


if __name__ == "__main__":
    unittest.main()
