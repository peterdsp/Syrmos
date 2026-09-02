#!/bin/zsh
# Syrmos autonomous next-release watchdog.
#
# Purpose: keep the long-horizon autonomous session alive until 20:00 Europe/Athens by resuming the same
# named Claude Code session whenever it exits, is compacted, is rate-limited, or the CLI crashes. Claude is
# the worker; this watchdog owns the deadline. Claude itself cannot outlive a hard usage/session stop, so
# this loop restarts it when capacity returns.
#
# One-time setup, done by a human in the interactive Claude Code session you want to keep alive:
#   /rename syrmos-next-release-autonomy
# (this names the persisted session so --resume can find it)
#
# Run this watchdog from a SEPARATE terminal, and keep the Mac awake:
#   caffeinate -dimsu /Users/peterdsp/git/Syrmos/scripts/autonomous-watchdog.sh
#
# It is deliberately conservative: it never forces changes, only resumes the session with an auto permission
# mode and a resume prompt. All safety boundaries still live inside the session's own policy.

set -u

export TZ="Europe/Athens"

REPO="/Users/peterdsp/git/Syrmos"
SESSION="syrmos-next-release-autonomy"

# Compute today's 20:00 Athens as an epoch (BSD date on macOS).
END_EPOCH=$(TZ=Europe/Athens date -j -f "%Y-%m-%d %H:%M:%S" \
  "$(TZ=Europe/Athens date '+%Y-%m-%d') 20:00:00" "+%s")

cd "$REPO" || { echo "repo not found: $REPO"; exit 1; }

RESUME_PROMPT='Resume the autonomous Syrmos next-major-release (3.0 Journeys) mission.
First run: TZ=Europe/Athens date. Then read docs/ops/NEXT_RELEASE_SESSION_STATE.md, run git status, git log
-5, gh pr list, gh run list. Reconcile the saved state with reality (git/CI/PRs are the truth, not the file).
If Athens time is before 20:00, continue from the highest-value unfinished task. Do NOT summarize and stop,
and do NOT produce a FINAL report, just because the session was resumed. Persist updated state before you
return.'

while [ "$(date +%s)" -lt "$END_EPOCH" ]; do
  echo "[$(TZ=Europe/Athens date '+%Y-%m-%d %H:%M:%S %Z')] resuming session '$SESSION'"

  claude \
    --resume "$SESSION" \
    --permission-mode auto \
    -p "$RESUME_PROMPT"
  EXIT_CODE=$?

  # Deadline reached during the run: stop.
  if [ "$(date +%s)" -ge "$END_EPOCH" ]; then
    break
  fi

  if [ "$EXIT_CODE" -ne 0 ]; then
    # Unavailable / crashed / rate-limited: back off before retry so we do not hammer an unavailable API.
    echo "[$(TZ=Europe/Athens date '+%H:%M:%S')] claude exited $EXIT_CODE; backing off 10m"
    sleep 600
  else
    # Returned normally before 20:00: resume again rather than treating that as completion.
    echo "[$(TZ=Europe/Athens date '+%H:%M:%S')] claude returned normally; re-resuming in 30s"
    sleep 30
  fi
done

echo "[$(TZ=Europe/Athens date '+%Y-%m-%d %H:%M:%S %Z')] 20:00 Athens reached. Autonomous window complete."
