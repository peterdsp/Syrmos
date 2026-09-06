# Changelog

User-facing and architectural changes to Syrmos. Keep this file up to date with every release. Format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

Current production: **iOS 2.0.0** (App Store, build 138), **Android 2.0.0** (Play, versionCode 223), **Web** (rolling). **3.0.0 beta train:** iOS marketing version **3.0.0** (build auto-stamped by CI), Android versionName **3.0.0**; beta.1 used versionCode 224, beta.2 uses **225**, distributed to TestFlight + Play internal. Burned Android version codes never reusable: 105, 106, 109-138, and 200-224; the next release must use 225+.

Tag-driven CI ships iOS + Android + web automatically on a `v*` tag. See [docs/ops/RELEASE.md](docs/ops/RELEASE.md).

The long-range product roadmap by version (1.1 through 2.0, with quarterly targets) lives in [docs/CASE_STUDY.md, Appendix K](docs/CASE_STUDY.md#appendix-k--product-roadmap). Detailed historical context for each shipped change lives in the same file's Revision Log. This changelog summarises the version-to-feature mapping.

Product direction: Syrmos is a companion, not a schedule. Every feature is measured against the answer-first / proactive / reassuring / low-decision rules in [docs/PRODUCT_PRINCIPLES.md](docs/PRODUCT_PRINCIPLES.md).

## Unreleased

Honest live-vehicle freshness on the map, an offline-aware livestream, a web
offline Service Worker, and a subtle map offline indicator, across every client.
Answers the offline-first data-status rule that an aged position must never be
shown as live, and that the app must say when it is running on schedule/cached
data. See [docs/OFFLINE_FIRST_AND_DATA_SOURCES.md](docs/OFFLINE_FIRST_AND_DATA_SOURCES.md).

- **Map offline indicator (iOS + Android + web).** A subtle, accessible pill on
  the map appears when the device is offline OR no live data has arrived within
  the freshness window (a device reporting "online" does not prove the API is
  reachable), and hides the moment live data resumes. The map keeps working from
  the bundled schedule + last-known data throughout; the pill never interrupts.
  Android gained instant offline detection (`ConnectivityObserver.onLost` +
  a shared `LiveDataFreshness.isNetworkAvailable` flag) and, in passing, a fix so
  "hide vehicles" also clears the live-train layer.
- **Backoff + jitter on the live polls (iOS + Android + web).** Every live poll
  (trains, live-positions, airport buses) now backs off exponentially with jitter
  on repeated failure (`base -> 2x -> 4x`, capped, +/-25% so installed clients do
  not retry a down Pi in lockstep) and resets to its base interval on the next
  success, replacing the previous fixed-interval loops. One shared rule:
  `PollBackoff` (KMP + iOS) / `pollBackoffMs` (web).

- **Live-GPS markers age out honestly (iOS + Android + web).** A real train/bus
  position is now classified by the age of its own `updatedAt`: fresh (<=90s)
  draws live; stale (90s-600s) draws de-emphasised (grey, no pulse), never a plain
  live dot; expired (>600s) is dropped so the line falls back to the schedule
  projector. Freshness is recomputed on a timer, so a marker ages LIVE -> STALE ->
  gone even when the feed stops emitting (offline / dropped) with no new data. Root
  cause fixed on Android/KMP where live trains carried no timestamp at all
  (`updatedAt = ""`) and a frozen ghost both looked live and blocked its line's
  offline projection. One shared rule: `core/model/status/LiveVehicleFreshness.kt`
  (KMP), `LiveVehicleFreshnessRule` (iOS), `classifyLiveBatch` (web).
- **Offline-aware livestream.** iOS gained an explicit "Livestream requires an
  internet connection" state, automatic reconnect when connectivity returns (no
  restart), and background/foreground pause-resume; Android no longer offers a
  dead "Watch Live" link when the train is not currently tracked, showing the same
  offline message instead.
- **Web loads and runs fully offline (Service Worker).** A new `sw.js` precaches
  the app shell + bundled seed and runtime-caches the content-hashed bundles, so
  after one visit a reload or a dropped connection keeps the map, stations, routes
  and schedules usable with no network instead of blanking the page. Navigations
  and the live API are network-first (online always gets the latest; offline falls
  back to cache), map tiles use a capped opportunistic cache, and the seed init was
  hardened so a first-ever offline visit degrades to an empty-but-alive map. See
  [docs/OFFLINE_FIRST_AND_DATA_SOURCES.md](docs/OFFLINE_FIRST_AND_DATA_SOURCES.md).
- **Tests.** `LiveVehicleFreshnessTest` (KMP, 12), `LiveTrainClassificationTest`
  (KMP, 5), iOS `SuburbanProjectionTests` (+3, 7 total), web
  `live-train-freshness.test.js` (8) plus `service-worker.test.js` /
  `service-worker-behavior.test.js` (13, the latter executing the real `sw.js`
  handlers). Full suites green: iOS 173, web 81, Android build + unit tests.

## 3.0.0-beta.2 - 2026-09-03

Live GO: the get-off cue that is the point of the whole feature. GO no longer waits for you to tap through
your journey; it tracks your position and tells you when to get off, hands-free.

- **iOS live GO.** A "Start live guidance" toggle in the GO screen tracks the rider's GPS and advances the
  journey on its own, firing a local notification + haptic when they are one stop from a leg's alight point
  (board / change / get off next), localized EN/EL/SQ/IT. Advance is forward-only (GPS jitter never rewinds)
  and threshold-gated (holds between stops). Still gated behind the internal-build flag (TestFlight/internal
  only). The get-off decision reuses the same advancer verified end to end on web.
- **Web live GO.** The web GO panel gained the same live mode over the browser's geolocation
  (`web-go.js advancedPosition` + panel `startLive`), verified end to end in a browser: emitted fixes drove
  the panel ride -> get off at the interchange -> transfer -> get off at the destination, one alert per leg.
  The modules ship in the web bundle; wiring them into the Plan workspace is a follow-up.
- **Cross-client advancer parity.** The GPS-proximity advancer is implemented identically on iOS
  (`GoLocationAdvancer`) and web (`web-go.js`), with the web tests mirroring the iOS scenarios exactly.

Known issues: iOS live GO's GPS-to-notification path is exercised by internal testers (not verifiable in the
build environment); very coarse GPS sampling could skip a leg's get-off cue (mitigated by a 50m distance
filter). Web live GO is not yet wired into the app's Plan workspace.

iOS 3.0.0 (build auto-stamped) / Android 3.0.0 versionCode 225. Tagged as `v3.0.0-beta.2`.

## 3.0.0-beta.1 - 2026-09-03

The first beta of **3.0 "Journeys"** ([docs/plans/3.0-JOURNEYS.md](docs/plans/3.0-JOURNEYS.md)): the shift
from a next-departure companion to an end-to-end journey companion (plan A to B, then be guided through the
ride, board / ride / get off next / change here, without opening the app). This beta lands the GO engine
across every platform and the first visible GO experience on iOS.

- **GO trip-guidance engine, all four targets.** The pure, offline, deterministic core of GO: given a planned
  journey (legs of ordered stops) and the rider's current stop, it returns the one instruction that matters
  now (board / ride / get off next / transfer / arrived) plus a single get-off-alert predicate. Implemented
  identically in web (`web-go.js`), server (`go_guidance.py`), iOS (`JourneyGuidance.swift`) and Android/KMP
  (`GoGuidance.kt`), all validated against one cross-client golden contract
  (`fixtures/go-guidance/cases.json`, exact-equality). Independently reviewed by Codex: no divergence across
  a 1,290-state sweep, and the get-off cue is never dropped.
- **iOS GO journey screen (internal beta only).** A screen that guides a rider through a planned journey one
  instruction at a time, line-tinted, with the get-off cue emphasised, localized EN/EL/SQ/IT. Reachable from
  More -> "Journey guide (GO)". Gated behind the internal-build flag, so it ships to TestFlight/internal
  testers but stays hidden in a public App Store build until live GPS auto-advance and get-off notifications
  land. Advancing is manual in this beta (step through your journey); live auto-advance is the next phase.
- **iOS planner -> GO connection.** `JourneyPlanner.planDetailed` exposes each leg's full ordered stop
  sequence and feeds the GO engine, with no change to the existing Ariadne-facing routing (verified by the
  full 138-test iOS suite).
- **Data + release engineering.** The 139-test backend API suite now runs as a CI gate; a zero-dependency web
  JS test harness gates the web client (guardrails for shipped-and-fixed bug classes + a bundled-seed
  contract); six T7 tram stop coordinates reconciled to the canonical registry with an exact regression
  guard; the flaky watchOS-runtime CI step no longer fails iOS workflows.

