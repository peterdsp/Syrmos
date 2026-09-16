#!/usr/bin/env bash
# Phase Z visual-baseline capture harness (iOS).
#
# Makes a capture reproducible, which is the whole point: a screenshot taken
# against the wall clock, a live API and whatever state the last run left behind
# can never be diffed against an approved baseline.
#
# What it pins:
#   clock     SYRMOS_CAPTURE_NOW is read once by SyrmosClock and verified from a
#             receipt the app writes into its container, so a launch where the
#             environment never arrived fails loudly instead of silently
#             producing wall-clock pixels.
#   state     the app is uninstalled and reinstalled, so no saved journey,
#             reminder or GO session leaks in from an earlier run.
#   location  pre-granted and set to a fixed point, so nothing blocks on a
#             permission dialog and the map is not wherever the host happens to be.
#   chrome    status bar frozen (time, full bars, charged battery) and UI
#             animations disabled, so no frame is caught mid-transition.
#   theme     light or dark, set explicitly rather than inherited.
#
# usage:
#   scripts/capture-baselines.sh setup  [--device NAME] [--theme light|dark] [--at ISO8601]
#   scripts/capture-baselines.sh shot   <screen> <label>
#   scripts/capture-baselines.sh theme  <light|dark>
#   scripts/capture-baselines.sh teardown
set -euo pipefail

REPO=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
OUT="$REPO/docs/screenshots/release-3.0.0"
STATE="${TMPDIR:-/tmp}/syrmos-capture.env"

DEVICE=${SYRMOS_CAPTURE_DEVICE:-iPhone 17}
THEME=${SYRMOS_CAPTURE_THEME:-light}
LOCALE=${SYRMOS_CAPTURE_LOCALE:-en}
# A weekday mid-morning inside Athens service hours, so departures, countdowns
# and "leave now" plans all have real content. An overnight instant renders the
# same screens empty and makes a useless baseline.
AT=${SYRMOS_CAPTURE_AT:-2026-09-16T08:42:00+03:00}
# Live data moves under the app between runs even with the clock pinned, so the
# default capture renders from the bundled seed. Set to 0 to capture the online
# variants, accepting that they are not diffable until recorded fixtures exist.
OFFLINE=${SYRMOS_CAPTURE_OFFLINE:-1}
SETTLE=${SYRMOS_CAPTURE_SETTLE:-12}
# Geometry label in the file name, matching the cells in RELEASE-3.0.0-GATE.md.
# C402 is the iPhone 17 content rect; the gate calls this bucket C390.
WINDOW=${SYRMOS_CAPTURE_WINDOW:-C402}
FONT=${SYRMOS_CAPTURE_FONT:-default}
# Syntagma. Central, on several lines, so "near me" and the map have content.
LAT=${SYRMOS_CAPTURE_LAT:-37.9755}
LON=${SYRMOS_CAPTURE_LON:-23.7348}
APP=${SYRMOS_CAPTURE_APP:-}
# Read from the bundle being installed rather than hardcoded, so a rename cannot
# leave the harness silently driving a stale identifier.
BUNDLE=${SYRMOS_CAPTURE_BUNDLE:-}
# Must match kWhatsNewCurrentVersion in Features/WhatsNew/WhatsNewView.swift.
WHATS_NEW=${SYRMOS_CAPTURE_WHATS_NEW:-2.0.0}

die() { printf 'capture: %s\n' "$*" >&2; exit 1; }
say() { printf '\033[36m▌ capture\033[0m  %s\n' "$*"; }

udid_for() {
  xcrun simctl list devices available -j \
    | python3 -c '
import json,sys
want = sys.argv[1]
data = json.load(sys.stdin)["devices"]
for runtime, devices in data.items():
    for d in devices:
        if d["name"] == want:
            print(d["udid"]); raise SystemExit
raise SystemExit("no available simulator named %r" % want)
' "$1"
}

