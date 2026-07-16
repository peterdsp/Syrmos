# Thessaloniki network (metro + suburban) — design

Date: 2026-07-15
Author: Petros Dhespollari
Status: design agreed, all data in hand, ready for implementation.

## Goal

Add the whole Thessaloniki rail network to Syrmos on iOS, Android and Web at the
Athens quality bar: real line geometry on the map, offline-simulated / projected
trains, predicted departures, plus Ariadne and Fares support. Two families:

- **Metro** — Line 1 (live) + Line 2 Kalamaria extension (greyed).
- **Suburban (Proastiakos Thessalonikis)** — Thessaloniki–Sindos shuttle,
  Thessaloniki–Larisa, Thessaloniki–Edessa–Florina.

This is the app's first second region, so it introduces the `region` concept the
roadmap needs for 1.3 (national rail) and 1.4 (Thessaloniki suburban). Hellenic
Train brands all three suburban corridors as "Thessaloniki Regional lines /
Προαστιακός Θεσσαλονίκης", so they group under `region = thessaloniki` even
though the tracks physically reach Larisa (~150 km) and Florina (~160 km).

North star unchanged: companion, not a schedule. Nothing may fabricate a
departure or a train for track that does not run, or that has no authoritative
timetable.

## Confirmed decisions

1. **`region` field**, not `city`. A line can cross cities (Larisa, Florina), so
   the grouping is `region: athens | thessaloniki` on Line + Station, all seed
   schemas and the server DB. Existing data backfills to `athens`.
2. **Metro Line 2 = greyed, not live.** Render the under-construction Kalamaria
   extension as a visible greyed/dashed line, no departures, no trains. Driven by
   the `status` field (below), so flipping it live later is a data change.
3. **Metro offsets distance-derived**, calibrated to ~18.5 min end-to-end, ~20 s
   dwell per stop, symmetric. Flagged "estimated" in the changelog.
4. **Suburban rides the scheduled-trips path** (explicit named trips with HH:MM
   per stop), exactly like Athens A1–A4 — not frequency bands.
5. **All three suburban corridors ship now.** Sindos has full per-stop times;
   Larisa + Florina have endpoint times (see modeling rule in decision 8).
6. **Ariadne understands Thessaloniki** (metro + suburban). Station vocabulary
   EN/EL/SQ + Greeklish, region-scoped.
7. **Thessaloniki fares included**, region-scoped.
8. **Suburban intermediate-stop rule.** For lines with endpoint-only times
   (Larisa, Florina): Regional trains interpolate intermediate stop times by
   track distance (flagged estimated); Express trains are endpoint-only (no
   fabricated intermediate stops). Origin board exact for every train.

## Line identifiers

Metro:
- `TM1` — Line 1, red, **operational**. 13 stations, New Railway Station <-> Nea
  Elvetia, 9.5 km, ~18.5 min. Simulator-driven (no live feed).
- `TM2` — Line 2 Kalamaria extension, blue, **under_construction**, greyed. 5 new
  stations (Nomarchia, Kalamaria, Aretsou, Nea Krini, Mikra) branching from 25
  Martiou. Verify current status before ship (may open ~July 2026).

Suburban (Proastiakos):
- `TP1` — Thessaloniki–Larisa. 12 stations. ~9–10 trains/day each way, Regional +
  Express. **Endpoint timetable in hand — ships now** (intermediate interpolated).
- `TP2` — Thessaloniki–Edessa–Florina. 22 stations on map; running trains stop at
  an express subset. Rail resumed 30 May 2026, 2 trains/day each way (730–733).
  **Endpoint timetable in hand — ships now.**
- `TP3` — Thessaloniki–Sindos shuttle. 2 published endpoints, ~18 trips/day each
  way, Mon–Fri. **Full timetable in hand — ships now.**

`TM`/`TP` prefixes keep them unambiguous against Athens `M*`/`A*` in every
hard-coded id list. Never route any of them through an `M3_AIR`/airport branch.

Shared track (handled fine by independent scheduled trips, as Athens does):
TP1/TP2/TP3 share Thessaloniki→Sindos; TP1/TP2 share Thessaloniki→Platy, then
branch (TP1 south to Larisa, TP2 west to Florina).