Known issues / boundaries in this beta: GO is visible on iOS only (Android/web carry the engine but no GO UI
yet); GO advance is manual, not live; the T7 physical-coordinate source (June 2026 realignment vs. the
node-referenced package) still needs OSM/field verification before the Pi DB is updated.

iOS 3.0.0 (build auto-stamped) / Android 3.0.0 versionCode 224. Tagged as `v3.0.0-beta.1`.

## 2.0.0 - 2026-09-02

The 2.0.0 general release: the culmination of the 2.0.0-beta.1 through beta.23 line (capsule vehicle markers, the three-column web redesign, anonymous Ichnos community history, the multi-airport hub, the Ariadne assistant with an async cloud provider chain, and proximity-based station interchanges), promoted to production after a full cross-platform QA and parity hardening pass.

Final parity + QA hardening in this release (iOS / Android / Web brought closer to a true 1:1 product):

- **Live-feed decode resilience (Android).** `/api/live-positions` and `/api/station-offsets` now decode row by row, so one malformed vehicle or offset entry is skipped instead of silently dropping the whole live map to the simulator. Matches the iOS trains/announcements resilience.
- **Accent- and case-insensitive station search (Android).** A shared `normalizeForSearch` folds Greek tonos/final-sigma and Latin diacritics, and search runs in memory over the station list, fixing Greek names that the SQLite `LIKE` path never matched.
- **Athens time via the IANA zone (Android).** `currentAthensTime/Date` delegate to `Europe/Athens` instead of hand-rolled DST arithmetic, removing transition-hour drift and unifying with the projector.
- **Offline rail news (Android).** The news section keeps the last good payload when the feed is unreachable, matching iOS.
- **Date-scoped trips (Android).** Trip `validDates` are honored end to end (schema migration + projector filter), so seasonal services no longer show on the wrong dates.
- **Fast live/departures timeouts (Android).** An 8s per-call timeout on the live and departures calls drops to the bundled/simulated layer in seconds when the Pi is unreachable instead of hanging on the 30s client default.
- **Interactive Airport tab (iOS + Android).** Rail service tiles, departure rows and the Thessaloniki metro-leg cards now open the stop's full Station Detail (departures + map + interchanges).
- **Circular bus routes (iOS).** Loop bus routes (e.g. Patras PU1) draw their return leg, matching Android/web via a Swift port of the shared `RouteGeometry.closeLoop`.
- **iOS reference resilience.** One GPS-less train no longer blanks the live map, and one malformed announcement no longer voids the feed.
- **Release engineering.** Fixed the iOS release build-number read (it was reading the literal `$(CURRENT_PROJECT_VERSION)` placeholder from the Info.plist, which would have made App Store Connect reject the upload).

iOS 2.0.0 build 138 / Android 2.0.0 versionCode 223. To be tagged as `v2.0.0`.

## 2.0.0-beta.9 - 2026-08-06

The capsule markers and web redesign beta.

- **Capsule vehicle markers.** All three platforms now render moving vehicles as smooth 24x13 capsule markers (border-radius 6, 2px white border) instead of directional triangles. Shared interpolation math (distance table, cubic-bezier train-glide easing, bearing low-pass filter) lives in KMP core:common so only the rendering layer is per-platform.
- **Web three-column layout.** Desktop web gets a nav rail (72px), context rail (352px), and flex map canvas. Responsive breakpoints at 960px and 720px collapse rails progressively. Dark mode uses CSS custom properties via body.dark-mode.
- **Smooth rAF animation (web).** Vehicles animate along polyline geometry using requestAnimationFrame with cubic-bezier(0.16,1,0.30,1) easing and bearing rotation with a 0.15/frame low-pass filter.
- **iOS capsule + bearing.** CADisplayLink tick now computes per-frame bearing from coordinate deltas, applies low-pass filter, and re-renders capsule image with rotation. Train-glide easing replaces the old smoothstep.
- **Android capsule.** Triangle bitmap replaced with density-scaled rounded-rect capsule using MapDesignTokens from core:common.
- **OASA airport bus integration.** Pi-side scraper polls OASA telematics API for X93/X95/X96/X97 airport buses every 30s, writes atomic JSON, nginx serves with 15s cache. KMP OasaAirportBusService + OasaLiveArrivalsProvider wired into the live arrivals router.

iOS 2.0.0 build 123 / Android 2.0.0 versionCode 208. Tagged as `v2.0.0-beta.9`.

## 2.0.0-beta.8 - 2026-08-06

The permanent Ichnos history beta.

- **Anonymous railway history.** Every accepted community report now updates a permanent daily aggregate by scope and condition. Individual reports still expire and are deleted, while anonymous counts remain available across days, months, and years.
- **Good and issue trends.** Android and iOS show matching reports, good, and issue totals with period bars and condition breakdowns. Estimated journeys are never written into historical totals.
- **Correct refinements and undo.** Changing a report moves its count between historical buckets without double counting. Undo removes the count as well as the active report.
- **Live Raspberry Pi persistence.** Database schema 26, the history endpoint, reverse proxy route, and deployment migration are installed on the production Pi.

iOS 2.0.0 build 122 / Android 2.0.0 versionCode 207. Tagged as `v2.0.0-beta.8`.

## 2.0.0-beta.7 - 2026-08-06

The production Ichnos and Airport Hub beta.

- **Production anonymous Ichnos reporting.** Android, iOS, Apple Watch, Live Activities, and tracked-departure notifications submit privacy-minimized reports to the Raspberry Pi API. Reports contain no account, device identifier, or precise location, expire from active summaries after two hours, and are deleted after seven days.
- **Honest community summaries.** When no problem is active, Ichnos shows a deterministic daily journey estimate clearly labeled as an estimate. A single active issue hides the estimate and surfaces the issue instead. Report refinement and undo update the server rather than demo-only local counters.
- **Local contributor identity.** Personal names, profile photos, and the unexplained settings action are removed. Each device receives a playful rail-themed contributor title, while progress and milestones remain local.
- **Airport Calendar Hub.** Android and iOS can read airport-related events from the device calendar, browse the next eight days, and adjust metro, suburban rail, and bus planning for a selected future date. No sample flight or fabricated live timetable remains.
- **Navigation and layout repairs.** Contribution and Ichnos detail screens support system back navigation, including iOS left-edge swipe. The quick-report sheet and station bottom sheet fit correctly above the tab bar and safe area.
- **Four-language content integrity.** English, Greek, Albanian, and Italian announcement content is selected from validated server translations. Visible RailPulse naming is replaced with Ichnos throughout the app.
- **Settings and attribution cleanup.** The redundant Network map row is removed. The disclaimer now covers STASY, OASA, OSETH, OASTH, Thessaloniki Metro, OSE, Hellenic Train, and Athens, Thessaloniki, and Patras suburban services.

iOS 2.0.0 build 121 / Android 2.0.0 versionCode 206. Tagged as `v2.0.0-beta.7`.

## 2.0.0-beta.6 - 2026-08-06

The proactive Home beta.

- **Adaptive Rail Pulse on Android and iOS.** Home now opens with one answer-first surface that combines the next train, later departures, tracking, last-train context, live freshness, weather, disruptions, and offline state.
- **Living map strip.** A compact line diagram previews current network activity and opens the full map without adding another decision-heavy card.
- **Ranked insights.** Service alerts, operator announcements, and rail news are merged into one priority-ranked stream with concise previews and an expandable history.
- **Radial nearby discovery.** Nearby stations are presented spatially with inline departures, a selected-station detail card, and a List accessibility fallback.
- **Adaptive visual language.** Pulse gradients respond to normal, late-night, disrupted, tracking, and offline states. Shared typography and motion tokens keep Android and iOS behavior aligned.
- **Localized on all supported languages.** New Home copy is available in English, Greek, Albanian, and Italian.
- **iOS build repair.** The map annotation image helper now has the narrow visibility required by the current Swift compiler, restoring a clean full-scheme build.

iOS 2.0.0 build 120 / Android 2.0.0 versionCode 205. Tagged as `v2.0.0-beta.6`.

## 2.0.0-beta.5 - 2026-08-05

The Explore V2 and RailPulse beta.

