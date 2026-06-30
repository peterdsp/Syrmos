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

- "Ask Ariadne" from the Home screen opens a chat that parses natural language fully offline and dispatches to the deterministic use cases the app already ships (departures, last train, trip planning, line info, alerts, find station). It never generates a transit fact; it picks an approved action and the projector/planner answers.
- Trilingual by design (EN/EL/SQ) via a rule parser, so no supported language degrades. Shared brain in `core/domain/assistant` (`AthensTransitParser`, `AssistantIntent`), mirrored in Swift under `iosApp/.../Features/Assistant`. Scope is fenced to Syrmos and Athens public transport; weather is accepted only as a routing constraint, everything else is declined.
- This also wired `PlanJourneyUseCase` (Dijkstra routing that already existed but was never in the DI graph) into the Compose app; iOS uses a compact 0/1-transfer planner over the bundled network.

Track this departure (Tier 2 primitive, shared `core/common` `TrackedDeparture` / `DepartureTracking`):

- "Track" on the Home hero pins a live countdown card that ticks offline on all three platforms.
- iOS: full Live Activity. ActivityKit integration in `DepartureTracking.swift` (`SyrmosTrackingAttributes` + start/update/end), `NSSupportsLiveActivities` set, and a real `SyrmosWidgetExtension` target (`iosApp/SyrmosWidget/`) that renders the Lock Screen + Dynamic Island. The extension is built and embedded into the app by the normal scheme build.
- Android: the parallel surfacing is an ongoing count-down notification (`DepartureTrackingNotifier`), driven by the same shared `DepartureTracking` primitive, with a live chronometer and a `POST_NOTIFICATIONS` request on Android 13+.
- Web: the in-app pinned countdown card is the surface; there is no OS-level equivalent.

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

## Shipped

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
