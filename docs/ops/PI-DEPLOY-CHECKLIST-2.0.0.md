# Syrmos 2.0.0 server (Pi) deploy + verification checklist

The rider-facing server correctness fixes for 2.0.0 are merged and test-verified
but are **not yet live**: they take effect only when the Pi at
`peterdsp@192.168.10.10` is redeployed. The sandbox can reach the Pi's SSH port
but the environment safety layer blocks SSH to the production server, so the
deploy itself is a human step from the LAN. This checklist makes that step
deterministic: run it and the fixes go live with the exact before/after
assertions below, no further investigation.

## Fixes waiting on this deploy

| Item | What | Merged in | Production today |
|---|---|---|---|
| #12 | Short-turn last-train destination override (M1 00:30 -> Omonia, not Kifissia) | #80, #83 | WRONG: shows the line terminal |
| #9  | Peania-Kantza / Koropi phantom "towards Doukissis Plakentias" rows | #89 | WRONG: phantom rows on Android/Web |
| #9  | Server rounding unified to half-up (matches all clients) | #89 | <=1 min drift vs the apps |
| deploy | `deploy.sh` now runs the last-train scraper (was orphaned) | this PR | table stays empty without it |

## Pre-deploy

1. Be on the LAN with SSH access to `peterdsp@192.168.10.10` (key `~/.ssh/syrmos_pi_ed25519`).
2. `git pull` on the deploy source so `ops/syrmos-api` is at the 2.0.0 tip
   (projector.py with the #83/#89 fixes; deploy.sh with the last-train scraper).
3. A DB backup exists / will be taken: `deploy.sh` runs on top of
   `db/syrmos.db`, and `syrmos-backup.service/.timer` snapshots to `db`/`backups`.
   Optionally snapshot first: `ssh PI 'cp ~/syrmos-api/db/syrmos.db ~/syrmos-api/backups/pre-2.0.0.db'`.

## Deploy

Run the repository's own idempotent deploy (re-runnable, upgrades in place):

```bash
cd ops/syrmos-api && ./deploy.sh
```

It: rsyncs code -> Pi; installs the venv/deps; **applies migrations
automatically** (`db.migrate` runs every `migrations/*.sql`, all
`CREATE TABLE IF NOT EXISTS`, so ordering/idempotency are safe and 0017
`last_train_endpoints` is created if missing); runs the importer + all scrapers
**including the newly added `scripts/scrape-stasy-last-trains.py`** (best-effort:
a stasy.gr failure is non-fatal, like the other scrapers); runs the generator to
rebuild the per-line bundles from the DB; installs the systemd units; and
restarts the service.

- **Migration ordering / idempotency**: handled by `db.migrate` (applies all
  `*.sql` in order, each `IF NOT EXISTS`). No manual migration step.
- **Scraper compatibility**: `scrape-stasy-last-trains.py` reads `SYRMOS_DB_PATH`
  and inserts into `last_train_endpoints`; it depends on stasy.gr's timetable
  page layout. If STASY changed the page it logs and the deploy continues (the
  override then stays a no-op rather than serving wrong data).
- **Service restart**: via the systemd units the deploy copies; confirm
  `syrmos-admin.service` is active after.
- **Rollback**: restore `db/backups/<snapshot>.db` over `db/syrmos.db` and
  restart, or redeploy the previous commit. The projector code changes are
  backward-safe (empty `last_train_endpoints` -> the pre-fix behaviour).

## Post-deploy verification (public endpoints, no SSH needed)

Health:
```bash
curl -s https://api-syrmos.peterdsp.dev/healthz          # expect {"ok":true}
```

### #12 short-turn destinations

```
BEFORE (current production)
  GET /api/schedules/M1                       -> lastTrains: []  (count 0)
  GET /api/departures/next?stationId=M1_PIR&lineIds=M1&direction=outbound&now=<late-night>
                                              -> last train direction == "Kifissia"

AFTER (assertions the deploy must make true)
  GET /api/schedules/M1                       -> lastTrains count > 0
  the M1 short-turn slot's direction          == its real short-turn terminal
                                                 (e.g. "Omonia"), NOT "Kifissia"
```

Check:
```bash
curl -s "https://api-syrmos.peterdsp.dev/api/schedules/M1" | python3 -c "import sys,json;print('lastTrains',len(json.load(sys.stdin).get('lastTrains',[])))"
# expect a number > 0 after deploy (0 before)
```

### #9 phantom rows (Peania-Kantza / Koropi)

```
BEFORE  GET /api/departures/next?stationId=M3_PEA&lineIds=M3
          -> includes frequent "towards Doukissis Plakentias" (city) rows
AFTER   same query -> only airport (M3_AIR) trains; no city phantom rows
```

### #9 rounding

```
For a half-integer-headway slot the returned HH:MM is half-up
(matches the iOS/Android/Web apps to the minute).
```

## Done criteria

The server deploy is complete when: `/healthz` is `{"ok":true}`, `M1` bundle
`lastTrains` count > 0, the M1 short-turn slot reports its real terminus, and
Peania-Kantza/Koropi show no phantom city rows. Until then, **live production
transport correctness for these items is FAIL**, independent of the merged +
tested code.
