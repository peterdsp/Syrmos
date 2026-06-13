#!/usr/bin/env bash
# Deploy syrmos-api (schedules layer) to the Pi.
# Idempotent: re-running upgrades in place. Run from your laptop.
set -euo pipefail

PI=${PI:-peterdsp@192.168.10.10}
REMOTE_HOME=/home/peterdsp/syrmos-api
HERE=$(cd "$(dirname "$0")" && pwd)
REPO_ROOT=$(cd "$HERE/../.." && pwd)

echo ">>> syncing code to $PI"
rsync -avz --delete \
  --exclude data \
  --exclude db \
  --exclude out \
  --exclude backups \
  --exclude .venv \
  --exclude '*.log' \
  --exclude __pycache__ \
  --exclude '*.pyc' \
  "$HERE"/ "$PI:$REMOTE_HOME"/

echo ">>> syncing icon package to $PI"
ssh "$PI" "mkdir -p $REMOTE_HOME/assets/icons"
rsync -avz --delete \
  "$REPO_ROOT/assets/athens-transit-package/icons"/ "$PI:$REMOTE_HOME/assets/icons"/

echo ">>> syncing OSM line-geometry to $PI"
ssh "$PI" "mkdir -p $REMOTE_HOME/assets/line-geometry"
rsync -avz --delete \
  "$REPO_ROOT/assets/line-geometry"/ "$PI:$REMOTE_HOME/assets/line-geometry"/

echo ">>> installing venv + deps"
ssh "$PI" bash <<'REMOTE'
set -euo pipefail
cd ~/syrmos-api
export SYRMOS_DB_PATH=/home/peterdsp/syrmos-api/db/syrmos.db
export SYRMOS_API_OUT_DIR=/home/peterdsp/syrmos-api/out
if [ ! -d .venv ]; then
  /usr/bin/python3 -m venv .venv
fi
.venv/bin/pip install --quiet --upgrade pip
.venv/bin/pip install --quiet -r requirements.txt
mkdir -p db out backups
.venv/bin/python -m syrmos_admin.db
REMOTE

echo ">>> running importer + generator (idempotent)"
ssh "$PI" bash <<'REMOTE'
set -euo pipefail
cd ~/syrmos-api
export SYRMOS_DB_PATH=/home/peterdsp/syrmos-api/db/syrmos.db
export SYRMOS_API_OUT_DIR=/home/peterdsp/syrmos-api/out
.venv/bin/python -m scripts.import_athens_package --apply
.venv/bin/python -m scripts.import_icons --apply
.venv/bin/python -m syrmos_admin.scraper_24mmm || echo "scraper failed, continuing"
.venv/bin/python -m syrmos_admin.generator
REMOTE

echo ">>> installing systemd units"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-admin.service /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-scraper-24mmm.service /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-scraper-24mmm.timer /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-backup.service /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-backup.timer /etc/systemd/system/"
ssh "$PI" "sudo systemctl daemon-reload"
ssh "$PI" "sudo systemctl enable --now syrmos-admin.service"
ssh "$PI" "sudo systemctl enable --now syrmos-scraper-24mmm.timer"
ssh "$PI" "sudo systemctl enable --now syrmos-backup.timer"

echo ">>> reminder: merge nginx.locations.conf into ~/syrmos-proxy/nginx.conf and reload nginx"
echo "    ssh $PI 'sudo systemctl reload syrmos-static.service'"

echo "done"
