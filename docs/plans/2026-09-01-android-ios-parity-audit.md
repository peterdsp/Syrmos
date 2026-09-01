# Android ↔ iOS parity audit (Syrmos)

Date: 2026-09-01. iOS (`iosApp/`, SwiftUI) is the product reference; Android is Kotlin Multiplatform + Compose (`composeApp/`, `feature/`, `core/`). This audit is **code-based** (static). Runtime-only checks (airplane-mode passes, Logcat, TalkBack traversal, multi-device rotation, live-map recomposition profiling) could not be executed in this environment (no local JDK) and are marked **BLOCKED — needs on-device/CI** where relevant.

## Headline

Parity is high on architecture, data flow, projector core (band/midnight/holiday logic is near-line-for-line), announcements, freshness model, enums (both defensive), and map rendering. Android parsing is defensively coded — **no crash sites found**. The real gaps are: one **critical sync bug**, an **owed feature** (proximity interchange), several **shared-logic drifts**, and a set of **UX/affordance** gaps. A few genuine bugs live in the iOS reference itself.

## Priority legend

- **P0** critical/functional (silently breaks a core contract)
- **P1** high (missing feature or wrong data in a common path)
- **P2** medium (parity gap, UX, localization)
- **P3** low/polish/a11y
- **iOS** a real bug in the reference (small, safe fix)

---

## Master matrix (actionable discrepancies only; broad MATCHED areas summarized after)

