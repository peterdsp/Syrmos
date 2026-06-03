#!/usr/bin/env bash
# Syrmos simulator.sh (powered by Kujto)
# Build, install and launch on iOS Simulator with zero configuration.
# Detects workspace, scheme, simulator and bundle id automatically.
#
# Usage:
#   ./simulator.sh                          full auto, debug
#   ./simulator.sh --release                full auto, release
#   ./simulator.sh --device "iPhone 16"     pin device
#   ./simulator.sh --scheme Syrmos          pin scheme
#   ./simulator.sh --clean                  clean DerivedData first
#   ./simulator.sh --no-logs                do not stream logs after launch
#   ./simulator.sh --list                   list schemes and devices, then exit
#   ./simulator.sh --stop                   terminate app and shutdown simulator
#   ./simulator.sh --help                   show this help
#
# Requires: Kujto (https://github.com/peterdsp/kujto)
# If Kujto is installed, delegates directly. Otherwise runs a standalone
# build-install-launch flow for this KMP project.

set -euo pipefail

BANNER="\033[36m▌ Syrmos · iOS\033[0m"
log() { printf "%b  %s\n" "$BANNER" "$*"; }
die() { printf "\033[31m!!\033[0m %s\n" "$*" >&2; exit 1; }

# Try kujto first
if command -v kujto >/dev/null 2>&1; then
    log "Using Kujto to launch on simulator"
    exec kujto simulator "$@"
fi

KUJTO_SH=""
for candidate in \
    "$HOME/git/personal/kujto/simulator.sh" \
    "$HOME/.kujto/simulator.sh" \
    "/usr/local/share/kujto/simulator.sh"; do
    if [[ -x "$candidate" ]]; then
        KUJTO_SH="$candidate"
        break
    fi
done

if [[ -n "$KUJTO_SH" ]]; then
    log "Delegating to $KUJTO_SH"
    exec "$KUJTO_SH" "$@"
fi

# Fallback: standalone build for this project
log "Kujto not found, running standalone build"

[[ "$(uname)" == "Darwin" ]] || die "Requires macOS"
command -v xcodebuild >/dev/null 2>&1 || die "Xcode not installed"

CONFIGURATION="Debug"
CLEAN=0
while (( "$#" )); do
    case "$1" in
        --release) CONFIGURATION="Release"; shift ;;
        --clean) CLEAN=1; shift ;;
        -h|--help) sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) shift ;;
    esac
done

XCWORKSPACE="$(find . -maxdepth 2 -name '*.xcworkspace' -not -path '*.xcodeproj/*' 2>/dev/null | head -n1)"
XCPROJECT="$(find . -maxdepth 2 -name '*.xcodeproj' 2>/dev/null | head -n1)"

if [[ -n "$XCWORKSPACE" ]]; then
    PROJECT_FLAG=(-workspace "$XCWORKSPACE")
elif [[ -n "$XCPROJECT" ]]; then
    PROJECT_FLAG=(-project "$XCPROJECT")
else
    die "No .xcworkspace or .xcodeproj found. Run inside the project root."
fi

UDID="$(xcrun simctl list devices available --json | /usr/bin/python3 -c '
import json, sys
data = json.load(sys.stdin)
for rt, devs in data.get("devices", {}).items():
    if "iOS" not in rt: continue
    for d in devs:
        if d.get("isAvailable") and "iPhone" in d.get("name", ""):
            print(d["udid"]); sys.exit(0)
' 2>/dev/null)"

[[ -n "$UDID" ]] || die "No iPhone simulator found"
DEV_NAME="$(xcrun simctl list devices | grep "$UDID" | sed 's/ (.*//' | xargs)"
log "Device: $DEV_NAME"

xcrun simctl bootstatus "$UDID" -b >/dev/null 2>&1 || xcrun simctl boot "$UDID"
open -a Simulator --args -CurrentDeviceUDID "$UDID" >/dev/null 2>&1 || true

DERIVED="$HOME/Library/Developer/Xcode/DerivedData/Syrmos"
[[ "$CLEAN" -eq 1 ]] && rm -rf "$DERIVED"

log "Building ($CONFIGURATION)"
xcodebuild "${PROJECT_FLAG[@]}" \
    -scheme "iosApp" \
    -configuration "$CONFIGURATION" \
    -destination "platform=iOS Simulator,id=$UDID" \
    -derivedDataPath "$DERIVED" \
    -skipMacroValidation \
    -quiet \
    build || die "Build failed"

APP="$(find "$DERIVED/Build/Products" -type d -name '*.app' -path "*$CONFIGURATION*" 2>/dev/null | head -n1)"
[[ -n "$APP" ]] || die "No .app found"

BUNDLE_ID="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$APP/Info.plist")"
log "Installing $BUNDLE_ID"
xcrun simctl install "$UDID" "$APP"
xcrun simctl launch "$UDID" "$BUNDLE_ID" >/dev/null

log "Launched. Streaming logs (Ctrl-C to stop)"
PROCESS="$(basename "$APP" .app)"
exec xcrun simctl spawn "$UDID" log stream \
    --level=info --style=compact \
    --predicate "process == \"$PROCESS\""
