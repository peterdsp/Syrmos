# Airport bus telematics integration research (OSETH + OASA)

Verified live on **2026-09-01** by inspecting the production web applications with
browser developer tools (Network + JS bundles + direct API calls from the page
origin). Every endpoint, identifier, parameter and field below was observed from
the real services. Nothing here is copied from historical documentation, blog
posts or third party mirrors. Where a fact could not be verified in this session
it is called out explicitly as unverified rather than guessed.

Raw sanitized captures: `scratchpad/oseth-evidence.json`, `scratchpad/oasa-evidence.json`.

---

## 0. Executive summary

- **OSETH (Thessaloniki)** serves a clean REST/JSON telematics API proxied through
  its Drupal site: `https://oseth.com.gr/{lang}/telematics-api/...`. It returns
  ordered stops with coordinates, route geometry as a WKT `LINESTRING`, scheduled
  timetables, and live vehicle/arrival enrichment. It sends **no CORS header**, so
  a browser (Kotlin/Wasm web app) cannot call it cross origin. **Not currently
  integrated in Syrmos** (only an HTML announcements scraper exists).
- **OASA (Athens)** still runs the legacy `https://telematics.oasa.gr/api/?act=...`
  POST API. It is **already integrated and proxied** through the Pi
  (`scripts/oasa-airport-bus-watcher.py` -> `/api/oasa-airport-buses`), because
  `telematics.oasa.gr` **geo-blocks non-Greek IPs**. The airport stop code `10705`
  hard-coded in that watcher is **confirmed still correct**.
- The new Thessaloniki express **Χ3 (Greek capital Chi, not Latin X)** exists and
  runs Σταθμός Μετρό Μίκρα ↔ Αεροδρόμιο Μακεδονία. Athens **Χ93/Χ95/Χ96/Χ97** all
  still operate (also spelled with Greek Chi in `LineID`).
- **Recommended architecture: keep proxying through the Syrmos FastAPI/Pi backend**
  (mandatory for OASA because of the geofence, and correct for OSETH because of the
  missing CORS header), bundle the scheduled timetable offline, and only ever fetch
  live positions/arrivals through the backend. The app must show the scheduled
  airport timetable with no backend and no internet.

---

## 1. OSETH findings (Thessaloniki)

Host `https://oseth.com.gr`. The frontend is a Drupal module `oasth_telematics_app`
(bundle `/modules/custom/oasth_telematics_app/dist/assets/main-*.js`) that calls a
same-origin REST prefix `/{lang}/telematics-api`. All calls are **GET**, no auth
(a Drupal session cookie is present but the endpoints answer without it),
`Content-Type: application/json`, `Cache-Control: must-revalidate, no-cache,
private`, and **no `Access-Control-Allow-Origin`** (same origin only).

| Capability | Endpoint (prefix `/el/telematics-api`) | Method | Auth | Format | Verified |
|---|---|---|---|---|---|
| Lines/routes list | `/route?page=1&size=2000` | GET | none | JSON `{data:{routes:[...]}}` | 2026-09-01 |
| Route detail (ordered stops + shape + live vehicles) | `/route/{routeId}/info?shapeId={shapeId}&language=el` | GET | none | JSON `{data:{...,stops[],shape,vehicles[]}}` | 2026-09-01 |
| Route timetable (scheduled + live) | `/route/{routeId}/timetable?date={d/m/Y H:i:s}&shapeId={shapeId}&language=el` | GET | none | JSON `{data:{...,trips[]}}` | 2026-09-01 |
| Stop search / autocomplete | `/stop?page=&size=&language=el&stopName={q}` | GET | none | JSON | 2026-09-01 (from bundle) |
| Stop detail | `/stop/{stopId}/info?language=el` | GET | none | JSON | 2026-09-01 (from bundle) |
| Stop timetable | `/stop/{stopId}/timetable?language=el&date={d/m/Y H:i:s}` | GET | none | JSON | 2026-09-01 (from bundle) |
| Nearby stops | `/stop/nearby?page=&size=&longitude=&latitude=&language=el` | GET | none | JSON | 2026-09-01 (from bundle) |
| Trip planner | `/trip/plan?latitudeFrom=&longitudeFrom=&latitudeTo=&longitudeTo=&date=&language=el` | GET | none | JSON | 2026-09-01 (from bundle) |

Notes:
- **`routeId` format:** `{shortName}_{internalId}_{direction}_3`, e.g. `Χ3_9718_1_3`.
  Direction `1` = outbound, `2` = inbound. `shapeId` selects the headsign/geometry.
