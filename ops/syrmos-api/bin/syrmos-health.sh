#!/usr/bin/env bash
#
# syrmos-health.sh — self-healing watchdog for the Syrmos API.
#
# Invoked as root by syrmos-health.timer (every ~2 min). It probes the local
# FastAPI health endpoint and, when the API is unreachable or unhealthy,
# restarts the service — clearing any systemd start-limit lockout first — and
# escalates to the Cloudflare tunnel if the API stays down. Every remediation
# is rate-limited (so a genuinely broken deploy is not restarted forever) and
# appended to a log.
#
# Context: on 2026-08-18 the API went to HTTP 500 and stayed down ~9 days even
# though syrmos-admin.service has Restart=always. Restart=always does NOT cover
# (a) a crash-loop that exhausts systemd's start limit and parks the unit as
# 'failed', nor (b) a process that stays alive while every route returns 500.
# This watchdog covers both.
#
set -uo pipefail

HEALTH_URL="${SYRMOS_HEALTH_URL:-http://127.0.0.1:8092/healthz}"
API_SERVICE="${SYRMOS_API_SERVICE:-syrmos-admin.service}"
TUNNEL_SERVICE="${SYRMOS_TUNNEL_SERVICE:-cloudflared-syrmos.service}"
LOG="${SYRMOS_HEALTH_LOG:-/home/peterdsp/syrmos-api/health.log}"
STATE_DIR="/run/syrmos-health"
FAIL_FILE="$STATE_DIR/consecutive_fails"
STAMP_FILE="$STATE_DIR/restart_stamps"   # epoch seconds, one per restart
PROBES=3                                  # probe attempts per run
PROBE_TIMEOUT=5                           # seconds per probe
TUNNEL_AFTER=2                            # unhealthy runs before bouncing tunnel
MAX_RESTARTS_PER_HOUR=6                   # safety cap on remediation

mkdir -p "$STATE_DIR"
ts()  { date '+%Y-%m-%dT%H:%M:%S%z'; }
log() { printf '%s %s\n' "$(ts)" "$*" >> "$LOG" 2>/dev/null || true; }

probe() {
  # Return 0 only on HTTP 200 from /healthz.
  local code
  code=$(curl -fsS -o /dev/null -m "$PROBE_TIMEOUT" -w '%{http_code}' "$HEALTH_URL" 2>/dev/null) || return 1
  [ "$code" = "200" ]
}

# --- health probe ----------------------------------------------------------
healthy=0
for _ in $(seq 1 "$PROBES"); do
  if probe; then healthy=1; break; fi
  sleep 2
done

if [ "$healthy" = "1" ]; then
  if [ "$(cat "$FAIL_FILE" 2>/dev/null || echo 0)" != "0" ]; then
    log "recovered — API healthy again"
  fi
  echo 0 > "$FAIL_FILE"
  exit 0
fi

# --- unhealthy: rate-limit remediation -------------------------------------
fails=$(( $(cat "$FAIL_FILE" 2>/dev/null || echo 0) + 1 ))
echo "$fails" > "$FAIL_FILE"

now=$(date +%s)
if [ -f "$STAMP_FILE" ]; then
  awk -v cutoff="$((now - 3600))" '$1 ~ /^[0-9]+$/ && $1 >= cutoff' \
    "$STAMP_FILE" > "$STAMP_FILE.tmp" 2>/dev/null && mv "$STAMP_FILE.tmp" "$STAMP_FILE"
fi
recent=$(wc -l < "$STAMP_FILE" 2>/dev/null | tr -d ' '); recent=${recent:-0}

log "UNHEALTHY ($HEALTH_URL) consecutive=$fails restarts_last_hour=$recent"

if [ "$recent" -ge "$MAX_RESTARTS_PER_HOUR" ]; then
  log "restart budget exhausted ($recent/$MAX_RESTARTS_PER_HOUR this hour) — NOT restarting; needs manual attention"
  exit 1
fi

# --- heal: clear start-limit lockout, then restart the API -----------------
echo "$now" >> "$STAMP_FILE"
log "restarting $API_SERVICE"
systemctl reset-failed "$API_SERVICE" 2>/dev/null || true
systemctl restart    "$API_SERVICE" 2>>"$LOG" || log "restart returned non-zero"

sleep 8
if probe; then
  log "restart succeeded — API healthy"
  echo 0 > "$FAIL_FILE"
  exit 0
fi

# --- escalate to the Cloudflare tunnel after repeated failures -------------
if [ "$fails" -ge "$TUNNEL_AFTER" ]; then
  log "still unhealthy after restart — bouncing $TUNNEL_SERVICE"
  systemctl reset-failed "$TUNNEL_SERVICE" 2>/dev/null || true
  systemctl restart    "$TUNNEL_SERVICE" 2>>"$LOG" || log "tunnel restart returned non-zero"
  sleep 5
  if probe; then
    log "healthy after tunnel restart"
    echo 0 > "$FAIL_FILE"
    exit 0
  fi
fi

log "still UNHEALTHY after remediation — will retry next cycle"
exit 1
