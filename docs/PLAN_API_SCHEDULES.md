# Plan: API-backed timetables, OASA 24mmm import, offline-first sync

Status: Draft, awaiting kickoff
Owner: Petros
Source of truth for data: `/Users/p.dhespollari/Desktop/athens_transit_icons_and_rules_package.zip`
Target API host: `api-syrmos.peterdsp.dev` (Cloudflare Tunnel → Raspberry Pi at 192.168.10.10)
Platforms in scope: iOS, Android, Web (all three together, no platform skipped)

## Goals

1. Move timetables, lines, stations, holiday rules, and dated overrides from app-embedded JSON into the Pi-hosted API as the single source of truth.
2. Keep the app offline-first: it must work with zero network from second 0, and refresh silently on cold start with no user action.
3. Make data editable live without an app, web, or store rerelease, through a tiny auth-protected admin UI.
4. Import the official data package as the v1 seed: line/station coordinates, frequency bands, holiday rules, T7 stops, icon assets.
5. Pull OASA 24mmm dated overrides for M2, M3, T6, T7 for the next 30 days, plus the public/bank holiday rules from `RULES.md`.

## Non-goals for v1

- M1 and suburban 24mmm overrides (rules still apply, just no per-date scrape)
- Live arrivals (separate API, separate problem)
- Push-based update delivery (FCM/APNs). ETag-on-launch is enough for a dataset that updates at most daily.

## What lives in the package and how we use it

- `RULES.md` — icon priority rules (metro over tram/suburban at interchanges), generic vehicle fallback rules, T7 Piraeus loop note.
- `athens_fixed_rail_station_coordinates.md` — canonical coordinates for M1/M2/M3, T6/T7, suburban A1/A2/A3/A4, plus the full embedded master schedule (weekly hours, frequency bands by daypart, M3 split, 24/7 Saturday rule, Dec 24/31 23:00 shutdown, Aug 15 flat 12-min, bank holiday rules).
- `athens_transit_t7_station_icons_updated/station_smart_codes/` — station icons, applied per RULES.md priority.
- `athens_transit_t7_station_icons_updated/directional_vehicle_icons/` — directional vehicle icons; generic fallbacks for unknown direction.

The schedule data in the package is frequency-based ("every 4 min in morning peak"), not minute-by-minute departures. The DB schema reflects that: a small rules + bands table, not a giant departures table.

---

## Phase 0 — Inspect the Pi at execution time

**Cannot be done from this network. Must be re-run when execution starts.**

When kicking off implementation, before anything else, do the following on the Pi:

```bash
ssh peterdsp@192.168.10.10
# Identify the service behind /api/lines:
systemctl list-units --type=service --state=running | grep -iE 'api|node|syrmos|caddy|nginx'
# Or:
docker ps --format '{{.Names}}\t{{.Image}}\t{{.Ports}}'
# Find its working dir and source:
ls ~ /srv /opt /var/www 2>/dev/null
# Check nginx/caddy routing to confirm /api/* upstream:
cat /etc/nginx/sites-enabled/* 2>/dev/null
cat /etc/caddy/Caddyfile 2>/dev/null
# Check for an existing DB:
ls -la ~/*.db ~/*.sqlite /var/lib/postgresql 2>/dev/null
sudo -u postgres psql -l 2>/dev/null
```

Record findings in a new `ops/API_INVENTORY.md` (stack, source path, deploy method, DB if any, who serves `/api/announcements`, `/api/trains`, `/api/lines`).

**Decision rule, per user request**: match whatever stack `/api/lines` uses. If inspection cannot disambiguate or there is no real service yet (only static JSON behind nginx), default to **Node + Fastify + SQLite** — smallest delta to the current footprint.

---

## Phase 1 — DB schema

SQLite file on the Pi, ~50 KB at full population. Single-file backup. If Phase 0 reveals Postgres already running for other projects, use Postgres instead.

Tables:

