# Syrmos foldable + iPhone Duo readiness record

Implementation evidence for [`docs/plans/ANDROID-FOLDABLES-IPHONE-DUO-IMPLEMENTATION-PROMPT.md`](../plans/ANDROID-FOLDABLES-IPHONE-DUO-IMPLEMENTATION-PROMPT.md).
This is a living record: each slice appends verified evidence, distinguishing what
runs from what is gated on tools or hardware. It never marks a gated item as done.

## Source, tools, and revision

| Fact | Value | Verified |
| --- | --- | --- |
| Repository revision at baseline | `70d38050` (branch `master`) | 2026-09-17 |
| Xcode | 27.0 (build 27A266a) | `xcodebuild -version` |
| iOS SDK | iphoneos27.0 | `xcodebuild -showsdks` |
| iOS deployment target | iOS 17 | `iosApp/project.yml` (per prompt section 2) |
| Simulator runtimes | iOS 27.0 (24A434), iOS 26.5 (23F77) | `xcrun simctl list runtimes` |
| JDK | Temurin 17.0.20.1+1 at `~/Library/Android/jdk/jdk-17.0.20.1+1` | `java -version` |
| Node | v24.20.0 | `node --version` |
| Kotlin / Compose MP / Voyager | 2.1.20 / 1.8.0 / 1.1.0-beta03 | `gradle/libs.versions.toml` |
| Android target | API 36 | version catalog |

## iOS Duo SDK capability probe (prompt section 9.1)

**Update (Xcode 27.1, iphoneos27.1 SDK at `~/Downloads/Xcode_27.1.app`):** the Duo
APIs are now source-visible, so the arrangement/region tier is unblocked. Use it via
`DEVELOPER_DIR` (no global switch). The earlier 27.0 probe is kept below for history.

| Symbol | 27.0 SDK | 27.1 SDK | Consequence |
| --- | --- | --- | --- |
| `ArrangementView` (+ `SplitArrangementViewStyle`, `splitArrangementLayoutRatio`) | No | **Yes** | Duo paired content is actionable; adopted in `SyrmosArrangement`. |
| `overlayArrangementEdge` / `overlayArrangementZIndex` | No | **Yes** | Overlay arrangements available. |
| `reservedRegions(kind:)` + `ReservedRegion.Kind` + `includeInactive` | `.tbd` only | **Yes** | Region API source-visible; ready for custom-geometry work. |
| `ToolbarOverflowMenu` / `visibilityPriority` / `toolbarVerticalCompressionBehavior` | Yes | Yes | Toolbar overflow available. |

All `@available(iOS 27.1, *)`; guard adoptions and keep the iOS 17 fallback. The
iPhone Duo simulator device type exists but needs an iOS 27.1 runtime that is not
installed and not downloadable here, so Duo **runtime** validation stays gated.

## Verified baseline (prompt section 2)

Rechecked against current code, all confirmed:

- `composeApp/.../SyrmosApp.kt` switches compact bottom bar vs a native
  `NavigationRail`, driven by `ContentBreakpoint`. It keeps `isSeeded` in
  composition state and holds a 3.5s splash (`LaunchedEffect(Unit)`).
- `SyrmosApp.kt` resolves `ContentBreakpoint` from the full `BoxWithConstraints`
  window, before the rail consumes width, despite the policy's post-inset contract.
- `core/common/.../layout/ContentBreakpoint.kt` resolves 600/840/1200 + a
  short-height fallback and has no hinge/posture/task/pane inputs.
- No Android WindowManager / Material adaptive dependencies in the version catalog.

## Landed: shared foundation (Phase 3 core)

- **Shared adaptive workspace policy** — `core/common/.../layout/AdaptiveWorkspace.kt`.
  A pure, platform-neutral function from usable content geometry + reported
  fold/occlusion regions + font scale + current task to semantic layout decisions
  (arrangement, pane roles + fitted rectangles, hinge clearance, adjustable
  divider). Reuses `ContentBreakpoint` for the plain-window path so a non-foldable
  device resolves exactly as before. No SDK types. The web app is untouched.
- **Tests** — `core/common/.../layout/AdaptiveWorkspaceTest.kt`, 20 cases; these
  are the cross-platform fixtures the SwiftUI mirror must also satisfy.

## Landed: six-posture policy fixtures and the Swift policy mirror (six-posture prompt, delivery step 1)

Source: `docs/plans/IPHONE-DUO-SIX-POSTURES-AWARD-DESIGN-PROMPT.md`, sections 3, 5, 11 and 12.

- **Tall-canvas axis rule (shared policy)**: `AdaptiveWorkspacePolicy` now pairs on
  a medium-width plain window (600 to 839 wide, not short, not large text) on the
  axis the task prefers (`WorkspaceTask.tallCanvasAxis`: GO and Explore stack the
  map above the list and controls; Plan, Home, Departures, Fares and Ariadne pair
  side by side), falling back to the other axis and then to the readable single
  column. Floors: side by side needs each floored half to clear the task floor
  (300) and the map floor (320), so the pairing floor is exactly 640 at default
  text, the same threshold the shipped iOS `SyrmosArrangement` uses; stacking
  needs 360 (map) plus 280 (task) of height and gives the map about 45 percent.
  Region-driven paths (book, laptop, tent) are unchanged. Behaviour change on
  Android: Plan on a 600 to 839 wide plain window (a tablet held upright, an
  unfolded book foldable) now renders its two-pane instead of one column, which
  the parent prompt's section 5 already allowed for that band.
- **Kotlin fixtures**: `core/common/.../layout/DuoPostureFixturesTest.kt`, 20 cases
  named after the postures and transitions (`p1_pocket_...` to `p6_laptop_...`,
  `t1_unfold_...` to `t6_pinnedVideo_...`) at the measured Duo geometry (cover
  466 x 678, inner 669 x 951 and 951 x 669). `AdaptiveWorkspaceTest.kt` grew to 25
  (medium-window pairing, medium form column, too-narrow medium window).
  Run: `:core:common:testDebugUnitTest --tests "com.syrmos.core.common.layout.*"`
  (JDK 17): AdaptiveWorkspaceTest 25/25, ContentBreakpointTest 9/9,
  DuoPostureFixturesTest 20/20.