cmd_setup() {
  while [ $# -gt 0 ]; do
    case $1 in
      --device) DEVICE=$2; shift 2 ;;
      --theme)  THEME=$2;  shift 2 ;;
      --at)     AT=$2;     shift 2 ;;
      --locale) LOCALE=$2; shift 2 ;;
      --app)    APP=$2;    shift 2 ;;
      --online) OFFLINE=0; shift ;;
      *) die "unknown option $1" ;;
    esac
  done
  [ -n "$APP" ] || die "--app <path to Syrmos.app> is required (build it first)"
  [ -d "$APP" ] || die "no app bundle at $APP"

  if [ -z "$BUNDLE" ]; then
    BUNDLE=$(/usr/libexec/PlistBuddy -c "Print :CFBundleIdentifier" "$APP/Info.plist") \
      || die "cannot read CFBundleIdentifier from $APP/Info.plist"
  fi

  local udid; udid=$(udid_for "$DEVICE")
  say "device $DEVICE ($udid), bundle $BUNDLE"

  xcrun simctl boot "$udid" 2>/dev/null || true
  xcrun simctl bootstatus "$udid" -b >/dev/null 2>&1 || true

  # Wipe prior state so the run does not inherit saved journeys or reminders.
  # Uninstalling is not enough: saved journeys, the leave-by board and the widget
  # bridge live in the shared App Group, which survives an uninstall, so a
  # journey saved during ordinary use would otherwise show up in every baseline.
  local group
  group=$(xcrun simctl get_app_container "$udid" "$BUNDLE" groups 2>/dev/null | awk '{print $2}' | head -1 || true)
  xcrun simctl uninstall "$udid" "$BUNDLE" 2>/dev/null || true
  if [ -n "${group:-}" ] && [ -d "$group" ]; then
    rm -rf "${group:?}/Library/Preferences" "${group:?}/Documents" 2>/dev/null || true
    say "cleared shared App Group state"
  fi
  xcrun simctl install "$udid" "$APP"

  # A notification prompt that was never answered is re-presented by SpringBoard
  # on every reinstall and would then sit on top of every capture. The app skips
  # the request under a pinned clock, so this only clears a prompt inherited from
  # an ordinary run of the app on this simulator.
  xcrun simctl privacy "$udid" reset all "$BUNDLE" 2>/dev/null || true
  xcrun simctl privacy "$udid" grant location-always "$BUNDLE" 2>/dev/null || true
  xcrun simctl location "$udid" set "$LAT,$LON"
  xcrun simctl ui "$udid" appearance "$THEME"
  # The status bar is chrome, not content: freeze it so it never diffs.
  xcrun simctl status_bar "$udid" override \
    --time "09:41" --dataNetwork wifi --wifiMode active --wifiBars 3 \
    --cellularMode active --cellularBars 4 --batteryState charged --batteryLevel 100
  xcrun simctl spawn "$udid" defaults write com.apple.UIKit UIAnimationDragCoefficient -float 0 2>/dev/null || true

  # Uninstalling does not clear UserDefaults. cfprefsd keeps the domain in
  # memory and serves it to the reinstalled app, so saved journeys and leave-by
  # reminders from ordinary use reappear in a supposedly clean run. Going through
  # `defaults` talks to cfprefsd itself, which editing the plist on disk does not.
  xcrun simctl spawn "$udid" defaults delete "$BUNDLE" >/dev/null 2>&1 || true

  # Onboarding and the "What's new" sheet are first-run flows, not screens under
  # test, and each covers the first screen. Marked as already seen.
  xcrun simctl spawn "$udid" defaults write "$BUNDLE" syrmos.onboarding.completed.v1 -bool true
  xcrun simctl spawn "$udid" defaults write "$BUNDLE" syrmos.whatsnew.version -string "$WHATS_NEW"

  say "launching with clock pinned to $AT (offline=$OFFLINE)"
  SIMCTL_CHILD_SYRMOS_CAPTURE_NOW="$AT" \
  SIMCTL_CHILD_SYRMOS_CAPTURE_OFFLINE="$OFFLINE" \
    xcrun simctl launch "$udid" "$BUNDLE" >/dev/null

  # Verify the pin actually reached the app rather than assuming it did.
  local container receipt seen
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    container=$(xcrun simctl get_app_container "$udid" "$BUNDLE" data 2>/dev/null || true)
    receipt="$container/Documents/capture-clock.txt"
    [ -n "$container" ] && [ -f "$receipt" ] && break
    sleep 1
  done
  [ -f "$receipt" ] || die "the app never wrote a capture receipt: the clock is NOT pinned, so nothing captured now is a baseline"
  seen=$(cat "$receipt")
  say "receipt says $seen"

  # Let the first frames settle. Data loads, the offline banner resolves and
  # entrance transitions finish over the first few seconds; capturing inside that
  # window produces a different image every run even with everything else pinned.
  say "settling for ${SETTLE}s"
  sleep "$SETTLE"

  # Quoted: device names contain spaces, and this file is sourced.
  cat > "$STATE" <<EOF