- **Date format is `d/m/Y H:i:s`** (e.g. `01/09/2026 00:00:00`), URL-encoded. An ISO
  date returns HTTP 400 with `{"error":"The date parameter is required and must be
  in d/m/Y H:i:s format."}`.
- **There is no separate vehicle-positions endpoint.** Live positions arrive inside
  `route/{id}/info` as `data.vehicles[]`, and live arrival/delay arrives inside
  `route/{id}/timetable` per trip.

### Example: `route/{id}/info` (Χ3 outbound, `Χ3_9718_1_3`, shapeId 5771)

```json
{"data":{
  "id":"Χ3_9718_1_3","shortName":"Χ3","color":"#000080",
  "longName":"ΣΤΑΘΜΟΣ ΜΕΤΡΟ ΜΙΚΡΑ - ΑΕΡΟΔΡΟΜΙΟ ΜΑΚΕΔΟΝΙΑ",
  "headsign":"ΣΤΑΘΜΟΣ ΜΕΤΡΟ ΜΙΚΡΑ - ΑΕΡΟΔΡΟΜΙΟ ΜΑΚΕΔΟΝΙΑ",
  "shape":{"id":"5771","color":"#000080","lineString":"LINESTRING (22.96801 40.56582, ... )"},
  "stops":[
    {"id":"45128","code":"45128","name":"Τ.Σ. Ε.Α.Κ. ΜΙΚΡΑΣ","latitude":40.56558,"longitude":22.96852,"sequence":1},
    ... ,
    {"id":"36013","code":"36013","name":"ΑΕΡΟΔΡΟΜΙΟ ΜΑΚΕΔΟΝΙΑ - ΑΦΙΞΕΙΣ","latitude":40.52411,"longitude":22.97735,"sequence":7}
  ],
  "vehicles":[]   // populated with live positions only while a bus is in service
}}
```
- Shape geometry is a **WKT `LINESTRING (lon lat, lon lat, ...)`** (note lon-first).
- `stops[]` is the ordered, coordinate-bearing stop list (7 stops for Χ3).

### Example: `route/{id}/timetable` (Χ3 outbound, date `01/09/2026 00:00:00`)

```json
{"data":{"id":"Χ3_9718_1_3","shortName":"Χ3","stop":{"id":"45128",...},
  "trips":[
    {"id":"38467826","arrivalTime":"05:00:00","departureTime":"05:00:00",
     "delay":0,"monitored":false,"arrivalInMinutes":null,"departureInMinutes":null,"vehicle":null,
     "shapeId":"5771","route":{"id":"Χ3_9718_1_3","shortName":"Χ3"}},
    {"id":"38467780","departureTime":"05:30:00", ...}
  ]}}
```
- Scheduled departures = `trips[].departureTime`. First Χ3 departure 05:00, then 05:30
  (30-minute frequency).
- **Live vs scheduled discriminator = `trips[].monitored`.** When `true`, `delay`,
  `arrivalInMinutes`/`departureInMinutes` and `vehicle` are live; when `false`, only
  the schedule is valid.

### Current Thessaloniki airport-serving lines (verified from the live `route` feed)

265 routes total; 10 serve the airport. The new express uses **Greek Chi `Χ`**, the
older express suffixes use **Latin `X`** (a real, easy-to-miss distinction).

| shortName | routeId (outbound) | longName | Notes |
|---|---|---|---|
| **Χ3** | `Χ3_9718_1_3` (in `5771`, out `5857`) | ΣΤΑΘΜΟΣ ΜΕΤΡΟ ΜΙΚΡΑ - ΑΕΡΟΔΡΟΜΙΟ ΜΑΚΕΔΟΝΙΑ | **New 27 Aug 2026**, Greek Chi, metro-fed |
| 01X | `01X_7904_1_3` (4687/5841) | Κ.Τ.Ε.Λ. - ΑΕΡΟΔΡΟΜΙΟ | Express |
| 01N | `01N_1107_1_3` (5470/5016) | Κ.Τ.Ε.Λ. - ΑΕΡΟΔΡΟΜΙΟ ΝΥΧΤΕΡΙΝΟ | Night |
| 02X | `02X_0745_1_3` (4535/4537) | Στ. Μετρό Ν. Ελβετία - Αεροδρόμιο | Metro-fed express |
| 1N | `1N_9797_1_3` | Κ.Τ.Ε.Λ. - Αεροδρόμιο μέσω ΙΚΕΑ ΝΥΧΤΕΡΙΝΟ | Night via IKEA |
| 79 | `79_8412_1_3` (4660/4911) | Α.Σ. ΙΚΕΑ - ΑΕΡΟΔΡΟΜΙΟ | Classic city bus |
| 79A | `79A_7915_1_3` | Α.Σ. ΙΚΕΑ - ΑΕΡΟΔΡΟΜΙΟ - ΚΤΕΛ ΧΑΛΚΙΔΙΚΗΣ | No active headsigns at capture |
| 79B | `79B_8487_2_3` | ΚΤΕΛ ΧΑΛΚΙΔΙΚΗΣ - ΑΕΡΟΔΡΟΜΙΟ - Α.Σ. ΙΚΕΑ | No active headsigns |
| 79E | `79E_5626_2_3` (4849) | ΑΕΡΟΔΡΟΜΙΟ - COSMOS - Α.Σ. ΙΚΕΑ | One direction |
| 79K | `79K_2994_1_3` | Α.Σ. ΙΚΕΑ - ΑΕΡΟΔΡΟΜΙΟ - COSMOS - ΚΤΕΛ ΧΑΛΚΙΔΙΚΗΣ | No active headsigns |