- **Explore V2 on Android and iOS.** Discover now combines universal transport search, the existing Network browser, a route pulse, Greece-wide community summaries, time-budget exploration, and Ariadne context in one tab.
- **RailPulse station and train details.** New detail screens show structured occupancy, delays, temperature, cleanliness, accessibility, facilities, confirmation counts, recency, expiry, and clear community-confidence labels.
- **Two-second Quick Report.** Riders can submit a structured condition with one tap, refine crowd level, and undo for 10 seconds. There is no free-text report field.
- **Local contribution profile.** Contribution counters, quality, weekly progress, levels, and badges remain on-device. No name, report history, location, photo, advertising identifier, or stable contributor identifier is transmitted.
- **Journey surfaces.** iOS Live Activities expose RailPulse context and an inline confirmation App Intent. Apple Watch adds current conditions and quick actions. Android tracked-departure notifications show community context and a local confirmation action.
- **Cross-platform design package.** Twelve SVG screen drafts, the combined Explore implementation prompt, image requirements, the staged implementation plan, and the implemented-versus-backend status are included under `docs/design/explore-v2/`.
- **Honest beta boundary.** Community conditions use fixtures and contributions update local counters only. Anonymous aggregate reads and writes, expiry enforcement, moderation, and unlinkable one-time proofs remain disabled until the audited backend exists.

iOS 2.0.0 build 119 / Android 2.0.0 versionCode 204. Tagged as `v2.0.0-beta.5`.

## 1.4.0 — 2026-07-28 through 2026-07-30

The voice-assistant + live-tracking release. Siri, Google Assistant, cloud Ariadne, push notifications, and a redesigned tracking experience.

- **Siri App Intents (iOS) + Google Assistant App Actions (Android).** "Hey Siri, next train from Syntagma" and "Hey Google, departures from Piraeus" now work natively. Intents cover departures, trip planning, and line status.
- **Cloud Ariadne LLM.** The assistant can now route through a cloud LLM (Groq primary, OpenRouter secondary, Cerebras tertiary) for queries the on-device model and rule parser cannot handle. The cloud path enriches the prompt with live transit context and alert awareness. The on-device rule parser remains the offline floor.
- **Live train tracking with real GPS.** Suburban train tracking now prefers real GPS coordinates from the live feed, falling back to the projector when offline.
- **Rail news.** A scraper pulls official Hellenic Train announcements (switched from mixed sources to official HT feeds only). News appears on the Home screen in a dedicated "Rail Network Updates" section, separate from service alerts.
- **Push notifications.** Service alerts, weather warnings, and nearby-station departures can now push to the device. Permission requested during onboarding and shown in What's New.
- **Offline scheduled-trip departures for all lines.** Every line (not just metro/tram) now shows offline departures from the bundled trips, with `valid_dates` support for date-specific scheduled services.
- **Tab bar rename.** Home | Explore | Map | Departures | More (was Airport for Departures).
- **Light-first restyle pass.** All platforms received a visual pass toward the 2.0 Hellenic Rail Atlas light-first identity.
- **Service alert banners on line and station detail screens.** Active alerts now surface contextually, not just on Home.
- **Train numbers.** Suburban and intercity departures now show the train number (e.g. P1-1234).
- **Polyline-snapped train markers.** Moving vehicles snap to the real track geometry instead of chord-lerping between stations.
- **Home customization.** Users can reorder and toggle Home sections.
- **Live trains in Explore.** The Explore tab now shows currently running trains.
- **Station name translation.** Server-side and client-side English/Greek station name translation for the map and search.
- **Auto-polling live trains (iOS).** iOS now polls for live suburban positions automatically instead of requiring manual refresh.
- **Patras PU1 route fix.** Retraced the University campus shuttle route with corrected station coordinates.
- **CI gate workflow.** KMP, Android, and iOS tests now run as a CI gate on push.
- **Telemetry + Watch Live on web.** Web gained a live stream player and usage telemetry.

iOS 1.4.0 (build 115) / Android versionCode 138. Built through tags v1.4.0 to v1.4.4 (build iterations).

## 1.3.3 — 2026-07-28

- **Line search and filters.** The Lines/Explore tab gained a search bar and type filters (metro, tram, suburban, bus), so users can find a line without scrolling.
- **Ariadne download meter.** The model download progress is now visible inline.
- **Hellenic Train info links.** Each line card links to the operator's official page.

## 1.3.2 — 2026-07-28

- **Nationwide fares via API.** Non-Athens stations now return fares from the live API instead of showing nothing. Web fares fetch bypasses the browser cache to avoid stale prices.
- **Ariadne owl mark fix.** The assistant launcher icon renders correctly across platforms.
- **What's New refresh.** Updated the in-app changelog to cover recent releases.

## 1.3.1 — 2026-07-28

- **External timetable links for non-Athens stations.** Stations outside Athens (national, Thessaloniki, Patras) now show a link to the operator's official timetable page when no local schedule data is available.
- **Design system color unification.** All line colors moved onto `SyrmosColorTokens`, deleting the legacy `Color.kt` extension file.

## 1.3.0 — 2026-07-27

iOS map parity: the whole country's stations, plus the missing controls.

- **Every station on the iOS map (all networks).** The iOS map drew station dots from a hardcoded Athens-only list (`StationCoords`: M1-M3, T6/T7, A1-A4), so Thessaloniki metro + suburban, the national/intercity corridors and the Patras suburban had lines but no stations. It now reads the same bundled `seed-schedules-v2/lines.json` as the polylines and web — 389 stations across Athens, Thessaloniki, national and Patras — merging each station's line memberships so interchanges are right. Falls back to the old list only if the bundle is unreadable. Web and Android already drew all networks (Android seeds every station from `lines.json` into its DB); this brings iOS to parity.
- **Ariadne owl on the map.** The Map tab suppresses the app-level Ask-Ariadne pill (the Locate + Vehicles buttons own the corner), which left iOS with no way to reach Ariadne from the map. Added a circular owl launcher at the top of the map's control column — same round owl mark as web and Android — opening the assistant sheet.
- **Compass no longer overlaps the header.** MapKit's default compass floated into the top-right under the `CompactTabHeader` (the map ignores the top safe area). Hidden it: this is a flat, north-up transit map with pitch disabled, and web + Android carry no compass either, so the header is now clean and consistent across platforms.

iOS 1.3.0 / Android versionCode 119. iOS-led release; Android + web rebuild unchanged for version alignment.

## 1.2.14 — 2026-07-27

Fares, nationwide. A complete ticket-price feature across web, iOS and Android, on grounded operator data.

- **Fares menu (all networks).** The tickets screen now spans every network — Athens (OASA), Thessaloniki (OSETH), all-Greece suburban (incl. the Patras A1/A/B/C zone grid), and intercity — grouped by operator, trilingual. Intercity is honestly shown as "at booking" rather than a made-up number. `/api/fares` grew from 14 OASA-only products to 24 across all operators; the bundle carries them so the native menu works offline.
- **Journey fare planner (from → to → price).** Pick a start and destination station and get the exact grounded fare (full + reduced, product, operator), or the official booking path for intercity. On web (in the fares card), iOS (SwiftUI, searchable station pickers) and Android (Compose, autocomplete). Verified live: Syntagma→Airport €9.00/€4.50, Rio→Patra €1.40, Thessaloniki→Sindos €0.80 (OSETH).
- **Ariadne answers fares.** "How much from X to Y" now returns the same grounded fare card in chat — KMP brain (Android), Swift mirror (iOS) and web — all offline. Fixed the stale "€0.90" canned answer to the current €1.20 integrated ticket.
- **One shared engine.** A single fare model (`ComputeFareUseCase`, 6 unit tests) with Swift + JS mirrors sharing identical grounded tables. It charges a trip on the local network the two stations share, so a Thessaloniki interchange that also sits on the national line is billed as a local suburban trip, not intercity — a real bug the tests now lock.
- All fare figures are transcribed from official operator sources (oasa.gr, oseth.com.gr, hellenictrain.gr); see docs/data/2026-07-27-fares-collection.md.

iOS 1.2.14 / Android versionCode 118. First iOS build to type-check the new SwiftUI fares screens.

## 1.2.13 — 2026-07-27

Station-position corrections + Ariadne button placement.

- **Molos** (was "Mylos") — the station on the Athens–Lamia line was an interpolated placeholder 3 km off the track. Corrected to the real Hellenic Train station **Molos** (Μώλος) at its OSM coordinates and re-tagged national.
- **Agios Vasileios** (Patras) — the PU2 terminus was interpolated in the sea; moved to the real OSM Rio suburb, and the PU2 line no longer runs into the water. Coords are real OSM, never invented. Fixed at the Pi source, reseeded, and propagated to all bundles.
- **Ariadne owl button (web)** moved from bottom-left (out of place) to bottom-right, clear of the info column, and lifted above the attribution/sheet on mobile so it never overlaps. Verified live.
- **Robustness:** the nightly seed refresh now preserves the full 31-line `shapes.json` (national geometry), so the 1.2.12 line-geometry fix can't silently revert.

