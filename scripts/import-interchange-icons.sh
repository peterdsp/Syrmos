#!/usr/bin/env bash
# Import the combined-line interchange icons from the athens transit package
# into the iOS asset catalog as PNG @1x/@2x/@3x.
#
# These are the icons that show ALL connecting lines together at multi-line
# interchanges (Syntagma M2+M3+T6, Monastiraki M1+M3, Dimotiko Theatro M3+T7).
# The icon resolver prefers these at interchange stations over single-line icons.
#
# Requires: rsvg-convert (brew install librsvg)
set -euo pipefail

PKG=/tmp/athens_pkg/athens_transit_icons_and_rules_package/athens_transit_t7_station_icons_updated/station_smart_codes/athens_station_smart_code_icons/station_connection_icons
DST=iosApp/iosApp/Assets.xcassets/stations
BASE_SIZE=44

command -v rsvg-convert >/dev/null || { echo "need rsvg-convert (brew install librsvg)"; exit 1; }

# Only the truly multi-line interchanges. The T7-only ones (evangelistria, etc.)
# already exist as per-line icons in the catalog.
CONNECTIONS=(
    "station_monastiraki_m1_m3"
    "station_syntagma_m2_m3_t6"
    "station_dimotiko_theatro_m3_t7"
    "station_dimarhio_dimotiko_theatro_m3_t7"
)

for name in "${CONNECTIONS[@]}"; do
    src="$PKG/${name}.svg"
    [[ -f "$src" ]] || { echo "missing $src"; exit 1; }
    imageset="$DST/${name}.imageset"
    mkdir -p "$imageset"
    for scale in 1 2 3; do
        out="$imageset/${name}"
        case $scale in
            1) out+=".png" ;;
            2) out+="@2x.png" ;;
            3) out+="@3x.png" ;;
        esac
        size=$((BASE_SIZE * scale))
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
    echo "+ $imageset"
done
