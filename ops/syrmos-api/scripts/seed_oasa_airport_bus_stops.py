"""Seed the Athens airport express bus lines (X93/X95/X96/X97) with their real,
ordered stops in BOTH directions, pulled from OASA Telematics.

Why this is a Pi-side script and not part of the app build: telematics.oasa.gr
geo-blocks non-Greek IPs, so only the box that already runs
scripts/oasa-airport-bus-watcher.py can reach it. This fills the gap the app has
today, where those express buses render as just "Syntagma -> Airport" because no
per-stop data was ever seeded.

Directions: the schedule projector and station-offsets keying only understand
`outbound` and `inbound` (see line_stations.direction across the seed). So each
line is seeded with:
  outbound = the to-airport route's ordered stops (city -> Airport)
  inbound  = the from-airport route's ordered stops (Airport -> city)
using the separate to/from route codes from oasa-airport-bus-watcher.py. Seeding
a single made-up direction (an earlier bug) is ignored by the projector and
loses the reverse route entirely.

OASA Telematics API (open, no auth):
  webGetStops:  GET https://telematics.oasa.gr/api/?act=webGetStops&p1={route_code}
    -> ordered list of {StopCode, StopID, StopDescr, StopDescrEng, StopLat, StopLng}

Stops are seeded as stations id "OASA_<StopCode>" (region athens) and linked to
the bus line via line_stations for both directions. Idempotent: re-running
refreshes names/coords and rebuilds line_stations for each line.

Run (on the Pi):
    cd ~/syrmos-api && .venv/bin/python -m scripts.seed_oasa_airport_bus_stops
Then regenerate the app snapshots (POST /api/regenerate or the admin button) so
/api/stations and the seed bundle carry the new stops.
"""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request

# NOTE: `syrmos_admin` is imported lazily inside main() so the pure planning
# helpers (station_union / line_station_rows) and --selftest run offline on any
# machine, with no DB package on the path.

API = "https://telematics.oasa.gr/api/"
USER_AGENT = "syrmos-oasa-watcher/1.0 (+https://syrmos.peterdsp.dev)"
REGION = "athens"
BUS_COLOR = "#E8792B"  # SyrmosTokens.warning, the express-bus orange used in-app.

# (line id, English name, Greek name, to-airport route code, from-airport route
# code). Codes are the to_airport / from_airport values in
# oasa-airport-bus-watcher.py (primary code per direction).
LINES = [
    ("X93", "X93 Kifisos B Station – Airport", "Χ93 ΚΤΕΛ Κηφισού – Αεροδρόμιο", 5675, 5676),
    ("X95", "X95 Syntagma – Airport", "Χ95 Σύνταγμα – Αεροδρόμιο", 2051, 2052),
    ("X96", "X96 Piraeus – Airport", "Χ96 Πειραιάς – Αεροδρόμιο", 5532, 5533),
    ("X97", "X97 Elliniko Metro – Airport", "Χ97 Ελληνικό – Αεροδρόμιο", 5373, 5374),
]


def fetch_json(url: str, timeout: int = 20):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read()
            if not raw or raw.strip() == b"null":
                return None
            return json.loads(raw)
    except (urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError) as e:
        print(f"  fetch failed: {url} -> {e}", file=sys.stderr)
        return None


def _first(d: dict, *keys, default=None):
    for k in keys:
        if k in d and d[k] not in (None, ""):
            return d[k]
    return default


def route_stops(route_code: int) -> list[dict]:
    """Ordered stops for one route code, normalized to a stable shape."""
    data = fetch_json(f"{API}?act=webGetStops&p1={route_code}")
    if not isinstance(data, list) or not data:
        return []
    stops = []
    for i, s in enumerate(data):
        code = _first(s, "StopCode", "StopID", "stop_code", "stopid")
        if code is None:
            continue
        try:
            lat = float(_first(s, "StopLat", "stop_lat", default=0) or 0)
            lng = float(_first(s, "StopLng", "stop_lng", default=0) or 0)
        except (TypeError, ValueError):
            lat, lng = 0.0, 0.0
        name_el = str(_first(s, "StopDescr", "stop_descr", default="")).strip()
        name_en = str(_first(s, "StopDescrEng", "stop_descr_eng", default=name_el)).strip()
        order = _first(s, "RouteStopOrder", "routeStopOrder", default=i + 1)
        stops.append({
            "id": f"OASA_{code}",
            "name_en": name_en or name_el or f"Stop {code}",
            "name_el": name_el or name_en or f"Στάση {code}",
            "lat": lat,
            "lng": lng,
            "seq": int(order),
        })
    stops.sort(key=lambda s: s["seq"])
    return stops


def station_union(outbound, inbound):
    """Unique station rows across both directions, keyed by id, order preserved."""
    seen = {}
    for s in outbound + inbound:
        if s["id"] not in seen:
            seen[s["id"]] = s
    return list(seen.values())


