# Syrmos 3.0.0 — station all-directions release acceptance ledger

Opened 2026-09-30 (Europe/Athens), against `master` @ `0f9549a0`. Delivered in PR #241.

This ledger maps every acceptance case of the station-departures specification and its Ariadne, Ichnos and
foldable companion requirements to implementation, platform coverage, tests, runtime evidence and status.
It is updated as work lands; it is not a plan.

Status vocabulary: **verified complete**, **partial**, **failed**, **externally blocked**, **unverified**.

## Toolchain inventory (re-inspected 2026-09-30 00:30 EEST)

| Tool | State | Consequence |
| --- | --- | --- |
| Xcode | 27.0 (27A266a), Swift 6.4 | iOS build, XCTest, simulator capture available locally |
| iOS runtimes | 26.5, 27.0, 27.1 | Duo-capable runtime present |
| iPhone Duo simulator | `com.apple.CoreSimulator.SimDeviceType.iPhone-Duo` present | native Duo runtime verification is possible locally |
| watchOS runtimes | 26.5, 27.0 | watch target buildable |
| Node | v24.20.0 | web `node:test` suites and a served local web harness run locally |
| JDK | **absent** | Kotlin/Compose/Android and the Wasm web build are CI-only; Kotlin changes are verified on CI, not locally |
| Android SDK | present at `~/Library/Android/sdk` (no JDK) | `adb`/`emulator` usable for install/capture only if an APK is produced by CI |
| gh CLI | authenticated as `peterdsp` | PRs, CI, release dispatch drivable |

## Baseline root cause (traced 2026-09-30, commit `0f9549a0`)

Station resolution -> member stops -> service enumeration -> source reconciliation -> grouping -> rows:

1. `core/data/.../seed/stations.json`: `GR_ATH.line_ids` lists only `IC1` although `schedules-v2/lines.json`
   places `GR_ATH` on `RG1` as well, and marks it `is_interchange: false`. RG1 (Athens - Leianokladi) can
   therefore never reach any station board.
2. Web `buildStationNodes()` assigns `stationIdByLineId[lineId]` to the *first* member stop that merely lists
   the line, and the seed's `line_ids` carry interchange unions. At Athens this maps `A1 -> M2_STA`, i.e. the
   metro stop id is sent to a railway schedule lookup.
3. Web `buildStationDepartures()` returns `realTimetableDepartures()` exclusively whenever it is non-empty, so
   one suburban published-timetable response suppresses both metro directions for the whole complex.
4. `realTimetableDepartures()` matches boarding stops by display name only and caps at 10 rows.
5. `buildStationDepartures()` caps at `.slice(0, 10)` before destination coverage is established.
6. The projected fallback assigns `terminalA`/`terminalB` by result index parity, inventing a direction.
7. `setupHero()` renders `deps[0]` plus an unlabelled `deps.slice(1, 3)` "then" tail.
8. iOS `HomeView.nearestUpcoming()` requests 3 departures per line and `DepartureGrouping.directionBoard`
   caps at 4 rows with `UUID()` identity rendered by array offset.
9. Kotlin `GetStationDeparturesUseCase` ends with `.take(8)` and `HomeDirectionBoard.rows` caps at 4 groups
   keyed by `(lineId, Direction)`, which cannot represent branch or trip-specific destinations.

## Outcome status

Updated 2026-09-30 after merge and release.

| Outcome | Status | Evidence |
| --- | --- | --- |
| Station departures board | **verified complete** on web and iOS at runtime; **Android verified on CI only** | web: production capture below; iOS: pinned-clock simulator capture; Android: KMP tests + `Android build + lint` green, no emulator on this machine |
| Ariadne | **partial** | local-first orchestration, turn serialization, Stop, no-setup path and board-grounded answers verified at runtime on iOS and web. Android/Kotlin assistant not reworked in this pass |
| Ichnos | **partial** | scoped read, honest states, contextual reporting with idempotent retry and undo implemented and unit-verified on iOS. Web and Android entry points unchanged; submission against a test backend not exercised |
| Foldables / iPhone Duo | **failed (native), fixed (shipping fallback)** | the native `ArrangementView` path renders a blank content area on a real Duo simulator and is deliberately not shipped; a real cover-display defect in the shipping path was found and fixed. Android foldable unverified (no JDK) |

