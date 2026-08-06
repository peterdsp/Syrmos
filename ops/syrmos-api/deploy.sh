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
  --exclude admin.env \
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
.venv/bin/python -m scripts.seed_fare_products
.venv/bin/python -m scripts.seed_station_offsets
.venv/bin/python -m syrmos_admin.scraper_24mmm || echo "scraper failed, continuing"
.venv/bin/python -m syrmos_admin.scraper_stasy_announcements || echo "STASY scraper failed, continuing"
.venv/bin/python -m syrmos_admin.scraper_hellenic_train || echo "Hellenic Train scraper failed, continuing"
.venv/bin/python -m syrmos_admin.scraper_ht_important_info || echo "Hellenic Train alerts scraper failed, continuing"
.venv/bin/python -m syrmos_admin.scraper_oseth || echo "OSETH scraper failed, continuing"
.venv/bin/python -m syrmos_admin.scraper_thessmetro || echo "Thessaloniki Metro scraper failed, continuing"
.venv/bin/python -m syrmos_admin.generator
REMOTE

echo ">>> installing systemd units"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-admin.service /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-scraper-24mmm.service /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-scraper-24mmm.timer /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-backup.service /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-backup.timer /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-seed-daily.service /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-seed-daily.timer /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-osm-shapes.service /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-osm-shapes.timer /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-scraper-stasy.service /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-scraper-stasy.timer /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-scraper-hellenic-train.service /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-scraper-hellenic-train.timer /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-scraper-ht-important-info.service /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-scraper-ht-important-info.timer /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-scraper-oseth.service /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-scraper-oseth.timer /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-scraper-thessmetro.service /etc/systemd/system/"
ssh "$PI" "sudo cp ~/syrmos-api/systemd/syrmos-scraper-thessmetro.timer /etc/systemd/system/"
ssh "$PI" "sudo systemctl daemon-reload"
ssh "$PI" "sudo systemctl enable --now syrmos-admin.service"
ssh "$PI" "sudo systemctl enable --now syrmos-scraper-24mmm.timer"
ssh "$PI" "sudo systemctl enable --now syrmos-backup.timer"
ssh "$PI" "sudo systemctl enable --now syrmos-seed-daily.timer"
ssh "$PI" "sudo systemctl enable --now syrmos-osm-shapes.timer"
ssh "$PI" "sudo systemctl enable --now syrmos-scraper-stasy.timer"
ssh "$PI" "sudo systemctl enable --now syrmos-scraper-hellenic-train.timer"
ssh "$PI" "sudo systemctl enable --now syrmos-scraper-ht-important-info.timer"
ssh "$PI" "sudo systemctl enable --now syrmos-scraper-oseth.timer"
ssh "$PI" "sudo systemctl enable --now syrmos-scraper-thessmetro.timer"

echo ">>> patching ~/syrmos-proxy/nginx.conf with dynamic API routes + reloading nginx"
ssh "$PI" bash <<'REMOTE'
set -e
conf=~/syrmos-proxy/nginx.conf
if ! grep -q "/api/departures/next" "$conf"; then
  cp "$conf" "$conf.bak.$(date +%s)"
  python3 - <<'PY'
from pathlib import Path
p = Path.home() / "syrmos-proxy/nginx.conf"
txt = p.read_text()
marker = "# --- admin UI (FastAPI"
block = """        # --- server-side projector: next departures per station ---
        location = /api/departures/next {
            proxy_pass http://127.0.0.1:8092/api/departures/next$is_args$args;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-Proto $scheme;
            add_header Access-Control-Allow-Origin "*" always;
            add_header Cache-Control "no-store" always;
        }

        """
p.write_text(txt.replace(marker, block + marker, 1))
print("patched departures")
PY
fi
if ! grep -q "/api/ariadne/chat" "$conf"; then
  cp "$conf" "$conf.bak.ariadne.$(date +%s)"
  python3 - <<'PY'
from pathlib import Path
p = Path.home() / "syrmos-proxy/nginx.conf"
txt = p.read_text()
marker = "# --- admin UI (FastAPI"
block = """        # --- Ariadne LLM chat (proxied to FastAPI) ---
        location = /api/ariadne/chat {
            proxy_pass http://127.0.0.1:8092/api/ariadne/chat;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_set_header Content-Type $content_type;
            add_header Access-Control-Allow-Origin "*" always;
            add_header Access-Control-Allow-Methods "POST, OPTIONS" always;
            add_header Access-Control-Allow-Headers "Content-Type" always;
            add_header Cache-Control "no-store" always;
            proxy_read_timeout 30s;
            if ($request_method = OPTIONS) {
                return 204;
            }
        }

        """
p.write_text(txt.replace(marker, block + marker, 1))
print("patched ariadne")
PY
fi
if ! grep -q "/api/community/summary" "$conf"; then
  cp "$conf" "$conf.bak.community.$(date +%s)"
  python3 - <<'PY'
from pathlib import Path
p = Path.home() / "syrmos-proxy/nginx.conf"
txt = p.read_text()
marker = "# --- admin UI (FastAPI"
block = """        # --- anonymous Ichnos community reporting ---
        location = /api/community/reports {
            proxy_pass http://127.0.0.1:8092/api/community/reports;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_set_header Content-Type $content_type;
            add_header Access-Control-Allow-Origin "*" always;
            add_header Access-Control-Allow-Methods "POST, OPTIONS" always;
            add_header Access-Control-Allow-Headers "Content-Type" always;
            add_header Cache-Control "no-store" always;
            if ($request_method = OPTIONS) { return 204; }
        }

        location ~ ^/api/community/reports/([A-Za-z0-9_-]+)$ {
            proxy_pass http://127.0.0.1:8092/api/community/reports/$1;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            add_header Access-Control-Allow-Origin "*" always;
            add_header Access-Control-Allow-Methods "DELETE, OPTIONS" always;
            add_header Cache-Control "no-store" always;
            if ($request_method = OPTIONS) { return 204; }
        }

        location = /api/community/summary {
            proxy_pass http://127.0.0.1:8092/api/community/summary$is_args$args;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            add_header Access-Control-Allow-Origin "*" always;
            add_header Cache-Control "no-store" always;
        }

        """
p.write_text(txt.replace(marker, block + marker, 1))
print("patched community reporting")
PY
fi
/usr/sbin/nginx -t -c "$conf" -p ~/syrmos-proxy/
pid=$(cat ~/syrmos-proxy/nginx.pid 2>/dev/null || true)
if [ -n "$pid" ]; then
  kill -HUP "$pid"
  echo "nginx HUP sent to pid $pid"
fi
REMOTE

echo ">>> restarting admin service"
ssh "$PI" "sudo systemctl restart syrmos-admin.service"

echo "done"