- **Swift mirror**: `iosApp/iosApp/DesignSystem/AdaptiveWorkspacePolicy.swift`
  (`SyrmosAdaptiveWorkspacePolicy`, with `SyrmosContentBreakpoint` folded in) is
  a line-for-line twin of the Kotlin policy: same types, same constants, same
  rules, plus a `CGSize` entry point that floors to whole points. It is not yet
  wired into `SyrmosArrangement` (delivery step 3); the shipped two-pane still
  decides by width alone.
- **Swift fixtures**: `iosApp/iosAppTests/DuoPostureFixturesTests.swift`, 42 cases:
  the 20 posture and transition twins by the same names and numbers, the general
  `AdaptiveWorkspaceTest` twins, the `CGSize` flooring, and a parity guard that the
  shipped `SyrmosArrangement.minPairWidth` (640) equals the policy's side-by-side
  floor (`2 x minMapPane`) with 640 pairing and 639 stacking. Registered through
  `scripts/add-duo-posture-files.py`.
- **Evidence tier**: synthetic policy fixtures on both platforms, plus the full
  iOS unit suite (315 tests, 0 failures, iOS 27.0 simulator, default Xcode 27.0
  SDK, which is the CI-equivalent compile of the new Swift) and the Android
  running surface for the one behaviour change: on the `syrmos_tablet` emulator
  (density 160) Plan at 669 x 951 with the nav rail keeps its single column
  (content box about 589 wide, compact), and Plan at 768 x 1024 renders the
  two-pane (query and saved task pane beside the results pane, content box about
  688 wide, halves of 344). Screenshots `android-plan-669.png` and
  `android-plan-768.png` in the session scratchpad. The Duo inner display on
  Android therefore pairs only through its reported hinge region, as before;
  the medium-width rule reaches Android windows whose content box clears 640.

## Landed: iOS reserved-region adapter and hinge-aware map padding (six-posture prompt, delivery step 2)

Source: `docs/plans/IPHONE-DUO-SIX-POSTURES-AWARD-DESIGN-PROMPT.md`, section 9 item 2; parent prompt section 9.4.

- **Adapter** `iosApp/iosApp/DesignSystem/ReservedRegionAdapter.swift`:
  `SyrmosReservedRegionAdapter.normalize(raw, in: box)` is a PURE function from
  the regions the system reports (kind, frame, active) to the content box's own
  coordinates. It translates once, clips to the box, rounds to whole points,
  drops a fold beside the box (a nav rail's fold never splits the content), keeps
  the active flag (an inactive division reaches the policy as inactive, never as
  blank pixels), and separates two outputs: `regions` (bars spanning at least
  90 percent of the box on their axis, the policy's input) and `cutouts` (active
  occlusions that do not span, such as a camera cutout, which never split the
  layout and only steer overlays and map padding).
- **Gated reader**: `GeometryProxy.syrmosRawReservedRegions()` calls
  `reservedRegions(kind:options:)` for `.occlusion` and `.division` with
  `.includeInactive`, inside `#if SYRMOS_DUO_SDK` and `if #available(iOS 27.1, *)`,
  and returns nothing otherwise. `syrmosReservedGeometry()` normalises into the
  proxy's own box. The frames are assumed to be in the proxy's local space; the
  Duo runtime probe below records what the system actually reports.
- **Test seam**: `\.syrmosReservedGeometryOverride` environment value lets a test
  or preview inject a geometry on a simulator that reports none.
- **Map padding** `SyrmosMapPadding.insets(mapRect:geometry:)`: base 44 pt on
  every edge; an active occluding bar that crosses the map gives up the smaller
  side of the map (bottom or top, right or left); a cutout that touches the map
  insets the nearest edge past it; divisions never pad; the padding can never
  claim more than three quarters of an axis. `visibleCenter` and
  `compensatingPoint` give the point math for centring a coordinate in the
  padded area rather than the geometric centre.
- **GO companion wiring** (`GoJourneyView.swift`): the companion reads its box's
  geometry (override first, then the system), computes the map insets for the
  map's rect at the top of the companion, and passes them to `GoRouteMapView`,
  which now fits the route with those insets, recentres the current stop in the
  padded visible area (offset measured in map points at the current zoom, so
  zoom and bearing are kept: parent prompt 9.4 rule 6, no camera reset), and
  refits the route when the insets change with no current stop to follow.
- **Tests**: `iosApp/iosAppTests/ReservedRegionAdapterTests.swift`, 26 cases
  (normalisation, cutouts, clipping, rounding, the P4 book fixture through the
  adapter into the policy, padding on every side, the no-inversion clamp, the
  centre math, and the reader returning nothing without the Duo API). New render
  `iosAppTests/__DuoSnapshots__/go-duo-inner-landscape-hinge.png` from
  `DuoSnapshotTests.test_goScreen_duoInnerLandscape_hingeAcrossCompanion_render`:
  an injected occluding bar at 220 to 260 of the companion crosses the lower part
  of the 281 pt map, so the map pads its bottom by 105 and the current stop
  (Piraeus) renders at the padded visible centre with the whole route above the
  bar. Registered through `scripts/add-duo-reserved-region-files.py`.
- **Evidence tier**: iOS 27.0 simulator (default Xcode 27.0 SDK, the
  CI-equivalent compile of the gated code): ReservedRegionAdapterTests 26/26,
  DuoSnapshotTests 6/6, DuoPostureFixturesTests 42/42. Duo runtime under the
  27.1 SDK with `SYRMOS_DUO_SDK` (Xcode 27.1, booted iPhone Duo simulator,
  `-destination id=92B2C61A-...`): ReservedRegionAdapterTests 26/26 and
  DuoSnapshotTests 6/6, so the gated `reservedRegions` reader compiles and
  executes on the real Duo runtime. The probe (`test_readerIsEmptyWithoutTheDuoApiOrRegions`)
  hosted a `GeometryReader` in a plain test `UIWindow` at 669 x 951 and the
  system reported NO regions there (`regions: [], cutouts: []`). So the Duo
  simulator does not surface its fold to a bare test window; whether a
  scene-hosted window reports it, and in which coordinate space, is still
  Pending and is the first thing to read on real Duo hardware. The rendered
  reference images stay the 27.0 renders (the 27.1 native `ArrangementView`
  path re-renders them differently, as recorded above).

## Landed: policy-driven arrangement, stacked axis for GO (six-posture prompt, delivery step 3)

Source: `docs/plans/IPHONE-DUO-SIX-POSTURES-AWARD-DESIGN-PROMPT.md`, section 9 item 1; section 5 (P5, P6).

- **`SyrmosArrangement` is now driven by `SyrmosAdaptiveWorkspacePolicy`**
  (`PlanFlow.swift`): the container measures its box, reads the regions the
  system reports for it (override first, then the gated reader), maps Dynamic
  Type to the policy's font scale (`SyrmosDynamicType.fontScale`, body size over
  17 pt) and resolves the task-aware workspace. `single` renders the shipped
  `combined` column; `sideBySide` puts the task pane beside the companion;
  `stacked` puts the companion (map, overview) above and the task with its
  controls below. GO passes `task: .go` and Plan `task: .plan`; Explore has no
  iOS two-pane yet, so its stacked preference is policy-only until an Explore
  companion exists (per-flow work, delivery step 3 continued in a later slice).