## Release

| Item | Value |
| --- | --- |
| PR | [#241](https://github.com/peterdsp/Syrmos/pull/241) |
| Merge commit | `2f1ebb89470080ceeedfd0dad4e29f5972a7bc62` |
| Required checks on the exact PR head `bfe94c8` | CI, iOS, iOS UI inspection: all success |
| Checks on the merge commit | CI, iOS, iOS UI inspection, Pages: all success |
| Release tag | `v3.0.0-beta.8` -> `2f1ebb89` |
| Marketing version | 3.0.0 (unchanged) |
| Android versionName / versionCode | 3.0.0 / **231** (230 consumed by the accepted beta.7 upload) |
| iOS build number | epoch-stamped by the release workflow, unique by construction |

### Release-engineering defect fixed on the way

`v3.0.0-beta.7`'s iOS release failed at "Validate + upload to TestFlight". The
log shows altool's content delivery returning a transient `status code 500` on
`GET UPLOAD STATE`, immediately followed by `VERIFY SUCCEEDED with no errors` and
`No errors validating archive`. The step's guard matched a bare `ERROR:` and
declared the build not delivered. The guard now fails only on definitive failure
markers and additionally **requires a positive success marker**, so a transient
service error no longer fails a successful validation and silence is no longer
mistaken for delivery.

## Acceptance cases

| # | Case | Implementation | Tests | Runtime evidence | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | Athens fixture: both M2 directions plus validated suburban and intercity, correct boarding stop | registry + board core, 3 clients | `station-board.test.js` fixture case 1, Swift `testFixtureCases`, Kotlin `everyDirectionIsRepresented` | web `web-athens-board-en.png`, iOS `home__C402__light__en__default.png` | verified complete (web, iOS); Android unverified at runtime |
| 2 | Now + 1 min opposite direction both represented | board core | fixture case 1 | iOS capture: Elliniko Now / Anthoupoli 2 min | verified complete |
| 3 | >4 groups, many early departures on one line, nothing disappears | no cap before grouping | fixture case 2 | web + iOS show 9 groups at Athens | verified complete |
| 4 | Published rail and projected metro coexist | per-trip reconciliation | fixture case 3 | Athens board carries M2 estimates and A/IC/RG published trips together | verified complete |
| 5 | Branch/short-turn headsigns stay distinct | `tripTravelOrder` / `TripInterpretation` | fixture case 4, `trips: a short turn keeps its own headsign` | web board showed the real A3 Afidnes short turn | verified complete |
| 6 | Duplicate trip records collapse; same-minute distinct trains survive | `dedupe` | fixture case 5 + 3 unit tests per platform | | verified complete (unit) |
| 7 | Aliases resolve; unrelated nearby stops excluded; interchange unions are not boarding membership | reviewed registry | `registry:` suite (web), `StationComplexRegistryTests` (Swift) | | verified complete |
| 8 | Terminal arrivals, non-pickup, cancelled, suspended, date exceptions, midnight, both DST changes | `boardsHere`, projector reuse | fixture cases 6-7, `trips: a terminal arrival...` | | partial: DST and date-exception paths reuse the existing projector, which has its own tests; not re-exercised here |
| 9 | No departure / unavailable / offline cached render distinct truthful states | coverage rows | fixture cases 8-9 | web RG1 row "No departure in the next 12 hours"; iOS same | verified complete |
| 10 | Live delay updates the matching departure without duplication or label bleed | per-trip merge | fixture case 10 | | verified complete (unit) |
| 11 | No location, manual choice, region change, refresh, restoration preserve station scope | `pinnedBoardNodeId` / `pinnedBoardStationId` | `station-board-wiring.test.js` pin-before-location | | partial: pin logic in place; restoration across scenes not re-exercised |
| 12 | Track/Station/row actions keep their target across data arrival | selected group id | wiring guardrails | iOS: tapping Anthoupoli changed the button to "Track · Anthoupoli" and highlighted that row only | verified complete (iOS, web) |
| 13 | iOS, Android, web produce equivalent groups from one fixture and clock in 4 languages | shared fixture | `station-board-parity.test.js` + the three suites | | verified complete for the board contract; localized label rendering verified on iOS/web only |
| 14 | Compact, unfolded, large text, dark/light, screen reader, reduced motion remain readable | accessibility-size row collapse; Duo cover-display fix | | iOS hierarchy dump: one merged element per row with a complete label; Duo cover pill collapse found and fixed | partial: large-text and dark captures not taken for the new board |
| 15 | Actual app captures show the complete Athens board on each main platform | | | web + iOS captured | partial: Android capture pending |
| 16 | Clean install, each language, Ariadne answers without model setup; offline provenance honest | web Ariadne now reads the board | | | pending |
| 17 | "What leaves from here?" + correction yields board-equal results and a working action | both answered from the shared board | wiring guardrails | headless browser, offline: full board in 93 ms, "and toward Anthoupoli?" narrowed to that one direction in 89 ms, same numbers as the card | **verified complete** on web |
| 18 | Provider timeout, cancellation, rapid turns, keyboard, pane replacement keep drafts and reject stale | turn ids, Stop, bounded optional steps | `testAStoppedTurnCannotAppendItsResultLater`, `testTheOptionalUnderstandingStepsAreBounded` | an unbounded optional call held a turn open for 391 s in the suite; bounded it completes in 3.4 s | partial: cancellation and stale rejection verified; keyboard and pane replacement not exercised |
| 19 | Ichnos scope fidelity | narrowest-scope resolution, cross-line rejection, stale labelling | `StationBoardIchnosTests` (9) | | partial: unit-verified on iOS; no runtime capture, web and Android unchanged |
| 20 | Report submission, failure/retry, undo, expiry on a test backend | idempotent report id, confirm-then-count, undo by the same id | | | **unverified**: not exercised against a test backend |
| 21 | Ariadne labels community evidence as community | | | | pending |
| 22 | Station row -> Ichnos -> Ariadne -> alternative -> return to the same selection | | | | pending |
| 23 | Each Duo workflow works end to end on the installed app | reserved-region adapter corrected to the real 27.1 API | | booted Duo simulator: the native ArrangementView path renders a BLANK content area, the fallback renders | **failed**: reproduced, and therefore deliberately not shipped |
| 24 | Repeated fold/unfold, rotation, multitasking, keyboard, restoration keep state | | | | pending |
| 25 | Compact and open walkthroughs demonstrate the benefit | | | | pending |
| 26 | Language, accessibility, appearance, offline, unavailable checks cover the new Ichnos/Ariadne content | | | | pending |

## Inspected and accepted

| Observation | Decision |
| --- | --- |
| The floating Ariadne launcher overlaps the trailing edge of whichever board row happens to sit behind it, clipping part of one countdown at rest (for example "9h 16min" reading as "9h 16"). | Pre-existing behaviour of the floating launcher shipped in #238, not introduced by the board; it applies to any scrolling content. Every row remains reachable by scrolling, the row stays tappable, and its accessibility label carries the complete text. Moving the launcher would be an unrelated redesign, which the specification rules out. Recorded rather than silently fixed. |
| The last board row and the source legend sit under the tab bar at the resting scroll position. | Ordinary below-the-fold content: the scroll view's bottom inset is correct and the last card clears the tab bar at maximum scroll. |
| Board rows read through the floating title capsule and into the status bar while scrolling. | Fixed: `CompactTabHeader` now draws a scrim that extends into the top safe area. |
