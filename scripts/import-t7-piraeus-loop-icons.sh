#!/usr/bin/env bash
# Import the 6 T7 Piraeus-loop station icons from the athens transit package
# into the iOS asset catalog as PNG @1x/@2x/@3x.
#
# The web app (Compose web) reads the SVGs directly so no work there.
# iOS needs rastered PNGs because the asset catalog imageset format
# expects bitmap variants.
#
# Requires: rsvg-convert (brew install librsvg)
set -euo pipefail

PKG=/tmp/athens_pkg/athens_transit_icons_and_rules_package/athens_transit_t7_station_icons_updated/station_smart_codes/athens_station_smart_code_icons/stations_smart_codes/tram/T7
DST=iosApp/iosApp/Assets.xcassets/stations
BASE_SIZE=44   # @1x point size used by the rest of the T7 imagesets

command -v rsvg-convert >/dev/null || { echo "need rsvg-convert (brew install librsvg)"; exit 1; }

NEW_ICONS=(
    "tram_t7_38_gipedo_karaiskaki_gk"
    "tram_t7_39_mikras_asias_ma"
    "tram_t7_40_grigoriou_lambraki_gl"
    "tram_t7_41_evangelistria_ev"
    "tram_t7_42_plateia_deligianni_dg"
    "tram_t7_43_dimarhio_dimotiko_theatro_dt"
)

for name in "${NEW_ICONS[@]}"; do
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