- **Fallback (the shipping path, CI and release builds)**: `HStack` on the
  horizontal axis with the task column at the policy's task-pane right edge, and
  `VStack` on the vertical axis with the companion band at the policy's
  companion bottom edge. An occluding hinge's thickness is left empty between
  the panes; a division gets a hairline. `SyrmosArrangementRule` holds these
  numbers so tests can pin them.
- **Native path (27.1 SDK, `SYRMOS_DUO_SDK`)**: `ArrangementView` with the
  `.split` style; the policy contributes the pane ORDER (companion first on a
  stacked decision so it lands on top) and the ratio. FINDING: restricting the
  axis with `.arrangementViewStyle(.split.axes(.horizontal))` or `.axes(.vertical)`
  made the Duo runtime HIDE the secondary pane (landscape Plan probe rendered
  the task pane only; the GO screen rendered its single column), so the axis
  restriction is not used and the system keeps owning the axis on the Duo.
- **Outcomes on the Duo inner display (fallback path)**: GO upright is stacked
  (map band 427 of 951 above, instruction and controls below); Plan upright is
  side by side at 334 | 335; both wide are side by side (task 384, gap 24, map
  519); an occluding horizontal hinge stacks any task on the fold and leaves
  the hinge band empty; the keyboard shrinks the measured box, so Plan on a
  laptop fold collapses to the query above the keyboard (P6 fixture).
- **Tests**: `DuoPostureFixturesTests` grew to 49 (arrangement numbers from the
  P3, P5 and hinge workspaces, share clamps, the Dynamic Type mapping and its
  collapse threshold at xxxLarge); `DuoSnapshotTests` grew to 9 with three new
  probes and renders: `arrangement-duo-inner-portrait-go.png` (blue band on
  top, red below), `arrangement-duo-inner-portrait-plan.png` (red left, blue
  right), `arrangement-duo-inner-landscape-hinge.png` (blue above, empty band
  at 314 to 354, red below). Positional assertions apply to the fallback; under
  `SYRMOS_DUO_SDK` they assert presence only because the native split owns the
  axis. The committed `go-duo-inner-portrait.png` reference changed on purpose:
  GO upright is now the stacked map-over-timeline layout.
- **Evidence tier**: iOS 27.0 simulator (default Xcode 27.0 SDK, CI-equivalent
  compile): DuoPostureFixturesTests 49/49, DuoSnapshotTests 9/9,
  ReservedRegionAdapterTests 26/26 (84 total). iOS 27.1 SDK with
  `SYRMOS_DUO_SDK` on the booted iPhone Duo simulator: DuoPostureFixturesTests
  49/49 and DuoSnapshotTests 9/9 (58 total) with the presence-only assertions,
  after the axis-restriction finding above was applied. The committed renders
  are the 27.0 run's (the Duo run was executed first, then the 27.0 run, so the
  shipping fallback's images are the ones on disk). Explore two-pane and the
  Android side of the stacked axis remain open.

## Landed: foldable UI polish on iPhone Duo and Android fold devices (six-posture prompt, delivery step 4, first slice)

Source: `docs/plans/IPHONE-DUO-SIX-POSTURES-AWARD-DESIGN-PROMPT.md`, sections 5 (P3 to P6) and 7.
Owner direction (2026-09-26): a visual polish of the paired and stacked layouts on
the iPhone Duo and Android fold devices only; GO stays.

