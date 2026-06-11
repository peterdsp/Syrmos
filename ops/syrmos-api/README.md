# syrmos-api: schedules layer

SQLite-backed schedules + lines + holidays API served from the Pi.
Lives at `~/syrmos-api/` on the Pi, behind nginx + Cloudflare Tunnel at
`https://api-syrmos.peterdsp.dev`.

## Live endpoints

| Path | Source | Cache |
|---|---|---|
| `/api/lines` | `out/lines.json` | 1h |
| `/api/schedules` | `out/schedules.json` | 10m |
| `/api/schedules/manifest` | `out/schedules-manifest.json` | 1m |
| `/api/schedules/{lineId}` | `out/schedules/{lineId}.json` | 10m |
| `/api/holidays` | `out/holidays.json` | 1h |
| `/api/overrides` | `out/overrides.json` | 5m |
| `/admin/` | uvicorn 127.0.0.1:8092 | no-store |

Line IDs: `M1`, `M2`, `M3`, `T6`, `T7`, `A1`, `A2`, `A3`, `A4`, `M3_AIR`.
`M3_AIR` is a virtual schedule-only line for the airport branch frequencies;
it does NOT appear in `/api/lines`.

## Layout

```
syrmos-api/
├── migrations/0001_init.sql     # DB schema
├── pkg/                          # Reference data from RULES.md + coords
├── scripts/import_athens_package.py
├── syrmos_admin/
│   ├── db.py                     # SQLite connect + migrations
│   ├── generator.py              # DB → static JSON snapshots
│   ├── scraper_24mmm.py          # OASA Saturday 24mmm scraper
│   └── app.py                    # FastAPI admin UI
├── systemd/                       # Unit files (manual sudo install)
├── nginx.locations.conf          # Drop-in for ~/syrmos-proxy/nginx.conf
└── deploy.sh                      # Idempotent rsync + venv + import
```

## Deploy

```
./deploy.sh                       # rsync + venv + DB seed + nginx patch
```

The script:
1. Syncs source to the Pi
2. Creates a venv, installs FastAPI + uvicorn
3. Runs migrations, imports the package, runs the 24mmm scraper, regenerates JSON
4. Patches `~/syrmos-proxy/nginx.conf` in place and reloads nginx (no sudo needed — pid file is owned by peterdsp)

## Remaining manual step (sudo required)

Install systemd units the first time you run deploy:

```
ssh peterdsp@192.168.10.10
sudo cp ~/syrmos-api/systemd/*.service ~/syrmos-api/systemd/*.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now syrmos-admin.service
sudo systemctl enable --now syrmos-scraper-24mmm.timer
sudo systemctl enable --now syrmos-backup.timer
```

After that, `deploy.sh` re-runs are fully unattended.

## Admin auth

Two modes. Pick one in production:

1. **Cloudflare Access** (recommended). Configure a CF Access app for
   `api-syrmos.peterdsp.dev/admin/*`, allow your email. The service reads
   `Cf-Access-Authenticated-User-Email`.
2. **Token fallback**. Set in `~/syrmos-api/admin.env`:
   ```
   SYRMOS_ADMIN_TOKEN=longrandomstring
   ```
   Then send `X-Admin-Token: longrandomstring` on every request.

## Data flow

```
import_athens_package  →  SQLite  ←  admin UI (writes)
                            ↓
                       generator.py
                            ↓
                       out/*.json
                            ↓
                      nginx + Cloudflare
                            ↓
                      iOS / Android / Web
```

Every write triggers `generator.generate()` which atomically replaces the JSON
files. ETag in the manifest body is the source of truth for client sync.

## Rollback

```
ls ~/syrmos-proxy/nginx.conf.bak.*    # backups created on every deploy
cp <backup> ~/syrmos-proxy/nginx.conf
/usr/sbin/nginx -c ~/syrmos-proxy/nginx.conf -s reload
```

DB backups land in `~/syrmos-api/backups/syrmos-YYYY-MM-DD.db`, kept 30 days
(after the systemd timer is installed).
