"""Pull each transit line's actual track geometry from OpenStreetMap.

Why: drawing a polyline through station coords with Catmull-Rom smoothing
cuts through buildings and crosses Piraeus diagonally. The right answer
is the operator's actual route relation in OSM — those `way` members trace
the real tracks.

Output: assets/athens-transit-package/line-geometry/{lineId}.geojson
Each file is a LineString (or MultiLineString for branches like the T7 loop)
ready to consume on web, iOS, and Android.

Run when stations or routes change:
    python3 scripts/fetch-osm-line-geometry.py
"""
from __future__ import annotations

import json
import math
import sys
import time
from pathlib import Path
from urllib.error import URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent.parent
DST = ROOT / "assets" / "athens-transit-package" / "line-geometry"
DST.mkdir(parents=True, exist_ok=True)

OVERPASS_URL = "https://overpass-api.de/api/interpreter"

# OSM relation IDs come from athens_fixed_rail_station_coordinates.md.
# When a line has a route_master with multiple sub-routes (one per direction),
# both relation IDs are listed and merged.
LINES = {
    "M1":     [445858],
    "M2":     [7963539],
    "M3":     [445945],
    "T6":     [3648688],
    "T7":     [6792078],
    "A1":     [8467445],
    "A2":     [8467443],
    "A3":     [8467442],
    "A4":     [8467515],
}


def query_overpass(relation_id: int) -> dict | None:
    """Fetch the full geometry of one relation as Overpass JSON output."""
    q = f"""
[out:json][timeout:60];
relation({relation_id});
(._;>>;);
out geom;
""".strip()
    data = urlencode({"data": q}).encode("utf-8")
    req = Request(OVERPASS_URL, data=data, headers={"User-Agent": "syrmos-osm-fetcher/1.0"})
    for attempt in range(3):
        try:
            with urlopen(req, timeout=90) as resp:
                return json.loads(resp.read())
        except URLError as e:
            print(f"  attempt {attempt + 1} failed: {e}", file=sys.stderr)
            time.sleep(5)
    return None


def extract_line_strings(payload: dict) -> list[list[tuple[float, float]]]:
    """Walk the Overpass response, return each `way` as a list of (lng, lat)
    points. Order preserved as OSM returned them."""
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

    # Use the route relation member order to chain ways into a contiguous track.
    line_strings: list[list[tuple[float, float]]] = []
    for rel in relations:
        coords: list[tuple[float, float]] = []
        last_point: tuple[float, float] | None = None
        # Exclude "backward" role: those are return-track ways on bidirectional
        # corridors that, when chained, cause the polyline to double back
        # through the same area. Apple Maps shows a single centerline.
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
    """Perpendicular distance from p to segment a-b (in lng/lat degrees; we
    treat them as planar for simplification purposes — accurate enough for
    city-scale tram tracks)."""
    ax, ay = a
    bx, by = b
    px, py = p
    dx = bx - ax
    dy = by - ay
    if dx == 0 and dy == 0:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    cx = ax + t * dx
    cy = ay + t * dy
    return math.hypot(px - cx, py - cy)


def ramer_douglas_peucker(points: list[tuple[float, float]], epsilon: float) -> list[tuple[float, float]]:
    """RDP simplification. Drops intermediate points that lie within
    `epsilon` (degrees) of the straight line between their neighbours."""
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


# Per-line tolerance. Trams have tight curves that need a finer epsilon than
# a long suburban rail line. ~7m in latitude at Athens (1deg lat = ~111km).
SIMPLIFY_EPSILON = {
    "M1": 0.00003,
    "M2": 0.00003,
    "M3": 0.00003,
    "T6": 0.00004,
    "T7": 0.00004,
    "A1": 0.00006,
    "A2": 0.00006,
    "A3": 0.00006,
    "A4": 0.00006,
}


def to_geojson(line_strings: list[list[tuple[float, float]]], line_id: str) -> dict:
    eps = SIMPLIFY_EPSILON.get(line_id, 0.00004)
    simplified = [ramer_douglas_peucker(s, eps) for s in line_strings]
    simplified = [s for s in simplified if len(s) >= 2]
    if len(simplified) == 1:
        geom = {"type": "LineString", "coordinates": simplified[0]}
    else:
        geom = {"type": "MultiLineString", "coordinates": simplified}
    return {
        "type": "Feature",
        "properties": {"lineId": line_id, "source": "OpenStreetMap"},
        "geometry": geom,
    }


def main() -> None:
    for line_id, relation_ids in LINES.items():
        print(f"--- {line_id} (relations: {relation_ids})")
        merged_strings: list[list[tuple[float, float]]] = []
        for rid in relation_ids:
            payload = query_overpass(rid)
            if not payload:
                print(f"  WARN: no response for relation {rid}", file=sys.stderr)
                continue
            merged_strings.extend(extract_line_strings(payload))
            time.sleep(1)  # be polite to Overpass
        if not merged_strings:
            print(f"  ERROR: no geometry for {line_id}", file=sys.stderr)
            continue
        feature = to_geojson(merged_strings, line_id)
        out = DST / f"{line_id}.geojson"
        out.write_text(json.dumps(feature, separators=(",", ":")))
        total_points = sum(len(s) for s in merged_strings)
        print(f"  wrote {out.relative_to(ROOT)} ({len(merged_strings)} segments, {total_points} points)")


if __name__ == "__main__":
    main()
