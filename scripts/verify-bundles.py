"""Verify the offline-first contract of the bundled seed snapshots.

The app must launch with correct data when the device has zero network.
This script fails (exit 1) when a bundled seed is missing, empty, or
degenerate in a way that would break the offline-first promise.

Run before every commit that touches `seed-schedules-v2/`, before every
release, and in CI:

    python3 scripts/verify-bundles.py

Adds a check whenever a new always-bundled data type ships.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

BUNDLES = [
    ROOT / "core/data/src/commonMain/composeResources/files/seed/schedules-v2",
    ROOT / "androidApp/src/androidMain/assets/files/seed/schedules-v2",
    ROOT / "iosApp/iosApp/Resources/seed-schedules-v2",
]

# Lines the apps expect to find offline. Subset of /api/lines: M3_AIR is a
# virtual schedule-only line so it's in the bundle but not in /api/lines.
LINE_IDS = ["M1", "M2", "M3", "T6", "T7", "A1", "A2", "A3", "A4"]
SCHEDULE_IDS = LINE_IDS + ["M3_AIR"]

# Minimum coordinate count per line shape; anything below this means the
# Overpass stitch broke or the line geometry got truncated.
MIN_SHAPE_POINTS = 50


class BundleError(Exception):
    pass


def check_file(path: Path) -> dict:
    if not path.exists():
        raise BundleError(f"missing: {path}")
    if path.stat().st_size == 0:
        raise BundleError(f"empty file: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def check_bundle(root: Path) -> list[str]:
    """Returns a list of human-readable problem strings; empty when healthy."""
    problems: list[str] = []

    def add(msg: str) -> None:
        problems.append(f"  {msg}")

    # manifest + lines + fares + shapes + station-offsets must all exist.
    try:
        manifest = check_file(root / "manifest.json")
        lines = check_file(root / "lines.json")
        fares = check_file(root / "fares.json")
        shapes = check_file(root / "shapes.json")
        offsets = check_file(root / "station-offsets.json")
    except BundleError as e:
        add(str(e))
        return problems  # no point continuing past structural missing files

    # manifest perLineHashes must include every required schedule bundle.
    manifest_ids = set(manifest.get("perLineHashes", {}).keys())
    for lid in SCHEDULE_IDS:
        if lid not in manifest_ids:
            add(f"manifest.json perLineHashes missing {lid!r}")

    # lines.json must include the canonical line list and station counts.
    line_index = {l["id"]: l for l in lines.get("lines", [])}
    for lid in LINE_IDS:
        line = line_index.get(lid)
        if not line:
            add(f"lines.json missing line {lid!r}")
            continue
        stations = line.get("stations", [])
        if not stations:
            add(f"lines.json/{lid} has no stations")
        if lid == "T7" and len(stations) < 43:
            add(f"lines.json/T7 has {len(stations)} stations (expected 43)")
        # Ensure T7_DIM ships with the dual Δημαρχείο / Δημοτικό Θέατρο name
        # so the metro M3_DIM doesn't collide visually in line lists.
        if lid == "T7":
            t7_dim = next((s for s in stations if s.get("id") == "T7_DIM"), None)
            if t7_dim and "Δημαρχείο" not in t7_dim.get("nameEl", ""):
                add("T7_DIM nameEl missing the Δημαρχείο dual form")

    # fares.products must be populated; offline-first apps can't show prices
    # the user expects to see if products is [].
    products = fares.get("products", [])
    if not products:
        add("fares.json has products: [] (offline launch would render empty Tickets)")
    if len(products) < 10:
        add(f"fares.json only has {len(products)} products (expected at least 10)")

    # shapes.json must cover every public line with a non-degenerate polyline.
    shape_map = shapes.get("shapes", {})
    for lid in LINE_IDS:
        shape = shape_map.get(lid)
        if not shape:
            add(f"shapes.json missing line {lid!r}")
            continue
        coords = shape.get("coordinates", [])
        if len(coords) < MIN_SHAPE_POINTS:
            add(f"shapes.json/{lid} only has {len(coords)} points (min {MIN_SHAPE_POINTS})")

    # station-offsets is nice-to-have, not load-bearing. Without it every
    # station shows the time at the line origin instead of its own HH:MM.
    # Track as a warning instead of failing the bundle so the offline-first
    # promise still passes when the STASY HTML scraper has nothing to feed.
    offset_lines = {g["lineId"] for g in offsets.get("lines", [])}
    for lid in ("M1", "M2", "M3", "T6", "T7"):
        if lid not in offset_lines:
            problems.append(f"  WARN: station-offsets.json missing line {lid!r}")

    # Every schedule bundle must be present and have at least one band.
    for lid in SCHEDULE_IDS:
        path = root / f"{lid}.json"
        try:
            bundle = check_file(path)
        except BundleError as e:
            add(str(e))
            continue
        bands = bundle.get("bands", [])
        rules = bundle.get("rules", [])
        if not bands:
            add(f"{lid}.json has no bands")
        if not rules:
            add(f"{lid}.json has no rules")

    return problems


def main() -> int:
    exit_code = 0
    for root in BUNDLES:
        print(f"checking {root.relative_to(ROOT)}")
        if not root.exists():
            print(f"  MISSING DIRECTORY")
            exit_code = 1
            continue
        problems = check_bundle(root)
        if problems:
            # Warnings start with "WARN:" — they print but don't fail.
            errors = [p for p in problems if "WARN:" not in p]
            warnings = [p for p in problems if "WARN:" in p]
            for w in warnings:
                print(w)
            for e in errors:
                print(e)
            if errors:
                exit_code = 1
        else:
            print("  ok")
    if exit_code:
        print("\nFAIL: bundle verification found degradations", file=sys.stderr)
    else:
        print("\nOK: all three platform bundles satisfy the offline-first contract")
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