**Airport stop (Χ3):** `36013` "ΑΕΡΟΔΡΟΜΙΟ ΜΑΚΕΔΟΝΙΑ - ΑΦΙΞΕΙΣ" at (40.52411, 22.97735).

Per-airport-line data available: public shortName, internal routeId + direction, shapeId,
ordered stops with coordinates, WKT shape, scheduled departures, live vehicles, live
arrivals/delay. Service calendar is expressed per query `date`.

---

## 2. OASA findings (Athens)

Host `https://telematics.oasa.gr`. The production app (jQuery + Leaflet,
`/scripts/script.min.js`) calls the legacy API `/api/?act={action}` by **POST**
with a form-encoded body (`p1=`, `p2=`). No auth. `Access-Control-Allow-Origin: *`
(CORS is open) but the body is mislabeled `Content-Type: text/html`. **Reachability
is gated by a Greek-IP geoblock** (documented in
`ops/syrmos-api/scripts/seed_oasa_airport_bus_stops.py`), which is why the Pi proxy
exists.

| Capability | Action (`/api/?act=`) | Params | Format | Verified |
|---|---|---|---|---|
| Lines (master) | `webGetLines` / `webGetLinesWithMLInfo` | none | `[{LineCode,LineDescr,LineDescrEng,LineID}]` (469) | 2026-09-01 |
| Routes for a line | `getRoutesForLine` | `p1=LineCode` | `[{route_code,route_descr,route_id,route_active}]` | 2026-09-01 |
| Ordered stops of a route | `webGetStops` | `p1=route_code` | `[{RouteStopOrder,StopCode,StopID,StopDescr,StopLat,StopLng,StopHeading,...}]` | 2026-09-01 |
| **Live vehicle positions** | `getBusLocation` | `p1=route_code` | `[{VEH_NO,CS_DATE,CS_LAT,CS_LNG,ROUTE_CODE}]` | 2026-09-01 |
| **Live stop arrivals** | `getStopArrivals` | `p1=StopCode` | `[{route_code,veh_code,btime2(min)}]` | 2026-09-01 |
| Service-day categories | `getScheduleDaysMasterline` | `p1=LineCode` | `[{sdc_code,sdc_descr,sdc_descr_eng}]` | 2026-09-01 |
| Daily schedule | `getDailySchedule` | `p1=LineCode` (+ service-day context) | `{come:[],go:[]}` | 2026-09-01 (empty without service-day context) |

Notes:
- **`LineID` uses Greek Chi** (`Χ93`, `Χ95`, `Χ96`, `Χ97`). **`LineCode`** is the
  internal integer used by every other call. **`route_code`** is the direction-specific
  id used for stops/positions.
- **`getBusLocation.CS_DATE`** is the freshness timestamp, Athens local, format
  `YYYY-MM-DD HH:MM:SS.mmm` (e.g. `2026-09-01 12:14:08.000`). Use it for
  `LIVE_POSITION_MAX_AGE`.
- **Scheduled timetable is a two-step flow:** `getScheduleDaysMasterline(LineCode)`
  to get the `sdc_code` for today's service day, then the schedule call for that day.
  `getDailySchedule(LineCode)` alone returned `{come:[],go:[]}` for Χ95, so the exact
  scheduled-times call/params are **not fully verified** in this session and must be
  finalized on the Pi (Greek IP) before relying on OASA for offline schedule. The Pi
  already derives live behaviour without it.

### Example: live positions + arrivals (Χ95, LineCode 1025, route 2051 = Σύνταγμα->Αεροδρόμιο)

