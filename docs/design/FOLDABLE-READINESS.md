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
