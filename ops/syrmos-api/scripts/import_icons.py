"""Walk the athens transit package on disk and register every icon in the
`icons` table.

Default URL pattern (served by nginx on the Pi, see nginx.locations.conf):
   https://api-syrmos.peterdsp.dev/icons/stations/{mode}/{LINE}/{file}
   https://api-syrmos.peterdsp.dev/icons/stations/connection/{file}
   https://api-syrmos.peterdsp.dev/icons/vehicles/directional/{mode}/{corridor}/{file}
   https://api-syrmos.peterdsp.dev/icons/vehicles/generic_vehicle/{file}

Idempotent: re-running upserts every row, preserving any admin override_url.

Run:
    python3 -m scripts.import_icons --apply
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import os
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
from syrmos_admin import db as dbmod  # noqa: E402

# Pi: icons live at ~/syrmos-api/assets/icons (uploaded out-of-tree).
# Repo dev: assets/athens-transit-package/icons (in repo root).
# Override via env var if needed.
_REPO_DEFAULT = ROOT.parent.parent / "assets" / "athens-transit-package" / "icons"
_PI_DEFAULT = ROOT / "assets" / "icons"
PKG_ICONS_DIR = Path(os.environ.get(
    "SYRMOS_ICONS_DIR",
    str(_PI_DEFAULT) if _PI_DEFAULT.exists() else str(_REPO_DEFAULT),
))
BASE_URL = os.environ.get("SYRMOS_ICONS_BASE_URL", "https://api-syrmos.peterdsp.dev/icons")


# Per-line station icons follow this filename pattern:
#   metro_m1_01_piraeus_pi.svg -> seq=1, line=M1, mode=metro
PER_LINE_RE = re.compile(r"^[a-z]+_([a-z0-9]+)_(\d+)_")

# Map sequence position on the package's stations to the app's station_id.
# We derive this by reading the existing line_stations table.


def relative_path(svg: Path) -> str:
    rel = svg.relative_to(PKG_ICONS_DIR)
    return f"{BASE_URL}/{rel.as_posix()}"


def build_seq_to_station(conn) -> dict[str, dict[int, str]]:
    """Return {line_id: {seq: station_id}}. seq is 1-based, matches the
    `01` `02` ... padding in the package filenames."""
    out: dict[str, dict[int, str]] = {}
    rows = conn.execute(
        "SELECT line_id, station_id, seq FROM line_stations WHERE direction='both' ORDER BY line_id, seq"
    ).fetchall()
    for r in rows:
        line = r["line_id"]
        out.setdefault(line, {})[r["seq"]] = r["station_id"]
    return out


# Interchange filename -> station_ids that should resolve to this combined icon.
INTERCHANGE_TO_STATIONS = {
    "station_syntagma_m2_m3_t6": ["M2_SYN", "M3_SYN", "T6_SYN"],
    "station_monastiraki_m1_m3": ["M1_MON", "M3_MON"],
    "station_dimotiko_theatro_m3_t7": ["M3_DIM"],
    "station_dimarhio_dimotiko_theatro_m3_t7": ["T7_DIM"],
}


def parse_vehicle(svg: Path) -> dict | None:
    """tram_t6_left_to_syntagma -> {line_id:T6, direction:inbound}.
    Returns None for generic vehicles (no direction)."""
    fname = svg.stem
    m = re.match(r"^([a-z]+)_([a-z0-9]+)_(left|right)_to_(.+)$", fname)
    if m:
        mode, line_token, lr, dest = m.groups()
        line_id = line_token.upper()
        direction = "inbound" if lr == "left" else "outbound"
        if "airport" in dest.lower() and line_id == "M3":
            direction = "airport"
            line_id = "M3_AIR"
        return {"line_id": line_id, "direction": direction}
    # Generic vehicle: vehicle_metro / vehicle_tram / vehicle_train
    if fname.startswith("vehicle_"):
        return None
    return None


def apply(conn, dry_run: bool) -> dict:
    dbmod.migrate(conn)
    if not PKG_ICONS_DIR.exists():
        raise RuntimeError(f"package missing at {PKG_ICONS_DIR}")

    seq_map = build_seq_to_station(conn)

    rows = []  # (scope, station_id, line_id, direction, default_url, description)
    summary = {"station": 0, "interchange": 0, "vehicle_directional": 0, "vehicle_generic": 0}

    # Per-line station icons
    stations_root = PKG_ICONS_DIR / "station_smart_codes" / "athens_station_smart_code_icons" / "stations_smart_codes"
    for svg in sorted(stations_root.rglob("*.svg")):
        parts = svg.relative_to(stations_root).parts
        # parts = [mode, LINE, filename]
        if len(parts) != 3:
            continue
        mode, line_letter, fname = parts
        m = PER_LINE_RE.match(fname)
        if not m:
            continue
        seq = int(m.group(2))
        # Map P1/P2/P3 (Hellenic Train labels) to A1/A2/A3/A4 line ids in DB
        line_id = {"P1": "A1", "P2": "A4", "P3": "A3"}.get(line_letter, line_letter)
        sid = seq_map.get(line_id, {}).get(seq)
        if sid is None:
            continue
        rows.append(("station", sid, None, None, relative_path(svg), f"Per-line icon for {sid}"))
        summary["station"] += 1

    # Combined interchange icons
    inter_root = PKG_ICONS_DIR / "station_smart_codes" / "athens_station_smart_code_icons" / "station_connection_icons"
    for svg in sorted(inter_root.glob("*.svg")):
        basename = svg.stem
        stations = INTERCHANGE_TO_STATIONS.get(basename)
        if not stations:
            continue
        for sid in stations:
            rows.append(("interchange", sid, None, None, relative_path(svg), f"Combined-line interchange icon for {sid}"))
            summary["interchange"] += 1

    # Directional vehicle icons
    vehicles_root = PKG_ICONS_DIR / "directional_vehicle_icons"
    for svg in sorted((vehicles_root / "directional").rglob("*.svg")):
        info = parse_vehicle(svg)
        if not info:
            continue
        rows.append(("vehicle", None, info["line_id"], info["direction"], relative_path(svg), f"{info['line_id']} {info['direction']}"))
        summary["vehicle_directional"] += 1

    # Generic vehicles (mode-only fallback)
    for svg in sorted((vehicles_root / "generic_vehicle").glob("*.svg")):
        rows.append(("vehicle", None, None, svg.stem.removeprefix("vehicle_"), relative_path(svg), f"Generic {svg.stem}"))
        summary["vehicle_generic"] += 1

    print({k: v for k, v in summary.items()}, "(total {})".format(sum(summary.values())))

    if dry_run:
        return summary

    cur = conn.cursor()
    cur.execute("BEGIN")
    try:
        # Preserve override_url on re-import: only update default_url + description.
        cur.execute("DELETE FROM icons WHERE override_url IS NULL")
        # For rows with override_url set, leave them alone — re-insert only the
        # ones we don't already have.
        existing_override = {
            (r["scope"], r["station_id"], r["line_id"], r["direction"])
            for r in cur.execute("SELECT scope, station_id, line_id, direction FROM icons WHERE override_url IS NOT NULL")
        }
        cur.executemany(
            "INSERT INTO icons(scope, station_id, line_id, direction, default_url, description)"
            " VALUES(?,?,?,?,?,?)",
            [r for r in rows if (r[0], r[1], r[2], r[3]) not in existing_override],
        )
        cur.execute("COMMIT")
    except Exception:
        cur.execute("ROLLBACK")
        raise

    return summary


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--apply", action="store_true")
    args = p.parse_args()
    with dbmod.connect() as conn:
        apply(conn, dry_run=not args.apply)


if __name__ == "__main__":
    main()
