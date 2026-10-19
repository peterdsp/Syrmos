"""Build accurate road-following geometry for the Athens OASA airport buses.

Why this replaces the OSM-relation approach: stitching OSM `route=bus` relations
produced messy lines (ways doubling back, routes missing entirely, e.g. X95
airport->Syntagma had only stop nodes). Instead, take the operator's own ordered
stops (from the live /api/airport-buses snapshot, which the watcher already
serves) and snap them to the real road network with OSRM. That gives one clean,
dense, road-following polyline per direction, at metro-quality spacing.

Multi-variant routes (X96, X97) interleave two route codes under one direction;
grouping by `route_code` and routing the primary (most-stops) variant avoids the
zigzag that merging them caused.

Output: ops/syrmos-api/data/oasa-airport-shapes.json
    { "routes": { "X95": { "to_airport": [[lat,lng],...], "from_airport": [...] } } }
The airport-bus watcher reads this and publishes it as the OASA route shapes.

Run when the routes change:
    python3 scripts/fetch-airport-bus-geometry.py
"""
from __future__ import annotations

import json
import math
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DST = ROOT / "ops" / "syrmos-api" / "data" / "oasa-airport-shapes.json"

API_URL = "https://api-syrmos.peterdsp.dev/api/airport-buses"
OSRM_URL = "https://router.project-osrm.org/route/v1/driving"
# ~5 m at Athens latitude: keeps the road shape, trims OSRM's ~30 m point spam.
SIMPLIFY_EPSILON = 0.00005
DIRECTIONS = ("to_airport", "from_airport")


def curl_json(url: str) -> dict | None:
    """GET JSON via curl (the macOS system Python's old TLS fails on these hosts)."""
    for attempt in range(4):
        try:
            out = subprocess.run(
                ["curl", "-s", "--max-time", "45", "-A", "syrmos-airport-geometry/1.0", url],
                capture_output=True, timeout=55,
            )
            if out.returncode == 0 and out.stdout:
                return json.loads(out.stdout)
            raise ValueError(f"curl rc={out.returncode}")
        except (subprocess.SubprocessError, ValueError) as e:
            wait = 4 * (attempt + 1)
            print(f"  fetch failed ({e}); retrying in {wait}s", file=sys.stderr)
            time.sleep(wait)
    return None


def osrm_route(stops: list[dict]) -> list[list[float]]:
    """Road-following [lat, lng] polyline through the ordered stops, or []."""
    coords = ";".join(f"{s['longitude']},{s['latitude']}" for s in stops)
    data = curl_json(f"{OSRM_URL}/{coords}?overview=full&geometries=geojson")
    if not data or data.get("code") != "Ok" or not data.get("routes"):
        return []
    # OSRM returns [lng, lat]; store [lat, lng] to match the watcher's contract.
    return [[round(lat, 6), round(lng, 6)] for lng, lat in data["routes"][0]["geometry"]["coordinates"]]


def _perp(p, a, b) -> float:
    (px, py), (ax, ay), (bx, by) = p, a, b
    dx, dy = bx - ax, by - ay
    if dx == 0 and dy == 0:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def rdp(points: list[list[float]], eps: float) -> list[list[float]]:
    if len(points) < 3:
        return list(points)
    dmax, idx, end = 0.0, 0, len(points) - 1
    for i in range(1, end):
        d = _perp(points[i], points[0], points[end])
        if d > dmax:
            dmax, idx = d, i
    if dmax > eps:
        return rdp(points[: idx + 1], eps)[:-1] + rdp(points[idx:], eps)
    return [points[0], points[end]]


def direction_geometry(route: dict, direction: str) -> list[list[float]]:
    stops = [s for s in (route.get("stops") or []) if s.get("direction") == direction]
    if len(stops) < 2:
        return []
    # Split interleaved variants by route_code; route the one with the most stops.
    by_code: dict[object, list[dict]] = {}
    for s in stops:
        by_code.setdefault(s.get("route_code"), []).append(s)
    primary = max(by_code.values(), key=len)
    primary.sort(key=lambda s: s.get("sequence", 0))
    geo = osrm_route(primary)
    if len(geo) < 2:
        return []
    return rdp(geo, SIMPLIFY_EPSILON)


def main() -> None:
    snapshot = curl_json(API_URL)
    if not snapshot:
        print("ERROR: could not fetch the airport-bus snapshot", file=sys.stderr)
        sys.exit(1)
    oasa_routes = snapshot.get("operators", {}).get("oasa", {}).get("routes", [])
    if not oasa_routes:
        print("ERROR: no OASA routes in the snapshot", file=sys.stderr)
        sys.exit(1)

    routes: dict[str, dict[str, list[list[float]]]] = {}
    for route in oasa_routes:
        rid = route.get("route_id")
        if not rid:
            continue
        print(f"--- {rid}")
        shapes: dict[str, list[list[float]]] = {}
        for direction in DIRECTIONS:
            geo = direction_geometry(route, direction)
            if geo:
                shapes[direction] = geo
                print(f"  {direction}: {len(geo)} points")
            else:
                print(f"  {direction}: NO GEOMETRY", file=sys.stderr)
            time.sleep(1)  # be polite to the public OSRM
        if shapes:
            routes[rid] = shapes

    if not routes:
        print("ERROR: produced no geometry; leaving the existing file untouched", file=sys.stderr)
        sys.exit(1)

    out = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source": "OASA operator stops, snapped to roads via OSRM",
        "attribution": "Routing © OSRM / OpenStreetMap contributors; stops © OASA",
        "routes": routes,
    }
    DST.parent.mkdir(parents=True, exist_ok=True)
    DST.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")))
    total = sum(len(pts) for r in routes.values() for pts in r.values())
    print(f"wrote {DST.relative_to(ROOT)} ({len(routes)} routes, {total} points)")


if __name__ == "__main__":
    main()
