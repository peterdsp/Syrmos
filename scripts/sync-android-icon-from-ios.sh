#!/usr/bin/env bash
#
# Sync the Android launcher icon set from the current iOS AppIcon so the
# two platforms show the same brand mark. Regenerates ic_launcher.png and
# ic_launcher_foreground.png at every mipmap density using macOS `sips`,
# and drops a 512x512 PNG under output/ that you upload manually to
# Play Console -> Grow -> Store presence -> Main store listing -> Graphics
# -> App icon (the Play Store listing icon is separate from the launcher
# icon shipped inside the AAB).
#
# Usage: ./scripts/sync-android-icon-from-ios.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SRC="iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-Default-1024.png"
ANDROID_RES="androidApp/src/androidMain/res"
OUTPUT_DIR="output"

if [[ ! -f "$SRC" ]]; then
    echo "Missing source: $SRC" >&2
    exit 1
fi

resize() {
    sips -Z "$1" "$SRC" --out "$2" >/dev/null
}

echo "==> Syncing Android launcher icon from $SRC"

for entry in \
    "mdpi 48 108" \
    "hdpi 72 162" \
    "xhdpi 96 216" \
    "xxhdpi 144 324" \
    "xxxhdpi 192 432"
do
    read -r density legacy fg <<<"$entry"
    dir="$ANDROID_RES/mipmap-$density"
    mkdir -p "$dir"
    resize "$legacy" "$dir/ic_launcher.png"
    resize "$fg" "$dir/ic_launcher_foreground.png"
    printf "  %-8s  legacy=%3d px  foreground=%3d px\n" "$density" "$legacy" "$fg"
done

mkdir -p "$OUTPUT_DIR"
resize 512 "$OUTPUT_DIR/play-store-icon-512.png"
echo ""
echo "==> Play Store listing icon (512x512) written to:"
echo "     $OUTPUT_DIR/play-store-icon-512.png"
echo ""
echo "Next steps:"
echo "  1. Rebuild the AAB:"
echo "       ./gradlew :androidApp:bundleRelease"
echo "  2. Upload the new AAB to Play Console (Testing or Production)."
echo "  3. Upload the 512x512 PNG to:"
echo "       Play Console -> Grow -> Store presence -> Main store listing"
echo "         -> Graphics -> App icon"
