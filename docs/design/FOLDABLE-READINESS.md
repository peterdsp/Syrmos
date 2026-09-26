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

## Landed: polish round 4 (GO refinements from the master plan's screenshot review)

Source: `docs/plans/FOLDABLES-IPHONE-DUO-MASTER-PLAN.md`, section 2 (screenshot findings) and the GO contract in section 4.

- **Per-leg map colours**: `GoRouteProjection.legRuns` yields one coordinate run
  per leg with its line id (legs with fewer than two placeable stops draw
  nothing); `GoRouteMapView` draws one `GoLegPolyline` per run in the leg's real
  line colour, so the interchange reads as a colour change on the map as it does
  on the timeline. Test `test_legRuns_oneRunPerLegInRideOrder_skippingUnplaceableLegs`.
- **Portrait and folded composition**: `SyrmosArrangement` now publishes
  `\.syrmosArrangementAxis`; on the vertical axis GO keeps the map alone in the
  upper region and reads the timeline under the instruction, so the timeline is
  no longer squeezed into the upper band. Side by side is unchanged.
- **End confirmation** on both platforms: ending an unfinished journey asks
  ("End this journey?" with "End journey" / "Keep going", four languages);
  Finish after arrival stays one tap. One end path on iOS (`endJourney()`), the
  existing `endJourney()` on Android behind an `AlertDialog`.
- **Android custom top bars below the status bar**: the GO and Plan screens draw
  their own top bar (not a `TopAppBar`), and it sat under the status bar, so
  the End and Back controls were only partly tappable on the emulator. Both bars
  now take `statusBarsPadding()`. Verified on the Pixel emulator at the fold
  geometry: the End button reports bounds below the status bar and the tap
  opens the confirmation dialog.

## Landed: polish round 5 (Plan: compare with confidence)

Source: master plan section 4 (Plan contract) and Phase 3 item 1 (populated
route comparison and selected-route context with stable identity).

