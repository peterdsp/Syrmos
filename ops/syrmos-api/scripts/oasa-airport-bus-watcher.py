"""Poll OASA Telematics for airport express bus positions and stop ETAs.

Writes /home/peterdsp/syrmos-api/out/oasa-airport-buses.json every 30 seconds.
The admin API serves this file at /api/oasa-airport-buses.

OASA Telematics API (open, no auth):
  getBusLocation:  GET https://telematics.oasa.gr/api/?act=getBusLocation&p1={route_code}
  getStopArrivals: GET https://telematics.oasa.gr/api/?act=getStopArrivals&p1={stop_code}

Run (on the Pi):
    python3 scripts/oasa-airport-bus-watcher.py

Systemd unit: oasa-airport-bus-watcher.service
"""
from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(os.environ.get("PIPELINE_ROOT", str(Path(__file__).resolve().parent.parent)))
OUT_FILE = ROOT / "out" / "oasa-airport-buses.json"
JOURNAL = ROOT / "out" / ".oasa-watcher-journal.jsonl"
USER_AGENT = "syrmos-oasa-watcher/1.0 (+https://syrmos.peterdsp.dev)"
API = "https://telematics.oasa.gr/api/"
POLL_SECONDS = 30

AIRPORT_ROUTES = {
    "X93": {
        "to_airport": [5675],
        "from_airport": [5676],
    },
    "X95": {
        "to_airport": [2051],
        "from_airport": [2052],
    },
    "X96": {
        "to_airport": [5532, 3196],
        "from_airport": [5533, 3008],
    },
    "X97": {
        "to_airport": [5373, 5375],
        "from_airport": [5374, 5376],
    },
}

AIRPORT_STOP = 10705


def fetch_json(url: str, timeout: int = 15):
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


def poll_positions():
    all_codes = []
    for line_id, dirs in AIRPORT_ROUTES.items():
        for codes in dirs.values():
            all_codes.extend(codes)
    vehicles = []
    for rc in all_codes:
        data = fetch_json(f"{API}?act=getBusLocation&p1={rc}")
        if not data or not isinstance(data, list):
            continue
        for v in data:
            try:
                vehicles.append({
                    "vehicleId": str(v.get("VEH_NO", "")),
                    "lat": float(v["CS_LAT"]),
                    "lng": float(v["CS_LNG"]),
                    "heading": float(v.get("VEH_HEADING", 0)),
                    "routeCode": int(v.get("ROUTE_CODE", rc)),
                    "lineId": route_to_line(int(v.get("ROUTE_CODE", rc))),
                    "timestamp": v.get("CS_DATE", ""),
                })
            except (KeyError, ValueError, TypeError):
                continue
    return vehicles


def poll_airport_arrivals():
    data = fetch_json(f"{API}?act=getStopArrivals&p1={AIRPORT_STOP}")
    if not data or not isinstance(data, list):
        return []
    arrivals = []
    for a in data:
        try:
            rc = int(a.get("route_code", 0))
            line_id = route_to_line(rc)
            if not line_id:
                continue
            arrivals.append({
                "lineId": line_id,
                "routeCode": rc,
                "vehicleId": str(a.get("veh_code", "")),
                "minutesAway": int(a.get("btime2", 0)),
            })
        except (ValueError, TypeError):
            continue
    arrivals.sort(key=lambda x: x["minutesAway"])
    return arrivals


def route_to_line(rc: int) -> str | None:
    for line_id, dirs in AIRPORT_ROUTES.items():
        for codes in dirs.values():
            if rc in codes:
                return line_id
    return None


def run_once():
    vehicles = poll_positions()
    arrivals = poll_airport_arrivals()
    now = datetime.now(timezone.utc).isoformat()
    payload = {
        "updatedAt": now,
        "vehicles": vehicles,
        "airportArrivals": arrivals,
        "routes": {
            line_id: {
                "toAirport": dirs["to_airport"],
                "fromAirport": dirs["from_airport"],
            }
            for line_id, dirs in AIRPORT_ROUTES.items()
        },
    }
    tmp = OUT_FILE.with_suffix(".tmp")
    tmp.write_text(json.dumps(payload, separators=(",", ":")))
    tmp.replace(OUT_FILE)
    journal_line = json.dumps({
        "ts": now,
        "vehicles": len(vehicles),
        "arrivals": len(arrivals),
    })
    with open(JOURNAL, "a") as f:
        f.write(journal_line + "\n")
    print(f"[{now}] {len(vehicles)} vehicles, {len(arrivals)} airport arrivals")


def main():
    print(f"OASA airport bus watcher starting (poll every {POLL_SECONDS}s)")
    print(f"Output: {OUT_FILE}")
    while True:
        try:
            run_once()
        except Exception as e:
            print(f"Error: {e}", file=sys.stderr)
        time.sleep(POLL_SECONDS)


if __name__ == "__main__":
    main()
