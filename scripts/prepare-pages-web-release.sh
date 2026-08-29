#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_DIR="${1:-$ROOT_DIR/composeApp/build/web-release}"
TARGET_DIR="${2:-$ROOT_DIR/composeApp/build/github-pages}"

rm -rf "$TARGET_DIR"
mkdir -p "$TARGET_DIR"
cp -R "$SOURCE_DIR"/. "$TARGET_DIR"/

version_asset() {
    local basename="$1"
    local path="$TARGET_DIR/$basename"

    if [[ ! -f "$path" ]]; then
        return
    fi

    local extension="${basename##*.}"
    local stem="${basename%.*}"
    local hash
    hash="$(shasum -a 256 "$path" | awk '{print substr($1,1,12)}')"
    local versioned="${stem}.${hash}.${extension}"

    mv "$path" "$TARGET_DIR/$versioned"
    perl -0pi -e "s/\Q$basename\E(?:\\?v=[^\"']+)?/$versioned/g" "$TARGET_DIR/index.html"
}

version_asset "composeApp.js"
version_asset "web-map.js"
version_asset "web-map.css"

# --- Static workspace entry points (GitHub Pages has no SPA fallback) ---------
# Each workspace is a directory whose index.html is a byte-for-byte copy of the
# finished root document. Because every asset reference in index.html is
# root-absolute, each copy loads the exact same (content-hashed) assets, and the
# in-page router (web-router.js) restores the workspace from the path on load.
# The result is real HTTP 200 deep links, reloads and shareable URLs with no
# SPA-fallback dependency and no <base href> (which would break the inline SVG
# <use href="#ic-..."> icons). Dynamic identifiers travel as query parameters
# (e.g. /line/?id=M3), so every route is a single fixed directory rather than an
# unbounded path. See docs/adr/0001-web-url-model.md.
for route in now plan explore departures tickets line station; do
    mkdir -p "$TARGET_DIR/$route"
    cp "$TARGET_DIR/index.html" "$TARGET_DIR/$route/index.html"
done

# 404.html is recovery only. It lets a mistyped or stale deep path still boot
# the app and route client-side, but the canonical entry points above already
# return 200, so a custom 404 (which stays an HTTP 404 on GitHub Pages) is never
# the primary routing mechanism.
cp "$TARGET_DIR/index.html" "$TARGET_DIR/404.html"

if [[ -f "$ROOT_DIR/docs/press.html" ]]; then
    cp "$ROOT_DIR/docs/press.html" "$TARGET_DIR/press.html"
    mkdir -p "$TARGET_DIR/assets" "$TARGET_DIR/screenshots"
    cp "$ROOT_DIR"/docs/assets/*.png "$TARGET_DIR/assets/" 2>/dev/null || true
    cp "$ROOT_DIR"/docs/assets/*.zip "$TARGET_DIR/assets/" 2>/dev/null || true
    cp "$ROOT_DIR"/docs/screenshots/*.png "$TARGET_DIR/screenshots/" 2>/dev/null || true
fi
