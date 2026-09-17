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

## Landed in this slice

- **Shared adaptive workspace policy** — `core/common/.../layout/AdaptiveWorkspace.kt`.
  A pure, platform-neutral function from usable content geometry + reported
  fold/occlusion regions + font scale + current task to semantic layout decisions
  (arrangement, pane roles + fitted rectangles, hinge clearance, adjustable
  divider). Reuses `ContentBreakpoint` for the plain-window path so a non-foldable
  device resolves exactly as before. No SDK types. The web app is untouched.
- **Tests** — `core/common/.../layout/AdaptiveWorkspaceTest.kt`, 20 cases; these
  are the cross-platform fixtures the SwiftUI mirror must also satisfy.

## Evidence levels (prompt section 12.3)

| Level | Status | Evidence |
| --- | --- | --- |
| Supported foundations | In progress | Shared policy + 20 tests green via `:core:common:testDebugUnitTest` (JDK17); `ContentBreakpointTest` 9/9 still green (no regression). |
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
| Android running-surface scenarios (12.1) | Pending | Policy not yet consumed by Android screens; emulator run not yet executed. |
| iPhone/iPad running-surface scenarios (12.1 #20) | Pending | iOS mirror + scene wiring not yet implemented. |
| Duo D01–D24 (12.2) | Pending | Gated on Duo SDK arrangement APIs and a Duo runtime. |

## Remaining phases (prompt section 11)

1. **Continuity on both platforms** — surviving task/session ownership, map camera
   intent, canonical selection, presentation coordination. Validate a selected
   station and a running GO session through Android activity recreation and iOS
   `SceneDelegate` host replacement before multiplying panes.
2. **Native geometry + navigation** — adopt the shared policy in the Android root
   on the post-rail canvas (fixes the flagged post-inset contract), add a
   WindowManager/FoldingFeature adapter (confirm catalog-compatible versions),
   and drive an adaptive scaffold from the policy. On iOS, adopt
   `NavigationSplitView` + the available toolbar overflow APIs; keep the Duo
   arrangement/region calls behind the section 9.1 gate.
3. **Complete product flows** — Plan and GO first on both platforms, then Now,
   Explore, Departures, fares, Ariadne, settings, onboarding.
4. **Interaction + visual refinement** — bars/overflow, keyboard, focus,
   typography, source labels, reduced motion/transparency, map padding.
5. **Regression + evidence** — full matrix, builds/tests, performance traces;
   update this record with per-case Pass/Fail/Pending and artifact paths.

## Exact unresolved dependencies

- Duo paired-content arrangement APIs (`ArrangementView`, `overlayArrangementZIndex`)
  are not in the installed iphoneos27.0 SDK interface.
- A Duo simulator runtime / device for D01–D24.
- Android `androidx.window` (WindowManager/FoldingFeature) and Material adaptive
  libraries are not yet in the version catalog; add at catalog-compatible versions
  through a narrow platform boundary when Phase 2 geometry work begins.
- A running Android foldable emulator/device and iOS/iPad simulators for the
  running-surface scenarios.