| # | Pri | Area | iOS (file:line) | Android (file:line) | Discrepancy | Action |
|---|----|------|-----------------|---------------------|-------------|--------|
| 1 | P0 | Schedule sync | `Core/Networking/SyrmosSchedulesService.swift:106-112` (per-line etag compare) | `core/data/.../sync/ScheduleSyncRepository.kt:119-124` | Android per-line filter compares the manifest hash to itself (always false) and `LineSchedule` stores no hash; with all lines pre-hydrated, **the server bundle is never fetched** — timetables can't update without an app release | Persist per-line hash; fetch when `stored[lid] != manifest[lid]` |
| 2 | P0 | Interchange | `Core/Schedule/TransitData.swift:578-611` `interchangeTargets` (150m, operational+hasSchedule, real per-line stationId) + `Views/Stations/StationDetailView.swift:63-83` actionable | absent; `StationDetailScreen.kt:186-220` static badge + `lineIds`-based non-actionable `connectingLines` | Proximity interchange (owed from iOS PR #48) not ported; no tappable per-line transfer | Add shared `InterchangeResolver` in `core/domain`; render actionable list |
| 3 | P1 | Timetable | holiday applied on every path `ScheduleProjector.swift:500-509` | `GetNextDeparturesUseCase.kt:164-173`, `feature/map/MapViewModel.kt:456-459` | Android seed-DB fallback + map fallback ignore holidays (Aug 15 on a Tue reads weekday offline) | Apply holiday resolver before choosing seed `DayType` |
| 4 | P1 | Search | `Views/Lines/LinesView.swift:1154-1163` matches name/nameEl/**nameSq**/**id** | SQL `SyrmosDatabase.sq:132-135` (name/name_el only); `StationRepositoryImpl.kt:62-65,132-151` drops nameSq/id; `LinesRefresher.kt:69` nulls name_sq | Albanian names + station codes never match on Android | Add nameSq+id to SQL & fallback; stop nulling name_sq |
| 5 | P1 | Data model | `SyrmosSchedulesService.swift:194` `validDates` honored (`ScheduleProjector.swift:973-977`) | KMP `TripEntry` (`SyrmosSchedulesService.kt:150-156`) lacks the field; `DataSeeder.seedTripSchedules` seeds all trips | Date-scoped trips shown on wrong dates | Add `validDates`; filter in `seedTripSchedules` |
| 6 | P1 | Home | denied/empty location CTA `HomeView.swift:76-92,877-910` | `HomeScreen.kt:363` renders nearby only if non-empty; no CTA | Nearby collapses to nothing with location off; no way to enable | Add permission-CTA card + `openAppSettings()` expect/actual |
| 7 | P1 | Home / freshness | `.refreshable` `HomeView.swift:54`; `NWPathMonitor` retry `DataFreshness.swift:59-68` | none; 60s poll only (`HomeViewModel.kt:110-119`); `DataFreshness.kt:66 requestRetry()` dead | No pull-to-refresh; no event-driven reconnect retry | `PullToRefreshBox` + `expect/actual` connectivity monitor wiring `requestRetry()` |
| 8 | P1 | Line detail | live trains any line + upcoming-departures fallback `LineDetailView.swift:65-138` | `LineDetailScreen.kt:218` gates live to SUBURBAN; no upcoming section; title/count English-only (`:172,314-318`) | Metro/tram line detail lacks live + upcoming; not localized | Remove gate / add source; add projected departures; localize |
| 9 | P1 | Departures source | Athens = local projector only (`StationDetailView.swift:327-360`) | server-first every 15s (`GetNextDeparturesUseCase.kt:49-73`) | Different source of truth; Android hits network for Athens, can differ from on-device projector | Decide one policy (local-first for Athens recommended) in shared use case |
| 10 | P1 | Live fallback | no live abstraction | `LiveArrivalsRouter`/`Provider` built in DI (`DomainModule.kt:39-47`) but never injected → dead; working `OasaLiveArrivalsProvider` unused | Designed LIVE→SCHEDULED fallback is dead code; X93-X97 live ETAs never surfaced | Wire router as first tier in shared departures use case (or delete) |
| 11 | P2 | Confidence | blanket `.scheduled` `TransitData.swift:188` | `SCHEDULED/ESTIMATED/OFFLINE` `GetNextDeparturesUseCase.kt:106,137,160` | Same departure labelled differently across platforms | Define confidence rule once in shared code; both consume |
| 12 | P2 | Projector | `lastTrains` short-turn override in projector `ScheduleProjector.swift:759-805` | UI-only `feature/schedule/ScheduleScreen.kt:898`; absent from `ComputeDeparturesFromBandsUseCase` | Home/widgets miss short-turn destinations | Move override into shared projector |
| 13 | P2 | Strings | subtitle "Live Greece rail times" `Localization.swift:140`; news "Rail network updates" `:160`; tab "Airport" `:196` | "Live Athens rail times" `Localization.kt:73`; "Metro & Tram updates" `:109`; "Departures" `:163` | 3 user-visible copy mismatches (all 4 langs) | Pick canonical per key; align both |
| 14 | P2 | Settings | recents `SettingsView.swift:238-272`; refresh alert `:78-84`; digest backed | no recents (`SettingsScreen.kt:60` unused); no refresh feedback; `notif_morning_digest` dead | Missing recents; no refresh result; dead toggle | Shared recents store; refresh snackbar; wire or hide digest |
| 15 | P2 | Networking | per-call 6-10s timeouts | one 30s client (`NetworkModule.kt:37-41`) | Unreachable Pi stalls Android live/departures ~30s before seed fallback | Per-call `timeout{}` overrides for live/departures |
| 16 | P2 | `/api/lines` | overlay-only novel stations `SyrmosLinesService.swift:59-69` | upserts line status/region + reorders stations `LinesRefresher.kt:45-88` | Different semantics; Android can change what iOS won't | Decide canonical behaviour |
| 17 | P3 | Search | neither folds Greek tonos (`MapStationNode.kt:86-100` used only for clustering) | same | Accent-insensitive search missing both | Shared `normalizeStationText` used in all predicates |
| 18 | P3 | a11y | compound departure label `StationDetailView.swift:229`; `.isSelected` traits | `DepartureCard` separate nodes; no `heading()`; no `liveRegion` | TalkBack reads fields separately; no heading/alert semantics | Merge card semantics; add headings + alert live-region (both) |
| 19 | P3 | News offline | cached to UserDefaults `RailNewsService.swift:66` | `emptyList()` on failure (`RailNewsService.kt:85`) | News blank offline on Android | Persisted cache/seed |
| 20 | P3 | Athens time | OS IANA everywhere | band path IANA but seed-fallback/day-type use hand-rolled DST (`DateTimeExtensions.kt:13-28`) | Two mechanisms; latent transition-hour drift | Delegate to `kotlinx.datetime.TimeZone.of("Europe/Athens")` |
| 21 | P3 | Map cosmetics | pad 0.7; CADisplayLink glide; no selected-recenter | pad 0.5; per-tick jumps; recenters on select | Minor visual/motion diffs | Align pad; optional vehicle tween |
| 22 | iOS | Live map | `TransitData.swift:855-856` `lat/lng` required | Android nullable+filter (`RailwayGovLiveTrackerService.kt:52,142-143`) | One GPS-less train blanks the whole iOS live map | iOS: make lat/lng optional, skip null rows |
| 23 | iOS | Map geometry | `MapView.swift:117-118` no loop closure | `RouteGeometry.closeLoop` | PU1 draws with ~1km open return leg on iOS | iOS: port `closeLoop`/`isLoopTerminals` |
| 24 | iOS | Announcements | required fields (`STASYService.swift:254-273`) → one bad row voids all | Android defaults+filter | Brittle decode on iOS | iOS: optional fields + skip blank |
| 25 | P2 | Live decode | — | `SyrmosLivePositionsService.LiveTrain` required fields (`:47-55`) | One malformed vehicle row drops the whole live layer to simulator (silent) | Default the fields, skip bad rows |

### Broad areas verified at parity (no action)
Tab set/order + persistence; theme (system/light/dark); 4-language runtime switch; Operators/attribution; Contact endpoint; onboarding/What's-New gating; announcement disruption derivation (M3↔M3_AIR alias, serviceUntil cutoff) — already shared in `AnnouncementsRepository`; freshness model (90s window); enum unknown-value handling (both default safely); map rendering (colours, weights, dashes, dot styling, zoom decluttering bands, vehicle heading, live/projected dedupe, stale removal, band culling, offline dot projection); weather; fares/icons/line-display/train-timestamps endpoints; offline band-projected departures (metro/tram work offline both).

---

## Prioritized fix sequence (focused, Codex-gated, CI-tested PRs)

1. **Schedule-sync hash fix (P0 #1)** — restore server timetable updates on Android. Test the per-line diff.
2. **Search parity (P1 #4, P3 #17)** — nameSq+id in SQL/seed/refresher + shared diacritic-folding normalizer. Test repository + normalizer.
3. **Holiday in seed fallback (P1 #3)** — shared holiday resolver on the seed/day-type path. Test with holiday dates.
4. **validDates (P1 #5)** — add field + honor in `seedTripSchedules`. Test date gating.
5. **Interchange proximity port (P0 #2)** — shared `InterchangeResolver` in `core/domain` + actionable station-detail list. Test resolver invariants (mirrors iOS `InterchangeAssociationTests`).
6. **Departures source policy + confidence + live wiring (P1 #9/#10, P2 #11/#12)** — consolidate source-selection order and SourceConfidence tagging in the shared use case; wire or remove `LiveArrivalsRouter`; move `lastTrains` override into the shared projector. Test source order + confidence.
7. **Home parity (P1 #6/#7)** — nearby permission CTA + pull-to-refresh + connectivity retry. Test VM state; UI BLOCKED for on-device.
8. **Line detail parity (P1 #8)** — metro/tram live + upcoming, localization. Test upcoming projection.
9. **Content strings + settings (P2 #13/#14)** — align copy; recents store; refresh feedback; digest wire/hide.
10. **Networking timeouts + live decode resilience (P2 #15/#25, #16)** — per-call timeouts; default live-train fields.
11. **a11y + Athens-time unification + news offline (P3 #18/#20/#19)**.
12. **iOS reference bugs (#22/#23/#24)** — small safe iOS fixes (brittle `/api/trains` decode, PU1 loop closure, announcement decode).

Each lands as its own branch/PR with unit tests run in CI and an adversarial-review gate before merge. Runtime/device verification (airplane-mode, TalkBack, rotation, live-map profiling) is **BLOCKED** here and flagged for CI/device.

## Shared-KMP consolidation candidates (fix Android to match iOS now; full iOS→KMP consumption is a separate architectural project)
Band/trip/active-train projection; holiday resolver (copied 3-4×); direction labelling; search predicates + text normalization; date/minutes helpers; last-train/overnight selection. Where the shared home is already `core/domain`/`core/common` (consumed by Android), fixes land there. Rewriting the native-Swift iOS app to consume KMP is out of scope for parity and is called out as a follow-up.

## Definition-of-done tracking
Each matrix row will be resolved to **MATCHED**, **INTENTIONALLY DIFFERENT** (with reason), or **BLOCKED** (with reason) as its PR merges. Intentional differences already identified: Material vs SwiftUI tab bar; on-device AI engine story (Gemini Nano vs bundled llama); debug-only iOS Developer/Diagnostics screens; per-tick vs CADisplayLink vehicle motion (engine difference).
