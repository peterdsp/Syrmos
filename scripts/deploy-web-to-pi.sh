#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REMOTE_HOST="${REMOTE_HOST:-peterdsp@192.168.10.10}"
REMOTE_DIR="${REMOTE_DIR:-/home/peterdsp/syrmos-web}"
STAGE_DIR="$ROOT_DIR/composeApp/build/web-release"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

cd "$ROOT_DIR"

./gradlew :composeApp:stageWebRelease

cp -R "$STAGE_DIR"/. "$TMP_DIR"/
cp "$ROOT_DIR/ops/syrmos-web/nginx.conf" "$TMP_DIR/nginx.conf"

version_asset() {
    local basename="$1"
    local path="$TMP_DIR/$basename"

    if [[ ! -f "$path" ]]; then
        return
    fi

    local extension="${basename##*.}"
    local stem="${basename%.*}"
    local hash
    hash="$(shasum -a 256 "$path" | awk '{print substr($1,1,12)}')"
    local versioned="${stem}.${hash}.${extension}"

    mv "$path" "$TMP_DIR/$versioned"
    perl -0pi -e "s/\Q$basename\E(?:\\?v=[^\"']+)?/$versioned/g" "$TMP_DIR/index.html"
}

version_asset "composeApp.js"
version_asset "web-map.js"
version_asset "web-map.css"

scp -r "$TMP_DIR" "$REMOTE_HOST:${REMOTE_DIR}.next"

ssh "$REMOTE_HOST" "
set -euo pipefail
rm -rf '${REMOTE_DIR}.prev'
if [ -d '$REMOTE_DIR' ]; then mv '$REMOTE_DIR' '${REMOTE_DIR}.prev'; fi
mv '${REMOTE_DIR}.next' '$REMOTE_DIR'
find '$REMOTE_DIR' -type d -exec chmod 755 {} +
find '$REMOTE_DIR' -type f -exec chmod 644 {} +
docker restart syrmos_web >/dev/null
"

echo "Deployed Syrmos web to $REMOTE_HOST:$REMOTE_DIR"