## New model fields

On the `Line` model (KMP `Line.kt`, Swift `TransitData.swift`, `lines.json`,
server DB), and where noted `Station`:

- `region: String` — `athens | thessaloniki`, on Line + Station. Migration
  backfills existing rows to `athens`.
- `status: String` — `operational | under_construction`, on Line. Every
  prediction path skips non-operational; only the map renders it greyed.

`status` rules: map draws greyed + informational pins, no train dots; projector /
simulator / home hero / track-picker / last-train all skip it.

## Where the data lives (critical constraint)

`scripts/snapshot-api-to-seed.py` **rmtree's and rewrites** every bundled
`schedules-v2/` dir from the live Pi API on each refresh, so hand-authored bundle
files get wiped. Thessaloniki data must live in the **Pi server DB (source of
truth)** and flow out through the existing generators, even without a live
scraper.

Plan:
- New idempotent `ops/syrmos-api/scripts/seed_thessaloniki.py`: inserts TM/TP
  lines, stations, metro bands + offsets, suburban `scheduled_trips` /
  `scheduled_trip_stops`, fare products; adds the `region`/`status` columns via
  DB migration.
- Regenerate bundles via `snapshot-api-to-seed.py` (all four copies) + OSM
  geometry via `snapshot-osm-shapes.py`.
- Suburban projection reuses the existing server dual-path: `SCHEDULED_TRIP_LINES`
  gains `TP1/TP2/TP3`; on-device, the bundled `{LINE}.json` carry the trips (KMP
  + Swift project from trips like they approximate A1–A4 today).

## Metro stations (TM1, NW -> SE)

TM1_NRS New Railway Station / Νέος Σιδηροδρομικός Σταθμός · TM1_DIM Dimokratias /
Δημοκρατίας · TM1_VEN Venizelou / Βενιζέλου · TM1_AGS Agia Sofia / Αγία Σοφία ·
TM1_PAN Panepistimio / Πανεπιστήμιο · TM1_SYN Sintrivani/Ekthesi / Συντριβάνι ·
TM1_PAP Papafi / Παπάφη · TM1_EFK Efklidi / Ευκλείδη · TM1_FLE Fleming /
Φλέμινγκ · TM1_ANA Analipsi / Ανάληψη · TM1_25M 25 Martiou / 25ης Μαρτίου ·
TM1_VOU Voulgari / Βούλγαρη · TM1_NEL Nea Elvetia / Νέα Ελβετία.

## Suburban stations (from official Hellenic Train corridor maps)

TP1 Thessaloniki–Larisa (12): Thessaloniki, Sindos, Adendro, Platy, Aeginio,
Korinos, Katerini, Litochoro, Leptokarya, Neoi Poroi, Rapsani, Larissa.

TP2 Thessaloniki–Edessa–Florina (22): Thessaloniki, Sindos, Adendron, Platy,
Leianovergion, Alexandreia, Loytros, Kefalochori, Xechasmeni, Kouloura, Messi,
Veroia, Naoussa, Episkopi, Petria, Skydra, Edessa, Arnissa, Ag. Panteleimon,
Amyntaion, Vevi, Florina. (Running trains 730–733 stop at an express subset:
Thessaloniki, Sindos, Adendro, Platy, Alexandria, Veroia, Naousa, Skydra,
Edessa, Arnissa, Amyntaio.)

TP3 Thessaloniki–Sindos (shuttle): Thessaloniki, Sindos. Shared stations reuse
the same station ids across TP1/TP2/TP3 (one physical Sindos, Platy, etc.).

## TP3 Sindos timetable (in hand, 2025-11 PDF) — Mon–Fri

Thessaloniki -> Sindos (dep -> arr, 17 trips): 05:00-05:11, 05:10-05:21,
07:30-07:41, 08:05-08:16, 09:00-09:11, 09:40-09:51, 10:20-10:31, 11:25-11:36,
12:20-12:31, 14:35-14:46, 15:10-15:21, 15:30-15:41, 16:40-16:51, 17:30-17:41,
19:10-19:21, 19:45-19:56, 21:50-22:01.