- **iOS GO companion**: the route map is a card inside the pane (16 pt gutters,
  large radius, hairline outline) and the padding rule sees the card's rect; the
  timeline is a continuous rail per leg in the leg's real line colour with a dot
  per stop, the current stop emphasised, past stops dimmed, and the alight point
  labelled Change here or Destination; the leg header uses `LinePill` (official
  line colours) instead of a brand-tinted capsule. The stale footnote ("live
  get-off alerts are coming next") now describes live guidance honestly. The
  whole screen sits on the Calm Signal surface colour.
- **iOS Plan**: the companion pane has a Routes title in every state and a calm
  empty state before the first search (icon, one-line prompt, one-line
  explanation on the muted surface), so the unfolded display never shows a blank
  half; the screen sits on the Calm Signal surface colour.
- **Android GO**: `GoJourneyScreenRoute` now resolves the shared policy for its
  content box (`rememberContentWorkspace(WorkspaceTask.GO, ...)`): side by side
  puts the instruction and its controls beside a new `JourneyTimeline` companion
  (line-coloured pills from the seed's `Line.color`, a rail per leg, current stop
  emphasised, Change here and Destination labels); stacked puts the timeline
  above and the instruction below; a phone keeps the shipped single column. The
  stale footnote was replaced.
- **Android Plan**: the two-pane results column has a Routes title, the same
  calm empty state before the first search, and a hairline between the panes.
- **Tests and renders (iOS 27.0 simulator, default Xcode 27.0 SDK)**:
  DuoSnapshotTests 12/12 (three new Plan renders `plan-duo-inner-landscape.png`,
  `plan-duo-inner-portrait.png`, `plan-duo-cover.png`; GO references re-rendered
  on purpose), DuoPostureFixturesTests 49/49, JourneyGuidanceTests 6/6,
  GoJourneyViewModelTests 7/7, ReservedRegionAdapterTests 26/26 (100 total).
- **Android running surface** (`syrmos_tablet` emulator, density 160, the app's
  own seed data): at 841 x 673 dp (a fold's inner display, wide) Plan pairs the
  query with the Routes pane (empty state, then a real Piraeus to Syntagma result
  with its detail) and GO pairs the instruction with the timeline (M1 rail,
  Change here at Monastiraki, M3 leg to Syntagma marked Destination); at
  841 x 900 dp GO stacks the timeline above the instruction and controls.
  Screenshots `fold-plan.png`, `fold-plan-results.png`, `fold-go-wide.png`,
  `fold-go-tall.png` in the session scratchpad. Android pairs by content width,
  so an upright fold (673 wide with the nav rail) keeps a single column unless
  the device reports its fold region.
- **Timeline, second pass (owner: "still looks basic")**: the timeline is now
  leg cards. Each card's header carries the line pill (official colour), the
  direction and the leg's stop count; the stops sit on a 4 pt rail with origin
  and alight rings, small intermediate dots, a haloed current marker, and a
  caption pill naming the moment (Now, Next, Change here, Destination); the rail
  dims behind the rider; a dotted walking connector with "Change to M3" sits
  between legs; a one-line summary ("Piraeus to Elliniko, 3 lines, 18 stops")
  sits under the title. The row semantics live in a pure projection on both
  platforms: `GoTimelineProjection` (iOS, 6 tests in JourneyGuidanceTests) and
  `core/domain/.../go/GoTimeline.kt` (Kotlin, 7 tests), same journey and same
  expected roles, states and counts on both. Android draws the same cards from
  the shared projection. Renders: `go-duo-inner-landscape.png`,
  `go-duo-inner-portrait.png`; emulator `fold-go-wide2.png`.
- **Home direction board (owner request, both platforms)**: the Home hero used
  to show only the soonest departure and a "then 13, 23 min" line that mixed
  directions. It now shows, under the hero, one row per line and direction from
  the nearest station with the next two times (soonest direction first, capped
  to four rows); a single-direction station keeps the compact "then" line.
  iOS: `DepartureGrouping.directionBoard` (2 tests) reuses the shared grouping;
  Android: `HomeDirectionBoard.rows` in core/domain (2 tests) computed in
  `HomeViewModel` from all lines and both directions before the hero's own
  truncation, drawn by `DirectionBoard` in `HomeScreen.kt`. Verified on the iOS
  simulator at Omonia (M2 to Anthoupoli 3 and 18, M2 to Elliniko 8, M1 to
  Piraeus 10 and 25, M1 to Kifissia 12); on Android the board compiles and its
  rows are unit-tested, and the emulator run is recorded below.
- **Third pass (owner: "polish it more")**: a segmented per-leg progress bar in
  the line colours with "Stop X of Y" and the percentage replaces the grey bar
  (`GoLegProgress` on iOS, `GoTimeline.legProgress` on Kotlin, twin tests); the
  hero carries a state label (Ready to board, Riding, Alight soon, Transfer,
  Arrived) on both platforms and the Android hero sits on a wash of the line
  colour; the phone column shows the timeline cards under the controls instead
  of empty space on both platforms.
- **Two Android defects found while verifying**: (1) `LineColor.fromHexOrType`
  matched the seed hex exactly or fell back by type, so M2 (#E61E2A) and M3
  (#0083C9) rendered GREEN everywhere `Line.color` is used; it now snaps to the
  nearest palette colour (`LineColorTest`, 3 cases), verified blue on the
  emulator. (2) `GoJourneyScreenRoute` carried a non-serialisable journey, so a
  rotation or fold (activity recreation) dropped GO back to the tab root; its
  fields are now transient and the screen rebuilds the journey from the
  persisted live session, verified by resizing the window mid-journey (D05,
  D12 on Android).
- **Evidence tier**: iOS simulator renders and a live simulator run plus the
  Android emulator running surface (`go-colour.png`, `go-recreated.png`,
  `phone-go.png` in the session scratchpad); no fold hardware, no Duo hardware.

## Landed: polish round 2 after 3.0.0-beta.3 (Departures pairing, Home interchange cluster, small fixes)

- **Departures pairs** (six-posture prompt section 6, parent prompt section 6):
  iOS `TimetablesView` hands `planningCards` and `boardCards` to
  `SyrmosArrangement(task: .departures)`; Android `AirportHubScreen` resolves
  `rememberContentWorkspace(WorkspaceTask.DEPARTURES, ...)` and lays the same
  two groups side by side with a hairline. `rememberContentWorkspace` and
  `LocalReservedRegions` moved to `core/designsystem/.../layout/` so feature
  modules can pair without depending on the app module. Fixtures:
  `departures_pairsSideBySideOnBothInnerOrientations` (Kotlin) and its Swift
  twin. Renders `departures-duo-inner-landscape.png`, `departures-duo-cover.png`.
- **Home sees the whole interchange on Android**: `NearestStationCluster`
  (150 m radius over the per-line station ids, never enriching line ids), so the
  hero and the direction board load M1 and M2 at Omonia like iOS; verified on
  the Google-APIs Pixel emulator (four directions, two times each).
- **What's new for 3.0** on both platforms; **Plan endpoint placeholder** on both
  platforms; **dark GO render**; launcher clearance under the Android Plan query
  column.
- **Readable width on Android** (Home, Explore, Settings lists centre in a 760 dp
  column, the iOS `ReadableTabContent` maximum), verified at 1280 x 800 dp on the
  emulator; the 1280 capture also exposed the **hero countdown wrap** (a train at
  the platform read "23h 59min"), fixed in `LocalTime.secondsUntil` with a
  one-minute grace and a zero clamp mirroring iOS (`DateTimeExtensionsTest`
  11/11). **TalkBack parity**: direction-board rows, timeline stops and the
  segmented progress bar are single merged elements on Android, as their iOS
  twins are for VoiceOver; the Android GO top bar reads GO with the
  journey-in-progress line beneath, as on iOS.
- **Open, queued as their own rounds**: Explore list-detail pairing (push
  navigation today on both platforms, needs a split-view shape), Home paired
  with a map on wide windows, Duo hardware confirmation of the reported regions.

## Landed: polish round 3 (Explore pairing, orientation-aware axis)

- **Explore pairs** (six-posture prompt section 6): iOS `LinesView` hands its list
  to `SyrmosArrangement(task: .explore)` as the task pane and shows the selected
  line's `LineDetailView` in the companion (a calm invitation before a choice, the
  selected row highlighted); the arrangement now publishes `\.syrmosIsPaired` so
  list content swaps push links for selection. Android `ExploreTab` hosts
  `LineDetailPane` (extracted from `LineDetailScreenRoute`) beside `LinesScreen`
  through `rememberContentWorkspace(EXPLORE)`, side by side or stacked; back in
  the pane clears the selection instead of popping. Phones keep push navigation.
- **Orientation-aware axis**: the first Android capture (841 x 673 with the rail,
  a 761 x 649 content box) stacked Explore with the placeholder band above the
  list because the medium-canvas rule honoured the task's tall-canvas preference
  regardless of orientation. The shared policy and its Swift twin now apply that
  preference only when height exceeds width; a landscape window pairs as two
  columns for every task. Fixture `wideMediumWindow_pairsSideBySideEvenForStackingTasks`
  on both platforms (Kotlin suite 22, Swift suite 51).

## Build gating: the native ArrangementView path (SYRMOS_DUO_SDK)

`ArrangementView` and its modifiers are iOS 27.1 **SDK** symbols. `#available(iOS
27.1, *)` gates only the runtime, not compilation, and the Swift compiler version
does not discriminate (Xcode 27.0 and 27.1 both ship Swift 6.4, but only the 27.1
SDK exposes the symbol). Swift has no SDK-version `#if`, so `SyrmosArrangement`
compiles the native `ArrangementView` split only under the custom flag
`SYRMOS_DUO_SDK`; otherwise it compiles the width-split `HStack` fallback, which is
the shipping two-pane.

- **CI and release builds** run on Xcode 26.x / the 27.0 SDK, leave the flag
  undefined, and build the fallback. Verified: `xcodebuild ... -destination
  "generic/platform=iOS Simulator" ARCHS=arm64` under `/Applications/Xcode.app`
  (27.0 SDK) BUILD SUCCEEDED. Without the flag guard the same build failed with
  "cannot find 'ArrangementView' in scope" (PlanFlow.swift), which is what broke
  the PR's `iOS build + tests` / `Build app scheme` jobs.
- **To exercise the native path** build against the 27.1 SDK with the flag defined:
  `xcodebuild ... SWIFT_ACTIVE_COMPILATION_CONDITIONS="SYRMOS_DUO_SDK"` under
  `~/Downloads/Xcode_27.1.app`. Verified BUILD SUCCEEDED. All runtime verification
  and the committed Duo snapshots use the fallback (the shipping path), so nothing
  above depends on the flag being on.

## Landed: iPhone Duo snapshot tests (Phase 5, iOS)

- **`iosAppTests/DuoSnapshotTests`** renders the adaptive two-pane at the **real**
  iPhone Duo display geometry and writes reference PNGs to
  `iosAppTests/__DuoSnapshots__/` so the Duo layout can be seen without the
  hardware. **Geometry correction (finding):** the first cut rendered at 466 x 678
  pt, which is the Duo's **cover (folded)** display, not the unfolded inner screen,
  so the "Duo" images were a cramped phone column instead of the two-pane. Measured
  on the booted Duo sim (pixels / scale 3): inner (unfolded) 2007 x 2853 px ->
  **669 x 951 pt**; cover (folded) 1398 x 2034 px -> **466 x 678 pt**. The inner
  display is wider than the 640pt pair threshold in both orientations, so the
  unfolded Duo always pairs; the cover is below it, so the folded phone stays single
  column. Five cases, all green: the probe pairs on the unfolded inner display and
  stays single column on the folded cover; `GoJourneyView` renders as the two-pane
  on the inner display (both orientations) and as the single column on the cover.
  Assertions are presence-based (both pane colours when paired, only the combined
  colour when single; real content, not blank), so they hold whether the split is
  side-by-side or stacked.
- **Reference images** (committed): `arrangement-duo-inner.png` / `-cover.png`
  (probe split vs single) and `go-duo-inner-landscape.png` / `go-duo-inner-portrait.png`
  / `go-duo-cover.png`. The inner shots show the intended, branded two-pane:
  instruction + controls on the left, the live Athens route map (M1 polyline over
  the Esri gray base) with the full multi-leg journey timeline on the right. The
  render pins light appearance so the UI is captured as shipped, not on a black
  default canvas.
- **Native ArrangementView finding (iOS 27.1 Duo runtime):** the 27.1 runtime and
  an iPhone Duo device (`SimRuntime.iOS-27-1`) are now installed, so
  `ArrangementView(...).arrangementViewStyle(.split)` executes. On the real Duo it
  splits the two panes **vertically** (primary top, secondary bottom) and reserves
  a hinge/occlusion region on one edge, rather than the side-by-side split of the
  pre-27.1 `HStack` fallback. In a headless `UIHostingController` snapshot the
  native path also honours that reserved region, which offsets content, so the
  committed reference images are rendered via the fallback path (27.0) for a clean,
  faithful view of the two-pane content; the native split is validated separately
  by the probe (both panes present). Follow-up: have GO/Plan content respect the
  Duo reserved-region insets so nothing is clipped under the hinge.

## Landed: iOS GO two-pane (Phase 3/4, iOS)

- **`GoJourneyView`** adopts `SyrmosArrangement`: on a regular-width display the
  current instruction + reachable actions (hero, progress, Back / Next stop, live
  toggle) sit in the task pane, and a new **leg/stop timeline** companion beside it
  shows every leg (line badge + towards) with the current stop highlighted (prompt
  9.2 GO: "current instruction ... alongside ... upcoming legs"). Compact keeps the
  shipped single column. The body is wrapped in a `NavigationStack` so the title +
  End toolbar render, and the two GO presentations in `PlanFlow` became
  `fullScreenCover` so GO is full-window (End is now a deliberate toolbar action).
- **Runtime-verified on the iPad Pro 13" (iOS 27.0) under Xcode 27.1:** GO renders
  the two-pane (task pane | divider | timeline); tapping **Next stop** advances the
  instruction AND moves the timeline highlight in sync (Piraeus -> Faliro).
  Screenshots: `go_twopane.png`, `go_twopane_advanced.png`.
- **Companion is now a live route map above the timeline.** `GoRouteMapView`
  (`UIViewRepresentable` wrapping `MKMapView`, matching the app's map screen rather
  than SwiftUI `Map`) draws the journey's stops as one polyline over the shared Esri
  gray base (`SyrmosMKMapView.makeEsriGrayOverlay`), tinted with the current line
  colour, and marks the rider's current stop with an emphasised dot; the map
  recenters on the current stop as the rider advances. The stop-to-coordinate
  placement is a pure helper (`GoRouteProjection`, resolver-injected) shared with
  the tests. Companion layout: map on top (~42% height, min 200pt) then the
  leg/stop timeline, so the rider sees position and upcoming legs in one glance
  (prompt 9.2 GO: "current progress ... alongside the map").
- **Runtime-verified on the iPad Pro 13" (iOS 27.0) under Xcode 27.1:** the Piraeus
  -> Elliniko demo renders the M1 route line over the gray base with a current-stop
  dot at Piraeus; tapping **Next stop** moves the instruction to "Stay on M1", the
  timeline highlight to Faliro, and recenters the map on the new current stop.
  Screenshots: `go_ipad_3.png`, `go_ipad_next_3.png`. Tests:
  `JourneyGuidanceTests` `test_routeCoordinates_*` / `test_currentCoordinate_*`
  (4 new, all green) cover ordering, unplaceable-stop skipping, position tracking,
  and out-of-range/nil.

## Landed: iOS Duo two-pane for Plan (Phase 3, iOS)

Unblocked by **Xcode 27.1** (at `~/Downloads/Xcode_27.1.app`, iOS 27.1 SDK): the
Duo APIs are now source-visible (`ArrangementView`, `SplitArrangementViewStyle`,
`splitArrangementLayoutRatio`, `overlayArrangementZIndex`, `reservedRegions(kind:)`,
`ReservedRegion.Kind`, plus `ToolbarOverflowMenu`/`visibilityPriority`).

- **`SyrmosArrangement`** (in `iosApp/.../Features/Assistant/PlanFlow.swift`): an
  adaptive two-pane container. On a regular-width container it pairs the task pane
  with its companion; on iOS 27.1 it uses the native `ArrangementView` split style
  (`.arrangementViewStyle(.split)` + `.splitArrangementLayoutRatio`), which further
  adapts to Duo postures/regions itself, with an `HStack` width-split fallback for
  iOS 17..<27.1. On a compact width it renders the shipped single scrolling column,
  so the ordinary iPhone flow is unchanged. Gated by `horizontalSizeClass`.
- **Plan adopts it**: the editable query + saved journeys form the task pane and the
  route options + selected detail the companion pane, mirroring the Android two-pane
  (prompt section 9.2). The body was split into `planPreamble` / `planQuery` /
  `planResults` blocks with no duplicated source of truth.

Verification: the app **builds and runs under Xcode 27.1** on 27.0 iPhone and iPad
sims, and the Duo `ArrangementView` path **compiles against the 27.1 SDK** (Duo
SDK-integration tier). The **compact single-column Plan is runtime-verified** on both
the iPhone (full-width sheet) and the iPad (form sheet) — both correctly stay single
column.

`SyrmosArrangement` decides by **available width** (>= 640 pt to pair), mirroring the
Android policy. A UIKit form sheet is always horizontally *compact* regardless of
pixel width (and the iPad Plan form sheet was only ~564 pt), so `LinesView` now
presents Plan as a **full-screen cover on regular width** (iPad, iPhone Duo inner
display) and keeps the bottom sheet on compact (iPhone).

**Runtime-verified on the iPad Pro 13" (iOS 27.0) under Xcode 27.1:** with Plan
full-window, `SyrmosArrangement` renders the **two-pane split** — the editable query +
station search + saved journeys in the ~42% task pane, a vertical divider, and the
route-results companion pane (screenshot in the session scratchpad,
`ipad_plan_twopane.png`). On the 27.0 runtime this is the `HStack` fallback path;
the native `ArrangementView` path is compile-verified against 27.1 and would take over
on an iOS 27.1 / Duo runtime. Compact single-column is verified on the iPhone. The
D01-D24 Duo-specific runtime cases remain gated on an iOS 27.1 runtime (not
downloadable here).

## Landed: browse + detail screen continuity (Phase 2, continuity)

- **Filters already survive recreation** — verified empirically that the Explore
  segment (Discover / Network) and its region/type filters persist across a
  rotation, because `LinesViewModel` (and the other feature view models) are Koin
  `single`s whose state lives at the process level. Browse-list scroll uses
  Compose's default saveable `LazyListState`. No change was needed here; a fix
  would have been invented where none was warranted.
- **Detail-screen scroll fixed** — a pushed detail screen (`LineDetailScreen`, and
  defensively `StationDetailScreen`) lost its scroll position on recreation: the
  route re-loads the entity in a `LaunchedEffect`, which briefly shows the loading
  spinner, and the `LazyColumn`'s implicit list state (created inside the content
  branch) was discarded during that flash. Hoisted a saveable `rememberLazyListState`
  above the loading gate so the visible item and offset survive the flash and the
  recreation (section 7: "preserve the visible list item and offset").

## Landed: map camera intent continuity (Phase 2, continuity)

- **No reset to Athens on recreation** — `PlatformMapView.android.kt` persisted its
  camera (center lat/lng + zoom) in `rememberSaveable`, updated live from the map
  listener and restored when the `MapView` is recreated. The initial Athens fit now
  only runs when there is no saved camera, so a rotation / fold no longer snaps the
  map back to the Athens frame. The selection-recenter and locate-me effects gained
  per-change guards (`lastAnimatedSelectionId`, `lastLocateHandled`) so they fire on
  a real change, not on every recreation, which would otherwise fight the restored
  camera. Manual pan/zoom is honored; a NaN sentinel distinguishes "no camera yet".

## Landed: GO session continuity (Phase 2, continuity)

- **Progress already durable** — the GO screen derives its position from the
  persisted `ActiveJourneyRepository`, so the current instruction/leg already
  survives an activity recreation (verified: rotating mid-ride kept "Stay on M3,
  12 stops"). No begin flow runs in the GO screen, so recreation cannot duplicate
  the session.
- **Return to GO after process death** — the app landed on the last tab after a
  cold launch, losing the live session from view. Added a one-shot, per-process
  gate (`GoResumeGate`) in the root: on a genuine cold start with a persisted
  session it switches to Home and dispatches `NotificationNavEvent.ResumeGo`; the
  Home navigator rebuilds guidance from the snapshot (`buildGuidanceJourney`, now
  reusable) and pushes GO where the rider left off. The gate is a process field so
  an activity recreation does NOT re-trigger it, and the Home handler skips the
  push if GO is already on top, so no duplicate GO is ever stacked.

## Landed: Plan continuity ownership (Phase 2, continuity)

- **Draft + selection survive recreation** — `PlanScreenRoute.kt` moves the small
  restoration keys (from/to ids, open picker, query, timing mode, arrive-by text,
  step-free, selected option index) from `remember` to `rememberSaveable`, so they
  survive a configuration change (rotation / fold / resize) and process death.
  Large derived data (stations, options, disruption) stays in `remember` and is
  reconstructed after restoration by a `LaunchedEffect(stations)` that re-plans the
  saved endpoints without disturbing the rider's selected option (`runPlan` gained
  a `resetSelection` flag; the restoration path clamps instead of snapping to 0).

## Landed: Android geometry + first pane flow (Phase 2)

- **FoldingFeature adapter** — `composeApp/.../app/platform/ReservedRegions.kt`
  (expect) with an Android actual reading `WindowInfoTracker.windowLayoutInfo`
  (lifecycle-scoped flow via `produceState`, no sensor polling) and mapping each
  `FoldingFeature` to a `ReservedRegion` in window dp; iOS and wasmJs actuals
  return empty. Added `androidx.window:window:1.3.0` to the catalog and the
  composeApp Android source set only.
- **Compose glue** — `composeApp/.../app/layout/AdaptiveWorkspaceCompose.kt`:
  `LocalReservedRegions` (provided once at the app root) and
  `rememberContentWorkspace(task, width, height, ...)` which resolves the policy
  for a measured content box and translates window-space fold regions into the
  box's own coordinates (the post-inset contract), so a fold behind the nav rail
  never splits a pane.
- **Plan two-pane** — `PlanScreenRoute.kt` now measures its own content box and,
  on a `SIDE_BY_SIDE` workspace, renders the editable query + saved journeys in
  the task pane and the route options + selected detail in the companion pane;
  compact keeps the shipped single scrolling column in the shipped order.
- **Duo device-geometry fixture (parity with iOS)** — `AdaptiveWorkspaceTest`
  pins the corrected iPhone Duo geometry so the shared policy agrees with the iOS
  `DuoSnapshotTests`: the unfolded **inner** display is 669 x 951 dp (2007 x 2853
  px / scale 3), NOT the folded cover's 466 x 678. Android pairs the Duo on the
  hinge the window reports (region driven), not raw width, so the two inner-display
  cases (landscape 951 x 669 and portrait 669 x 951) include the book-posture
  vertical hinge and resolve `SIDE_BY_SIDE`, while the folded cover (466 x 678, a
  plain phone window) stays `SINGLE`. This mirrors the iOS inner-geometry
  correction rather than the original cover-size render.

## Runtime verification (Android, this slice)

Emulator: `syrmos_tablet` AVD (pixel_tablet, android-34), 2560x1600 @ 320dpi =
1280x800 dp. Debug `:androidApp` built and installed; artifacts in the session
scratchpad.

| Check | Result | Evidence |
| --- | --- | --- |
| Wide window (1280dp): Plan renders two panes | Pass | Query + saved in the ~360dp task pane; "1 route" + option card + leg timeline + Start journey in the companion pane (Syntagma → Airport, M3, Comfortable). |
| Compact window (411dp via `wm size`): Plan single column | Pass | From/To/chips/Find/Saved stacked in the shipped order. |
| Nav adaptation | Pass | Navigation rail on the wide window, floating bottom bar on the compact window (existing behavior preserved). |
| Continuity through activity recreation | Pass | Rotated the tablet with a planned Syntagma to Airport route: endpoints, timing mode, and the reconstructed route (M3, timeline, Start journey) all restored, while the layout re-fit from two-pane to single column at 800dp. Before the change the same recreation wiped the screen to empty. |
| GO session continuity: activity recreation | Pass | Rotated mid-ride: "Stay on M3, 12 stops to Airport" preserved, no duplicate GO, no jump to Home. |
| GO session continuity: process death | Pass | Killed the process mid-ride and cold-launched: the app auto-restored GO at the same position (12 stops) instead of the last tab. Back from the restored GO returns to Home content (single GO on the stack). |
| Map camera intent continuity | Pass | Panned the Map tab far east of Athens, then rotated (recreation): the map stayed at the panned camera instead of re-fitting the Athens frame. The initial Athens fit still runs on a genuine first open. |
| Explore filters continuity | Pass | Switched to the Network segment + Tram filter, rotated: both preserved (singleton view model state). |
| Detail-screen scroll continuity | Pass | Scrolled the Tram T7 line detail near the end (station 30+ of 43), rotated: the visible station/offset was preserved. Before the fix the same rotation reset it to the top. |
| No crash / launch, onboarding, permissions | Pass | Cold launch, onboarding skip, location/notification prompts, no `FATAL`. |
| Cross-target compile | Pass | `:composeApp:compileKotlinWasmJs` and `compileKotlinIosSimulatorArm64` green; web build and iOS untouched. |

## Evidence levels (prompt section 12.3)

| Level | Status | Evidence |
| --- | --- | --- |
| Supported foundations | In progress | Shared policy + 20 tests green via `:core:common:testDebugUnitTest` (JDK17); `ContentBreakpointTest` 9/9 still green. Android running-surface: Plan two-pane at 1280dp and single-column at 411dp verified on the emulator; wasmJs + iOS compile green. |
| Duo SDK integration | In progress | Xcode 27.1 (iOS 27.1 SDK) exposes the Duo APIs; the iOS Plan two-pane uses the real `ArrangementView` split style and compiles against 27.1, with the iOS 17 fallback also building. |
| Duo runtime validation | Pending | No Duo simulator/device runtime available. |
| Physical-device quality | Pending | No foldable / Duo hardware in this environment. |

## Acceptance snapshot (prompt sections 12.1 / 12.2)

Only rows with real evidence are marked; everything else is Pending with a reason.
Synthetic-geometry policy fixtures cannot satisfy a native-runtime requirement.

| Case | Result | Evidence / reason |
| --- | --- | --- |
| Policy fit: compact / medium / expanded / wide window | Pass (synthetic) | `AdaptiveWorkspaceTest` window cases. |
| Policy fit: vertical fold (book), horizontal fold (tabletop) | Pass (synthetic) | `AdaptiveWorkspaceTest` region cases. |
| Occluding hinge kept clear, no pane bridges it | Pass (synthetic) | `aVerticalOcclusion...`, `aHorizontalOcclusion...`. |
| Division vs occlusion distinguished; inactive region blanks nothing | Pass (synthetic) | `aVerticalDivision...`, `anInactiveRegion...`. |
| Large text / short window collapse to one column | Pass (synthetic) | `largeTextForcesOneColumn...`, `shortWindow...`. |
| Fold outside the window does not split it | Pass (synthetic) | `aRegionOutsideTheWindow...`. |
| Android two-pane on a wide running surface (12.1 #19) | Pass | Plan at 1280dp: query/saved task pane + results companion pane on the emulator. |
| Android compact single column (12.1 #1) | Pass | Plan at 411dp: shipped single scrolling column, floating bottom bar. |
| Android fold-posture running-surface scenarios (12.1 #2-8) | Pending | No foldable AVD in this environment; the fold path is unit-tested + compile-verified only. |
| Android continuity through recreation (12.1 #11-13) | Partial | Plan draft/selection, the GO session, the map camera, the Explore filters, and detail-screen scroll all survive activity recreation; the GO session also survives process death (auto-restored on cold launch). Residual: singleton view-model browse state (Explore segment/filters) resets on a cold launch after process death, accepted as lower priority than a live journey; the iOS side is separate. |
| iPhone/iPad running-surface scenarios (12.1 #20) | Pending | iOS mirror + scene wiring not yet implemented. |
| Duo D01–D24 (12.2) | Pending | Gated on Duo SDK arrangement APIs and a Duo runtime. |
| Six-posture policy P1 to P6 and transitions T1 to T6 (six-posture prompt, section 11) | Pass (synthetic) | `DuoPostureFixturesTest.kt` 20/20 and `DuoPostureFixturesTests.swift` twins, same names and numbers on both platforms. |
| Swift policy mirror reproduces the Kotlin fixtures | Pass (XCTest) | `DuoPostureFixturesTests.swift` on the iOS 27.0 simulator; includes the 640 pairing-floor parity guard against the shipped `SyrmosArrangement`. |
| Android Plan two-pane on a medium-width plain window (parent prompt section 5, 688 to 839 band) | Pass | Tablet emulator at 768 x 1024 dp: two-pane; at 669 x 951 dp with the rail: single column, unchanged. |
| Reserved regions normalised into the content box; fold beside the box never splits it; cutout is not a division (parent 9.4 rules 1 to 3) | Pass (synthetic) | `ReservedRegionAdapterTests.swift` normalisation cases. |
| Map padding from visible panel and occupied regions, no camera reset (parent 9.4 rule 6; D15) | Pass (render) | `SyrmosMapPadding` cases plus `go-duo-inner-landscape-hinge.png`: current stop at the padded centre, route clear of the injected bar. |
| Gated `reservedRegions` reader on the Duo runtime | Partial | Compiles and runs under 27.1 with `SYRMOS_DUO_SDK` (32/32 on the Duo sim); the headless probe read no regions, so the reported frames and their coordinate space are unconfirmed. |
| GO on the tall inner display stacks map above timeline and controls (P5, section 5) | Pass (render) | `go-duo-inner-portrait.png` and `arrangement-duo-inner-portrait-go.png`, iOS 27.0 simulator. |
| Plan on the tall inner display pairs side by side at half width (P5) | Pass (render) | `arrangement-duo-inner-portrait-plan.png`. |
| An occluding horizontal fold stacks on the fold and leaves the band empty (P6, parent 9.4) | Pass (render, injected region) | `arrangement-duo-inner-landscape-hinge.png`; the system's own regions on hardware remain Pending. |
| Dynamic Type raises pane floors; accessibility sizes collapse to one column (D21) | Pass (synthetic) | `test_dynamicTypeScale_*` in `DuoPostureFixturesTests.swift`. |
| Native `ArrangementView` honours a requested axis on the Duo runtime | Fail (recorded) | `.split.axes(_:)` hid the secondary pane on the Duo simulator; the unrestricted `.split` shows both panes and the system owns the axis. |
| Paired panes read as their own surfaces: titles, empty states, no blank half (section 5) | Pass (render + emulator) | iOS `plan-duo-inner-*.png`; Android `fold-plan.png`. |
| GO companion is a map card plus a line-coloured timeline rail (sections 5, 7) | Pass (render) | iOS `go-duo-inner-landscape.png`, `go-duo-inner-portrait.png`. |
| Android GO pairs and stacks on a fold-sized window (parent prompt section 6, GO) | Pass (emulator) | `fold-go-wide.png` side by side, `fold-go-tall.png` stacked. |
| Timeline rows (roles, states, counts) agree on iOS and Android | Pass (synthetic) | `GoTimelineTest.kt` 7/7 and the six `test_timeline_*` twins in `JourneyGuidanceTests.swift`. |
| Home shows the next train in every direction of the nearest station | Pass (iOS simulator), Partial (Android: unit-tested, emulator location pending) | `DepartureGroupingTests` board cases, `HomeDirectionBoardTest.kt`; iOS run at Omonia. |

## Remaining phases (prompt section 11)

1. **Continuity on both platforms** (Plan + GO + map camera + browse/detail on
   Android done) — Plan's draft + selection, the live GO session, the map camera,
   the Explore filters, and detail-screen scroll now survive Android recreation
   (GO also survives process death). Remaining: process-death persistence of the
   browse view models (lower priority), and the iOS `SceneDelegate` host
   replacement.
2. **Native geometry + navigation** (Android done for the first flow; iOS not
   started) — the FoldingFeature adapter and the shared policy are wired and Plan
   consumes them. Remaining: adopt the workspace in the Android root itself and
   the other screens; on iOS adopt `NavigationSplitView` + the available toolbar
   overflow APIs, keeping the Duo arrangement/region calls behind the section 9.1
   gate.
3. **Complete product flows** — Plan two-pane done on Android; GO next, then Now,
   Explore, Departures, fares, Ariadne, settings, onboarding, and the iOS mirror.
4. **Interaction + visual refinement** — bars/overflow, keyboard, focus,
   typography, source labels, reduced motion/transparency, map padding.
5. **Regression + evidence** — full matrix, builds/tests, performance traces;
   update this record with per-case Pass/Fail/Pending and artifact paths.

## Exact unresolved dependencies

- Duo paired-content arrangement APIs (`ArrangementView`, `overlayArrangementZIndex`)
  are not in the installed iphoneos27.0 SDK interface.
- A Duo simulator runtime / device for D01–D24.
- A running Android foldable emulator/device (no foldable AVD here) to exercise
  the FoldingFeature path on a real surface, and iOS/iPad simulators for the iOS
  running-surface scenarios.
- Material adaptive (list/detail, supporting-pane) scaffolds are not yet added;
  the current adoption drives layout from the shared policy directly. `androidx.window`
  1.3.0 is now in the catalog.