UDID="$udid"
DEVICE="$DEVICE"
OFFLINE="$OFFLINE"
BUNDLE="$BUNDLE"
THEME="$THEME"
LOCALE="$LOCALE"
AT="$seen"
EOF
  say "ready. capture with: $0 shot <screen> <label>"
}

load_state() {
  [ -f "$STATE" ] || die "run '$0 setup' first"
  # shellcheck disable=SC1090
  . "$STATE"
}

cmd_theme() {
  load_state
  xcrun simctl ui "$UDID" appearance "$1"
  sed -i '' "s/^THEME=.*/THEME=\"$1\"/" "$STATE"
  say "theme $1"
}

cmd_shot() {
  load_state
  local screen=$1 label=${2:-}
  local dir="$OUT/ios"; mkdir -p "$dir"
  local name="${screen}__${WINDOW}__${THEME}__${LOCALE}__${FONT}.png"
  xcrun simctl io "$UDID" screenshot --type=png "$dir/$name" >/dev/null 2>&1
  local manifest="$OUT/manifest.tsv"
  [ -f "$manifest" ] || printf 'platform\tos\tviewport\tscale\tlocale\ttheme\tfont\tfixture_rev\tcommit\tscreen\tlabel\tfile\n' > "$manifest"
  local os; os=$(xcrun simctl list devices -j | python3 -c '
import json,sys
udid=sys.argv[1]
for runtime, devices in json.load(sys.stdin)["devices"].items():
    for d in devices:
        if d["udid"] == udid:
            print(runtime.rsplit(".",1)[-1].replace("-", " ")); raise SystemExit
print("unknown")
' "$UDID")
  printf 'ios\t%s (%s)\t%s\t3x\t%s\t%s\t%s\tclock=%s offline=%s\t%s\t%s\t%s\tios/%s\n' \
    "$os" "$DEVICE" "$WINDOW" "$LOCALE" "$THEME" "$FONT" "$AT" "${OFFLINE:-1}" \
    "$(git -C "$REPO" rev-parse --short HEAD)" "$screen" "$label" "$name" >> "$manifest"
  say "captured $name"
}

cmd_teardown() {
  load_state
  xcrun simctl status_bar "$UDID" clear || true
  xcrun simctl ui "$UDID" appearance light || true
  rm -f "$STATE"
  say "cleared status-bar override and capture state"
}

case ${1:-} in
  setup)    shift; cmd_setup "$@" ;;
  shot)     shift; [ $# -ge 1 ] || die "usage: $0 shot <screen> [label]"; cmd_shot "$@" ;;
  theme)    shift; [ $# -eq 1 ] || die "usage: $0 theme <light|dark>"; cmd_theme "$@" ;;
  teardown) shift; cmd_teardown ;;
  *) sed -n '2,30p' "$0"; exit 1 ;;
esac