Sindos -> Thessaloniki (17 trips): 06:49-07:00, 08:30-08:41, 08:44-08:55,
08:56-09:07, 09:20-09:31, 10:00-10:11, 11:19-11:30, 14:09-14:20, 14:55-15:06,
15:50-16:01, 16:24-16:35, 17:05-17:16, 17:26-17:37, 19:04-19:15, 21:09-21:20,
22:41-22:52, 23:34-23:45.

Service note: Mon–Fri only, suspended on national holidays and academic-year
breaks. Day-type = weekday; holidays already excluded by the Greek holiday map.
The academic-break suspension has no calendar feed — model as a caveat, not a
hard rule (ship weekday service, note it may not run during university breaks).
Some PDF-highlighted trips likely through-run beyond Sindos; do not over-model —
treat TP3 as the Thessaloniki–Sindos service.

## TP1 Larisa + TP2 Florina timetables (endpoint times, in hand)

Source PDF gives origin -> terminus dep/arr per train only (no per-station
times). Modeling rule (decision 8): Regional trains serve all intermediate stops
with times **interpolated by track distance** (flagged estimated, same basis as
metro offsets); Express trains are modeled **endpoint-only** (exact Thessaloniki
<-> terminus, no fabricated intermediate stops). Origin (Thessaloniki) board is
exact for every train. Days: treat as daily unless the operator marks otherwise
(verify weekend/holiday variants when a fuller PDF exists).

TP2 Thessaloniki -> Florina: train 731 10:40->13:44, train 733 13:55->17:01.
TP2 Florina -> Thessaloniki: train 730 06:45->09:49, train 732 14:15->17:21.
(Express-style long-distance; served subset: Sindos, Adendro, Platy, Alexandria,
Veroia, Naousa, Skydra, Edessa, Arnissa, Amyntaio. Times at those are
interpolated; the many small halts on the 22-stop map are not served.)

TP1 Thessaloniki -> Larisa (dep->arr, type): 05:00->06:44 R · 05:55->07:23 E ·
07:30->09:14 R · 10:20->12:04 R · 12:20->14:04 R · 15:10->16:54 R ·
16:49->18:17 E · 19:45->21:29 R · 21:50->23:34 R.
TP1 Larisa -> Thessaloniki: 05:15->07:00 R · 07:10->08:55 R · 09:45->11:30 R ·
10:39->12:10 E · 12:35->14:20 R · 14:50->16:35 R · 17:30->19:15 R ·
19:35->21:20 R · 21:39->23:10 E · 22:00->23:45 R.
(R = Regional = all 12 stops, interpolated. E = Express = endpoint-only.)

## Metro frequency bands (thessmetro FAQ) — 05:15–23:00, no midnight wrap

Day-type: mon_thu = fri = sat identical; sun separate.
Mon–Sat: 05:15–07:30 5.0 · 07:30–21:30 3.5 · 21:30–23:00 4.5.
Sunday: 05:15–12:30 5.0 · 12:30–21:30 4.0 · 21:30–23:00 4.5.

## Fares (Thessaloniki 2026 single-ticket reform, metro + bus + suburban urban zone)

Single 70-min €0.60 · day pass €2.50 · 11×70-min €5.80. Seed region-scoped.
(Suburban intercity fares to Larisa/Florina are distance-based and out of scope
for the flat urban products; note as a follow-up.)

## `region` is used in exactly five places

Geometry handles the rest (nearest station is naturally the local one), so
near-me needs no guard. `region` drives: (1) map default camera, (2) no-GPS home
hero, (3) track-picker grouping, (4) announcement scoping, (5) weather coords.

Region detection: nearest-region-centroid from GPS + a persisted manual override,
surfaced as a segmented control in the map header. Default athens with no
location and no saved choice. Note the Thessaloniki map bounding box is wide
(corridors reach Larisa/Florina) — the default camera frames the city; corridors
extend off-screen and are pannable.

## Honesty traps

- **Announcements**: STASY scraper is Athens-only; Thessaloniki (Thessmetro /
  Hellenic Train) has no feed. Scope announcements by region -> Thessaloniki shows
  "no live alerts", never Athens alerts.