- `lines(id, mode, name_en, name_el, color, terminal_a, terminal_b)` — replaces today's static `/api/lines` source
- `stations(id, name_en, name_el, lat, lng)`
- `line_stations(line_id, station_id, seq, direction)` — order + direction for both inbound and outbound
- `schedule_rules(line_id, day_type, open_time, close_time)` — replaces `OperatingHours.kt`. `day_type` ∈ `mon_thu`, `fri`, `sat`, `sun`, `holiday`, `bank_holiday`, `aug_15`, `dec_24_31`
- `frequency_bands(line_id, day_type, time_start, time_end, headway_minutes)` — replaces the JSON `Frequency` arrays. Handles the M3 split because M3 has two `line_id`s (`m3_city`, `m3_airport`)
- `holiday_rules(date_pattern, behavior, notes)` — pre-seeded from `RULES.md`. Patterns are either fixed dates (Dec 25), Orthodox-Easter-relative (`easter-2` for Good Friday), or named (`aug_15`)
- `date_overrides(date, line_id, source, payload_json, fetched_at)` — only rows where 24mmm differs from the rule result
- `meta(key, value)` — `version`, `updated_at`, `client_min_version` (kill switch), `etag`
- `scrape_log(run_at, source, ok, rows_written, error)`

Migration files numbered `0001_init.sql`, `0002_*.sql`, etc. Admin UI shows current DB version.

---

## Phase 2 — New API endpoints

Same Pi service, same Cloudflare Tunnel. All read endpoints respond with `ETag` and `Cache-Control: public, max-age=300, must-revalidate`. Cloudflare cache TTL ~1h fine for schedules.

- `GET /api/schedules/manifest` → `{ version, updatedAt, perLineHashes }`. Small, cheap, the canonical ETag source.
- `GET /api/schedules/:lineId` → bundle for one line (rules + bands + line metadata)
- `GET /api/schedules` → full snapshot, ~10–30 KB gzip
- `GET /api/overrides?from=YYYY-MM-DD&to=YYYY-MM-DD` → dated overrides only
- `GET /api/holidays` → holiday rules
- `GET /api/lines` (existing) → extended to include **T7** (currently not in seed; M1/M2/M3/P1/T6 only)

Admin endpoints (auth required, see Phase 4) under `/admin/api/*` for writes. Writes bump `meta.version`, recompute `meta.etag`, and purge the Cloudflare cache for affected paths via the CF API.

---

## Phase 3 — OASA 24mmm scraper

Cron on Pi, daily 04:00 Athens time. Scope: **M2, M3, T6, T7 only**.

For each of the next 30 days:
1. Render the rule output (rules + holiday rules)
2. Fetch the 24mmm page for that date and line
3. Diff against rule output; only write a row to `date_overrides` if it differs
4. Log every run to `scrape_log`

If the scrape breaks (HTML structure change, network), do not write. App silently falls through to the rules — no user-visible regression. Manual "Scrape now" button in admin triggers the same job.

---

## Phase 4 — Tiny auth-protected web admin

Served from the Pi at `api-syrmos.peterdsp.dev/admin`. Authentication: **Cloudflare Access** (free, no password to manage, ties to your existing CF account). Fallback: basic auth in nginx if CF Access setup is too much friction.

Pages:
- Lines + stations (edit name, color, terminals, coords)
- Frequency bands (table editor, copy-paste from spreadsheet)
- Holiday rules (pre-seeded, editable)
- Date overrides (list, filter by line/date, "Scrape now", manual add/edit)
- Scrape log + last sync status + DB version

Every save:
1. Write to DB
2. Bump `meta.version`
3. Recompute `meta.etag`
4. Purge Cloudflare cache for affected paths

Stack: matches the API service language. HTMX + server-rendered HTML is enough; no SPA needed.

---

## Phase 5 — Offline-first sync in the app (iOS + Android + Web)

Industry standard pattern. `core/network/.../SyrmosLinesService.kt` already follows this shape for lines (see line 30 — `runCatching` over a single GET, fall through to embedded data on failure). Replicate it for schedules.

Behavior:
1. **Seed JSON stays embedded in the app**. Offline from second 0, mandatory. This is the floor.
2. On cold start, and on resume after >1h backgrounded:
   - `GET /api/schedules/manifest` with `If-None-Match: <last-etag>`
   - 304 → done, nothing to do
   - 200 → for each changed line in `perLineHashes`, fetch its bundle, write to persistent store, atomic swap, bump local version
3. Persistent store:
   - Android: DataStore (Proto) keyed by `lineId`
   - iOS: file in `~/Documents/schedules/` (atomic write via `replaceItemAt`)
   - Web: IndexedDB
4. **Settings toggle "Offline-only mode"** — when on, app never hits the API. Default off. Covers "user that wants it offline" explicitly.
5. **Settings row** "Last updated: 2026-06-11 14:23" + "Check now" button.

