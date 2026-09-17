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

Targeted searches of the installed iphoneos27.0 SDK Swift interfaces:

| Symbol | Present in source-visible SDK interface? | Consequence |
| --- | --- | --- |
| `ArrangementView` | No | Duo paired-content arrangement API is **gated**. Do not fake it. |
| `overlayArrangementZIndex` | No | Gated with the arrangement API. |
| `reservedRegions` | Symbol in `SwiftUICore.tbd`; source visibility unconfirmed | Treat as **gated** until a compile probe confirms it; keep the call behind the iOS adapter. |
| `ToolbarOverflowMenu` | Yes | Newer toolbar API is **available** to adopt with runtime availability guards. |
| `visibilityPriority` | Yes | Available to adopt with guards. |

This matches the prompt's expectation: some newer toolbar APIs land before the Duo
layout APIs, so the toolbar work is actionable now while true Duo arrangements and
reserved regions stay behind an explicit gate.

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
| No crash / launch, onboarding, permissions | Pass | Cold launch, onboarding skip, location/notification prompts, no `FATAL`. |
| Cross-target compile | Pass | `:composeApp:compileKotlinWasmJs` and `compileKotlinIosSimulatorArm64` green; web build and iOS untouched. |

## Evidence levels (prompt section 12.3)

| Level | Status | Evidence |
| --- | --- | --- |
| Supported foundations | In progress | Shared policy + 20 tests green via `:core:common:testDebugUnitTest` (JDK17); `ContentBreakpointTest` 9/9 still green. Android running-surface: Plan two-pane at 1280dp and single-column at 411dp verified on the emulator; wasmJs + iOS compile green. |
| Duo SDK integration | Pending | `ArrangementView` / arrangement APIs absent from the installed SDK interface. |
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
| Android continuity through recreation (12.1 #11-13) | Partial | Plan draft + selection + reconstructed results survive an activity recreation (verified by rotation). Other screens not yet covered; process-death restoration via Voyager not separately verified. |
| iPhone/iPad running-surface scenarios (12.1 #20) | Pending | iOS mirror + scene wiring not yet implemented. |
| Duo D01–D24 (12.2) | Pending | Gated on Duo SDK arrangement APIs and a Duo runtime. |

## Remaining phases (prompt section 11)

1. **Continuity on both platforms** (Plan on Android done) — Plan's draft +
   selection now survive Android recreation. Remaining: a running GO session
   through recreation, map camera intent, and the iOS `SceneDelegate` host
   replacement, plus the other screens' state ownership.
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