iOS 1.2.13 / Android versionCode 117.

## 1.2.12 — 2026-07-27

Native map line geometry fix. On iOS and Android the national / intercity / rail-replacement-bus / Thessaloniki / Patras lines were drawn by splining through each line's own stations, so where an intercity line (few stops) and a suburban line (every stop) share a corridor they diverged instead of overlapping — the "lines are wrong on mobile" bug. Cause: the bundled `shapes.json` (OSM track geometry) shipped in the native apps carried only the 9 original Athens lines (A1-A4, M1-M3, T6, T7), while web's `shapes.json` carries all 31. So every nationwide line fell back to the station-spline on mobile.

- Synced the full 31-line `shapes.json` into all three native bundles (`core/data` composeResources, `androidApp` assets, `iosApp` Resources). Schema is byte-identical to web; the map reads it directly at mount (no seed-version bump needed). Now iOS/Android draw the exact real-track geometry web does, and shared corridors overlap correctly. Verified on the Android emulator: the red suburban (A3→Chalkida) and blue intercity (IC1→Thessaloniki) share the northbound Athens corridor and split at the real junction.

iOS 1.2.12 / Android versionCode 116.

## 1.2.11 — 2026-07-26

National + rail-replacement-bus departures now work, online and offline, plus the design-token foundation for the 2.0 redesign lands (spec only; no UI change yet). Tapping a national/IC/bus stop (Lamia, Lianokladi, Kalambaka, Volos, Kiato-Patra, Alexandroupoli, the tourist rails…) used to show a moving vehicle on the map but "no departures" in the sheet.

- **Live API fix (server).** The national/bus timetables (real HH:MM from `seed_greek_corridors.py`, transcribed from Hellenic Train) were never seeded into the production Pi DB — the deployed nightly unit had drifted from the repo and dropped `seed_greek_corridors` + `seed_thessaloniki`, and the API caches its DB connection from startup. Seeded the corridors (20 lines / 300 trips / 6911 stops) and restarted `syrmos-admin.service`; `/api/departures/next` now returns real times for TL1/IC1/KB1/VL1/DX1/KP1/RG1/AL1/KO1/PL1/PU1/PU2, verified live on the web sheet with the T8 "Scheduled" chip. A corrected nightly unit (re-adds the seeds + auto-restarts the API) is staged on the Pi for install.
- **Offline bundle fix (native, all platforms).** The apps bundle each line's per-train `trips`, but the boot seeder only fed metro/tram schedules into `schedule_entity`, so the offline departures fallback had nothing for the trip-based lines (the map's `TrainSimulator` read the same trips, which is why a bus animated while the stop stayed empty). `DataSeeder` now expands the bundled `trips` into `schedule_entity` — mapping the bundle's `mon_thu/fri/sat/sun` day-types to the query's `weekday/friday/saturday/sunday` — so iOS and Android show national/bus departures with no connection. `SEED_VERSION` bumped to 6 to re-seed existing installs. Verified the seed→query logic against the real bundle reproduces the live API's Lamia departures exactly.
- **2.0 design-token foundation.** The full 2.0.0 redesign is specified in [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) + [docs/design/REDESIGN_DESCRIPTION.md](docs/design/REDESIGN_DESCRIPTION.md) (nationwide nav, universal departures, structured Ariadne cards, tablet/web layouts) but is NOT implemented yet — this release ships only its foundation: semantic token aliases (`--sy-accent`, `--sy-text-primary`, `--sy-status-*`) across the generated web CSS, JSON, and Swift token sets, generated deterministically by `ops/designsystem/generate_tokens.py`. No visual change: the alias vars map to the existing values and nothing consumes them yet. The redesigned UI lands across a later 2.0.x.

iOS 1.2.11 / Android versionCode 115. The current app UI is unchanged; this is a data + foundation release.

## 1.2.10 — 2026-07-26

The batch that followed the map fix, brought native to parity with web and cleaned up a data seam.

- **Source-confidence, everywhere (T8).** Departures now say where the answer comes from — a calm chip reading Live / Scheduled / Estimated / Offline snapshot — on iOS, Android and web, trilingually. So a schedule-based time never masquerades as a live one.
- **Ariadne recovers instead of dead-ending (T9).** When she can't parse a question she no longer just declines: she offers the closest station ("did you mean Nikaia? Try 'next trains from Nikaia'") or nudges you toward what she can do — across the KMP brain, iOS and web, all offline.
- **One-glance hero on native (T7).** The iOS and Android home hero now carries the source chip and a "then 13, 23 min" follow-on list, matching the web hero.
- **Web: one search box (T6) + a cleaner face.** The search box now also asks Ariadne (type a question, get an "Ask Ariadne" row; press Enter and it routes to her) — no more two separate boxes. The header emoji controls are replaced with a clean line-icon set, and the Ariadne owl mark shows correctly (a circular launcher, the full lockup in the panel) instead of a squished logo.
- **Station-ID reconcile.** The Pi's `/api/station-offsets` uses a different station-ID scheme than the bundled data, which silently dropped many metro/tram/suburban stops to coarse band-only timing. The client now maps every offset to the right station by name, line-scoped so a same-name stop can't land on the wrong line — 474/474 stops resolve, verified against the live API, so every stop gets its exact offset again.

iOS 1.2.10 / Android versionCode 114. Web changes shipped rolling before this tag.

