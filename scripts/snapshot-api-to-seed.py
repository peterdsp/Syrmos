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
import urllib.request
from pathlib import Path

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
        print(f"  fetched {lid}: {len(bundles[lid]['bands'])} bands, {len(bundles[lid]['rules'])} rules")

    # OSM route shapes ride along — bundled offline-first, not pulled from
    # the API on each snapshot. Keep the repo-canonical copy across the
    # rmtree so we don't have to regenerate it after every schedules pull.
    shapes_canonical = ROOT / "assets/line-geometry/shapes.json"
    shapes_bytes = shapes_canonical.read_bytes() if shapes_canonical.exists() else None

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
        if shapes_bytes is not None:
            (dest / "shapes.json").write_bytes(shapes_bytes)
            total += len(shapes_bytes)
        for lid, payload in bundles.items():
            total += write(dest / f"{lid}.json", payload)
        n_files = (
            len(bundles)
            + 6
            + (1 if station_offsets is not None else 0)
            + (1 if shapes_bytes is not None else 0)
        )
        print(f"wrote {n_files} files ({total} bytes) -> {dest.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
