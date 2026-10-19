"""Pull the Athens OASA airport express buses' real road geometry from OpenStreetMap.

Why: OASA's live telematics API exposes only ordered stop coordinates, so the
Athens airport buses (X93/X95/X96/X97) were drawn as angular stop-to-stop lines.
Each route has an OSM route relation per direction whose `way` members trace the
actual roads, exactly like scripts/fetch-osm-line-geometry.py does for rail/tram.

Output: ops/syrmos-api/data/oasa-airport-shapes.json
    { "generated_at": ..., "source": "OpenStreetMap",
      "routes": { "X95": { "to_airport": [[lat,lng],...],
                           "from_airport": [[lat,lng],...] }, ... } }
The airport-bus watcher attaches these to the OASA routes as direction-tagged
shape points so the clients draw one road-following line per direction.

Run when the routes change:
    python3 scripts/fetch-osm-airport-bus-geometry.py
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

OVERPASS_URL = "https://overpass-api.de/api/interpreter"

# OSM route relation IDs, one per travel direction. Looked up by ref/name
# (X93/X95/X96/X97, airport <-> city), route_master sub-routes where present.
AIRPORT_BUS_RELATIONS = {
    "X93": {"to_airport": 3648693, "from_airport": 3648692},
    "X95": {"to_airport": 3648695, "from_airport": 3648694},
    "X96": {"to_airport": 3648697, "from_airport": 4112236},
    "X97": {"to_airport": 3648699, "from_airport": 3648698},
}

# ~6-7 m at Athens latitude; matches the bus epsilon in fetch-osm-line-geometry.
SIMPLIFY_EPSILON = 0.00006


def query_overpass(relation_id: int) -> dict | None:
    """Fetch the full geometry of one relation as Overpass JSON output.

    Uses curl as the transport: the macOS system Python ships an old LibreSSL
    that Overpass's TLS now rejects, while curl negotiates fine.
    """
    q = f"""
[out:json][timeout:60];
relation({relation_id});
(._;>>;);
out geom;
""".strip()
    for attempt in range(4):
        try:
            result = subprocess.run(
                ["curl", "-s", "--max-time", "90", "-A", "syrmos-osm-fetcher/1.0",
                 "--data-urlencode", f"data={q}", OVERPASS_URL],
                capture_output=True, timeout=100,
            )
            if result.returncode == 0 and result.stdout:
                return json.loads(result.stdout)
            raise ValueError(f"curl rc={result.returncode}")
        except (subprocess.SubprocessError, ValueError) as e:
            wait = 5 * (attempt + 1)
            print(f"  attempt {attempt + 1} failed ({e}); retrying in {wait}s", file=sys.stderr)
            time.sleep(wait)
    return None


def extract_line_strings(payload: dict) -> list[list[tuple[float, float]]]:
    """Chain the relation's forward `way` members into contiguous (lng, lat)
    tracks. Mirrors scripts/fetch-osm-line-geometry.py: skip the "backward"
    role so a one-direction relation yields a single centerline."""
    ways: dict[int, list[tuple[float, float]]] = {}
    relations: list[dict] = []
    for element in payload.get("elements", []):
        if element.get("type") == "way":
            geom = element.get("geometry") or []
            ways[element["id"]] = [(p["lon"], p["lat"]) for p in geom if "lon" in p and "lat" in p]
        elif element.get("type") == "relation":
            relations.append(element)

    if not relations:
        return [w for w in ways.values() if w]

    line_strings: list[list[tuple[float, float]]] = []
    for rel in relations:
        coords: list[tuple[float, float]] = []
        last_point: tuple[float, float] | None = None
        for member in rel.get("members", []):
            if member.get("type") != "way" or member.get("role") not in ("", "forward", None):
                continue
            w = ways.get(member.get("ref"))
            if not w:
                continue
            points = w if last_point is None or w[0] == last_point else w if w[-1] != last_point else list(reversed(w))
            if last_point is not None and points[0] == last_point:
                coords.extend(points[1:])
            else:
                if coords and len(coords) > 1:
                    line_strings.append(coords)
                coords = list(points)
            if coords:
                last_point = coords[-1]
        if len(coords) > 1:
            line_strings.append(coords)
    return line_strings or [w for w in ways.values() if w]


def _perp_distance(p: tuple[float, float], a: tuple[float, float], b: tuple[float, float]) -> float:
    ax, ay = a
    bx, by = b
    px, py = p
    dx = bx - ax
    dy = by - ay
    if dx == 0 and dy == 0:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def ramer_douglas_peucker(points: list[tuple[float, float]], epsilon: float) -> list[tuple[float, float]]:
    if len(points) < 3:
        return list(points)
    dmax = 0.0
    index = 0
    end = len(points) - 1
    for i in range(1, end):
        d = _perp_distance(points[i], points[0], points[end])
        if d > dmax:
            dmax = d
            index = i
    if dmax > epsilon:
        left = ramer_douglas_peucker(points[: index + 1], epsilon)
        right = ramer_douglas_peucker(points[index:], epsilon)
        return left[:-1] + right
    return [points[0], points[end]]


def direction_latlng(relation_id: int) -> list[list[float]]:
    """One direction's road geometry as an ordered [lat, lng] list."""
    payload = query_overpass(relation_id)
    if not payload:
        print(f"  WARN: no response for relation {relation_id}", file=sys.stderr)
        return []
    segments = extract_line_strings(payload)
    # Concatenate the stitched segments in member order, then simplify once so
    # the published shape is a single continuous polyline for this direction.
    merged: list[tuple[float, float]] = []
    for seg in segments:
        if merged and seg and seg[0] == merged[-1]:
            merged.extend(seg[1:])
        else:
            merged.extend(seg)
    if len(merged) < 2:
        return []
    simplified = ramer_douglas_peucker(merged, SIMPLIFY_EPSILON)
    # Store as [lat, lng] (Overpass gives lng, lat).
    return [[round(lat, 6), round(lng, 6)] for (lng, lat) in simplified]


def main() -> None:
    routes: dict[str, dict[str, list[list[float]]]] = {}
    for route_id, directions in AIRPORT_BUS_RELATIONS.items():
        print(f"--- {route_id}")
        route_shapes: dict[str, list[list[float]]] = {}
        for direction, relation_id in directions.items():
            coords = direction_latlng(relation_id)
            if coords:
                route_shapes[direction] = coords
                print(f"  {direction}: relation {relation_id} -> {len(coords)} points")
            else:
                print(f"  {direction}: relation {relation_id} -> NO GEOMETRY", file=sys.stderr)
            time.sleep(1)  # be polite to Overpass
        if route_shapes:
            routes[route_id] = route_shapes

    if not routes:
        print("ERROR: no geometry fetched; leaving existing file untouched", file=sys.stderr)
        sys.exit(1)

    out = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source": "OpenStreetMap",
        "attribution": "© OpenStreetMap contributors",
        "routes": routes,
    }
    DST.parent.mkdir(parents=True, exist_ok=True)
    DST.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")))
    total = sum(len(pts) for r in routes.values() for pts in r.values())
    print(f"wrote {DST.relative_to(ROOT)} ({len(routes)} routes, {total} points)")


if __name__ == "__main__":
    main()