**Verified 2026-07-26.** iOS 1.2.10 (build 1785022495) reached VALID in TestFlight; Android versionCode 114 shipped to Play internal; web deployed (after fixing a wasmJs build break the reconcile had introduced with a JVM-only `Map.putIfAbsent`, which had silently blocked the T6 web deploy). On the live web the whole country renders on the CARTO base: searching a Thessaloniki metro stop (Sintrivani) flies to the red Line 1 / blue Line 2 network with the T8 "Scheduled" source chips on its departures, and searching "Patra" flies to the Patras suburban network (blue suburban + dashed rail-replacement bus + green national). The station-ID reconcile was verified against the live `/api/station-offsets` at **474/474** stops resolved, and re-hardened to be line-scoped so a same-name stop can never remap onto the wrong line. Android was confirmed rendering the Athens + national network live on an emulator; the iOS local simulator build is blocked by a watchOS 26.5-SDK / 26.2-runtime skew (the embedded watch app can't compile locally), so iOS is verified via the CI-built TestFlight artifact, not the local sim.

## 1.2.9 — 2026-07-21

**The map now looks the same on all three platforms.** Web was the reference (flat base, coloured line network, clean dots, directional-triangle trains); iOS and Android had drifted badly and web itself had two theme bugs. Fixed everywhere:

- **iOS: the coloured line network was invisible.** The flat CARTO base tile layer, which is opaque and replaces the map, was drawn at the top overlay level, so it painted straight over every route line. Only station and train markers showed. The base now sits at the bottom and the lines render on top of it. National, Thessaloniki and Patras corridors also draw now (iOS previously only splined the Athens lines).
- **iOS + Android: markers matched web.** Both platforms drew custom artwork - iOS bundled per-station icons with baked-in labels ("M3 AM"), Android per-station PNGs on selection - instead of web's clean coloured dot with a white ring. Both now draw the same simple dot (interchanges read as a white-cored ring), with the station name/detail living in the sheet, not the pin.
- **Trains are one directional triangle everywhere.** Web draws the whole fleet as line-coloured triangles pointing the way of travel; native gave metro and tram their own sprite badges. Native now uses the same triangle for every vehicle.
- **Line colours are the real per-line colours.** Web reads each line's exact colour from the data; Android was collapsing every line into one of five buckets, so national/Thessaloniki/Patras lines all came out purple. Android now reads the true per-line colour, matching web and iOS.
- **Web dark mode was pure black.** A brightness filter meant to darken the map crushed the already-dark base map's streets to invisibility. Softened so the street grid shows under the coloured lines again.
- **Web light mode text was washed out.** The app's dark colour tokens were keyed to the OS `prefers-color-scheme`, not the app's own light/dark toggle, so with the OS in dark mode but the app switched to light, every on-surface text (station title, search-result names, departure line names) went near-white and vanished on the light surfaces. The tokens now follow the app's own theme class, fixing contrast app-wide in every OS/app combination.

All platforms bumped to 1.2.9 (Android versionCode 113). Line weights, opacity (0.9), the bus dash and dot sizes were also aligned to web's exact values.

## 1.2.8 (iOS) — 2026-07-21

**iOS national + regional + bus moving vehicles.** iOS now shows moving vehicles for national intercity rail (IC/RG/KO/PL/DK/AL), the Thessaloniki and Patras suburban corridors, and the rail-replacement buses (KB/VL/DX/KP/TL/PU/PSB) — the same fleet Android and web already carry from 1.2.7. These lines have no live feed and no station-offsets table on the Pi, so iOS projects them entirely offline from the bundled timetables (`seed-schedules-v2/{lineId}.json` `trips[]`): for every trip whose day-type matches today, the current segment is found by wall clock and the chord between its two stations is interpolated. They render as the same directional triangles as the suburban fleet, and each carries its real line colour instead of a single purple. This closes the iOS-only gap that 1.2.7 deferred (iOS runs a separate Swift simulator from the KMP `MapViewModel`, so the KMP projector only reached Android). No Android or web change — both already ship this — so 1.2.8 is an iOS-only release; Android stays on versionCode 112 / 1.2.7.

## 1.2.9 and earlier rolling changes (shipped in 1.2.9 through 1.3.0)

**Viewport-culled map markers** (iOS, Android, Web). With the network nationwide (389 stops across Greece), every platform kept all of them live on the map at once, so zooming felt heavy and distant coastal lines (Katakolo, Corinth-Patras) painted over the sea at the edges of the Athens view. Now only stations inside the padded current viewport are drawn, and panning re-culls as you move. Also confirmed, after a deep investigation, that no station is actually mislocated: the dots that looked "in the sea" were real stations elsewhere in Greece (e.g. Alfeios on the Katakolo-Pyrgos-Olympia line) rendered off-screen, not bad coordinates.

**The map opens on Athens again, and zooming is calm** (iOS, Android, Web). Going nationwide made the map try to show the whole country at once: it opened fit to every station (Ioannina to Alexandroupoli to Kalamata), so Athens was a tiny corner that decluttering emptied out, and Android was worst of all (the fit clamped to min-zoom 9 and centred on the national centroid in central Greece, pushing Athens off-screen entirely). Now every platform opens framed on the Athens network with metro, tram, suburban and live trains on screen; zoom out for the country, and GPS "locate me" still recenters on you. Decluttering is also softer: detail resolves nearer the default view (major hubs from z8, all stops from z10), dots keep one size across zoom instead of resizing at z12, and per-station artwork only shows for the stop you select instead of every dot ballooning at z14, so far fewer markers snap in and out as you pinch. Also clamped the train polyline-follow so a shape that loops or runs past its terminus (the T7 tram past Voula) can no longer fling a train into the sea.

**Vehicles declutter with the map** (iOS, Android, Web). At country and regional zoom the whole Athens metro + tram fleet used to render as ~48 individual dots stacked on one ~15px point at the coastline, which read as "trains scattered in the sea." Stations already collapse to lines-only at that zoom; the moving vehicles now follow the same rule and are hidden below the regional band (`MapDesignTokens.majorHubMinZoom`), reappearing on their lines when you zoom into Athens. Web gates the simulated-train markers and re-renders on `zoomend`; iOS hides the train annotations in `regionDidChangeAnimated`; Android keys the simulated + live train overlays on the zoom band. Diagnosed from the live map: 47 of 48 vehicles were correctly on the Athens network and only 1 coastal tram sat marginally offshore, so the "sea" was a decluttering artefact, not wrong coordinates.

**Modern dot markers on the map** (iOS, Android, Web). The big teardrop station pins are replaced by small, centre-anchored dots in the line colour with a crisp white ring — roughly half the size (10-13px vs 22-32px) so the now country-wide map stays clean and lightweight. The mode glyph only appears when a stop is selected or you zoom in; interchanges read as a white-cored "target" ring; suspended lines still grey out. The whole marker + line design (dot sizes, ring/halo widths, glyph rule, greyed colour, bus/greyed dash) now lives in **one shared source of truth** (`MapDesignTokens` in core:common), mirrored to iOS and web so the three maps can't drift.

**Ariadne, cleverer again.** Two more grounded capabilities on top of the earlier batch: **which lines serve a station** ("ποιες γραμμές περνάνε από X" / "cilat linja shërbejnë X") and **how many stops / how far between two stations** (stop count + duration from the deterministic planner). All trilingual, across iOS/Android/Web, wired through the rule parser, dispatch, session memory and the on-device LLM tier.

**Ariadne got cleverer** across iOS, Android and Web (shared KMP brain + Swift and JS mirrors, all trilingual, still tool-only — she never invents a transit fact):

- **Understands more phrasings.** Broadened the EN/EL/SQ cue vocabularies (departures, planning, arrivals, "I want to go", "navigate to", …) so far more natural wording resolves without a dead-end. Albanian station recall widened from ~4 stops to ~20 high-traffic ones, including real Albanian exonyms (Selanik → Thessaloniki, Athina → Athens) — and the web build now applies those aliases too (it previously didn't).
- **New capabilities.** **First train** of the day (mirror of last train), **step-free accessibility** ("is X wheelchair accessible / does it have a lift?", answered from the bundled per-station flag), and **reverse trip** ("and back?" / "return" / "kthimi" flips the last route). Each is deterministic and grounded.
- **Smarter conversations.** Day-change follow-ups ("what about tomorrow?", "the weekend?") re-run the last departures query without repeating the station, and web now has durable session memory (current station + last route) so multi-turn works there like it already did on iOS/Android. The Swift session context was brought back into parity with the KMP one (last intent, current line/direction).
- **Smarter LLM tier.** The on-device model's few-shot classification prompt and GBNF grammar (all three copies — Kotlin, web `.gbnf`, Swift) plus the IntentGrounder now cover the new intents, with EL/SQ worked examples so Greek and Albanian stay first-class.

All-Greece rail coverage, shipped as bundled data + web with no client-code changes (the app is fully data-driven for lines/stations/geometry):

- **Five more corridors from the complete Hellenic Train timetable** (31-corridor authoritative source, 18 Jul 2026), bringing the network to **30 lines**: **TL1 Tithorea - Lianokladi - Lamia - Stylida** replacement bus (15 stations, 28 daily services with the Lianokladi hub and per-run stop patterns), **KO1 Katakolo - Pyrgos - Olympia** tourist rail (Mon-Sat), **PL1 Pelion** tourist rail (Ano Lechonia - Milies, weekends - only the origin departure is officially published so only that is seeded, nothing invented), and **DK1 Diakopto - Kalavryta rack railway** which is suspended since 13 March 2026 and therefore renders greyed with no departures. Exact per-station times transcribed from the official timetable; coordinates are real OSM rail-station nodes.
- **Patras University campus shuttles** (PU1 Kastelokampos–University–Hospital loop; PU2 Kastelokampos–Agios Vasileios), the last two of the 31 corridors, completing the entire timetable file. Real OSM University-of-Patras building coordinates where mapped (Chemical Engineering, Medicine, Pedagogy, Rectory); OAED and Agios Vasileios are unmapped campus/suburb bus stops placed on the known route. Network total: **32 lines** (athens 10, national 11, thessaloniki 6, patras 5).

- **Real OSM track geometry for every national + Patras corridor.** IC1, RG1, TP1-TP4, PS1/PS2/PSB now follow the real curved rail alignment (stitched in OSM relation-member order from the Greece extract) instead of straight station-to-station chords. Injected into all five bundled `shapes.json` copies. Every served station sits on its line (0-76 m); a few multi-km straight segments on IC1/RG1 are genuine gaps in OSM's mapping of the new high-speed alignment, not stitching errors (not hand-drawn, to avoid inventing track).
- **Two more national corridors.** **AL1 Alexandroupoli - Orestiada - Ormenio** (Evros/Thrace, 31 stations, four daily trains 1680/1681/1682/1683 incl. the Orestiada short-turns) as a real suburban train; **KB1 Paleofarsalos - Kalambaka** (Sofades, Karditsa, Trikala) as a rail-replacement **bus** (C881/C1889/C1880/C888), which is how Hellenic Train serves that branch today. Every time transcribed from the official Hellenic Train timetable, stored as exact per-station departures; coordinates and track geometry from the OSM service relations (14122316/14122315 for AL1, 14007294/14007293 for KB1), never invented.
- **Three rail-replacement bus corridors** (mode = bus, region national), completing the country's replacement-bus network alongside KB1: **VL1 Volos - Larisa** (Velestino; 7 buses each way daily), **DX1 Drama - Xanthi - Alexandroupoli** (15 stations, nine services with per-run stop patterns incl. the Drama-Alexandroupoli express C602 and the Xanthi-Drama C679 that skips Toxotes), and **KP1 Kiato - Patra** (via Diakopto on the longer runs; 40+ services incl. Friday and Friday+Sunday-only departures). Exact per-station times from the official Hellenic Train timetable. Geometry from OSM rail alignments; KP1's coastal Kiato-Patra segment was rebuilt by axis-projection ordering because relation 1769919's member order is scrambled (member-order and greedy stitching both produced 45-50 km phantom jumps). Total network is now **26 lines** across four regions (athens 10, national 7, thessaloniki 6, patras 3).
- Fixed the nightly importer that scoped `DELETE` too broadly and wiped every non-Athens line overnight; corrected station-region tagging so Evros/Kalambaka read national and Patras reads patras.

## 1.2.7 (2026-07-21)

**A flat minimal map + moving vehicles for the whole network** (iOS, Android, Web).

- **Flat, label-free base map** like the railway.gov.gr live tracker: CARTO Positron
  (light) / Dark-Matter (dark), theme-aware, replacing the busy street map on all
  three platforms. The coloured line network + station/train markers are the whole
  picture - a clean transit diagram, not a road atlas.
- **Moving vehicles for suburban, national rail, and rail-replacement buses**, not
  just metro/tram. Suburban A1-A4 ride the Pi `/api/live-positions` feed online and
  bundled station-offsets offline; national rail + buses (which have no live feed or
  offsets) are projected client-side from the bundled timetables - every in-progress
  trip is interpolated by the wall clock along its line. On native this is a new KMP
  `projectScheduledTrains` wired into the map view-model; on web it's the JS twin.
- **Directional triangles**: national/bus/suburban vehicles render as a triangle
  pointing the way they travel (metro/tram keep their per-line directional sprites),
  so a moving train is never mistaken for a station dot.
- **Web map fixes** from the canvas rework: stations are tappable again (nearest-stop
  within a forgiving radius), and the simulator's name-resolution is scoped per line
  so a shared station name can't pull a train onto the wrong track.
- Carries the tightened on-device coordinate audit from 1.2.6.

## 1.2.6 (2026-07-21)

Tightens the on-device station-coordinate audit added in 1.2.5. The audit box now
covers the full Athens-line extent (north to the A3 Chalkida line ~38.46, west to
the A4 Kiato terminus ~22.73: box 37.7-38.55 N, 22.65-24.15 E), and is scoped to
Athens lines (M/T/A) on every platform. National lines (IC/RG toward
Lamia/Thessaloniki, Patras) legitimately span all of Greece and can't be boxed
into Attica, so they're no longer audited. Verified: 0 of 201 Athens-line stations
fall outside the box, so the audit stays silent unless a stop is genuinely
misplaced. No user-facing change; debug logging only.

## 1.2.5 (2026-07-21)

Native store build carrying the map overhaul that shipped to web after 1.2.4.

**The map opens on Athens and stays light** (iOS + Android). Every platform used to fit the map to the whole nationwide network at launch, so Athens was a tiny corner (Android was worst: it clamped to min-zoom and centred on the national centroid in the sea, leaving Athens off-screen). Now it opens framed on the Athens network. Decluttering is softer (major hubs from z8, all stops from z10, one dot size, no z14 artwork balloon), moving vehicles hide below the regional zoom so the fleet never piles into a coastal blob, and the map viewport-culls so only stations in view stay drawn. The train polyline-follow is clamped so a looping/past-terminus shape (T7 tram past Voula) can't fling a train into the sea. Adds an on-device station-coordinate audit that logs any Athens stop outside the Attica box (a quiet console confirms the bundled data is clean). Web additionally moved to canvas circle markers (preferCanvas) so dots stop lagging behind the tiles on zoom.

## 1.2.4 (2026-07-19)

Native store build to carry the country-zoom map change that shipped to web after
1.2.3 was cut.

**Lines-only country zoom (iOS + Android).** At the whole-Greece view the map now
draws only the coloured line network, no station dots (below
`MapDesignTokens.LINES_ONLY_MAX_ZOOM`); dots resolve on zoom-in (major cross-modal
hubs, then all interchanges, then every stop). Matches the web behaviour and reads
as a clean rail atlas instead of a scatter of hub rings over the Aegean.

The web-only 2.0 work since 1.2.3 (the app-shell bottom sheet, the answer-first
one-glance hero, the compact mobile header) is not in these native binaries; the
native hero and app-shell remain to be built.

## 1.2.3 (2026-07-19)

The first store binaries to carry the all-Greece network, the cleverer Ariadne, and
the 2.0 design-system foundation. Everything in the rolling section above (modern dot
markers, Ariadne v2, the 32-line all-Greece coverage with real OSM geometry) is now in
the iOS + Android builds, plus:

**Country-wide map legibility (iOS, Android, Web).** A three-tier zoom rule replaces
the old "draw every stop" behaviour that turned the country view into confetti. At
country zoom only the major cross-modal hubs show (a genuine transfer whose lines span
2+ types, ~20 Greece-wide); at regional zoom all interchanges; in a city every stop.
The coloured line strokes carry the network in between. Roughly 389 markers down to ~20
at country zoom. A missing station smart-code icon now falls back to the dot instead of
the browser's broken-image placeholder. The rule lives in the shared `MapDesignTokens`.

**Design system 2.0 foundation (the "Hellenic Rail Atlas" light-first identity).** A
canonical KMP design-token module (`core:designsystem` `theme/tokens/`) is now the single
semantic source for colour, typography, spacing, shape, and the "train glide" motion
easing. A generator emits the Swift and CSS-variable mirrors so the three platforms never
drift. The identity is adopted at the foundation: the Android Compose theme, iOS adaptive
semantic colours (dark mode preserved), and the web chrome (Aegean-blue brand, warm
station-white surfaces, warm near-black text). Two new trust-layer catalog components
landed — the source-confidence chip and the offline pill.

## 1.2.2 (2026-07-10)

Fixes after tagging:

- **Android on-device LLM never actually ran (silent, since the feature shipped).** `libsyrmos_llama.so` was linked against `libc++_shared.so`, which is not packaged in the APK/AAB, so `System.loadLibrary("syrmos_llama")` always threw `dlopen failed: library "libc++_shared.so" not found`. `LlamaBridge` caught it, set `available=false`, and Ariadne fell back to the rule parser — so nothing crashed and nothing looked wrong, which is exactly why it went unnoticed. llama.cpp's own libs static-link libc++ and loaded fine; only the JNI shim asked for the shared runtime. The shim is now linked with `-static-libstdc++` (matching llama.cpp), needs no extra runtime, and all five libs load on device. Verified on an arm64 emulator: libs load, the 1.5B GGUF loads (~1.2 GB RSS) and inference executes multi-threaded. Inference latency on a real device is still unmeasured (the emulator is too slow to time it meaningfully).

- **Android 16 KB page-size support (blocking Play).** The vendored llama.cpp libs (`libllama`, `libggml*`, `libsyrmos_llama`) were linked with the default 4 KB max page size, so Google Play rejected the 1.2.2 bundle ("Your app does not support 16 KB memory page sizes") and on a 16 KB-page device the libs would fail to load, silently dropping Ariadne to the rule parser. llama.cpp's CMake does not set the alignment and the NDK default did not apply, so the rebuild now passes `-Wl,-z,max-page-size=16384` explicitly to both the CMake link and the JNI shim. All five arm64 libs now report LOAD align `0x4000`. The recipe + a verification step are documented in `composeApp/src/androidMain/jniLibs/README.md`.

Headline: Ariadne gets a **real on-device LLM** (one model, every platform) plus a **cleverer rule parser**. The model does the understanding step only (messy message to an approved intent and quoted slots); it never generates a transit fact, and the deterministic rule parser stays the offline floor, so nothing regresses when the model is absent.

- One model everywhere via **llama.cpp** (GGUF), replacing the old per-platform "smart" tiers (Apple Intelligence on iOS, Gemini Nano on Android, nothing on Web) that lit up on almost no devices. Model: **Qwen2.5-1.5B-Instruct (Q4_K_M)**, chosen after a real browser benchmark on messy trilingual queries where 0.5B scored 3/9 (failing every Greek and Albanian case) and 1.5B scored 9/9. Albanian and Greek stay first-class.
- **On-demand, not bundled**: only the small runtime ships with the app; the ~1.1 GB model is downloaded in-app on explicit opt-in and cached, so first install stays lightweight. Single shared manifest (`AriadneModelManifest`, pinned URL + SHA-256); the binary is fetched + verified by CI, never committed.
- **Web (shipped, browser-verified)**: `@wllama/wllama` (llama.cpp to WASM) runs the model in the browser, cached in OPFS, multi-threaded under COOP/COEP with a single-thread fallback. A GBNF grammar locks output to the exact intent JSON, so an invalid intent is impossible. Output is grounded by the existing `IntentGrounder` and dispatched to the same deterministic use cases.
- **Cleverer rule parser**: expanded EN/EL/SQ cue coverage (departures, last train, plan, find, fare, help) so more natural phrasings resolve without the model, and fixed a bug where a weather question about a place we do not serve ("what's the weather in London") answered with Athens weather instead of declining.
- Shared `IntentGrounder.classificationPrompt` rewritten as a few-shot trilingual prompt (also improves the native paths).
- **iOS + Android native runtimes**: shipped and build-verified. Pinned llama.cpp (Swift `LlamaSession` via an xcframework on iOS; JNI `libsyrmos_llama` on Android arm64), the same on-demand model, an in-app "Download Ariadne's brain" control with live progress, and grammar-locked JSON grounded by the shared `IntentGrounder`. The deterministic rule parser is the floor on both until the model is downloaded. On-device inference is verified per device at first run.

## 1.2.1 (2026-07-08, in review)

Headline: the **Ariadne co-pilot** (Phases 1 to 4) plus first-class Albanian coverage. Ariadne now keeps session context ("I'm at Syntagma" then "go airport faster"), answers honest station status backed by live STASY advisories, ranks routes by preference with a weather tilt, reasons about live-vs-seasonal weather, and drives a live Apple Watch app + widgets with voiced (TTS) read-back. Details below.


Companion surfacing, implemented on iOS, Android and Web (UI on top of data the app already has):

- Answer-first home: the lead element is now one actionable line ("Next M2 to Syntagma, 4 min") with the timetable grid demoted below it. Compose (`feature/home`) and SwiftUI (`HomeView`) both compute the soonest departure across the nearest station's lines via the existing projector.
- Offline-alive indicator: a small pill surfaces whether arrivals are live or "Running offline · predicted from schedule". Driven by a shared freshness rule (`core/common` `FreshnessEvaluator`, mirrored in iOS `DataFreshness`); live network success paths mark the data fresh, and it ages back to predicted after the window.
- Single-line "last train home" teaser: inverts tonight's last departure into "Last M2 · leave by 00:14" for a single line, no transfers. New `GetLastTrainUseCase` (Kotlin) and `ScheduleProjector.lastTrainTonight` (iOS), ahead of the multi-leg version in 1.2.

Ariadne, the offline transit assistant (a constrained, tool-only intent router, implemented on iOS, Android and Web; brought forward from the 1.5 roadmap slot in constrained form):

- "Ask Ariadne" from the Home screen opens a chat that parses natural language fully offline and dispatches to the deterministic use cases the app already ships (departures, last train, trip planning, line info, ticket prices, service alerts, find station, favorite a station). It never generates a transit fact; it picks an approved action and the projector/planner answers.
- Day-aware departures: "M3 from Syntagma this weekend / tomorrow / Saturday" projects that whole service day from 00:00 (`ComputeDeparturesFromBandsUseCase.invokeForDay`, the projector dayOffset path on iOS), not just today.
- Fares: surfaces the relevant ticket products with prices (standard single vs the airport ticket). Favorites: a real `FavoritesRepository` (Kotlin, SQLDelight) + UserDefaults store on iOS persist the toggle.
- iOS trip planning now runs a full Dijkstra (`JourneyPlanner.swift`, with transfer edges between co-located interchange ids) so iOS matches the Android/Web `PlanJourneyUseCase` for any number of transfers.
- Trilingual by design (EN/EL/SQ) via a rule parser, so no supported language degrades. Shared brain in `core/domain/assistant` (`AthensTransitParser`, `AssistantIntent`), mirrored in Swift under `iosApp/.../Features/Assistant`. Scope is fenced to Syrmos and Athens public transport; weather is accepted only as a routing constraint, everything else is declined.
- This also wired `PlanJourneyUseCase` (Dijkstra routing that already existed but was never in the DI graph) into the Compose app.
- Co-pilot Phase 1 (iOS + Android + Web): Ariadne now keeps a small session memory (`AssistantSessionContext`), so "I'm at Syntagma" then "go airport faster" resolves without re-asking the origin. New intents `SetCurrentLocation` ("I'm at X / I'm here / jam te X / είμαι στο X") and `StationStatus` ("is X open/closed/working?"), plus a `RoutePreference` (fastest / fewest-changes) parsed from cues like "faster" / "γρήγορα" / "shpejt" / "direct". Station status is honest: it leads with any matching STASY advisory and otherwise falls back to the timetable ("no live closure alert… should be operating… check STASY") instead of asserting a station is open. A new `ServiceAdvisoryMatcher` links the same STASY announcements + severe-weather signal shown on Home to the station, line, or route the user asked about, so a live closure (e.g. the M3 evening station closures) is surfaced in the answer and appended as a "Heads up:" caveat on trip plans. Shared brain in `core/domain/assistant`, mirrored in Swift (`AriadneDomain.swift`, `AriadneParser.swift`, `AriadneModel.swift`) with parity tests both sides. Voice spec captured in `docs/ARIADNE_VOICE.md`. The iOS Swift `STASYAnnouncement` model now decodes `affectedLines` / `severity` / validity window (feed, cache, and bundle paths), so iOS advisory matching reaches whole-line notices and true closure severity exactly like KMP.

Weather and on-device intelligence:

- Weather card on Home (iOS + Android + Web): current conditions from Open-Meteo (free, no key, CORS-friendly), styled as an immersive condition-tinted gradient with the temperature, today's high/low, a next-six-hours forecast strip, and feels-like / humidity / wind. `WeatherService`/`WeatherRepository` in KMP, `WeatherStore` on iOS; central-Athens default so Web and no-location launches still show something. (Visual language inspired by the shared weather-app design; the Figma community file couldn't be extracted over MCP due to the plan's rate limit.)
- Weather-aware routing: Ariadne's rainy-day trip answers now read the cached weather snapshot and the route's exposure (metro underground/sheltered, tram open-air) via `StationComfort`, and give real advice instead of a generic disclaimer. Degrades honestly with no cached weather.
- Weather context, live-vs-seasonal split (Ariadne Phase 2, iOS + Android + Web): a structured `WeatherContext` (source LIVE / SEASONAL_FALLBACK / UNKNOWN, a dominant state of NORMAL/HOT/RAINY/WINDY, and heat/rain/wind risk bands) now drives trip advice. When there's a live reading Ariadne says "It's hot in Athens right now…"; when there isn't, it falls back to the Athens seasonal norm (`AthensClimate` monthly table) phrased honestly as "…this time of year is usually hot and dry", never as "now". Route exposure plus the weather state add a targeted nudge ("prefer an underground route to avoid long sun-exposed waits"). Built in `core/model/weather/WeatherContext.kt` + `core/domain/assistant/WeatherContextBuilder.kt`, mirrored in Swift, tests both sides. Also added an injectable clock seam (`currentAthensTime/Date(clock = Clock.System)`) so time- and season-dependent logic is deterministically testable.
- Route ranking (Ariadne Phase 3, iOS + Android + Web): when a trip has a real choice, Ariadne no longer just reports the fastest route. `RouteRanker` scores candidates by the user's `RoutePreference` (fastest / fewest-changes / balanced) with an adverse-weather tilt, so on a hot or wet day a slightly slower but sheltered all-metro route can win over a faster tram/surface one, and she says why ("The faster route is more exposed; in this weather I'd take this one"). `PlanJourneyUseCase` gained a `metroOnly` alternative for the sheltered option. Athens' network rarely offers genuinely distinct rail routes, so this mostly adds honest framing plus the metro-vs-tram weather tilt where a corridor has both. Built in `core/domain/assistant/RouteRanker.kt`, mirrored in Swift, tests both sides.
- Journey-specific fares: `ExplainFare` carries the trip endpoints, so an airport fare is derived from an actual airport-station endpoint, not just the keyword.
- Ariadne on-device LLM front-end: on Apple Intelligence devices (iOS 26+), `AriadneBrain` uses Apple Foundation Models to rewrite fuzzy input before the deterministic parser classifies it; the model never picks intents or invents facts, and older devices/Simulator fall straight through to the rule parser. KMP has the matching `AssistantQueryNormalizer` seam (no-op default) for a future Android Gemini Nano backer.

Track this departure (Tier 2 primitive, shared `core/common` `TrackedDeparture` / `DepartureTracking`):

- "Track" on the Home hero pins a live countdown card that ticks offline on all three platforms.
- iOS: full Live Activity. ActivityKit integration in `DepartureTracking.swift` (`SyrmosTrackingAttributes` + start/update/end), `NSSupportsLiveActivities` set, and a real `SyrmosWidgetExtension` target (`iosApp/SyrmosWidget/`) that renders the Lock Screen + Dynamic Island. The extension is built and embedded into the app by the normal scheme build.
- Android: the parallel surfacing is an ongoing count-down notification (`DepartureTrackingNotifier`), driven by the same shared `DepartureTracking` primitive, with a live chronometer and a `POST_NOTIFICATIONS` request on Android 13+.
- Web: the in-app pinned countdown card is the surface; there is no OS-level equivalent.
- Tracking card redesign (all three platforms): the old side-by-side "Tracking your train" and "NEXT TRAIN" cards duplicated the same countdown. The tracked card is now a single Uber-style block with a LIVE pulse, an "Arriving <Station>" subtitle, a headline countdown, a progress bar that fills as the target time approaches, line badge and destination, and a bottom "Stop tracking" action. The answer-hero card is hidden while tracking is active so there is exactly one countdown on screen. Line accent color is now derived from the tracked line (M1 green / M2 red / M3 blue / tram orange / suburban purple), not hard-coded to metro blue.
- Track any train (all three platforms): a "Track a train" chip on Home opens a linear four-step picker (Line → Direction → Station → Departure) so users can pin any train, not just the one at the nearest station. Shared Compose `TrackPickerSheet` on Android + Web, mirrored SwiftUI `TrackPickerSheet` on iOS, both hydrating a `TrackedDeparture` and handing off to the shared primitive.
- iOS Live Activity redesigned to mirror the in-app card: LIVE pulse eyebrow, "Arriving <Station>" header, huge line-tinted countdown, progress bar, line badge, and destination trailer. `ContentState` gains an optional `progress` field the app fills on each tick.
- Live Apple Watch app + widgets (iOS, "Phase 4"): the Watch departures screen was redesigned to a station header + "Syrmos Watch", a red "now" hero for the imminent train, line-badge rows with clock + minutes, an "Updated" line, and a "Next trains nearby" screen. It now ticks **live every second on-wrist** via `TimelineView(.periodic)` reading each departure's absolute `targetEpoch` (the phone sends absolute target times + the app language, so the countdown stays live between pushes without battery-heavy polling). Added **voiced Ariadne, read-back only**: a speaker control reads the soonest departure aloud via `AVSpeechSynthesizer`, trilingual (EN/EL/SQ) and offline. iOS home-screen widgets now show an OS-native self-ticking countdown (`Text(timerInterval:)`) that updates every second with no timeline-reload cost and flips to a red "now", the correct way around WidgetKit's reload budget. Scope: these live surfaces are Apple-only (Apple Watch + WidgetKit); Android Glance/Wear and Web PWA live-updating are a later follow-up.

Tooling:

- Fixed `./gradlew check`: `composeApp` configured its `android {}` block directly and never inherited `SyrmosKmpLibraryPlugin`'s lint disables, so Google's `NonNullableMutableLiveDataDetector` crashed lint on `PlatformModule.kt`. Mirrored the lint-disable block into `composeApp`.

Fixes:

- Albanian (SQ) station coverage: the assistant vocabulary now carries Albanian and common Latin/Greeklish station spellings ("Aeroport"/"Aeroporti" for the airport, "Pireas"/"Pireu", "Sintagma") on top of the bundled EN + EL names, so Albanian input resolves as reliably as EN/EL instead of relying on fuzzy typo-matching. Albanians are a large Athens community, so SQ is treated as first-class. Built in `AssistantVocabularyBuilder` (KMP) and mirrored in the iOS `fromSyrmosData()` builder, with tests both sides (the iOS test runs against the real bundled data). Long-term the bundled station data should gain a `nameSq` field.
- Dark-mode contrast on the severe-weather card (iOS + Android + Web): the card fill was a hard-coded cream that never adapted, so the semantic body text washed out to near-invisible light-on-cream in dark mode. The fill now flips to a deep amber-brown in dark mode (keeping the amber warning identity), the title/border accent brightens for legibility, and the phone-number badges keep their deep-orange chip so the white glyphs stay readable. The Home "Service Alerts" card had the same latent cream-in-dark-mode issue on Compose and was brought to parity.
- Ariadne engine settings row (iOS): when Apple Intelligence is supported but switched off, the row now shows an "Open Settings" button that deep-links to the system Settings app (matching the "Turn it on in Settings" copy), instead of a "Learn More" button that opened a generic `support.apple.com/apple-intelligence` marketing page. The stale in-app Safari plumbing that only fed that link was removed.

Quality:

- iOS XCUITest target. Current CI iOS job is build-only; unit tests exist under `iosApp/iosAppTests/` but no UI test target yet.
- Compose snapshot tests for station detail and station list rows.
- Playwright end-to-end smoke against the live web bundle.

Infrastructure:

- TestFlight upload automation. iOS workflow does not exist yet; secrets (`APPLE_ID`, `APP_SPECIFIC_PASSWORD`, `APP_STORE_CONNECT_API_KEY`) must be added before the upload job can run.

## Older releases

### 1.1.1 (iOS build 41 / Android code 50)

Bug-fix build, no visual changes.

- Home Screen widget no longer hangs on the skeleton. The one-shot widget location request is now raced against a hard 2-second timeout (`withTaskGroup`), so a widget process that never gets a GPS callback falls through to the picked station instead of awaiting forever. `resolveStation` precedence is: nearest (time-bounded) then the picked station then a last-ditch nearest.
- Widget schedule hydration is now defensive: the timeline provider touches `SyrmosSchedulesStore.shared` on the main actor before projecting (forcing the bundle hydration), and if the `seed-schedules-v2` folder were ever missing from the built `.appex` it falls back to `SyrmosData.sampleDepartures` so the widget never renders zero rows. `os.Logger` breadcrumbs (`subsystem: com.syrmos.widget`) trace the path taken.
- Ariadne engine diagnostic in Settings, both platforms. iOS maps `SystemLanguageModel.availability` to an explicit `AriadneBrain.Availability` (available / Apple Intelligence off / model downloading / device ineligible / OS too old) and shows "Clever mode" vs "Rule parser" with an actionable Learn More link. Android mirrors it via a shared `AriadneEngineStatus` derived from the ML Kit GenAI (Gemini Nano / AICore) feature status.

### 1.0.5 (iOS)

iOS-only patch bump. Android remained on 1.0.4.

### 1.0.4 (Android, code 33)

- Suburban offline projection: A1 to A4 project offline like metro and tram
- Bottom-bar padding fixes
- iOS test target wired with XCTest unit suites (`NearbyStationDestinationTests`, `ScheduleProjectorTests`, `SuburbanProjectionTests`)
- Admin UI cleanup pass

### 1.0.3

- M3_AIR full route, offline announcements
- Albanian localisation, web i18n
- AGSL liquid glass shader on Android, map FAB fixes, train toggle
- Animated station sheet, real-time refresh, departure source priority fix
- Projector: 120 min slack on the rule-gate, rollover ignores horizon when empty, future bands emitted, hours formatter
- Fully offline by default; "Check now" is the only network path
- Android dark theme matched to iOS Settings; tab bar and header text fixes
- Map train markers fix on Android
- Web Google Play badge links to store listing
- Launch screen artwork matching iOS

### 1.0.1 and earlier

For the foundational work (June 2026): KMP scaffolding, Compose Multiplatform setup, SQLDelight database, Koin DI, Voyager navigation, frequency-band projector, OSM line geometry pipeline, Hellenic Train SSE relay, FastAPI admin on the Pi, Pi-hosted `api-syrmos.peterdsp.dev` with ETag-driven endpoints, daily upstream-source watcher, suburban explicit-trip ingestion (602 trips, 9,832 stop rows), zoom-aware map markers, OASA fares endpoint, STASY announcements pipeline with Greek-to-English translation, dual-license adoption, Athens transit reference package, iOS folder restructure into `App/Core/DesignSystem/Features/Shared/Views/Resources`.

Full per-date detail is in the Revision Log at the bottom of [docs/CASE_STUDY.md](docs/CASE_STUDY.md).

---

## How to update this file

When you cut a release:

1. Add a new `## <version>` entry at the top of the version list.
2. Bump the `Current shipping` line at the top.
3. Tag the release (`v<version>`) so CI ships it.

Source of truth for current shipping versions:

- Android: `androidApp/build.gradle.kts` (`versionName`, `versionCode`)
- iOS: `iosApp/Syrmos.xcodeproj/project.pbxproj` (`MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`)
