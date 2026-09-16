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
| `/api/departures/next` | FastAPI projector | no-store |
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
4. Patches `~/syrmos-proxy/nginx.conf` in place and reloads nginx (no sudo needed, pid file is owned by peterdsp)

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

## Announcement translation

Scraped announcements are Greek. The clients show the operator's Greek wording
when no translation exists, so a gap is visible rather than hidden, but the
point is to have translations.

Order of preference in `syrmos_admin/translation.py`:

1. **The Ariadne provider chain** (`build_chain()` in `ariadne_providers.py`):
   Groq, then Cloudflare Workers AI, then a local brain, then an optional extra
   endpoint. Same credentials, same per-provider circuit breaker. A strict
   system prompt asks for the translation alone, and the reply is rejected
   unless it survives `_clean_llm_translation` (no leftover Greek, no
   `NO_TRANSLATION`, no chatty paragraph, quotes and `Translation:` labels
   stripped). Repeated strings are translated once per process.
2. **deep-translator** (free Google, then MyMemory) as a backstop. These rate
   limit persistently; they are not something to rely on.

Both can return `""`, which means *no usable translation*, never *the
translation is empty*. The announcement upsert honours that: `title_en`,
`title_sq`, `title_it` and their summaries are written with
`COALESCE(NULLIF(excluded.x, ''), announcements.x)`, so a failed run leaves the
last good translation in place. Getting this wrong is what emptied every
translated field in the feed.

### Configuration

The chain reads the same variables as the assistant, from
`/home/peterdsp/syrmos-api/admin.env`. Every scraper unit that translates loads
that file with `EnvironmentFile=-`; **without it the chain builds empty in the
scraper and translation silently degrades to the rate-limited free endpoints.**

- `ARIADNE_GROQ_API_KEY` (or `GROQ_API_KEY`), optional `ARIADNE_GROQ_MODEL`
- `CLOUDFLARE_ACCOUNT_ID` + `CLOUDFLARE_AI_TOKEN`, optional `ARIADNE_CLOUDFLARE_MODEL`
- `SYRMOS_TRANSLATION_USE_ARIADNE=0` turns the chain off and leaves only the
  free fallback
- `SYRMOS_TRANSLATION_GIVE_UP_AFTER` (default 2) is how many failed strings a
  provider gets before it is skipped for the rest of the run. A provider that is
  refusing is refusing for the whole run, so without this cap a scrape pays the
  full attempt-and-backoff ladder for every item in every language: with both
  free providers down that turned a scraper oneshot into a ten-minute job. A
  401/403 is dropped after a single string, since a bad key or an org-level
  model block will still be there for the next one.

To check coverage after a scrape:

```bash
sqlite3 db/syrmos.db "SELECT COUNT(*) total, SUM(title_en='') missing_en, SUM(title_sq='') missing_sq FROM announcements"
```

## Rollback

```
ls ~/syrmos-proxy/nginx.conf.bak.*    # backups created on every deploy
cp <backup> ~/syrmos-proxy/nginx.conf
/usr/sbin/nginx -c ~/syrmos-proxy/nginx.conf -s reload
```

DB backups land in `~/syrmos-api/backups/syrmos-YYYY-MM-DD.db`, kept 30 days
(after the systemd timer is installed).
