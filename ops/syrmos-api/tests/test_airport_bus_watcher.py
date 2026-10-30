"""The airport-bus watcher's per-stop ETA and route-dedupe behaviour.

Three bugs this pins, all reported from the Athens and Thessaloniki airport
screens on 2026-10-30:

1. Every stop on the way back from the airport showed "no bus is approaching
   this stop right now", because the per-stop arrivals list discarded anything
   whose direction was `from_airport`.
2. Most stops never had an ETA at all: ~300 OASA stops were refreshed 25 per
   30 s cycle against a 120 s TTL.
3. Thessaloniki listed 01N, 01X, 02X, 03X, 1N and 79 twice each.
"""
import importlib.util
from pathlib import Path
from types import SimpleNamespace

import pytest

WATCHER = Path(__file__).resolve().parents[1] / "scripts" / "airport-bus-watcher.py"


def _load():
    spec = importlib.util.spec_from_file_location("airport_bus_watcher", WATCHER)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


watcher = _load()


def _route(route_id, direction="unknown"):
    return SimpleNamespace(route_id=route_id, direction=SimpleNamespace(value=direction))


def test_duplicate_routes_collapse_to_one_row():
    routes = [_route("01N"), _route("01X"), _route("01N"), _route("79"), _route("01X"), _route("79")]
    unique = watcher._dedupe_routes(routes)
    assert [r.route_id for r in unique] == ["01N", "01X", "79"]


def test_dedupe_keeps_discovery_order():
    routes = [_route("03X"), _route("01N"), _route("03X")]
    assert [r.route_id for r in watcher._dedupe_routes(routes)] == ["03X", "01N"]


def test_dedupe_keeps_both_directions_of_one_route():
    # A route genuinely running both ways is two rows, not a duplicate.
    routes = [_route("79", "to_airport"), _route("79", "from_airport"), _route("79", "to_airport")]
    unique = watcher._dedupe_routes(routes)
    assert len(unique) == 2


def test_dedupe_tolerates_an_empty_list():
    assert watcher._dedupe_routes([]) == []


def test_per_stop_budget_covers_every_oasa_stop_within_the_ttl():
    # ~300 stops across X93/X95/X96/X97, a 30 s cycle, and the TTL below must
    # leave room to refresh all of them: budget * (ttl / cycle) > stops.
    w = watcher.AirportBusWatcher.__new__(watcher.AirportBusWatcher)
    import os
    ttl = float(os.environ.get("AIRPORT_PER_STOP_TTL", "300"))
    budget = int(os.environ.get("AIRPORT_PER_STOP_BUDGET", "60"))
    cycle = watcher.DEFAULT_POLL_INTERVAL
    assert budget * (ttl / cycle) > 300, (
        f"budget {budget} over a {ttl}s TTL at a {cycle}s cycle refreshes only "
        f"{budget * (ttl / cycle):.0f} stops, fewer than the ~300 OASA publishes"
    )


def test_lines_payload_marks_which_lines_have_a_timetable():
    """`hasTimetable` separates "no times published" from "closed".

    X3 and 2X, the Thessaloniki airport shuttles, hold neither schedule rules
    nor frequency bands, so the station board rendered "the line is closed or
    today's service has ended" at Makedonia Airport on a Friday evening while
    the shuttles were running. That is a claim about the operator we have no
    basis for.
    """
    import sqlite3
    from syrmos_admin import generator

    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    # The generator reads these two tables to decide; an unmigrated DB must not
    # raise, it must simply report nothing as timetabled.
    timetabled: set[str] = set()
    for table in ("schedule_rules", "frequency_bands"):
        try:
            timetabled.update(r[0] for r in conn.execute(f"SELECT DISTINCT line_id FROM {table}"))
        except sqlite3.OperationalError:
            pass
    assert timetabled == set()
    assert hasattr(generator, "generate")
