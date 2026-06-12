"""Walk the athens transit package on disk and register every icon in the
`icons` table.

Default URL pattern (served by nginx on the Pi, see nginx.locations.conf):
   https://api-syrmos.peterdsp.dev/icons/stations/{mode}/{LINE}/{file}
   https://api-syrmos.peterdsp.dev/icons/stations/connection/{file}
   https://api-syrmos.peterdsp.dev/icons/vehicles/directional/{mode}/{corridor}/{file}
   https://api-syrmos.peterdsp.dev/icons/vehicles/generic_vehicle/{file}

Idempotent: re-running upserts every row, preserving any admin override_url.

Matching strategy
-----------------
Older versions paired manifest entries to stations by position (seq number).
That breaks the moment the database has more (or differently-ordered) stops
than the icon manifest, which is the case on T6 (19 DB stops vs 13 manifest
icons). Stations ended up wearing each other's labels.

We now match by **station name** instead. Each manifest entry has a
canonical `station` field; we normalize both sides (lowercase, strip
accents, drop common Greek/English prefixes like "Aghios/Ag/Ano/Kato/Leof")
and pair them. Stations with no matching manifest entry simply don't get a
per-station icon. The web/app already falls back to a clean line-colored
marker, which is the desired behaviour for those routes.

Run:
    python3 -m scripts.import_icons --apply
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import unicodedata
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

# Hellenic-Train manifest line codes -> Syrmos line ids in the DB.
MANIFEST_LINE_TO_DB = {"P1": "A1", "P2": "A4", "P3": "A3"}

# Interchange filename -> station_ids that should resolve to this combined icon.
INTERCHANGE_TO_STATIONS = {
    "station_syntagma_m2_m3_t6": ["M2_SYN", "M3_SYN", "T6_SYN"],
    "station_monastiraki_m1_m3": ["M1_MON", "M3_MON"],
    "station_dimotiko_theatro_m3_t7": ["M3_DIM"],
    "station_dimarhio_dimotiko_theatro_m3_t7": ["T7_DIM"],
}


# Greek/English prefixes that are routinely shortened or dropped on signage.
# Normalize them away on both sides so "Aghios Eleftherios" matches
# "Ag. Eleftherios" and "Άγιος Ελευθέριος".
_PREFIX_PATTERNS = [
    r"^aghios\b", r"^aghia\b", r"^aghias\b",
    r"^agios\b", r"^agia\b", r"^agias\b", r"^agioi\b", r"^ag\b\.?",
    r"^leoforos\b\.?", r"^leof\b\.?",
    r"^platia\b", r"^plateia\b",
    r"^neos\b", r"^nea\b", r"^neo\b",
    r"^ano\b", r"^kato\b", r"^paleo\b", r"^paleos\b", r"^palaio\b",
    r"^m\.\s*",  # "M. Mousourou" -> "Mousourou"
]
_PREFIX_RE = re.compile("|".join(_PREFIX_PATTERNS))

_WORD_ALIASES = [
    # Same stations are romanized differently across STASY, OASA, Hellenic Train
    # and the icon package. Keep these narrow so we do not reintroduce
    # sequence-based false positives.
    (r"\bposidonos\b", "poseidonos"),
    (r"\bippodameias\b", "ippodamias"),
    (r"\bfoteinis\b", "fotini"),
    (r"\bzappeion\b", "zappio"),
    (r"\bthiseio\b", "thissio"),
    (r"\bpatisia\b", "patissia"),
    (r"\birakleio\b", "iraklio"),
    (r"\beirini\b", "irini"),
    (r"\bmarousi\b", "maroussi"),
    (r"\bmetaxourgeio\b", "metaxourghio"),
    (r"\bsyngrou\b", "sygrou"),
    (r"\bnikaia\b", "nikea"),
    (r"\bkatechaki\b", "katehaki"),
    (r"\bcholargos\b", "holargos"),
    (r"\bchalandri\b", "halandri"),
    (r"\bpaiania\b", "peania"),
    (r"\bzefiri\b", "zefyri"),
    (r"\bcorinth\b", "korinthos"),
    (r"\bhellinon\b", "ellinon"),
    (r"\bolympionikon\b", "olymbionikon"),
    (r"\bdemarhio\b", "dimarhio"),
    (r"\bagheiou\b", "angelou"),
    (r"\baghiou\b", "ag"),
    (r"\bagioi\b", "ag"),
    (r"\bs e f\b", "sef"),
    (r"\b(\d+)ou\b", r"\1"),
    (r"\bpezikou\b", ""),
    (r"\bvaso\b", ""),
    (r"\bplateia\b", "platia"),
]

_EXACT_ALIASES = {
    "tavros eleftherios venizelos": "tavros",
    "peace and friendship stadium": "sef",
    "omiridou skylitsi": "skylitsi",
    "fotini platia": "fotini",
    "ag fotini platia": "fotini",
}


def _fold(s: str) -> str:
    """Lowercase + strip diacritics."""
    nkfd = unicodedata.normalize("NFD", s).lower()
    return "".join(c for c in nkfd if not unicodedata.combining(c))


def normalize_name(name: str) -> str:
    """Canonicalize a station name for cross-source matching.
    Lowercase, strip accents, drop common prefixes, collapse whitespace and
    punctuation to single spaces, trim."""
    if not name:
        return ""
    s = _fold(name)
    s = re.sub(r"[\.\,'`’‘()\[\]]", " ", s)
    s = re.sub(r"[-\u2013\u2014/]", " ", s)
    s = re.sub(r"\s+", " ", s).strip()
    for pattern, replacement in _WORD_ALIASES:
        s = re.sub(pattern, replacement, s)
    s = re.sub(r"\s+", " ", s).strip()
    # Strip leading prefixes (may appear multiple times: "Ag. N. Faliro").
    for _ in range(3):
        new = _PREFIX_RE.sub("", s).strip()
        if new == s:
            break
        s = new
        s = re.sub(r"\s+", " ", s).strip()
    s = _EXACT_ALIASES.get(s, s)
    return s


def relative_path(svg: Path) -> str:
    rel = svg.relative_to(PKG_ICONS_DIR)
    return f"{BASE_URL}/{rel.as_posix()}"


def load_manifest() -> dict:
    """Load the package's station manifest.

    Returns:
      {
        "by_name": {(line_id, normalized_name): file_path},
        "by_station_id": {station_id: file_path},
      }
    """
    manifest_path = (
        PKG_ICONS_DIR
        / "station_smart_codes"
        / "athens_station_smart_code_icons"
        / "manifest.json"
    )
    if not manifest_path.exists():
        # Fall back to scanning the directory tree if the manifest is missing.
        return {"by_name": {}, "by_station_id": {}}
    data = json.loads(manifest_path.read_text())
    by_name: dict[tuple[str, str], str] = {}
    by_station_id: dict[str, str] = {}
    for entry in data.get("station_icons", []):
        line = entry.get("line", "")
        line_id = MANIFEST_LINE_TO_DB.get(line, line)
        name = entry.get("station", "")
        fname = entry.get("file", "")
        station_id = entry.get("station_id", "")
        if not (line_id and name and fname):
            continue
        if station_id:
            by_station_id[station_id] = fname
        by_name[(line_id, normalize_name(name))] = fname
    return {"by_name": by_name, "by_station_id": by_station_id}


def build_station_directory(conn) -> dict[str, list[dict]]:
    """{line_id: [{station_id, name_en, name_el}]} for every active line."""
    rows = conn.execute(
        "SELECT ls.line_id, ls.station_id, s.name_en, s.name_el"
        " FROM line_stations ls"
        " JOIN stations s ON s.id = ls.station_id"
        " WHERE ls.direction = 'both'"
        " GROUP BY ls.line_id, ls.station_id"
        " ORDER BY ls.line_id, ls.seq"
    ).fetchall()
    out: dict[str, list[dict]] = {}
    for r in rows:
        out.setdefault(r["line_id"], []).append(
            {
                "station_id": r["station_id"],
                "name_en": r["name_en"] or "",
                "name_el": r["name_el"] or "",
            }
        )
    return out


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
    if fname.startswith("vehicle_"):
        return None
    return None


def apply(conn, dry_run: bool) -> dict:
    dbmod.migrate(conn)
    if not PKG_ICONS_DIR.exists():
        raise RuntimeError(f"package missing at {PKG_ICONS_DIR}")

    manifest = load_manifest()
    if not manifest["by_name"] and not manifest["by_station_id"]:
        raise RuntimeError("manifest.json not found or empty")

    stations_by_line = build_station_directory(conn)

    rows = []
    summary = {
        "station": 0,
        "interchange": 0,
        "vehicle_directional": 0,
        "vehicle_generic": 0,
        "unmatched_stations": 0,
    }
    unmatched: list[tuple[str, str]] = []

    pkg_stations_root = PKG_ICONS_DIR / "station_smart_codes" / "athens_station_smart_code_icons"

    # Per-line station icons: walk the DB, look up each station's name in the
    # manifest. Stations with no manifest entry are left without an override.
    for line_id, stops in stations_by_line.items():
        for stop in stops:
            fname = manifest["by_station_id"].get(stop["station_id"])
            if fname:
                svg_path = pkg_stations_root / fname
                rows.append((
                    "station", stop["station_id"], None, None,
                    relative_path(svg_path),
                    f"Per-line icon for {stop['station_id']} ({stop['name_en']})",
                ))
                summary["station"] += 1
                continue
            for candidate in (stop["name_en"], stop["name_el"]):
                key = (line_id, normalize_name(candidate))
                fname = manifest["by_name"].get(key)
                if fname:
                    svg_path = pkg_stations_root / fname
                    rows.append((
                        "station", stop["station_id"], None, None,
                        relative_path(svg_path),
                        f"Per-line icon for {stop['station_id']} ({candidate})",
                    ))
                    summary["station"] += 1
                    break
            else:
                unmatched.append((line_id, stop["station_id"]))
                summary["unmatched_stations"] += 1

    # Combined interchange icons
    inter_root = pkg_stations_root / "station_connection_icons"
    for svg in sorted(inter_root.glob("*.svg")):
        basename = svg.stem
        sids = INTERCHANGE_TO_STATIONS.get(basename)
        if not sids:
            continue
        for sid in sids:
            rows.append(("interchange", sid, None, None, relative_path(svg),
                         f"Combined-line interchange icon for {sid}"))
            summary["interchange"] += 1

    # Directional vehicle icons
    vehicles_root = PKG_ICONS_DIR / "directional_vehicle_icons"
    for svg in sorted((vehicles_root / "directional").rglob("*.svg")):
        info = parse_vehicle(svg)
        if not info:
            continue
        rows.append(("vehicle", None, info["line_id"], info["direction"],
                     relative_path(svg), f"{info['line_id']} {info['direction']}"))
        summary["vehicle_directional"] += 1

    # Generic vehicles (mode-only fallback)
    for svg in sorted((vehicles_root / "generic_vehicle").glob("*.svg")):
        rows.append(("vehicle", None, None, svg.stem.removeprefix("vehicle_"),
                     relative_path(svg), f"Generic {svg.stem}"))
        summary["vehicle_generic"] += 1

    print({k: v for k, v in summary.items()}, "(total rows {})".format(
        sum(v for k, v in summary.items() if k != "unmatched_stations")
    ))
    if unmatched:
        print(f"Unmatched (no per-station icon): {len(unmatched)}")
        for line, sid in unmatched[:20]:
            print(f"  - {line} {sid}")
        if len(unmatched) > 20:
            print(f"  ... +{len(unmatched) - 20} more")

    if dry_run:
        return summary

    cur = conn.cursor()
    cur.execute("BEGIN")
    try:
        # Preserve override_url on re-import: drop default-only rows, then
        # re-insert anything that doesn't already have an admin override.
        cur.execute("DELETE FROM icons WHERE override_url IS NULL")
        existing_override = {
            (r["scope"], r["station_id"], r["line_id"], r["direction"])
            for r in cur.execute(
                "SELECT scope, station_id, line_id, direction FROM icons WHERE override_url IS NOT NULL"
            )
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