New files (mirror existing patterns):
- `core/network/src/commonMain/kotlin/com/syrmos/core/network/SyrmosSchedulesService.kt` (mirrors `SyrmosLinesService.kt`)
- `core/data/src/commonMain/kotlin/com/syrmos/core/data/sync/ScheduleSyncRepository.kt`
- iOS: `iosApp/iosApp/Models/SyrmosSchedulesService.swift`
- Web sync wired through `composeApp/src/wasmJsMain/...`

All three platforms in the same PR (per `feedback_all_platforms`).

---

## Phase 6 — One-shot importer for the package

`scripts/import-athens-package.mjs`:

1. Parse `athens_fixed_rail_station_coordinates.md`:
   - Line summary table → `lines` rows (adds T7)
   - Per-line station tables → `stations`, `line_stations` (adds T7's 43 stops including the 6-stop Piraeus loop noted in RULES.md)
   - "Minute-by-Minute Frequencies" tables → `frequency_bands` rows
   - "Weekly Operating Hours" table → `schedule_rules` rows
   - "Public, Bank and National Holiday Rules" → `holiday_rules` rows
2. Dry-run mode prints the diff vs current DB
3. Promote with `--apply`

Same script copies the icon assets:
- `athens_transit_t7_station_icons_updated/station_smart_codes/` → `composeApp/src/commonMain/composeResources/files/icons/stations/` and iOS asset catalog (`iosApp/iosApp/Assets.xcassets/Stations/`)
- `athens_transit_t7_station_icons_updated/directional_vehicle_icons/` → corresponding vehicle icon folders

Icon resolver follows `RULES.md` priority: metro over tram or suburban at shared stations (Syntagma, Monastiraki, Piraeus, Airport, Doukissis Plakentias). Generic fallback to `vehicle_train.svg` / generic metro / generic tram when direction unknown.

---

## Phase 7 — Rollback safety

- App must keep working if API is 500, offline, or rate-limited. The existing `runCatching` pattern at `core/network/src/commonMain/kotlin/com/syrmos/core/network/SyrmosLinesService.kt:30` already does this for lines; replicate for schedules.
- `meta.client_min_version` kill switch: if a bad data deploy ever ships, set this on the server. Old clients ignore the snapshot and keep their last-good cache. Avoids bricking the app from the admin UI.
- On every successful sync, log a counter through the existing diagnostics channel (`iosApp/iosApp/Models/Diagnostics.swift` for iOS, equivalent on the others).
- Backup: cron on the Pi copies the SQLite file to `/backups/schedules-YYYY-MM-DD.db` daily; retain 30 days.

---

## Risks + mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Pi or residential ISP outage | Medium | Low | Embedded seed + last-good cache. Acceptable downtime 48h. Beyond that, manually serve last-good `schedules.json` from Cloudflare Pages. |
| OASA 24mmm HTML changes break scrape | Medium | Low | Rules are the floor; missing override = fall back to rule. Scrape log makes break visible. |
| Bad data deploy from admin | Low | Medium | `client_min_version` kill switch + daily SQLite backup. |
| Schema migration error | Low | Medium | Numbered migration files; admin UI shows DB version; backup before every migration. |
| OASA 24mmm content licensing | Low | Low | OASA timetable data is public service information. Cite oasa.gr as source in About screen. |

---

## What to nail down before execution

These are blockers you flagged during planning. Resolve before starting Phase 1:

1. **Pi access for Phase 0**. Re-run the inspection commands from Phase 0 above when execution starts. The plan assumes Node + Fastify + SQLite by default; switch if Phase 0 reveals something else.
2. **Admin auth**: Cloudflare Access (recommended) vs basic auth vs GitHub OAuth.
3. **Confirm SQLite is fine**, unless Phase 0 finds Postgres already running on the Pi for another project.

## Execution order

Phases that can run in parallel are grouped.

1. **Phase 0** (inspection — blocks everything)
2. **Phase 1** (schema) and **Phase 6** (importer, against the package files) in parallel
3. **Phase 2** (API endpoints) and **Phase 4** (admin UI scaffold) in parallel
4. **Phase 5** (client offline+sync, all 3 platforms in one PR)
5. **Phase 3** (24mmm scraper)
6. **Phase 7** (rollback safety + backups, can be partially in 1 and 2 too)

Estimated effort: ~7–9 focused days end-to-end. Each phase ships independently behind the existing offline fallback, so no big-bang release.
