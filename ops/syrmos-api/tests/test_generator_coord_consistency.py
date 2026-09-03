"""Regression guard: the generator must expose ONE coordinate source.

Background (fix/t7-seed-coord-divergence): the committed bundled seed shipped six
T7 tram stops whose coordinates disagreed between the denormalized
`seed/lines.json` (each line carries a `stations[]` copy) and the canonical
`seed/stations.json` registry, by up to ~490 m. The client apps render markers
and nearest-station from the registry, so the denormalized copy was the stale one
and was reconciled to the registry.

The server generator (`syrmos_admin.generator`) is NOT where that divergence came
from: `_build_lines` and `_build_stations` both read `stations.lat` / `stations.lng`
from the SAME `stations` row (the line payload just joins through `line_stations`).
That single-source property is the invariant that keeps a regenerated snapshot from
ever reintroducing a lines-vs-registry coordinate split. This test locks it: it
builds a real DB through the production seed path and asserts that, for every
station a line references, the coordinate the line payload reports is identical to
the coordinate the flat station registry reports.

If a future refactor points either builder at a different table/column (a second
coordinate source), this test fails before the divergent snapshot can ship.

Uses the real `scripts.import_athens_package` seed path, like test_seed_integration;
skips cleanly if that pipeline is unavailable in the environment.
"""
import os
import sqlite3
import tempfile
import unittest

from syrmos_admin import db as dbmod
from syrmos_admin import generator


def _build_seeded_db(path: str) -> None:
    import scripts.import_athens_package as importer

    real_connect = dbmod.connect
    dbmod.connect = lambda *a, **k: real_connect(path)  # type: ignore[assignment]
    try:
        conn = real_connect(path)
        dbmod.migrate(conn)
        importer.apply(conn, dry_run=False)
        conn.close()
    finally:
        dbmod.connect = real_connect  # type: ignore[assignment]


class GeneratorCoordConsistencyTest(unittest.TestCase):
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

    def test_line_payload_and_registry_agree_on_every_shared_stop(self):
        lines = generator._build_lines(self.conn)["lines"]
        registry = {
            s["id"]: (s["lat"], s["lng"])
            for s in generator._build_stations(self.conn)["stations"]
        }

        # sanity: we actually exercised real data, including the T7 loop.
        line_ids = {ln["id"] for ln in lines}
        self.assertIn("T7", line_ids, "expected the T7 tram line in the seeded DB")

        mismatches = []
        checked = 0
        for ln in lines:
            for st in ln["stations"]:
                sid = st["id"]
                self.assertIn(
                    sid, registry,
                    f"{ln['id']}:{sid} is on a line but absent from the station registry",
                )
                checked += 1
                reg_lat, reg_lng = registry[sid]
                if (st["lat"], st["lng"]) != (reg_lat, reg_lng):
                    mismatches.append(
                        f"{ln['id']}:{sid} line=({st['lat']},{st['lng']}) "
                        f"registry=({reg_lat},{reg_lng})"
                    )

        self.assertGreater(checked, 0, "no line stops were checked")
        self.assertEqual(
            mismatches, [],
            "generator emits a line stop coordinate that differs from the station "
            "registry -- the single-source invariant is broken:\n  "
            + "\n  ".join(mismatches),
        )

    def test_t7_loop_stops_are_present_and_consistent(self):
        """The exact six stops from the bundled-seed divergence, checked end to end
        through the generator so a split can never re-open just for T7."""
        t7_loop = {"T7_DIM", "T7_PLA", "T7_EVA", "T7_GRI", "T7_MIK", "T7_GIP"}
        lines = generator._build_lines(self.conn)["lines"]
        registry = {
            s["id"]: (s["lat"], s["lng"])
            for s in generator._build_stations(self.conn)["stations"]
        }
        t7 = next((ln for ln in lines if ln["id"] == "T7"), None)
        self.assertIsNotNone(t7, "T7 line missing from generator output")
        seen = set()
        for st in t7["stations"]:
            if st["id"] in t7_loop:
                seen.add(st["id"])
                self.assertEqual(
                    (st["lat"], st["lng"]), registry[st["id"]],
                    f"{st['id']} line vs registry coordinate split",
                )
        self.assertEqual(seen, t7_loop, f"missing T7 loop stops: {t7_loop - seen}")


if __name__ == "__main__":
    unittest.main()