- **Live positions**: `/api/live-positions` is Athens. TM/TP have no live feed;
  TM1 is simulator-only, suburban is projected-from-trips only. Do NOT add TM/TP
  ids to `targetLines` / live-poll sets (would 404-poll). (If Hellenic Train live
  suburban SSE is ever added — as Athens A1–A4 have — TP lines can opt in then.)
- **No fabricated suburban departures**: TP1/TP2 render as route + pins only
  until real timetables are seeded. A corridor with no trips must show "route
  shown, no timetable yet", not invented times.

## Ariadne integration

Vocabulary: 13 TM1 stations + suburban station names (EN/EL/SQ/Greeklish) via
`AssistantVocabularyBuilder` (KMP) + iOS `fromSyrmosData()` mirror, tagged
region. Intents region-scoped. Departures / last-train / find-station / fares via
shared use cases. Metro planner trivial; suburban is linear per corridor with a
Platy interchange (TP1<->TP2) — a real but tiny transfer graph. Parity tests both
sides.

## The ~25 id-list audit

Line ids are hard-coded in ~25 sites (KMP/Swift/Web/server: `knownLineIds`,
`targetLines`, `projectedLineIds`, `lineIdsToFetch`, `SCHEDULED_TRIP_LINES`,
projector special cases). Per site: include TM1/TP* in seed hydration + map +
projection; add TP1/TP2/TP3 to `SCHEDULED_TRIP_LINES`; exclude all from
`targetLines`/live-poll and from every M3_AIR branch. End-to-end parity test:
every Thessaloniki line resolves seed -> projector -> map -> simulator on KMP and
Swift.

## Weather

Weather default coords follow the active region centroid.

## Tests (per test discipline)

Metro projector parity (bands/offsets/day-types). Suburban scheduled-trips parity
(TP3 real times, KMP vs Swift, incl. midnight-crossing 23:34 trip). `region`
migration test (all existing -> athens). `status` test (under_construction => 0
departures, still renders). Ariadne parity (Thessaloniki EN/EL/SQ). Map render
smoke ×3. Run `:feature:map:allTests` + `./gradlew check` before done.

## Open risks / verify at implementation

1. **OSM relation ids** for all TM/TP lines: query Overpass directly (web search
   returned wrong ids). Fallback: station-coordinate spline. Suburban corridors
   are on the national network route relations — likely well-mapped.
2. **TM2 operational status** may flip ~July 2026 (data flag, not code).
3. **Station coordinates** for ~30 suburban stations: source from OSM; verify the
   sparse rural halts (Rapsani, Vevi, Xechasmeni) exist in OSM before relying on
   auto-lookup.
4. Confirm `Europe/Athens` tz + Greek holidays are correct (they are).

## Data status — all unblocked

Endpoint timetables for TP1 (Larisa) and TP2 (Florina) received 2026-07-15 and
recorded above; TP3 (Sindos) has full per-stop times. No remaining data blockers.
Nice-to-have follow-ups (not blocking): per-intermediate-station times + Express
stop lists for Larisa/Florina (would replace the distance interpolation with
exact times); weekend/holiday timetable variants for the two corridors;
distance-based intercity suburban fares.

## Files to touch (summary)

Server: DB migration (region, status, fares, scheduled_trips for TP*), new
`seed_thessaloniki.py`, projector reads status + `SCHEDULED_TRIP_LINES += TP*`.
Generators: `snapshot-api-to-seed.py`, `snapshot-osm-shapes.py` `RELATION_FOR_LINE`.
KMP: `Line.kt`/`Station.kt` (region/status), projector status skip, id-list
sites, Ariadne vocab + region scope, map VM. Swift: `TransitData.swift`,
`StationCoordinates.swift`, `ScheduleProjector.swift`, id-list sites, Ariadne
mirror, `MapView` render + greyed style, region detection + switcher. Web:
`web-map.js` render + greyed + region scope + id lists. Bundled seed: regenerated
`schedules-v2/*` (×4) + `shapes.json` (×4). CHANGELOG.md + case study revision log.

## Rollout

Ship metro + TP3 Sindos once verified on all three platforms; TP1/TP2 depart-
ures follow when the timetables land. Changelog notes metro offsets are distance-
estimated, TM2 greyed pending confirmed opening, and TP1/TP2 route-only pending
timetables.