- **Pane roles**: on a paired layout the task pane is the editable query plus
  the route alternatives (count/save row, one card per usable option, the
  disruption chip and the no-route/suspended states); the companion pane is
  "Selected journey": the S05 detail (summary, comparison line, timeline,
  source line, Start). Before a search the companion shows the calm invitation;
  after a search that left nothing selectable it says so ("No journey to show
  yet.") instead of inventing a route. Single column keeps the shipped order
  (query, alternatives, selected detail, saved).
- **Shared comparison facts**: `JourneyComparison.facts(durations, changes)`
  (Kotlin core/domain, Swift twin in `Core/Journey/JourneyComparison.swift`)
  marks `fastest` and `fewestChanges` only when a real difference exists (a
  lone route is never decorated), and gives `minutesSlowerThanFastest` (rounded,
  zero dropped, unknown durations never compared) and `extraChanges`. Cards show
  chips ("Recommended" from the shared ranker, "Fastest", "Fewest changes"),
  the selected journey a line such as "+10 min vs fastest · 1 more change".
  Fixture parity: `JourneyComparisonTest` (10) and `JourneyComparisonTests` (10).
- **Stable itinerary identity**: `JourneySelection.retain(previous, ids)` keeps
  the selected option id across a results refresh when it is still offered and
  falls back to the first otherwise; both clients replaced the array index with
  the option id (`selectedId`), so a fold or a time-driven re-plan never changes
  the traveller's choice by position.
- **Verified**: iOS 32/32 (comparison, snapshot, journey detail) on the
  Syrmos 27 simulator, `plan-duo-inner-*.png` re-rendered; Android Pixel
  emulator at 841x673, Piraeus to Syntagma: left pane "2 routes" with
  "Recommended, ~28 min, 1 change, M1 to M2, Comfortable" and
  "Fastest, ~18 min, 1 change, M1 to M3, Tight", right pane the selected
  journey with "+10 min vs fastest" and the timeline (uiautomator dump and
  screenshot).

## Landed: polish round 6 (Home: the board is the task, the network reads alongside)

Source: master plan section 4 (Home contract) and Phase 4 item 1.

- **Pane roles**: `SyrmosArrangement(task: .home)` on iOS and
  `rememberContentWorkspace(HOME)` on Android. Task pane: the answer section
  (hero with the direction board for every direction, or the tracking card),
  the living map strip and the weather context. Companion pane: the insights
  stream (alerts, news, status), the radial nearby section and the live trains.
  The combined (single column) order is unchanged. The deep-link anchors keep
  working: the weather anchor lives in the task pane, the nearby anchor in the
  companion; on Android each column has its own list state and the
  scroll-to-weather request targets the answer column.
- **No duplicate work**: the two panes render the same view state; there is no
  second poll, location request or announcement fetch for the companion.
- **Verified (Android)**: Pixel emulator at 841x673, Home: left pane "MORNING
  COMMUTE", next train M2 to Elliniko with the four-direction board, Track and
  Track a train; right pane "What matters now" (network status and the STASY
  M3 works notice) and "Around you". At 411x891 the same build renders the
  single column (hero, board, then the rest).
- **Verified (iOS)**: `DuoSnapshotTests` now render `HomeView()` at the Duo
  inner landscape, inner portrait and cover sizes (`home-duo-*.png`); the inner
  renders show the two panes, the cover the single column.

## Landed: polish round 7 (Network Map: canvas plus inspector)

Source: master plan section 4 (Network Map contract) and Phase 3 item 3.

- **Shared policy**: a `MAP` task on both twins (`WorkspaceTask.MAP`,
  `SyrmosWorkspaceTask.map`). Its inspector (station or train) is the TASK pane
  and the canvas the COMPANION, the same roles GO and Explore use, so a tall
  window stacks the canvas above the inspector (`tallCanvasAxis = STACKED`) and
  a wide one puts the inspector beside it. Fixtures on both platforms:
  `p5_tallCanvas_mapStacksCanvasAboveInspector`,
  `p3_flatLandscape_mapPairsInspectorBesideCanvas`, `p1_cover_mapStaysSingleColumn`
  (Kotlin layout suite 59, Swift fixtures 54).
- **iOS**: `TransitMapView` wraps the canvas in `SyrmosArrangement(task: .map)`;
  the station and vehicle sheets are attached only to the single-column
  `combined` content, and the paired `mapInspector` shows the same
  `StationSheetView`, `SimulatedVehicleDetailSheet` or `TrainDetailSheet` with a
  Close control and a calm placeholder. One map instance; no padding is
  subtracted for the companion because the map's bounds already exclude it.
- **Android**: `MapScreen` resolves `rememberContentWorkspace(MAP)`; the canvas
  lambda (map, header, pills, controls) is laid out beside or above a
  `MapInspectorPane` that hosts the same `StationSheetCard`, `SimulatedTrainDetailCard`
  or `TrainDetailCard`; the slide-up overlays render only in the single column.
  The osmdroid view paints outside its bounds, so the canvas box is
  `clipToBounds()`; without it the map painted over the inspector.
- **Verified**: iOS `DuoSnapshotTests` 22/22 with `map-duo-inner-landscape.png`
  (inspector left, canvas right, header across), `map-duo-inner-portrait.png`
  (canvas above), `map-duo-cover.png` (single column). Android Pixel emulator at
  841x673: "On the map" inspector at x 96 with the placeholder, canvas from
  x 460 with the FABs at the right edge; tapping a projected train fills the
  inspector with the train card (Line 2 towards Anthoupoli, next station,
  trip progress, the honest "approximate estimate" note) while the map keeps
  its canvas.
- **Observed, not caused here**: after a forced restart the emulator once
  reported "Syrmos isn't responding" with reason "No response to onStopJob"
  (a scheduled background job); the UI recovered on Wait. Analysed in round
  15: the two WorkManager workers (`AlertCheckWorker`, `SnapshotWorker`) are
  `CoroutineWorker`s whose network calls run under `Dispatchers.IO` with 10 s
  timeouts, so `onStopJob` itself has nothing to wait for; that ANR is raised
  when the main thread cannot service the JobScheduler callback in time, which
  matches the moment it happened: a cold start right after `am force-stop`
  while Gradle and Xcode saturated the host CPU. Not reproduced since across
  a dozen restarts. No code change; keep an eye on cold-start main-thread work
  if it recurs on hardware.

## Landed: polish round 8 (Android GO route map: the flagship parity gap)

Source: master plan section 4 (GO contract, Map row and camera intent) and
Phase 5 (Android parity). Until this round Android GO paired the instruction
with the timeline only; iOS had the per-leg coloured route map since round 4.

- **Shared projection**: `GoRouteRuns.legRuns(journey, coordinate)` in
  core/domain/go is the Kotlin twin of iOS `GoRouteProjection.legRuns`: one
  coordinate run per leg in ride order, a leg with fewer than two placeable
  stops draws nothing; `currentPoint` places the rider's stop. Fixtures mirror
  the iOS test (`GoRouteRunsTest`, 3).
- **Map composable**: `GoRouteMapView` (feature/map, expect/actual). Android
  draws on osmdroid: one `Polyline` per leg in the line colour, a haloed
  `Marker` on the current stop, `CopyrightOverlay` for attribution, and a
  camera with explicit intent: the route is fitted when `fitTick` changes
  (first layout and the Fit route control) and manual exploration is respected
  otherwise. iOS and wasm targets draw the same legs on a canvas
  (`GoRouteFallbackMap`), so a missing tile layer never leaves a blank surface.
- **GO composition** (`GoJourneyScreenRoute`): side by side keeps the
  instruction in the task pane and puts the map card above the timeline in the
  companion; upright (stacked) gives the map the upper region alone and reads
  the instruction plus the timeline below, matching the iOS composition from
  round 4. One map instance; folding does not rebuild the journey.
- **Verified**: Pixel emulator, Piraeus to Syntagma, at 841x673 (side by
  side): the map draws the M1 leg in green and the M3 leg in blue with the
  current-stop dot, attribution and the Fit route control above the timeline;
  the instruction and controls stay reachable. Kotlin `GoRouteRunsTest` 3/3,
  wasm target of feature/map compiles (fallback canvas actual).
- **Finding (policy)**: at 673x841 the emulator showed the single column, not
  the stacked pair. The navigation rail takes 80 dp, so the content canvas is
  593 dp wide, under the 600 dp medium floor, and the shared policy resolves
  SINGLE. An upright Android fold therefore never stacks GO, Explore or Map.
  Fixed in round 9 (tall narrow canvas rule).

## Landed: polish round 9 (T7 Tall narrow canvas: stacking beside a navigation rail)

Source: the round 8 finding (an upright Android fold never stacked) and the
master plan's GO portrait contract (map overview above, instruction below).

- **Rule** (both twins, `TALL_NARROW_MIN_WIDTH` / `tallNarrowMinWidth` = 480):
  a plain window under the medium floor (600) but at least 480 wide, taller
  than wide, not at large text, stacks through `mediumStacked` for the tasks
  whose `tallCanvasAxis` is STACKED (GO, Explore, Map). Column tasks (Plan,
  Departures, Home) keep the single column there, and a phone column (440, the
  Duo cover at 466) never stacks. The medium and large rules are untouched.
- **Fixtures**: `t7_tallNarrowCanvas_goStacksBesideANavigationRail`,
  `t7_tallNarrowCanvas_columnTasksStaySingle`,
  `t7_tallNarrowCanvas_phoneColumnsAndLargeTextNeverStack` on both twins
  (Kotlin layout suite 62, Swift fixtures 57).
- **Verified**: Pixel emulator at 673x841 (593 x 761 canvas beside the rail),
  GO: the route map holds the upper region with Fit route and attribution, the
  instruction, progress and controls read below. Restored to 841x673 after.

## Landed: polish round 10 (GO map camera intent: follow, fit, manual)

Source: master plan GO contract ("Map camera has explicit intent: fit route,
follow, or manual exploration. Respect manual pan ... do not recenter on every
SwiftUI update or identical coordinate delivery").

- **Finding (iOS)**: `GoRouteMapView.updateUIView` recentred on the current
  stop on every SwiftUI update (any tick), so a manual pan was thrown away
  within seconds and there was no Fit route control.
- **Shared reducer**: `GoCamera.reduce(intent, event)` with intents FOLLOW /
  FIT / MANUAL, events user panned, Fit tapped, Follow tapped, current stop
  changed, geometry changed, and actions none / fit route / centre current.
  Kotlin `core/domain/go/GoCamera.kt` (`GoCameraTest`, 3) and the Swift twin in
  GoJourneyView.swift (three `test_camera_*` cases in `JourneyGuidanceTests`).
- **iOS**: the representable receives the intent plus a one-shot command with
  a tick and reports manual pans (an active pan or pinch gesture when the
  region starts changing); `updateUIView` acts only on a command, a real
  current-stop change or a fold-geometry change, never on an identical update.
  Card controls: Fit route always, Follow while not following.
- **Android**: `GoRouteMapView` fits the route once on first layout, then
  applies the same reducer; a finger move on the osmdroid view reports the
  manual pan; Follow animates to the current stop keeping the zoom. Same
  controls. `feature/map` now depends on `core:domain` for the reducer.
- **Verified (Android)**: Pixel emulator at 841x673: Next stop moved the camera
  to Faliro (follow); a swipe on the map revealed Follow (manual) and the view
  stayed where it was panned; Fit route reframed the whole route with Follow
  still offered.

## Landed: polish round 11 (single-column GO: the map stays reachable; launcher clearance)

Source: master plan GO contract, Cover row ("Map remains accessible even if it
is not permanently embedded").

- **Finding**: neither client showed a map in the single-column GO (phones,
  the Duo cover): iOS `combined` was instruction, timeline, footnote; Android
  SINGLE was the same.
- **Both clients**: a "Show route map" / "Hide route map" disclosure under the
  instruction reveals the shared route map card (260 pt, with the camera
  controls from round 10); iOS remembers it in `@AppStorage`
  (`syrmos.go.showCompactMap`), Android in `rememberSaveable`. Instruction
  first, map on request, then the timeline.
- **Android launcher**: `GoScreenPresence.onScreen` (set for the GO screen's
  composition) hides the floating Ariadne launcher while GO is on top, next to
  the existing More and Map exclusions; the pill had covered the Now / Next
  caption pills of the paired timeline.
- **Verified (Android)**: Pixel emulator at 411x891: "Show route map" under the
  instruction; tapping it shows the map card with Fit route and turns the
  control into "Hide route map". At 841x673: no launcher node on GO, launcher
  back on Home.
- **Verified (iOS)**: `DuoSnapshotTests` re-rendered `go-duo-cover.png` with
  the disclosure under the instruction.

## Landed: polish round 12 (GO timeline: stable browsing with Back to now)

Source: master plan GO contract, Timeline row ("Keep manual browsing stable
rather than repeatedly snapping the user back. Offer an explicit
return-to-current action").

- **Shared rule**: `GoTimelineFocus.isVisible(rowTop, rowBottom, viewportTop,
  viewportBottom)` and `targetOffset(rowTopInContent, viewportHeight,
  maxOffset)` (a third of the way down, clamped). Kotlin core/domain/go with
  `GoTimelineFocusTest` (3), Swift twin in GoJourneyView.swift with three
  `test_timelineFocus_*` cases in `JourneyGuidanceTests`.
- **Android** (`JourneyTimeline`, scrolling variant only): the viewport is read
  with `onGloballyPositioned` before the `verticalScroll` modifier, the current
  row reports its root position from `LegCard`, and a `FilledTonalButton`
  "Back to now" floats at the bottom while the row is out of view; tapping it
  animates the scroll to the shared target offset.
- **iOS** (`goTimeline`): `ScrollViewReader` plus a named coordinate space; the
  current row carries the anchor id and reports its frame through a preference
  (`GoCurrentRowTracker`, active only in the paired timeline so the single
  column stays untouched); the pill scrolls to the anchor at a third of the
  height. The list never auto-scrolls.
- **Verified (Android)**: Pixel emulator at 841x673: no pill at rest, the pill
  after a swipe up on the timeline, gone again after the tap with the "Now"
  row back in view.
- **Verified (iOS)**: unit twins green; the render suite still green (the pill
  needs a scrolled state, so it is not in a snapshot).

## Landed: polish round 13 (GO trust: the dot's meaning)

Source: master plan GO contract, Trust row ("A confirmed station is not
automatically a live GPS fix").

- Both clients show a source pill at the top-leading corner of the route map
  card. iOS: "Live position" while `model.isLive` (live guidance), else
  "Confirmed stop". Android GO is manual, so it always reads "Confirmed stop".
  Four languages; atomic (single line, no wrap).
- Verified: Android emulator at 841x673 (pill present in the hierarchy and the
  capture); iOS `DuoSnapshotTests` re-rendered with the pill on the map card.
  UI-only change with no new pure logic, so no new unit tests beyond the
  renders.

## Landed: polish round 14 (Ariadne docks beside the content)

Source: master plan section 4 (Ariadne: "Expanded may dock the same
conversation beside the relevant Plan/map context when there is sufficient
room. Moving between sheet and pane must not resend a prompt or initialize
another model").

- **Android** (`SyrmosApp.kt`): the shared policy resolves the ARIADNE task on
  the canvas after the rail; when it pairs (SIDE_BY_SIDE) and the rail layout
  is active, `TabContentWithOverlays` lays the current tab beside a docked
  `AssistantScreen` (companion width from the policy, capped at 480 dp);
  otherwise the full-screen presentation stays. The `AssistantViewModel` is
  now injected once at the shell and the open-time effect (location, pending
  query) is keyed on `showAriadne`, so closing, reopening or moving between
  the docked pane and the overlay never recreates the conversation or resends
  the pending prompt.
- **iOS** (`SyrmosApp.swift`): the root `.sheet` became `.inspector` with
  `inspectorColumnWidth(min: 320, ideal: 400, max: 480)`; on a regular width
  the same `AriadneView` docks as a trailing column, on a compact width the
  system presents it as a sheet. Settings keeps its own sheet.
- **Fixtures** (both twins): `p3_flatLandscape_ariadneDocksBesideTheContent`
  (951x669 and the 761x649 Android canvas beside the rail pair) and
  `p1_cover_ariadneStaysASheet` (466x678 single). Kotlin layout suite 64,
  Swift fixtures 59.
- **Verified (Android)**: Pixel emulator at 841x673: tapping Ask Ariadne keeps
  Home (hero and direction board) on the left and opens the conversation with
  its composer on the right; the hierarchy holds both.
- **Verified (iOS)**: build for the iPad simulator and a capture of the docked
  inspector (see below); the Duo portrait behaviour (regular or compact width
  class) is the system's call and was not runtime-checked.
- **Regression pass this round**: full KMP suite 630/630, full iOS unit target
  400/400.

## Landed: polish round 15 (Home never stacks)

Source: the round 14 iPad capture: beside a docked inspector Home had ~633 pt,
two columns did not fit, and the medium-canvas rule fell back to STACKED, which
put the network context above the next-train answer.

- **Rule** (both twins): `WorkspaceTask.stacks` / `SyrmosWorkspaceTask.stacks`
  is false for HOME; `resolveMediumCanvas` skips the STACKED axis for such a
  task, so Home is either two columns or its single column with the answer
  first. Plan, Departures and the others keep stacking as before.
- **Fixture** `t8_narrowRemainder_homeKeepsTheAnswerFirst` on both twins
  (633x1376: HOME single, PLAN stacked). Kotlin layout suite 65, Swift
  fixtures 60.
- **Verified (iOS)**: iPad simulator rebuilt with the rule; with Ariadne
  docked, Home renders its single column with the answer (hero, living map)
  first and the network context below. Finding on the way: a launcher tap
  during app launch set the inspector state without a presentation, and since
  the pill only ever set the flag to true, later taps were dead until a
  relaunch; the launcher now toggles the flag (also closing the docked
  inspector from the content side).

## Landed: polish round 16 (Android GO map continuity across a fold)

Source: master plan GO contract ("Respect manual pan/zoom/bearing/pitch across
reflow"; "Folding, rotating ... must not ... create a second location
subscription") and D05/D12 continuity. `MainActivity` has no `configChanges`,
so a fold or rotation recreates the activity and every osmdroid view with it.

- **Camera persistence** (`GoRouteMapView.android.kt`): centre and zoom are
  saved in `rememberSaveable` from a `MapListener` as the rider moves, restored
  in the `AndroidView` factory, and a restored camera skips the first fit. The
  camera intent is saved by name in `GoJourneyScreenRoute`
  (`cameraIntentName`), so a manual view stays manual after the fold.
- **Movable content**: the GO route map (`movableContentOf` with the legs,
  current stop and accent passed as parameters, the intent read through its
  state) and the Network Map canvas (`movableContentOf` with the modifier and
  the paired flag) move between pane slots when the arrangement changes
  without a recreation, instead of being rebuilt.
- **Verified**: Pixel emulator: GO at 841x673, pan the map (Follow appears),
  switch to 673x841 (activity recreation, stacked layout): Follow is still
  offered and the map shows the panned view rather than a refit. Restored to
  841x673.
- **iOS parity**: `GoRouteMapView` is a `UIViewRepresentable` whose
  `MKMapView` is recreated when `SyrmosArrangement` changes its structure (a
  fold flips side by side and stacked). The owning `GoJourneyView` now keeps
  the last settled region (`GoSavedCamera`, from `regionDidChangeAnimated`)
  and hands it to the new map as `initialCamera`, which skips the first fit,
  so a manual view survives the flip. Guidance and snapshot suites 44/44.

## Landed: polish round 17 (visual audit of the remaining tabs)

Source: a capture pass over Airport, Explore, More and Home on the Pixel fold
emulator (841x673) and the iPad simulator (1032x1376) after rounds 4 to 16
merged (#201, #202).

- **Policy, T9**: the tall-canvas stacking preference now applies only under
  `TALL_STACK_MAX_WIDTH` / `tallStackMaxWidth` (840). An upright iPad at
  1032 stacked Explore, leaving 45 percent of the height to a placeholder
  card; two comfortable columns beat that. The Duo inner display (669) keeps
  stacking. Fixture `t9_wideUprightTablet_pairsSideBySideEvenForStackingTasks`
  on both twins (Kotlin layout suite 66, Swift fixtures 61).
- **Insight dedupe** (both clients): `InsightDedupe.distinctByText` (Kotlin
  core/domain/usecase, Swift twin in HomeView.swift; 3 tests each) drops a
  notice whose normalised text already appeared; the iPad Home showed the same
  STASY notice twice under two ids. Android also drops its "Network status"
  card when the status feed repeats the top notice verbatim (the notice card
  carries the link, so it is the one to keep); iOS shows the status as a pill
  and had no duplicate. Verified on the emulator: no status card, two distinct
  notices. The Android card also hides a body that merely repeats its title.
- **Explore Plan button**: Android rests it at the list pane's bottom corner
  when paired (it hovered mid-list at 168 dp clearance meant for the compact
  bottom bar) and takes the navigation-bar inset (at 1280x800 it sat half
  behind the system bar); iOS gives the pill band 104 pt trailing padding when the
  arrangement axis is vertical, because the stacked list pane shares the
  window's bottom-right corner with the Ariadne launcher.
- **Airport route chips** (iOS): `lineLimit(1)` + `fixedSize` so "X93" no
  longer breaks into two lines in the route overview.
- **GO End confirmation anchor** (iOS): the `confirmationDialog` sat on the
  whole GO view, so on an iPad the popover hovered over the content with its
  arrow pointing at the map card; it now sits on the End / Finish toolbar
  button (`endButton`), so the popover points at the button. Phones are
  unchanged (an action sheet either way). Verified on the iPad simulator:
  the popover now hangs from the End button at the top-right; GO paired with
  the route map and the timeline, and Plan keeps its results after End.
- **Android dark mode: root content colour**: a dark-mode pass over the fold
  emulator showed the Home headings ("What matters now", "Around you") and
  the hero title near-black on the dark canvas. Nothing under `SyrmosTheme`
  provided a content colour, so Material's `LocalContentColor` default
  (black) applied to every text that names no colour; only dark mode shows
  it. The shell now wraps the themed root in a `Surface` with the background
  colour and `onBackground` content colour, which fixes every tab at once.
  Verified: dark Home and Explore on the emulator read correctly, light mode
  unchanged. iOS Home dark render added (`home-duo-inner-landscape-dark.png`),
  contrast fine there.
- **Empty companion never takes the upper region** (both clients): on the
  upright fold Explore stacked an invitation card above the list and gave it
  45 percent of the height. Android renders the single list until a line is
  chosen (then the detail stacks above); iOS gets
  `SyrmosArrangement(companionHasContent:)`, which renders the single column
  for a stacked result while the companion is empty. Side by side keeps the
  invitation card (a column is the right size for it). New render
  `explore-duo-inner-portrait.png`.
- **Launcher and control clearance on the upright fold** (Android): the
  Explore Plan button takes 96 dp bottom clearance when stacked (it shares the
  window's bottom-right corner with the launcher) and 16 dp side by side; the
  Map canvas controls drop the 96 dp bottom-bar clearance when the canvas is a
  pane, so on a short stacked canvas they no longer climb into the header.
  Verified on the emulator at 673x841.
- **Plan companion readable column** (both clients): the selected journey
  column is capped at 680 (iOS `frame(maxWidth:)`, Android `widthIn(max)`),
  so a wide companion pane on an iPad or tablet does not stretch the timeline
  and its prose across the full pane. Verified: iPad Plan Piraeus to Syntagma
  (three routes with Recommended / Fastest / Fewest changes chips, selected
  journey with its comparison line) and the Android tablet at 1280x800
  (Start journey centred in the capped column).
- **Readable stage (iOS)**: the T9 rule alone did not change the iPad, because
  every tab was wrapped in `ReadableTabContent` (760 pt), so the arrangement
  measured 760 and still stacked. Tabs that pair (Home, Explore, Departures)
  now get a 1200 pt stage (`pairedMaximumWidth`); More keeps 760. To keep a
  wide window that cannot pair readable, `SyrmosArrangement` now renders its
  single column at the policy's readable task width, centred, instead of full
  width. Android already let pairs span the full width (its 760 dp cap is on
  the single LazyColumn only), so this is parity.
- **T10, Home splits evenly on a large canvas** (both twins): the large
  two-pane rule gives every task a fixed 360/400 task column; on the iPad Home
  that put the answer in the narrow column beside a wide context pane.
  `WorkspaceTask.leadsWithTask` (HOME) splits the two panes evenly; Plan and
  the others keep the task column. Fixture
  `t10_largeCanvas_homeSplitsEvenlyOtherTasksKeepTheTaskColumn` (Kotlin layout
  suite 67, Swift fixtures 62).
- **Compact guard**: the arrangement's readable single column applies from
  600 wide; the compact breakpoint already subtracts 32 and the views own their
  16 pt gutters, so the first cut doubled the margins on the Duo cover
  (`departures-duo-cover.png` moved); the guard restores the phone rendering.
- **Verified**: Android emulator at 841x673 (Explore paired: button at the
  pane's bottom edge, Line 2 detail in the companion; Airport and More read
  correctly). iPad simulator (1032x1376) rebuilt: Explore pairs the list with
  the "Choose a line" companion side by side, the Plan pill sits clear of the
  launcher. New render `explore-ipad-portrait.png` in `DuoSnapshotTests`
  (23/23). Unit twins green on both sides.

## Landed: polish round 18 (folded cover audit)

Source: a capture pass over the four Android tabs at the folded cover
geometry (466x678) after round 17 merged (#203).

- **Home, Airport, Map** read correctly at the cover: the hero with the
  four-direction board, the airport hero and calendar hub, the map with its
  controls clear of the bottom bar.
- **Explore Plan button (Android)**: it floated 168 dp above the bottom bar,
  which on a 678 dp cover put it over the Ichnos card's Report button. It now
  sits BESIDE the launcher on the same row (84 dp end clearance, 96 dp bottom
  in the compact layout, 16 dp when stacked beside the rail) and keeps the
  free corner side by side. Verified at 466x678 and 673x841 (button and
  launcher bounds on one row).
- **iOS**: `explore-duo-cover.png` render added to `DuoSnapshotTests`; the
  Plan pill there is a bottom safe-area band that reserves its own height, so
  nothing is covered.

- **iPhone compact walk** (Syrmos 27 simulator, 402x874): Home with the
  four-direction board, Plan with the chips and the selected journey under
  the alternatives, GO with "Show route map" revealing the route card with
  its Confirmed stop pill and Fit route control. One defect: the Ariadne
  launcher covered the Explore "Plan a journey" pill; the pill band now keeps
  104 pt trailing clearance in the single column and when stacked, and only
  side by side (launcher over the companion) uses 16 pt.
- **Plan empty companion (iOS)**: the large-text render showed Plan stacking
  the invitation card over the upper region, the defect fixed for Explore in
  round 17; `PlanView` now passes `companionHasContent: planned &&
  selectedResult != nil`, so a stacked Plan is a pair only with a selected
  journey. Render `plan-duo-inner-portrait-xxl.png` (Dynamic Type xxLarge on
  the Duo inner portrait) pins it.
- **Android bus markers**: a tap on an airport-bus vehicle showed osmdroid's
  stock info bubble (a plain "X93" callout) over the designed canvas while the
  inspector stayed empty (there is no bus detail card yet). The marker now
  disables the info window and consumes the tap; the vehicle's line is already
  on its glyph. Verified at 1280x800 (no bubble after the tap).
- **Live train card times** (both clients): the suburban train card printed
  the feed's raw ISO timestamps ("2026-09-26T10:14:00.000Z") for departure and
  arrival. Shared rule `athensClockLabel` (Kotlin core/common extensions,
  Swift twin `AthensClockLabel` in MapView.swift): an ISO instant becomes an
  Athens HH:MM, a bare HH:MM[:SS] is normalised, anything else passes through.
  Tests on both sides (3 each). Verified on the emulator at the cover: "13:14"
  and "14:58" in the card.
- **Android slide-up cards clear the bottom bar**: at the cover the train
  card's Watch live row sat behind the tab bar; the station and vehicle
  overlays now take the navigation-bar inset plus 88 dp. Verified: Watch live
  bounds above the bar.
- **Android compact GO clears the bottom bar**: the single-column GO
  (phones, the folded cover) ended flush with the floating tab bar, so the
  timeline's last stop could sit behind it; a 96 dp plus navigation-bar spacer
  closes the column. Verified at the cover: the Destination row ends well
  above the bar after scrolling to the end. Cover GO otherwise reads as
  designed (instruction, controls, Show route map, timeline).
- **Explore list end clearance (Android)**: with the Plan button now on the
  launcher row (96 dp above the navigation bar), the list's 140 dp bottom
  padding left its last line under the pill on the cover; the padding is now
  168 dp plus the navigation-bar inset. Verified fully scrolled at the cover
  (last row above the pill). The Airport hub's single column ended under the
  launcher (its service-alerts card text sat behind the owl); it now takes
  168 dp plus the navigation-bar inset too. Verified fully scrolled at the
  cover: the card ends above the launcher.
- **Large text on the fold (Android, font scale 1.3)**: Home and Plan fall
  back to the readable single column on the 841x673 emulator (canvas 761 dp
  beside the rail). This is the policy's scaled floors at work
  (`MIN_TASK_PANE` 300 x 1.3 = 390 > the 380 dp half; stacking needs
  468 + 364 dp of height), pinned by the `largerText` fixtures on both twins.
  Nothing overlapped or truncated: the hero, the four-direction board, the
  Plan form and the route cards read correctly at 1.3. Recorded as by design;
  at 1.2 the tall canvas still stacks Plan (fixture
  `p5_tallCanvas_largerTextTurnsPlanFromSideBySideToStacked`).

## Landed: polish round 19 (More, station detail, assistant audit)

Source: a capture pass over More, the Explore line detail pane and the
docked Ariadne on the fold emulator, plus the iPad, after round 18 merged.

- **More** reads correctly on the fold (Assistant, Preferences, Map
  preferences, Operators sections at the readable width).
- **Line detail pane**: the small vehicle glyphs on the departure cards were
  mistaken for broken images at a glance; they are the real 384 px PNGs
  (`VehicleIcons.resourceFor`) rendered at card size. No change.
- **Ariadne heads-up (Android)**: the assistant's opening "Heads up" joined
  the same operator notice twice (the feed repeats it under two ids);
  `currentNotices` now goes through `InsightDedupe.distinctByText`, the rule
  Home uses. Verified in the docked assistant: one occurrence. iOS had the
  same two paths (`loadAlertNote`, `currentNotices` in AriadneModel.swift) and
  takes the same rule; HomeFeaturesTests 18/18.

- **Explore row action on iOS (defect)**: on the iPad, tapping a line in the
  paired Explore pushed the full-screen `LineDetailView` instead of filling
  the companion. `LinesView` read `\.syrmosIsPaired` on itself, and the
  owning view cannot read the environment `SyrmosArrangement` sets on its own
  panes (the round 4 gotcha), so `isPaired` was always false. `lineLink` now
  reads the axis inside the row through `SyrmosAxisReader`. The round 3
  Duo render only showed the pane structure, not the tap, which is why it
  passed. Verified on the iPad simulator: Line 2 fills the companion
  (departures and stations) and the row stays highlighted. The same
  owner-level read drove the Plan pill's trailing clearance (always 104 pt);
  the band now reads the axis inside `SyrmosAxisReader`, and the two dead
  owner-level environment properties are gone.
- **Android detail lists at tablet width**: the station and line detail
  `LazyColumn`s had no readable-width cap; at 1280x800 the departure cards
  stretched across the canvas. Both take the 760 dp cap Home and Explore use.
  Verified at 1280x800 (Dafni: capped column centred beside the rail).

- **Explore Plan button beside a docked assistant (Android)**: with Ariadne
  docked the tab drops to a single column under the rail, and the button kept
  the 96 dp clearance meant for the compact bottom bar, floating over the list
  rows. The shell now provides its real clearance through
  `LocalFloatingBarInset` (96 dp in the compact layout, 16 dp beside the
  rail) plus `LocalLauncherEndInset` (84 dp while the launcher is shown,
  16 dp when the shell hides it), and Explore reads both for the single
  column. Verified: docked, the button sits 16 dp above the window bottom and
  16 dp from the pane's end; compact, it keeps 96 dp beside the launcher. Lesson from the first cut: a brace inserted by counting landed
  after the rail/compact branches and the shell painted nothing; the
  structure is now checked by reading the tail of the function.
- **Map controls (Android)** read the same `LocalFloatingBarInset` for the
  single column, so beside a docked assistant they sit 16 dp above the
  bottom instead of 96 dp up the canvas. Verified docked and compact.
- **Explore beside a docked assistant on iOS** does not arise: the inspector
  is a system column and the pill band is a safe-area inset.

## Landed: polish round 20 (dark mode and language sweep)

- **Dark mode**: Android Explore (paired with Line 2), Map (inspector plus
  canvas), Airport and More at 841x673 read correctly after the round 17 root
  content-colour fix. iOS: `explore-duo-inner-landscape-dark.png`,
  `map-duo-inner-landscape-dark.png`, `plan-duo-inner-landscape-dark.png`
  added to `DuoSnapshotTests` (the map canvas is tile-less in the offline
  test host, as in the light render). No defects.
- **Greek on the fold** (Android, language switched in More after planning in
  English so the results survive): Plan pairs with "Επιλεγμένη διαδρομή",
  the chips "Προτεινόμενη" and "Ταχύτερη" stay atomic, GO pairs with the map
  card ("Επιβεβαιωμένη στάση", "Όλη η διαδρομή") and the timeline. One nit
  on both platforms: all-caps labels kept the tonos ("ΈΤΟΙΜΟΣ ΓΙΑ
  ΕΠΙΒΊΒΑΣΗ"), which Greek typography drops on capitals. Shared rule
  `displayUppercase()` (Kotlin core/common extensions, 2 tests) maps the
  accented capitals to plain ones after uppercasing; used at the GO state
  label, the Home status tag and the LIVE pill. iOS already had the tested
  `uppercasedForDisplay(language)` (locale-aware, on the Home tag); the GO
  state label and the map's departures heading now go through it too instead
  of a plain `uppercased()` or a device-locale `textCase`. Verified on the
  emulator ("ΕΤΟΙΜΟΣ ΓΙΑ ΕΠΙΒΙΒΑΣΗ"); iOS GreekTypography + HomeFeatures 23/23. Emulator note: adb cannot type
  Greek, and the Greek search matches Greek names only, so plan in English and
  switch the language afterwards.

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
