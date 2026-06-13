"""Pull OSM Overpass route geometry and write per-line GeoJSON for the Pi.

Runs on the Pi via syrmos-osm-shapes.timer (weekly). Updates
~/syrmos-api/assets/line-geometry/{lineId}.geojson which the existing
nginx /line-geometry/ location serves to the apps.

This is the in-pipeline counterpart to scripts/snapshot-osm-shapes.py
(which runs from the laptop and writes to the bundled seed directories).
Both share the same Overpass query and stitching logic so the data the
Pi exposes matches what's bundled in app releases.

Idempotent: rewrites all .geojson files even if unchanged, so the Pi
serves a consistent atomically-built set. The /api/fares manifest etag
picks up the change on the next regenerator run.

Run: cd ~/syrmos-api && .venv/bin/python scripts/refresh_osm_shapes.py
"""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request

OVERPASS_URL = "https://overpass-api.de/api/interpreter"

# Canonical direction per line (terminal_a -> terminal_b in our app data).
RELATION_FOR_LINE = {
    "M1": 445858,
    "M2": 7963539,
    "M3": 445945,
    "T6": 3648688,
    "T7": 6792078,
    "A1": 8467445,
    "A2": 8467443,
    "A3": 8467442,
    "A4": 8467515,
}

OUT_DIR = os.environ.get(
    "SYRMOS_LINE_GEOMETRY_DIR",
    "/home/peterdsp/syrmos-api/assets/line-geometry",
)


def overpass_query() -> str:
    rel_lines = "\n  ".join(f"rel({rid});" for rid in RELATION_FOR_LINE.values())
    return f"""[out:json][timeout:180];
(
  {rel_lines}
)->.routes;
.routes out;
way(r.routes);
out geom;
"""


def fetch_geometry() -> dict:
    body = overpass_query().encode()
    req = urllib.request.Request(OVERPASS_URL, data=body, headers={
        "User-Agent": "syrmos-osm-shapes/1.0 (+https://syrmos.peterdsp.dev)"
    })
    with urllib.request.urlopen(req, timeout=180) as r:
        return json.loads(r.read())


def stitch_ways(rel, ways):
    """Concatenate the relation's track ways into one polyline. Track ways
    have role = "" (empty); platform / platform_entry_only ways describe
    stops, not the rail itself, and are skipped. Adjacent ways may be
    oriented either direction; we reverse a way when its start does not
    connect to the running tail."""
    track_ms = [m for m in rel["members"] if m["type"] == "way" and m.get("role", "") == ""]
    if not track_ms:
        return []
    line = []
    EPS = 1e-7

    def same(a, b):
        return abs(a["lat"] - b["lat"]) < EPS and abs(a["lon"] - b["lon"]) < EPS

    for m in track_ms:
        w = ways.get(m["ref"])
        if not w:
            continue
        geom = w.get("geometry", [])
        if not geom:
            continue
        if not line:
            line = list(geom)
            continue
        tail = line[-1]
        if same(tail, geom[0]):
            line.extend(geom[1:])
        elif same(tail, geom[-1]):
            line.extend(reversed(geom[:-1]))
        elif same(line[0], geom[-1]):
            line = list(geom)[:-1] + line
        elif same(line[0], geom[0]):
            line = list(reversed(geom))[:-1] + line
        else:
            line.extend(geom)
    return line


def main() -> int:
    print(f"OSM shapes refresh -> {OUT_DIR}")
    try:
        data = fetch_geometry()
    except (urllib.error.URLError, TimeoutError) as exc:
        print(f"overpass fetch failed: {exc}", file=sys.stderr)
        return 1

    rels = {e["id"]: e for e in data["elements"] if e["type"] == "relation"}
    ways = {e["id"]: e for e in data["elements"] if e["type"] == "way"}

    os.makedirs(OUT_DIR, exist_ok=True)
    written = 0
    for line_id, rel_id in RELATION_FOR_LINE.items():
        r = rels.get(rel_id)
        if not r:
            print(f"  {line_id}: rel {rel_id} missing in response", file=sys.stderr)
            continue
        line = stitch_ways(r, ways)
        if len(line) < 2:
            print(f"  {line_id}: empty stitch", file=sys.stderr)
            continue
        feature = {
            "type": "Feature",
            "properties": {
                "lineId": line_id,
                "from": r["tags"].get("from", ""),
                "to": r["tags"].get("to", ""),
                "osmRelationId": rel_id,
                "attribution": "© OpenStreetMap contributors, ODbL",
            },
            "geometry": {
                "type": "LineString",
                "coordinates": [[round(p["lon"], 6), round(p["lat"], 6)] for p in line],
            },
        }
        path = os.path.join(OUT_DIR, f"{line_id}.geojson")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(feature, f, ensure_ascii=False, separators=(",", ":"))
        written += 1
        print(f"  {line_id}: {len(line)} pts -> {path}")
    print(f"wrote {written} GeoJSON features")
    return 0


if __name__ == "__main__":
    sys.exit(main())