def line_station_rows(line_id, outbound, inbound):
    """(line_id, station_id, seq, direction) rows for BOTH canonical directions:
    outbound = city -> Airport, inbound = Airport -> city. seq is 1-based per
    direction. Pure, so it unit-tests offline without network or a DB."""
    rows = [(line_id, s["id"], n + 1, "outbound") for n, s in enumerate(outbound)]
    rows += [(line_id, s["id"], n + 1, "inbound") for n, s in enumerate(inbound)]
    return rows


def main() -> None:
    from syrmos_admin import db as dbmod

    conn = dbmod.connect()
    dbmod.migrate(conn)
    total_stops = 0
    seeded_lines = []
    for line_id, name_en, name_el, to_code, from_code in LINES:
        outbound = route_stops(to_code)   # city -> Airport
        inbound = route_stops(from_code)  # Airport -> city
        if len(outbound) < 2 or len(inbound) < 2:
            print(
                f"  {line_id}: outbound={len(outbound)} inbound={len(inbound)} stops from telematics, "
                "need >= 2 each, skipping",
                file=sys.stderr,
            )
            continue

        # Station rows are the union of both directions (same physical stops,
        # but a direction may serve one the other doesn't).
        stations_list = station_union(outbound, inbound)

        cur = conn.cursor()
        cur.execute("BEGIN")
        try:
            for s in stations_list:
                conn.execute(
                    "INSERT INTO stations(id, name_en, name_el, lat, lng, region)"
                    " VALUES(?,?,?,?,?,?)"
                    " ON CONFLICT(id) DO UPDATE SET name_en=excluded.name_en,"
                    " name_el=excluded.name_el, lat=excluded.lat, lng=excluded.lng,"
                    " region=excluded.region",
                    (s["id"], s["name_en"], s["name_el"], s["lat"], s["lng"], REGION),
                )
            conn.execute(
                "INSERT INTO lines(id, mode, name_en, name_el, color, terminal_a,"
                " terminal_b, sort_order, region, status) VALUES(?,?,?,?,?,?,?,?,?,?)"
                " ON CONFLICT(id) DO UPDATE SET mode=excluded.mode,"
                " name_en=excluded.name_en, name_el=excluded.name_el,"
                " color=excluded.color, terminal_a=excluded.terminal_a,"
                " terminal_b=excluded.terminal_b, sort_order=excluded.sort_order,"
                " region=excluded.region, status=excluded.status",
                (line_id, "bus", name_en, name_el, BUS_COLOR, outbound[0]["name_en"],
                 outbound[-1]["name_en"], 70 + len(seeded_lines), REGION, "operational"),
            )
            conn.execute("DELETE FROM line_stations WHERE line_id=?", (line_id,))
            rows = line_station_rows(line_id, outbound, inbound)
            conn.executemany(
                "INSERT INTO line_stations(line_id, station_id, seq, direction)"
                " VALUES(?,?,?,?)",
                rows,
            )
            cur.execute("COMMIT")
        except Exception:
            cur.execute("ROLLBACK")
            raise

        total_stops += len(stations_list)
        seeded_lines.append(f"{line_id}(out {len(outbound)}/in {len(inbound)})")
        print(f"  {line_id}: outbound {len(outbound)}, inbound {len(inbound)} stops")

    print("seeded airport bus lines:", ", ".join(seeded_lines) or "none")
    print("total unique stops:", total_stops)
    if not seeded_lines:
        sys.exit("no lines seeded - telematics unreachable or returned no stops")


def run_selftest() -> None:
    """Offline check of the pure planning logic (no network, no DB import)."""
    out = [
        {"id": "OASA_1", "name_en": "City"},
        {"id": "OASA_2", "name_en": "Mid"},
        {"id": "OASA_3", "name_en": "Airport"},
    ]
    inb = [
        {"id": "OASA_3", "name_en": "Airport"},
        {"id": "OASA_2", "name_en": "Mid"},
        {"id": "OASA_1", "name_en": "City"},
    ]

    rows = line_station_rows("X95", out, inb)
    dirs = {r[3] for r in rows}
    assert dirs == {"outbound", "inbound"}, f"expected both directions, got {dirs}"
    assert "to_airport" not in dirs, "must not use the non-canonical to_airport label"
    assert [r for r in rows if r[3] == "outbound"] == [
        ("X95", "OASA_1", 1, "outbound"),
        ("X95", "OASA_2", 2, "outbound"),
        ("X95", "OASA_3", 3, "outbound"),
    ], "outbound (city -> Airport) order/seq wrong"
    assert [r for r in rows if r[3] == "inbound"] == [
        ("X95", "OASA_3", 1, "inbound"),
        ("X95", "OASA_2", 2, "inbound"),
        ("X95", "OASA_1", 3, "inbound"),
    ], "inbound (Airport -> city) order/seq wrong"

    union = station_union(out, inb)
    assert [s["id"] for s in union] == ["OASA_1", "OASA_2", "OASA_3"], (
        "station union must dedupe and preserve first-seen order"
    )
    assert line_station_rows("X", [], []) == [], "empty directions yield no rows"

    print("seed_oasa_airport_bus_stops self-test: OK")


if __name__ == "__main__":
    if "--selftest" in sys.argv:
        run_selftest()
    else:
        main()
