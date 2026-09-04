# Offline-first & transport data sources

How Syrmos stays fully usable with no connection, where its data comes from, and
how each on-screen value is classified as live, estimated, scheduled, cached or
unavailable. Companion to [PLAN_API_SCHEDULES.md](PLAN_API_SCHEDULES.md) and the
memory note `deploy-and-seed-playbook`.

## The backend ("PI") and the official sources

Clients never call a government API directly and hold no operator secrets. All
official feeds are normalised and proxied by the Syrmos backend, a **Raspberry
Pi** running the FastAPI `syrmos_admin` service behind a Cloudflare Tunnel at
`https://api-syrmos.peterdsp.dev`. That proxy is the "PI" integration.

| Source | Kind | What it feeds | How |
|---|---|---|---|
| **OASA Telematics** `telematics.oasa.gr/api/` (Athens transport authority, government) | Real-time | Airport express-bus positions + ETAs (X93/X95/X96/X97) | `getBusLocation` + `getStopArrivals` (btime2), polled 30s by `oasa-airport-bus-watcher.py` → `/api/oasa-airport-buses` |
| **railway.gov.gr** `/api/train-stream` (SSE) + `/api/public/trains/` (government railway) | Real-time GPS | Live suburban + intercity train positions | `railway-gov-sse.py` holds one SSE, relays a ~1.5 KB JSON → `/api/trains` |
| **Hellenic Train** `hellenictrain.gr` (operator) | Scheduled | Timetables, transcribed into the bundled seed | manual/scripted import |
| **STASY** (Athens metro/tram operator) | Scheduled + alerts | Last-trains, tram offsets, announcements | `scrape-stasy-*`, `stasy-*-watcher.py` |

Metro (M1/M2/M3) and tram (T6/T7) have **no operator live-GPS feed**. Their moving
map dots are the server's own schedule **projection** (`/api/live-positions` →
`active_trains`: `originDepartureMinute` / `elapsedMinutes` / `totalTravelMinutes`,
no lat/lng) — the client interpolates the position from progress. This is true
**online and offline**, so those dots are always "estimated from timetable", never
GPS.

## What is live vs estimated vs scheduled vs offline

| Data | Provenance | Notes |
|---|---|---|
| Airport-bus positions & ETAs | **LIVE** (real GPS + `updatedAt`) | soonest ETA is OASA `btime2` |
| Suburban / intercity train positions | **LIVE** (real GPS, `/api/trains` batch `updatedAt`) | absent overnight (no service) |
| Metro / tram map dots | **ESTIMATED** (interpolated from the timetable) | same online and offline |
| Station departures | **SCHEDULED** (projected from frequency bands / trip stops) | server + client projectors kept in lockstep |
| Anything when the Pi is unreachable | **OFFLINE** (bundled seed) | full snapshot ships in the app |

## The data-status model

Two orthogonal axes live in `core/model` and are mirrored per platform:

- **Provenance** — `SourceConfidence` (`LIVE`, `SCHEDULED`, `ESTIMATED`, `OFFLINE`,
  `OPERATOR_LINK`, `UNKNOWN`). Rendered as the calm source chip.
- **Freshness** — `Freshness` (`Fresh` / `Stale(age)` / `UnknownAge` /
  `NotApplicable` / `Unavailable`) resolved with provenance into a `DisplayKind`
  (`LIVE`, `LIVE_UNKNOWN_AGE`, `SCHEDULED`, `ESTIMATED`, `CACHED`, `OFFLINE`,
  `OPERATOR`, `UNAVAILABLE`).

### Live-vehicle marker freshness (map)

Real-GPS markers (suburban/intercity trains, airport buses) are classified by the
age of their **own** timestamp so an aged position is never drawn as live. One
rule, mirrored on all three clients:

- `core/model/status/LiveVehicleFreshness.kt` — `classifyLiveVehicle`/`…Iso`
- iOS `Core/Schedule/DataFreshness.swift` — `LiveVehicleFreshnessRule.classify`
- web `web-map.js` — `classifyLiveBatch`

| Age | State | Rendering |
|---|---|---|
| ≤ 90 s | **LIVE** | full styling, boardable |
| 90 s – 600 s | **STALE** | de-emphasised (grey, no pulse) + "updated N m ago"; never a plain live dot |
| > 600 s | **EXPIRED** | marker dropped; the line falls back to the schedule projector |
| no / bad / far-future timestamp | **STALE** (no age) | never live |

Freshness is recomputed against wall-clock on a timer (Android 1 s sim loop, iOS a
5 s `nowTick`, web a 15 s re-render), so a marker ages LIVE → STALE → gone even
when the feed stops emitting (offline / dropped) with no new data. A still-tracked
(LIVE or STALE) train hides its line's projected dot; an EXPIRED one releases the
line so the projector fills back in.

## Offline behaviour per platform

- **iOS / Android** bundle a full seed (`seed-schedules-v2/`) loaded synchronously
  at launch with **no blocking network call**; the DB seed is replaced atomically
  (Android `DataSeeder.seed()` in one transaction). Route shapes, stations and
  projected metro/tram/suburban dots all render offline.
- **Web** reads the bundled `lines.json` + geometry it serves and caches API
  augmentations in `localStorage`. (The base map tiles are remote, so the basemap
  is online-only by design; vector overlays still draw.)

## Known limitations

- Base-map tiles (Esri) are remote on every client; offline the basemap is blank
  while route shapes, stations and vehicle dots still render. (No unauthorized
  bulk tile download is performed.)
- Metro/tram positions are always estimated (no operator GPS exists).
- Live suburban/intercity trains are absent overnight (no service to track).
- Livestream (an on-train camera on some live suburban trains) needs connectivity
  by nature; offline it shows a clear "requires an internet connection" state and
  reconnects automatically when back online.
- The web app has no Service Worker yet, so a first cold start still needs the
  network to fetch the page shell (tracked as a follow-up).

## Verification

- Live feeds: `curl https://api-syrmos.peterdsp.dev/api/{trains,live-positions,oasa-airport-buses,schedules/manifest}`.
- Freshness logic: unit tests on all three clients (`LiveVehicleFreshnessTest`,
  `LiveTrainClassificationTest`, iOS `SuburbanProjectionTests`, web
  `live-train-freshness.test.js`).
