#!/usr/bin/env python3
"""Snapshot rail/tram route geometry from OpenStreetMap and write shapes.json.

Why:
- catmullRomSpline'd station coords misdraw Piraeus loop, M3 airport branch,
  A4 Megara curve and similar non-straight rail segments.
- OSM holds real track relations under ODbL with attribution required.
- This script pulls one canonical direction per line, stitches the ways in
  member order, and writes a unified shapes.json shipped offline-first in
  iOS, Android and Web bundles.

Usage:
    python3 scripts/snapshot-osm-shapes.py

Writes:
    iosApp/iosApp/Resources/seed-schedules-v2/shapes.json
    core/data/src/commonMain/composeResources/files/seed/schedules-v2/shapes.json
    androidApp/src/androidMain/assets/files/seed/schedules-v2/shapes.json
    composeApp/src/wasmJsMain/resources/shapes.json

Attribution: include "Data © OpenStreetMap contributors, ODbL" somewhere
visible in the app (already on the Leaflet web map tile credits).
"""
from __future__ import annotations
import json
import os
import sys
import urllib.request

OVERPASS_URL = "https://overpass-api.de/api/interpreter"

# Canonical direction per line (terminal_a -> terminal_b in our app data).
RELATION_FOR_LINE = {
    "M1": 445858,    # Piraeus -> Kifissia
    "M2": 7963539,   # Anthoupoli -> Elliniko
    "M3": 445945,    # Dimotiko Theatro -> Airport (full incl. airport branch)
    "T6": 3648688,   # Syntagma -> Pikrodafni
    "T7": 6792078,   # Akti Posidonos -> Asklipiio Voulas
    "A1": 8467445,   # Piraeus -> Airport
    "A2": 8467443,   # Ano Liosia -> Airport
    "A3": 8467442,   # Athens -> Chalcis
    "A4": 8467515,   # Piraeus -> Kiato
}


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
    req = urllib.request.Request(OVERPASS_URL, data=body)
    with urllib.request.urlopen(req, timeout=180) as r:
        return json.loads(r.read())


def stitch_ways(rel, ways):
    """Concatenate the relation's track ways into one polyline.

    Track ways have role = "" (empty). Platform / platform_entry_only ways
    describe stops, not the rail itself, and are skipped.
    Adjacent ways may be oriented either direction; we reverse a way when
    its start does not connect to the running tail.
    """
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
            # Disconnected segment, accept the small jump rather than dropping
            # the rest of the line.
            line.extend(geom)
    return line


def main():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    print(f"Fetching {len(RELATION_FOR_LINE)} relations from {OVERPASS_URL}...")
    data = fetch_geometry()
    rels = {e["id"]: e for e in data["elements"] if e["type"] == "relation"}
    ways = {e["id"]: e for e in data["elements"] if e["type"] == "way"}

    shapes = {}
    for line_id, rel_id in RELATION_FOR_LINE.items():
        r = rels.get(rel_id)
        if not r:
            print(f"  {line_id}: rel {rel_id} missing in response", file=sys.stderr)
            continue
        line = stitch_ways(r, ways)
        if len(line) < 2:
            print(f"  {line_id}: empty stitch", file=sys.stderr)
            continue
        shapes[line_id] = {
            "osmRelationId": rel_id,
            "from": r["tags"].get("from", ""),
            "to": r["tags"].get("to", ""),
            "points": len(line),
            "coordinates": [[round(p["lat"], 6), round(p["lon"], 6)] for p in line],
        }
        print(f"  {line_id} rel {rel_id}: {len(line)} pts")

    payload = {
        "version": 1,
        "source": "OpenStreetMap (ODbL)",
        "shapes": shapes,
    }
    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    out_paths = [
        os.path.join(repo_root, "iosApp/iosApp/Resources/seed-schedules-v2/shapes.json"),
        os.path.join(repo_root, "core/data/src/commonMain/composeResources/files/seed/schedules-v2/shapes.json"),
        os.path.join(repo_root, "androidApp/src/androidMain/assets/files/seed/schedules-v2/shapes.json"),
        os.path.join(repo_root, "composeApp/src/wasmJsMain/resources/shapes.json"),
        os.path.join(repo_root, "assets/line-geometry/shapes.json"),
    ]
    for p in out_paths:
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w", encoding="utf-8") as f:
            f.write(body)
        print(f"wrote {p}")

    # Per-line GeoJSON Features for the Pi's /line-geometry/{id}.geojson
    # endpoint. Lng/lat order per the GeoJSON spec (RFC 7946) — internal
    # shapes.json uses [lat, lng] to match Leaflet's L.polyline argument.
    geojson_dir = os.path.join(repo_root, "assets/line-geometry")
    os.makedirs(geojson_dir, exist_ok=True)
    for line_id, shape in shapes.items():
        feature = {
            "type": "Feature",
            "properties": {
                "lineId": line_id,
                "from": shape["from"],
                "to": shape["to"],
                "osmRelationId": shape["osmRelationId"],
                "attribution": "© OpenStreetMap contributors, ODbL",
            },
            "geometry": {
                "type": "LineString",
                "coordinates": [[c[1], c[0]] for c in shape["coordinates"]],
            },
        }
        path = os.path.join(geojson_dir, f"{line_id}.geojson")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(feature, f, ensure_ascii=False, separators=(",", ":"))
        print(f"wrote {path}")
    print(f"total bytes (shapes.json): {len(body):,}")


if __name__ == "__main__":
    main()