```json
// getBusLocation p1=2051
[{"VEH_NO":"61184","CS_DATE":"2026-09-01 12:14:08.000","CS_LAT":"37.9857290","CS_LNG":"23.7626080","ROUTE_CODE":"2051"}, ...]
// getStopArrivals p1=10705  (airport)
[{"route_code":2051,"veh_code":61199,"btime2":11}, {"route_code":2051,"veh_code":61212,"btime2":33}, ...]
// webGetStops p1=2051, last stop:
{"RouteStopOrder":19,"StopCode":10705,"StopID":"1010","StopDescr":"ΚΤΙΡΙΟ ΑΝΑΧΩΡΗΣΕΩΝ","StopDescrEng":"AIR TERMINAL (ALL DEPARTURES)","StopLat":37.9368263,"StopLng":23.9465442}
```

### Current Athens airport-serving lines (verified from `webGetLines`)

| LineID | LineCode(s) | Description | Notes |
|---|---|---|---|
| **Χ93** | 1521 | Στ. Υπερ. Λεωφ. Κηφισού - Αερολιμένας (EXPRESS) | KTEL bus station |
| **Χ95** | 1025 | Σύνταγμα - Αερολιμένας (EXPRESS) | routes 2051/2052 |
| **Χ96** | 892 (+1153 night) | Πειραιάς - Αερολιμένας (EXPRESS) | day + night variants |
| **Χ97** | 1547 (+1549/1552/1553) | Αερολιμένας - Στ. Ελληνικό (EXPRESS) | circular/metro-fed, night variants |

**Airport stop:** `10705` "ΚΤΙΡΙΟ ΑΝΑΧΩΡΗΣΕΩΝ / AIR TERMINAL (ALL DEPARTURES)" at
(37.9368263, 23.9465442). **Matches the Pi watcher's hard-coded stop exactly.**

---

## 3. Static vs real-time classification

| Data | OSETH source | OASA source | Class |
|---|---|---|---|
| Lines / master lines | `route` list | `webGetLines` | static / semi-static |
| Routes + directions | `route` `tripHeadsigns` | `getRoutesForLine` | static / semi-static |
| Ordered stops + coordinates | `route/{id}/info` `stops[]` | `webGetStops` | static |
| Route geometry | `route/{id}/info` `shape.lineString` (WKT) | derive from stop sequence (no shape field observed) | static |
| Scheduled timetable | `route/{id}/timetable` `departureTime` | masterline service-day flow | scheduled (per service day) |
| Live vehicle positions | `route/{id}/info` `vehicles[]` | `getBusLocation` | **real-time** |
| Live arrivals / delay | `route/{id}/timetable` `monitored`/`delay`/`arrivalInMinutes` | `getStopArrivals` `btime2` | **real-time** |

Everything above the two real-time rows can be bundled offline and refreshed only when
the seed is regenerated. Only the two real-time rows need a network call, and only
through the backend.

---

## 4. GTFS investigation

- **OSETH's telematics API is GTFS-derived.** The route ids (`01_7429_1_3`),
  `shapeId`, `tripHeadsigns` and stop `sequence` fields are a direct GTFS
  route/trip/shape/stop_times shape. So the REST API is effectively a live GTFS
  read; a separate static GTFS download is not needed to obtain stops/shapes/timetable.
- **No official GTFS-Static or GTFS-Realtime source URL was verified in this session**
  for either authority (I did not want to record an unverified URL). This needs a
  Pi-side check (Greek IP) against the OASA/OSETH official sites. Third-party mirrors
  (Transitland, Mobility Database) may be used for cross-checking only and must not
  become Syrmos' source of truth.
- Recommendation: the verified telematics REST APIs are sufficient and are the source
  of truth. Treat official static GTFS as an optional future enhancement to be
  confirmed, not a dependency.

---

## 5. Existing Syrmos backend (recon)

- **Backend shape:** most transit data is static JSON generated from a SQLite DB by
  `syrmos_admin/generator.py` and served by nginx from `out/*.json`. A thin FastAPI
  app (`syrmos_admin/app.py`, uvicorn `127.0.0.1:8092`) serves the admin UI plus a
  few dynamic endpoints (Ariadne, community, contact, `/api/live-positions`,
  `/api/departures/next`). Real-time upstreams are integrated by **standalone
  watcher daemons that poll and write a static JSON file** which nginx serves.
- **OASA already proxied:** `scripts/oasa-airport-bus-watcher.py` polls
  `getBusLocation` + `getStopArrivals` every 30 s for X93/X95/X96/X97 (stop 10705)
  and writes `out/oasa-airport-buses.json`, served at **`/api/oasa-airport-buses`**
  with nginx `max-age=15`. KMP `OasaAirportBusService` + `OasaLiveArrivalsProvider`
  already consume it. **iOS has no consumer yet.**
