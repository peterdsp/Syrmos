# Changelog

User-facing and architectural changes to Syrmos. Keep this file up to date with every release. Format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

Current shipping: **iOS 1.0.5**, **Android 1.0.4**, **Web** (rolling).

The long-range product roadmap by version (1.1 through 2.0, with quarterly targets) lives in [docs/CASE_STUDY.md, Appendix K](docs/CASE_STUDY.md#appendix-k--product-roadmap). Detailed historical context for each shipped change lives in the same file's Revision Log. This changelog summarises the version-to-feature mapping.

Product direction: Syrmos is a companion, not a schedule. Every feature is measured against the answer-first / proactive / reassuring / low-decision rules in [docs/PRODUCT_PRINCIPLES.md](docs/PRODUCT_PRINCIPLES.md).

## Unreleased (1.0.x hotfix window)

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

Weather and on-device intelligence:

- Weather card on Home (iOS + Android + Web): current conditions from Open-Meteo (free, no key, CORS-friendly), styled as an immersive condition-tinted gradient with the temperature, today's high/low, a next-six-hours forecast strip, and feels-like / humidity / wind. `WeatherService`/`WeatherRepository` in KMP, `WeatherStore` on iOS; central-Athens default so Web and no-location launches still show something. (Visual language inspired by the shared weather-app design; the Figma community file couldn't be extracted over MCP due to the plan's rate limit.)
- Weather-aware routing: Ariadne's rainy-day trip answers now read the cached weather snapshot and the route's exposure (metro underground/sheltered, tram open-air) via `StationComfort`, and give real advice instead of a generic disclaimer. Degrades honestly with no cached weather.
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

Tooling:

- Fixed `./gradlew check`: `composeApp` configured its `android {}` block directly and never inherited `SyrmosKmpLibraryPlugin`'s lint disables, so Google's `NonNullableMutableLiveDataDetector` crashed lint on `PlatformModule.kt`. Mirrored the lint-disable block into `composeApp`.

Quality:

- iOS XCUITest target. Current CI iOS job is build-only; unit tests exist under `iosApp/iosAppTests/` but no UI test target yet.
- Compose snapshot tests for station detail and station list rows.
- Playwright end-to-end smoke against the live web bundle.

Infrastructure:

- TestFlight upload automation. iOS workflow does not exist yet; secrets (`APPLE_ID`, `APP_SPECIFIC_PASSWORD`, `APP_STORE_CONNECT_API_KEY`) must be added before the upload job can run.

## 1.1 (target Q4 2026)

Per [Appendix K](docs/CASE_STUDY.md#appendix-k--product-roadmap):

- iOS Home Screen widget (WidgetKit, next 3 departures at a favorited station)
- Lock Screen Live Activity + Dynamic Island (next train, transfer countdown, destination progress), sharing the widget's departure-timeline pipeline
- Wear OS / watchOS companion (independent app, glanceable next departure + complication)
- Platform-direction slice ("trains to X depart from the left platform")
- Accessibility improvements

## 1.2 (target Q1 2027)

- Trip planner: point-to-point routing using the embedded line topology
- Live journey confidence score ("you'll make it" / "tight transfer" / "take the next one"), the face of the planner
- Multi-leg "last train home": must-leave-by time across transfers

## 1.3 (target Q2 2027)

- National InterCity rail coverage (E85 corridor: Athens-Thessaloniki, Athens-Patras)

## 1.4 (target Q3 2027)

- Thessaloniki suburban (THESLAR corridor)

## 1.5 (target Q4 2027)

- AI chat helper for natural-language schedule queries

## 2.0 (2028+)

- TBD. Either a redesign or a regional expansion (Patras, Heraklion).

---

## In progress: 1.2 "Widgets Everywhere" (branch `feat/1.2-widgets`, iOS build 100 / Android code 100)

Putting the offline-prediction intelligence on every home screen, lock screen, and wrist. Measured against the widget philosophy in [docs/PRODUCT_PRINCIPLES.md](docs/PRODUCT_PRINCIPLES.md) (single-glance, no-scroll, line-tinted, dark-first).

- Shared widget design system (`iosApp/iosApp/DesignSystem/WidgetKit/`): `SyrmosLineTokens` (single source of truth for line colors + labels), `LiquidGlassTile`, `LinePill`, `StationStripCompact` / `StationStripFull`, `DepartureRowCompact` — all usable by both the app and the widget extension.
- iOS widget families (`SyrmosWidgets.swift`): Next Train (small + medium), Live Departures (large), Near Me (large), All Lines Status (extra-large), Weather + Alerts (extra-large), and an iPad medium trio. All share one `WidgetConfigurationIntent` (primary station + primary line, nearest-mode overrides both).
- Live Activity redesign: full-width Lock Screen card, Dynamic Island tri-lane expanded view (next three trains on the tracked line), line-pill leading / minutes trailing / line-dot minimal, `widgetAccentable` for StandBy tinting.
- Android Glance widgets: migration of the RemoteViews departures widget to Compose Glance, plus Next Train / Live Departures / Line Status / Near Me families, fed by a WorkManager snapshot.
- Apple Watch companion: next-three departures view, complications (corner / circular / rectangular), WatchConnectivity handoff from the phone with a bundled-schedule fallback.

## Shipped

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

1. Move the relevant items out of **Unreleased** into a new `### <version>` entry under **Shipped**, dated with what actually shipped on each platform.
2. Bump the `Current shipping` line at the top.
3. Re-tag the git release so the tag matches reality (`v1.0.3` is currently stale; the real shipping versions are above).
4. Keep the **Unreleased** list pruned: anything still pending stays, anything dropped gets removed.
5. If the long-range roadmap in [Appendix K](docs/CASE_STUDY.md#appendix-k--product-roadmap) changes, edit that table in `CASE_STUDY.md` rather than duplicating it here.

Source of truth for current shipping versions:

- Android: `androidApp/build.gradle.kts` (`versionName`, `versionCode`)
- iOS: `iosApp/iosApp.xcodeproj/project.pbxproj` (`MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`)
- Git tags are often stale; do not trust them.
