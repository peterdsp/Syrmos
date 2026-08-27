#!/usr/bin/env bash
# Install / refresh the Syrmos API self-healing watchdog on the Pi.
# Idempotent — safe to re-run. Run from your laptop:
#
#     ./install-selfheal.sh                 # uses the `syrmos-pi` ssh alias
#     PI=peterdsp@192.168.10.10 ./install-selfheal.sh
#
set -euo pipefail

PI=${PI:-syrmos-pi}
REMOTE_HOME=/home/peterdsp/syrmos-api
HERE=$(cd "$(dirname "$0")" && pwd)

echo ">>> syncing self-heal files to $PI"
ssh "$PI" "mkdir -p $REMOTE_HOME/bin $REMOTE_HOME/systemd/syrmos-admin.service.d"
rsync -avz "$HERE/bin/syrmos-health.sh"                               "$PI:$REMOTE_HOME/bin/"
rsync -avz "$HERE/systemd/syrmos-health.service" \
           "$HERE/systemd/syrmos-health.timer"                        "$PI:$REMOTE_HOME/systemd/"
rsync -avz "$HERE/systemd/syrmos-admin.service.d/10-selfheal.conf"    "$PI:$REMOTE_HOME/systemd/syrmos-admin.service.d/"

echo ">>> installing units + drop-in on the Pi"
ssh "$PI" bash <<'REMOTE'
set -euo pipefail
chmod +x ~/syrmos-api/bin/syrmos-health.sh
sudo cp ~/syrmos-api/systemd/syrmos-health.service /etc/systemd/system/
sudo cp ~/syrmos-api/systemd/syrmos-health.timer   /etc/systemd/system/
sudo mkdir -p /etc/systemd/system/syrmos-admin.service.d
sudo cp ~/syrmos-api/systemd/syrmos-admin.service.d/10-selfheal.conf \
        /etc/systemd/system/syrmos-admin.service.d/
sudo systemctl daemon-reload
# Bring the API back right now, clearing any parked 'failed' state.
sudo systemctl reset-failed syrmos-admin.service || true
sudo systemctl restart      syrmos-admin.service
# Arm the watchdog.
sudo systemctl enable --now syrmos-health.timer
echo "--- health timer ---"
systemctl list-timers syrmos-health.timer --no-pager | head -3
REMOTE

echo ">>> done — self-healing watchdog is armed"
