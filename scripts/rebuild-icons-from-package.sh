#!/usr/bin/env bash
# Full reset: delete every station + vehicle icon in the apps and rebuild
# from the athens transit package as the single source.
#
# Web: copies SVGs preserving the package's mode/line subdirectory layout.
# iOS: rasters each SVG to PNG @1x/@2x/@3x as an Xcode imageset, using the
#      package's filename (sans .svg) as the imageset name. Existing Swift
#      references like `metro_m1_01_piraeus_pi` continue to resolve since
#      the imageset names match.
#
# At multi-line interchanges (Syntagma M2+M3+T6, Monastiraki M1+M3, Dimotiko
# Theatro M3+T7) the package provides combined "connection" icons. The web
# manifest applies them as overrides over the per-line entries; the iOS
# resolver in MapView.swift does the same.
#
# Requires: rsvg-convert (brew install librsvg) for iOS rasterization.

set -euo pipefail

PKG=/tmp/athens_pkg/athens_transit_icons_and_rules_package/athens_transit_t7_station_icons_updated
WEB_ICONS=composeApp/src/wasmJsMain/resources/icons
IOS_STATIONS=iosApp/iosApp/Assets.xcassets/stations
IOS_VEHICLES=iosApp/iosApp/Assets.xcassets/vehicles
IOS_BASE_SIZE=44

command -v rsvg-convert >/dev/null || { echo "need rsvg-convert (brew install librsvg)"; exit 1; }
[[ -d "$PKG" ]] || { echo "package missing at $PKG"; exit 1; }

echo "=== Wiping existing icons ==="
rm -rf "$WEB_ICONS"
rm -rf "$IOS_STATIONS"
rm -rf "$IOS_VEHICLES"
mkdir -p "$WEB_ICONS" "$IOS_STATIONS" "$IOS_VEHICLES"

# --- 1. Web: copy preserving layout, then generate manifest ---

echo "=== Web: copying station icons ==="
mkdir -p "$WEB_ICONS/stations"
cp -R "$PKG/station_smart_codes/athens_station_smart_code_icons/stations_smart_codes/." "$WEB_ICONS/stations/"
cp -R "$PKG/station_smart_codes/athens_station_smart_code_icons/station_connection_icons" "$WEB_ICONS/stations/connection"
echo "  station SVGs: $(find $WEB_ICONS/stations -name "*.svg" | wc -l)"

echo "=== Web: copying vehicle icons ==="
mkdir -p "$WEB_ICONS/vehicles"
cp -R "$PKG/directional_vehicle_icons/directional" "$WEB_ICONS/vehicles/directional"
cp -R "$PKG/directional_vehicle_icons/generic_vehicle" "$WEB_ICONS/vehicles/generic_vehicle"
echo "  vehicle SVGs: $(find $WEB_ICONS/vehicles -name "*.svg" | wc -l)"

echo "=== Web: regenerating manifest ==="
python3 - <<'PYEOF'
import json, re
from pathlib import Path

ROOT = Path("composeApp/src/wasmJsMain/resources/icons")
manifest = {}

# Per-line station icons: metro/M1/01 -> icons/stations/metro/M1/metro_m1_01_*.svg
for path in sorted((ROOT / "stations").rglob("*.svg")):
    rel = path.relative_to(ROOT.parent)
    parts = path.relative_to(ROOT / "stations").parts
    # Expect: <mode>/<LINE>/<file>.svg, e.g. metro/M1/metro_m1_01_piraeus_pi.svg
    if len(parts) == 3 and parts[0] in ("metro", "tram", "train"):
        mode, line, fname = parts
        m = re.match(r"^[a-z]+_[a-z0-9]+_(\d+)_", fname)
        if m:
            seq = m.group(1)
            manifest[f"{mode}/{line}/{seq}"] = str(rel)

# Combined interchange icons -> apply as station-id overrides
INTERCHANGES = {
    "M2_SYN": "station_syntagma_m2_m3_t6",
    "M3_SYN": "station_syntagma_m2_m3_t6",
    "T6_SYN": "station_syntagma_m2_m3_t6",
    "M1_MON": "station_monastiraki_m1_m3",
    "M3_MON": "station_monastiraki_m1_m3",
    "M3_DIM": "station_dimotiko_theatro_m3_t7",
    "T7_DIM": "station_dimarhio_dimotiko_theatro_m3_t7",
}
connection_dir = ROOT / "stations" / "connection"
for sid, basename in INTERCHANGES.items():
    p = connection_dir / f"{basename}.svg"
    if p.exists():
        manifest[f"interchange/{sid}"] = str(p.relative_to(ROOT.parent))

