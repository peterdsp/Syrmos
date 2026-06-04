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
