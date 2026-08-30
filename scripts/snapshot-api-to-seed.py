"""Snapshot the live api-syrmos.peterdsp.dev/api/schedules into bundled seeds.

Why: offline-first promise. The app must show CORRECT departures with zero
network on first launch. Today the embedded seed pre-dates the API and has
stale/wrong logic. This script bakes today's API answer into:

- core/data/src/commonMain/composeResources/files/seed/schedules-v2/
- androidApp/src/androidMain/assets/files/seed/schedules-v2/  (copy)
- iosApp/iosApp/Resources/seed-schedules-v2/  (copy)

The runtime loads schedules-v2 as the initial cache for ScheduleSyncRepository,
so cold start has correct data even without a network refresh. The next online
refresh just overlays newer data.

Usage:
    python3 scripts/snapshot-api-to-seed.py
"""
from __future__ import annotations

import json
import shutil
import sys
import urllib.error
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from seed_overnight import ensure_overnight_continuity  # noqa: E402

BASE = "https://api-syrmos.peterdsp.dev"
ROOT = Path(__file__).resolve().parent.parent

DEST_KMP = ROOT / "core/data/src/commonMain/composeResources/files/seed/schedules-v2"
DEST_ANDROID = ROOT / "androidApp/src/androidMain/assets/files/seed/schedules-v2"
DEST_IOS = ROOT / "iosApp/iosApp/Resources/seed-schedules-v2"

USER_AGENT = "syrmos-snapshot/1.0 (+https://syrmos.peterdsp.dev)"


def fetch(path: str) -> dict:
    req = urllib.request.Request(BASE + path, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read())


def fetch_geojson_optional(path: str) -> dict | None:
    """Single-shot fetch with no exceptions on 404. Used for shapes which
    may not exist for every line, so the snapshot tolerates partial sets."""
    try:
        return fetch(path)
    except (urllib.error.URLError, urllib.error.HTTPError):
        return None


def write(p: Path, payload: dict) -> int:
    p.parent.mkdir(parents=True, exist_ok=True)
    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    p.write_text(body, encoding="utf-8")
    return len(body)


def main() -> None:
    print(f"snapshot source: {BASE}")

    manifest = fetch("/api/schedules/manifest")
    holidays = fetch("/api/holidays")
    lines = fetch("/api/lines")
    icons = fetch("/api/icons")
    line_display = fetch("/api/line-display")
    fares = fetch("/api/fares")
    # STASY service-status badge + announcement feed. Required for the
    # offline-first promise: on a cold install with no network the home
    # screen still needs to render today's "Trains until 21:40" pill and
    # any active alerts. The Pi scrapes this every 5 minutes; we snapshot
    # whatever's current so the bundle never starts empty.
    announcements: dict | None = None
    try:
        announcements = fetch("/api/announcements")
    except Exception as e:  # noqa: BLE001
        print(f"  WARN: /api/announcements fetch failed: {e}")
    # Per-station minutes-from-origin scraped from STASY HTML. Apps need
    # this on cold start so M1/M2/M3/T6/T7 station detail screens render
    # the right HH:MM for every stop, not just the line origin. Optional:
    # if the endpoint is missing (older Pi build), we skip silently.
    station_offsets: dict | None = None
    try:
        station_offsets = fetch("/api/station-offsets")
    except Exception as e:  # noqa: BLE001
        print(f"  WARN: /api/station-offsets fetch failed: {e}")

    line_ids = list(manifest.get("perLineHashes", {}).keys())
    if not line_ids:
        print("ERROR: manifest has no lines", file=sys.stderr)
        sys.exit(1)

    bundles: dict[str, dict] = {}
    for lid in line_ids:
        bundles[lid] = fetch(f"/api/schedules/{lid}")
        # Guarantee the 24h Saturday overnight band is continuous before baking it
        # into the offline seed. If the live API is momentarily stale (a partial
        # 24mmm scrape dropped the 02:00->05:30 continuation), this keeps the
        # bundled fallback from shipping a dark overnight map. See seed_overnight.
        if ensure_overnight_continuity(bundles[lid]):
            print(f"  NOTE: healed {lid} Saturday overnight band before bundling")
        print(f"  fetched {lid}: {len(bundles[lid]['bands'])} bands, {len(bundles[lid]['rules'])} rules")

    # OSM route shapes from /line-geometry/{id}.geojson. The API is the
    # single source of truth so a snapshot run captures whatever is
    # currently deployed on the Pi, not whatever happens to be sitting in
    # a local working copy. Lines without geometry are skipped silently.
    shapes_payload = {
        "version": 1,
        "source": "OpenStreetMap (ODbL) via api-syrmos.peterdsp.dev/line-geometry",
        "shapes": {},
    }
    # Seed from the full OSM-derived file (all 31 lines) FIRST. The API's
    # /line-geometry only serves the 9 Athens lines (IC1/TL1/... return 404),
    # so rebuilding shapes from the API alone drops every national / bus /
    # Thessaloniki / Patras corridor and the native maps fall back to a
    # station-spline that diverges on shared track (the 1.2.12 bug). Starting
    # from the OSM file keeps all 31; the API loop below overlays the 9 Athens
    # lines with whatever geometry is currently deployed.
    osm_shapes_file = ROOT / "assets/line-geometry/shapes.json"
    if osm_shapes_file.exists():
        try:
            base_shapes = json.loads(osm_shapes_file.read_text(encoding="utf-8")).get("shapes", {})
            shapes_payload["shapes"].update(base_shapes)
            print(f"  seeded shapes from OSM file: {len(base_shapes)} lines")
        except Exception as e:  # noqa: BLE001
            print(f"  WARN: OSM shapes base load failed, API-only shapes: {e}")
    for lid in line_ids:
        feature = fetch_geojson_optional(f"/line-geometry/{lid}.geojson")
        if not feature:
            continue
        geom = feature.get("geometry") or {}
        coords = geom.get("coordinates") or []
        if not coords:
            continue
        props = feature.get("properties") or {}
        # GeoJSON ships [lng, lat]; the apps consume [lat, lng] to match
        # MapKit / Leaflet / osmdroid native LatLng order.
        shapes_payload["shapes"][lid] = {
            "osmRelationId": props.get("osmRelationId"),
            "from": props.get("from", ""),
            "to": props.get("to", ""),
            "points": len(coords),
            "coordinates": [[lat, lng] for lng, lat in coords],
        }
    shapes_bytes = json.dumps(shapes_payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")

    for dest in (DEST_KMP, DEST_ANDROID, DEST_IOS):
        if dest.exists():
            shutil.rmtree(dest)
        dest.mkdir(parents=True)
        total = 0
        total += write(dest / "manifest.json", manifest)
        total += write(dest / "holidays.json", holidays)
        total += write(dest / "lines.json", lines)
        total += write(dest / "icons.json", icons)
        total += write(dest / "line-display.json", line_display)
        total += write(dest / "fares.json", fares)
        if station_offsets is not None:
            total += write(dest / "station-offsets.json", station_offsets)
        if announcements is not None:
            total += write(dest / "announcements.json", announcements)
        (dest / "shapes.json").write_bytes(shapes_bytes)
        total += len(shapes_bytes)
        for lid, payload in bundles.items():
            total += write(dest / f"{lid}.json", payload)
        n_files = (
            len(bundles)
            + 7
            + (1 if station_offsets is not None else 0)
            + (1 if announcements is not None else 0)
        )
        print(f"wrote {n_files} files ({total} bytes) -> {dest.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