with open(ROOT / "stations" / "manifest.json", "w") as f:
    json.dump(manifest, f, indent=2, ensure_ascii=False)
print(f"  manifest entries: {len(manifest)}")

# Vehicle manifest — flat list of {file, mode, line, arrow, destination}
vehicles = []
for path in sorted((ROOT / "vehicles" / "directional").rglob("*.svg")):
    rel = path.relative_to(ROOT.parent)
    fname = path.stem
    # metro_m1_left_to_piraeus, tram_t6_right_to_pikrodafni, train_p1_left_to_piraeus
    parts = fname.split("_")
    if len(parts) >= 5 and parts[2] in ("left", "right"):
        mode = parts[0]
        line = parts[1].upper()
        arrow = "←" if parts[2] == "left" else "→"
        destination = " ".join(w.capitalize() for w in parts[4:])
        vehicles.append({
            "file": str(rel),
            "mode": mode,
            "line": line,
            "arrow": arrow,
            "destination": destination,
        })

# Generic fallbacks
for path in (ROOT / "vehicles" / "generic_vehicle").glob("*.svg"):
    vehicles.append({
        "file": str(path.relative_to(ROOT.parent)),
        "mode": path.stem.split("_")[-1],
        "line": "GENERIC",
        "arrow": "",
        "destination": "",
    })

with open(ROOT / "vehicles" / "manifest.json", "w") as f:
    json.dump({"directional_icons": vehicles}, f, indent=2, ensure_ascii=False)
print(f"  vehicle manifest entries: {len(vehicles)}")
PYEOF

# --- 2. iOS: raster each SVG into an imageset ---

raster_into_imageset() {
    local src="$1" dst_root="$2"
    local name; name=$(basename "${src%.*}")
    local imageset="$dst_root/${name}.imageset"
    mkdir -p "$imageset"
    for scale in 1 2 3; do
        local out="$imageset/${name}"
        case $scale in
            1) out+=".png" ;;
            2) out+="@2x.png" ;;
            3) out+="@3x.png" ;;
        esac
        local size=$((IOS_BASE_SIZE * scale))
        rsvg-convert -w "$size" -h "$size" -o "$out" "$src"
    done
    cat >"$imageset/Contents.json" <<EOF
{
  "images": [
    {"idiom": "universal", "filename": "${name}.png", "scale": "1x"},
    {"idiom": "universal", "filename": "${name}@2x.png", "scale": "2x"},
    {"idiom": "universal", "filename": "${name}@3x.png", "scale": "3x"}
  ],
  "info": {"version": 1, "author": "xcode"}
}
EOF
}

echo "=== iOS: rasterizing station icons ==="
count=0
while IFS= read -r svg; do
    raster_into_imageset "$svg" "$IOS_STATIONS"
    count=$((count+1))
done < <(find "$PKG/station_smart_codes/athens_station_smart_code_icons/stations_smart_codes" -name "*.svg" -type f)
echo "  station imagesets: $count"

echo "=== iOS: rasterizing combined interchange icons ==="
count=0
while IFS= read -r svg; do
    raster_into_imageset "$svg" "$IOS_STATIONS"
    count=$((count+1))
done < <(find "$PKG/station_smart_codes/athens_station_smart_code_icons/station_connection_icons" -name "*.svg" -type f)
echo "  interchange imagesets: $count"

echo "=== iOS: rasterizing vehicle icons ==="
count=0
while IFS= read -r svg; do
    raster_into_imageset "$svg" "$IOS_VEHICLES"
    count=$((count+1))
done < <(find "$PKG/directional_vehicle_icons" -name "*.svg" -type f)
echo "  vehicle imagesets: $count"

# Asset catalog needs a root Contents.json in each top-level folder
for dir in "$IOS_STATIONS" "$IOS_VEHICLES"; do
    [[ -f "$dir/Contents.json" ]] || cat >"$dir/Contents.json" <<EOF
{"info": {"version": 1, "author": "xcode"}}
EOF
done

echo ""
echo "=== Done ==="
echo "Web station SVGs: $(find $WEB_ICONS/stations -name "*.svg" | wc -l)"
echo "Web vehicle SVGs: $(find $WEB_ICONS/vehicles -name "*.svg" | wc -l)"
echo "iOS station imagesets: $(ls $IOS_STATIONS | grep -c imageset)"
echo "iOS vehicle imagesets: $(ls $IOS_VEHICLES | grep -c imageset)"
