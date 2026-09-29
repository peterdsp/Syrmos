# Syrmos 3.0.0 — station all-directions release acceptance ledger

Opened 2026-09-30 (Europe/Athens). Branch `codex/station-all-directions-3.0.0`, forked from `master` @ `0f9549a0`.

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

| Outcome | Status | Evidence |
| --- | --- | --- |
| Station departures board | in progress | see acceptance table |
| Ariadne | in progress | see acceptance table |
| Ichnos | in progress | see acceptance table |
| Foldables / iPhone Duo | in progress | see acceptance table |

## Acceptance cases

| # | Case | Implementation | Tests | Runtime evidence | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | Athens fixture: both M2 directions plus validated suburban and intercity, correct boarding stop | | | | pending |
| 2 | Now + 1 min opposite direction both represented | | | | pending |
| 3 | >4 groups, many early departures on one line, nothing disappears | | | | pending |
| 4 | Published rail and projected metro coexist | | | | pending |
| 5 | Branch/short-turn headsigns stay distinct | | | | pending |
| 6 | Duplicate trip records collapse; same-minute distinct trains survive | | | | pending |
| 7 | Aliases resolve; unrelated nearby stops excluded; interchange unions are not boarding membership | | | | pending |
| 8 | Terminal arrivals, non-pickup, cancelled, suspended, date exceptions, midnight, both DST changes | | | | pending |
| 9 | No departure / unavailable / offline cached render distinct truthful states | | | | pending |
| 10 | Live delay updates the matching departure without duplication or label bleed | | | | pending |
| 11 | No location, manual choice, region change, refresh, restoration preserve station scope | | | | pending |
| 12 | Track/Station/row actions keep their target across data arrival | | | | pending |
| 13 | iOS, Android, web produce equivalent groups from one fixture and clock in 4 languages | | | | pending |
| 14 | Compact, unfolded, large text, dark/light, screen reader, reduced motion remain readable | | | | pending |
| 15 | Actual app captures show the complete Athens board on each main platform | | | | pending |
| 16 | Clean install, each language, Ariadne answers without model setup; offline provenance honest | | | | pending |
| 17 | "What leaves from here?" + correction yields board-equal results and a working action | | | | pending |
| 18 | Provider timeout, cancellation, rapid turns, keyboard, pane replacement keep drafts and reject stale | | | | pending |
| 19 | Ichnos scope fidelity: opposite direction, line-wide, stale, contradictory, empty, unavailable | | | | pending |
| 20 | Report submission, failure/retry, undo, expiry on a test backend; no duplicates | | | | pending |
| 21 | Ariadne labels community evidence as community; operator alerts keep their source | | | | pending |
| 22 | Station row -> Ichnos -> Ariadne -> alternative -> return to the same selection | | | | pending |
| 23 | Each Duo workflow works end to end on the installed app | | | | pending |
| 24 | Repeated fold/unfold, rotation, multitasking, keyboard, restoration keep state and side-effect counts | | | | pending |
| 25 | Compact and open walkthroughs demonstrate the benefit for departures, Plan, GO, Ariadne, Ichnos | | | | pending |
| 26 | Language, accessibility, appearance, offline, unavailable checks cover the new Ichnos/Ariadne content | | | | pending |