- **OSETH real-time is not integrated** (only `scraper_oseth.py`, an HTML
  announcements scraper).
- **Offline bundle:** Thessaloniki `X3`/`2X` are seeded (bus mode, no timetable) and
  bundled; Athens `X93/X95/X96/X97` are **absent from the bundle** (they live only in
  the live feed). `scripts/snapshot-api-to-seed.py` bundles any line present in the
  Pi manifest, so adding a line to the Pi DB is what makes it ship offline.
- **Client base URL:** `https://api-syrmos.peterdsp.dev` (hard-coded per service).

---

## 6. Recommended architecture

Keep the established **proxy-through-backend** pattern. It is mandatory for OASA (Greek-IP
geofence) and correct for OSETH (no CORS header for the Wasm web client), and it gives one
integration point, normalized models, caching, and the ability to swap upstreams without a
client release.

```
                        INTERNET AVAILABLE
                                v
                     Syrmos FastAPI / Pi backend
                  (watchers write static out/*.json)
                         |               |
                         v               v
                   OSETH telematics   OASA telematics
                   /el/telematics-api  /api/?act=...
                         |               |
                         +-------+-------+
                                 v
                        normalized JSON (short max-age)
                                 v
                 iOS            Android            Web
```

Offline (no internet, or backend down):
```
              bundled static dataset (seed)
        line + ordered stops + shape + SCHEDULED timetable
                 v            v            v
                iOS        Android         Web   ->  scheduled departures only
```

Concrete plan:
- **Bundled offline (ships in the seed):** airport line metadata, ordered stops with
  coordinates, route geometry (OSETH WKT `lineString` -> polyline; OASA -> stop
  sequence), and a **scheduled timetable** for each airport line. The app must show the
  scheduled airport timetable with no backend and no internet.
- **Downloaded (semi-static):** nothing new required; refresh the bundle through the
  normal seed pipeline (`snapshot-api-to-seed.py`) when the Thessaloniki/Athens airport
  lines change (e.g. the 27 Aug 2026 Χ3 launch).
- **Live (backend only):** OSETH `route/{id}/info` vehicles + `route/{id}/timetable`
  monitored trips; OASA `getBusLocation` + `getStopArrivals`. Add a new
  `scripts/oseth-airport-bus-watcher.py` mirroring the OASA watcher, writing
  `out/oseth-airport-buses.json` served at `/api/oseth-airport-buses` with nginx
  `max-age=15`. Extend the OASA watcher only if new lines/variants are needed.
- **Cached by backend/nginx:** live feeds `max-age=15` (as today); semi-static longer.
- **Cached by clients:** ETag / `If-None-Match`; treat the live feed as ~15 s fresh.
- **Failure modes:**
  - OSETH fails -> serve last-good cached file; where absent or stale, show **SCHEDULED**.
  - OASA fails -> same (last-good cached file, else SCHEDULED).
  - `api-syrmos.peterdsp.dev` fails -> clients fall back to the **bundled offline
    SCHEDULED** timetable. The schedule never requires the backend.

---

## 7. Live-data validation rules

- A `200` is not proof of live data. Validate freshness before labeling anything LIVE.
- **Vehicle positions:** `now - CS_DATE` (OASA) or the OSETH vehicle timestamp must be
  `<= LIVE_POSITION_MAX_AGE` (suggest 90-120 s). Athens-local time; compare in the same
  zone the app already uses (`athensNow`).
- **OSETH arrivals:** show LIVE only when `trips[].monitored == true`; otherwise SCHEDULED.
- **OASA arrivals:** `btime2` is minutes-away derived from a live vehicle; if no live
  vehicle backs it, fall back to SCHEDULED.
- Ensure a prediction belongs to the current service day; never show a stale prediction
  as LIVE. When in doubt, degrade to SCHEDULED.

---

## 8. Open items to confirm on the Pi (Greek IP) before coding the live layer

1. OASA scheduled-times call: finalize the exact `getScheduleDaysMasterline` ->
   schedule flow and params (the plain `getDailySchedule(LineCode)` returned empty).
2. Whether official GTFS-Static/Realtime feeds are published by OASA/OSETH, and their
   authoritative source URLs (do not adopt mirrors as source of truth).
3. OSETH live behaviour during service hours (capture a populated `vehicles[]` and a
   `monitored:true` trip to lock the live schema; both were idle at capture time).

**Never invent an endpoint, identifier, parameter, response field, timetable or route.**
Every item above marked "Verified 2026-09-01" was observed from the live service; items
in section 8 are explicitly unverified and must be checked before implementation.
