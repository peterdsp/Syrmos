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
bash "$ROOT_DIR/scripts/prepare-pages-web-release.sh" "$STAGE_DIR" "$TMP_DIR"
cp "$ROOT_DIR/ops/syrmos-web/nginx.conf" "$TMP_DIR/nginx.conf"

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
