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
| `/api/departures/next` | FastAPI projector (honours station closures) | no-store |
| `/api/closures` | FastAPI, `station_closures` | 60s + ETag |
| `/api/broadcasts?since=` | FastAPI, `broadcasts` | 60s + ETag |
| `/api/push/register`, `/api/push/unregister` | FastAPI, `push_devices` | no-store |
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
sudo systemctl enable --now syrmos-broadcasts.timer
```

After that, `deploy.sh` re-runs are fully unattended.

## Admin auth

The repository is public, so admin security comes from secrets and the network
(`syrmos_admin/security.py`):

1. **Cloudflare Access** (production). The app verifies the
   `Cf-Access-Jwt-Assertion` JWT on every admin request: RS256 signature
   against `https://<CF_ACCESS_TEAM>.cloudflareaccess.com/cdn-cgi/access/certs`
   (cached for an hour, refetched once on an unknown key id), `aud` equal to
   `CF_ACCESS_AUD`, issuer equal to the team URL, unexpired. The email claim
   must be in `ADMIN_EMAILS`. The plain `Cf-Access-Authenticated-User-Email`
   header is no longer trusted on its own.
2. **Header token fallback** for scripts, only when set:
   `HTTP_ADMIN_TOKEN=longrandomstring` (the old name `SYRMOS_ADMIN_TOKEN` is
   still read), sent as `X-Admin-Token`, compared in constant time.

With neither configured every admin request gets 401.

Browser POSTs need the signed CSRF token that every admin form embeds
(`csrf_token`, HMAC over the identity and issue time, valid 12 h; set
`SYRMOS_CSRF_SECRET` to keep forms valid across restarts) and are refused when
`Sec-Fetch-Site` says cross-site. Header-token requests skip CSRF. Every
accepted POST writes one `admin_audit` row (actor, method, path, action,
sha256 of the submitted fields).

### Cloudflare Access setup (one time, by hand)

1. Zero Trust dashboard, Access, Applications, Add, Self-hosted:
   domain `api-syrmos.peterdsp.dev`, path `admin/*`. Session duration short
   (for example 8 h). Under cookie settings set SameSite to Strict and enable
   HttpOnly.
2. Policy: Allow, Include, Emails, only the owner's address. Login methods:
   one-time PIN and/or a passkey provider.
3. Copy the application's **Audience (AUD) tag** (Overview tab) and the team
   name (Settings, Custom Pages, the `<team>.cloudflareaccess.com` domain).
4. In `~/syrmos-api/admin.env` on the Pi:
   ```
   CF_ACCESS_TEAM=<team>
   CF_ACCESS_AUD=<aud tag>
   ADMIN_EMAILS=owner@example.com
   ```
   then `sudo systemctl restart syrmos-admin.service`.

The origin listens on 127.0.0.1 only and is reached through cloudflared and
nginx. The public `/api/*` proxy locations in `nginx.locations.conf` blank the
`Cf-Access-*` request headers; only `/admin/` forwards them.

## Station closures and broadcasts (3.0.2)

Contract: `docs/3.0.2-CONTRACTS.md`. Shared window rules:
`fixtures/closures/effect.json`, implemented in `syrmos_admin/closures.py`
and asserted case by case in `tests/test_closures.py`.

- `/admin/closures`: create, edit, approve, cancel. Stations and lines from the
  DB, Athens-local start/end converted to UTC epoch, "until further notice",
  Greek reason with a Translate button (same pipeline as announcements,
  results stay editable), optional replacement bus, status draft or
  scheduled. One-click template "Station closed by police order, trains pass
  without stopping until further notice". A closure that would close every
  station of a line is refused (`schedule_invariants.closure_emptied_lines`).
- The STASY scraper drafts closures (status `draft`, source `stasy`) from
  announcements that name stations and closing wording. Drafts are never
  public and never go live without the one-click approval.
- `/admin/broadcasts`: Greek title/body plus Translate, audience (all, region,
  line, station), send now / schedule / draft, expiry, deep link, optional
  closure link, dry run with the target device count and a preview per
  language.
- `/admin/push`: registered device counts per platform, language and topic.
- `syrmos-broadcasts.timer` runs `python -m syrmos_admin.dispatch` every
  minute: due broadcasts are claimed atomically (sent at most once), then
  pushed; closure status flips scheduled -> active -> ended by time. Clients
  evaluate the window themselves, so status is informational.

### Push credentials (one time, by hand)

All of these live only in `~/syrmos-api/admin.env` and key files in
`~/syrmos-api/secrets/` with mode 600 (never in git: both are gitignored, and
`deploy.sh` excludes them from its `rsync --delete`).

**APNs** (iOS, sent directly from the Pi over HTTP/2):

1. developer.apple.com, Certificates, Identifiers & Profiles, Keys, add a key
   with "Apple Push Notifications service (APNs)" enabled. Download the
   `AuthKey_<KEYID>.p8` once (it cannot be downloaded again).
2. Copy it to the Pi, `chmod 600`, and set:
   ```
   APNS_KEY_PATH=/home/peterdsp/syrmos-api/secrets/AuthKey_<KEYID>.p8
   APNS_KEY_ID=<KEYID>
   APNS_TEAM_ID=<10-character team id>
   APNS_TOPIC=com.syrmosApp.ios
   ```
   The app registers with `environment` `sandbox` (debug builds) or
   `production`, and the sender picks the matching APNs host per device.

**FCM** (Android, HTTP v1):

1. Firebase console, add the Android app to a project (needed for the
   client's `google-services.json`).
2. Project settings, Service accounts, Generate new private key. That JSON is
   the server credential; the account needs the Firebase Cloud Messaging API
   enabled (Google Cloud console, APIs).
3. Copy it to the Pi, `chmod 600`, and set:
   ```
   FCM_SERVICE_ACCOUNT_PATH=/home/peterdsp/syrmos-api/secrets/fcm-service-account.json
   FCM_PROJECT_ID=<firebase project id>
   ```

Without these variables the dispatcher still runs: devices of the missing
platform are skipped and counted in the broadcast's delivery report. Tokens
APNs rejects with 410 / BadDeviceToken and FCM with UNREGISTERED / NOT_FOUND
are deleted.

### New Python dependencies

`requirements.txt` adds `PyJWT[crypto]` (Access JWT verification, APNs ES256
and FCM RS256 assertions; pulls in `cryptography`, which has prebuilt wheels
for the Pi) and the `http2` extra of `httpx` (APNs only speaks HTTP/2).
`deploy.sh` installs them with the rest of `requirements.txt`.

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
