# Pi API Inventory

Inspected: 2026-06-11
Host: `peterdsp@192.168.10.10`
Public endpoint: `https://api-syrmos.peterdsp.dev` (Cloudflared named tunnel → nginx on `127.0.0.1:8090`)

## Layout

- `~/syrmos-api/` — Python scripts + JSON data files (single source of "API")
  - `scrape_stasy.py` — runs every 5 min via cron, writes `announcements.json`
  - `sse_trains_daemon.py` — long-running, writes `trains.json`
  - `lines.json` — currently static, hand-authored
  - `db/syrmos.db` — **new** SQLite DB owned by the schedules layer (added in this work)
- `~/syrmos-proxy/` — nginx config + start script. Listens on `127.0.0.1:8090`.
- `~/syrmos-web/` — built Compose web app (wasm)
- `~/syrmos-web.prev/` — previous web release for rollback

## Running services

- `syrmos-static.service` — nginx (web + JSON file serving + train SSE reverse proxy)
- `syrmos-trains.service` — Python SSE relay → `trains.json`
- `cloudflared-syrmos.service` — public tunnel
- (Docker, unrelated) `conservatio_postgres`, `magnetio_*`, `conservatio_api`

## Cron

```
*/5 * * * * /usr/bin/python3 /home/peterdsp/syrmos-api/scrape_stasy.py >> /home/peterdsp/syrmos-api/cron.log 2>&1
```

## Endpoints today

| Path | Source | Cache | Notes |
|---|---|---|---|
| `/api/lines` | `~/syrmos-api/lines.json` (alias) | `max-age=3600` | Hand-edited static file |
| `/api/announcements` | `~/syrmos-api/announcements.json` (alias) | `max-age=60` | Scraper output |
| `/api/trains` | `~/syrmos-api/trains.json` (alias) | `max-age=10` | SSE daemon output |
| `/api/train-stream` | `https://railway.gov.gr/api/train-stream` (proxy_pass) | `no-store` | Live SSE upstream |
| `/` | `~/syrmos-web/index.html` | `no-cache` | Web app shell |
| `*.wasm` | `~/syrmos-web/*.wasm` | `max-age=86400` | |
| `*.css`, `*.js` | `~/syrmos-web/*` | `max-age=31536000` immutable | Hashed |

## Runtime versions

- Python 3.13.5 (system)
- SQLite 3.46.1 (built-in)
- Flask: not installed
- FastAPI: not installed (will be added for the new admin/scheduler service)

## CI

- `~/actions-runner/` — self-hosted GitHub Actions runner. Web deploy goes via `scripts/deploy-web-to-pi.sh`.

## Decisions for the schedules work

1. **Read path stays nginx + static JSON.** Same pattern as today, max Cloudflare cacheability, no new failure modes on the hot path.
2. **New SQLite DB at `~/syrmos-api/db/syrmos.db`** is the source of truth. A small Python service (`syrmos-admin`) owns it.
3. **On any DB write**, the admin service regenerates the static JSON files (`schedules.json`, `schedules-{lineId}.json`, `manifest.json`, `holidays.json`, `overrides.json`) atomically and bumps `meta.version` / `meta.etag`.
4. **Admin service** binds to `127.0.0.1:8091`, fronted by nginx at `/admin`, gated by **Cloudflare Access**. Never exposed without auth.
5. **Postgres in Docker is for `conservatio`**, untouched. SQLite is intentional for Syrmos: tiny data volume, single-file backup, no docker dependency.

## 3.0.2 additions (closures, broadcasts, push)

Served by the FastAPI app (`syrmos-admin.service`, 127.0.0.1:8092) through the
nginx locations in `ops/syrmos-api/nginx.locations.conf`. Contract:
`docs/3.0.2-CONTRACTS.md`.

| Path | Method | Auth | Cache | Notes |
|---|---|---|---|---|
| `/api/closures` | GET | public | `public, max-age=60`, ETag, 304 | Non-draft, non-cancelled station closures, ended ones kept 24 h |
| `/api/broadcasts?since=<epoch>` | GET | public | `public, max-age=60`, ETag, 304 | Sent, unexpired broadcasts, newest first, max 50 |
| `/api/push/register` | POST | public | `no-store` | Anonymous token upsert keyed by sha256(token), 204, 400 on bad shape, 30/hour per IP |
| `/api/push/unregister` | POST | public | `no-store` | 204 |
| `/api/departures/next` | GET | public | `no-store` | Now also hides departures where a closure blocks boarding and returns `stationClosure` |
| `/admin/closures`, `/admin/broadcasts`, `/admin/push` | GET/POST | Cloudflare Access JWT + `ADMIN_EMAILS`, CSRF on POST | `no-store` | Every POST writes an `admin_audit` row |

Timer: `syrmos-broadcasts.timer` (every minute) runs `python -m syrmos_admin.dispatch`,
which sends due broadcasts once and flips closure status scheduled -> active -> ended.

Public `/api/*` proxy locations blank the `Cf-Access-Jwt-Assertion` and
`Cf-Access-Authenticated-User-Email` request headers; only `/admin/` forwards them.
