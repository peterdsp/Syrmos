# Syrmos — exceptional foldable experiences, with native iPhone Duo design

Revised 2026-09-17 after reviewing Apple's rendered iPhone Duo Human Interface Guidelines, its design examples, the related Apple technical sessions, and current repository/SDK evidence. This document is an implementation prompt, not evidence of implemented or released foldable support.

This revision supersedes the earlier Android-first/Duo-groundwork emphasis. Keep the filename stable. Deliver excellent Android foldable behavior and treat native iPhone Duo design as a primary product requirement, with actual Duo integration and validation gated only by the required tools. Platform guidance takes precedence over the generic geometry examples in older Syrmos prompts.

## 1. Mission and definition of success

Act as a senior iOS/SwiftUI engineer, Android/Compose engineer, adaptive-interface designer, and accessibility engineer. Implement a polished foldable experience for **Syrmos**, using the available screen space to make everyday travel easier. Deliver working Android adaptation and the supported iOS implementation toward a fully native **Apple iPhone Duo** experience.

The signature experience: a rider selects a journey on the cover screen, opens the device, and immediately sees that same journey beside its map. Partially folding the device creates useful book or tabletop arrangements. Closing it returns to the relevant compact screen with the same selection and active journey. No lost draft, restarted guidance, map reset, duplicate task, or repeated splash.

Work in `/Users/peterdsp/git/Syrmos`. Implement working product screens, motion, state continuity, and tests. Keep native Android Compose/Voyager and native iOS SwiftUI/MapKit. Preserve real branding, transit artwork, offline information, and current feature access. Do not release to stores, deploy the website, or change version numbers as a side effect.

Treat continuity, native navigation, and readable controls as the definition of quality. More columns or more animation alone do not satisfy this task. Finish every independently buildable part on both platforms; complete Duo-specific integration when its SDK and runtime are available. A generic wide-window preview is not a verified Duo build, and a toolchain limitation must not stop unrelated Android or supported-iOS work.

### Apple guidance translated into Syrmos requirements

The following is a concise reading of [Apple's iPhone Duo HIG](https://developer.apple.com/design/human-interface-guidelines/designing-for-iphone-duo). Later sections turn these principles into **Syrmos product decisions**, not purported Apple pixel specifications.

| Apple principle | Syrmos requirement |
| --- | --- |
| Retain one iPhone hierarchy across displays. | Expand the selected task; preserve its selection and actions. |
| Adapt using system layout information. | Use native size classes, margins, safe areas, and reserved regions. |
| Respect vertical controls and asymmetry. | Let navigation containers manage bars on either edge. |
| Distinguish navigation from content arrangement. | Use split navigation for hierarchy and arrangements for related content. |
| Keep controls near their content. | Station filters stay with results; map controls stay with the map. |
| Preserve functionality when controls compress. | Define action priorities and one system overflow menu. |
| Minimize movement around the fold. | Reposition affected elements without restarting the task. |
| Use standard presentations where possible. | Let sheets, menus, and alerts adapt before adding custom placement. |

Do not translate Android width thresholds into iPhone Duo device rules. Do not interpret “support every pose” as building a different app screen for every hinge angle.

## 2. Verify the current baseline before editing

Read repository instructions, inspect local changes, and record the commit and toolchains. Read `/Users/peterdsp/git/Syrmos/DESIGN_SYSTEM.md`, `/Users/peterdsp/git/Syrmos/docs/PRODUCT_PRINCIPLES.md`, and `/Users/peterdsp/git/Syrmos/docs/plans/3.0.0-IMPLEMENTATION-PROMPT.md`. Reconcile earlier proposals against current code. This task updates adaptive presentation and continuity; it is not a request to reimplement the entire Journeys roadmap.

The original Android observations were made on September 14; the iOS/toolchain observations were refreshed on September 17. Recheck all entries before editing:

| Area | Starting point and implication |
| --- | --- |
| Android root | `SyrmosApp.kt` already switches between compact navigation and a native navigation rail. Extend this; do not describe large-screen navigation as entirely absent. |
| Layout policy | `ContentBreakpoint.kt` resolves 600/840/1200 thresholds and a short-height fallback. It has no hinge/posture inputs. The root currently supplies dimensions before navigation consumes space, despite the policy's post-inset contract. |
| Fold APIs | No Android WindowManager/FoldingFeature or Material adaptive dependencies were found in the checked version catalog. Confirm compatible versions before adopting them. |
| Startup | `SyrmosApp.kt` keeps `isSeeded` in composition-local state and imposes a 3.5-second splash delay. Ensure activity recreation cannot repeat avoidable startup work or delay. |
| Planner | `PlanScreenRoute.kt` holds important draft and selection fields in plain `remember`. Move restoration-sensitive state to suitable ownership. |
| Android map | `PlatformMapView.android.kt` initializes a fixed Athens camera. Audit lifecycle cleanup and camera restoration before moving the map between layouts. |
| GO | `ActiveJourneyRepository.kt` already persists the active journey. Reuse it and extend only demonstrated gaps. |
| iOS host | UIKit `SceneDelegate` hosts SwiftUI and can recreate the host after backgrounding. Preserve its recovery behavior while moving durable state outside replaceable view trees. |
| iOS layout | The current shell largely uses a centered readable column and a full-width map. A wide column alone does not provide connected panes. |
| iOS tools | September 17 inspection found Xcode 27.0 (27A266a) and iPhoneOS SDK 27.0, with iOS 17 minimum deployment. Targeted SDK searches found no `ArrangementView`, `reservedRegions`, or `overlayArrangementZIndex` declarations. This is a foundation toolchain, not verified Duo API support. |

Key implementation paths:

- `/Users/peterdsp/git/Syrmos/androidApp/src/androidMain/kotlin/com/syrmos/android/MainActivity.kt`
- `/Users/peterdsp/git/Syrmos/androidApp/src/androidMain/AndroidManifest.xml`
- `/Users/peterdsp/git/Syrmos/composeApp/src/commonMain/kotlin/com/syrmos/app/SyrmosApp.kt`
- `/Users/peterdsp/git/Syrmos/core/common/src/commonMain/kotlin/com/syrmos/core/common/layout/ContentBreakpoint.kt`
- `/Users/peterdsp/git/Syrmos/core/common/src/commonTest/kotlin/com/syrmos/core/common/layout/ContentBreakpointTest.kt`
- `/Users/peterdsp/git/Syrmos/composeApp/src/commonMain/kotlin/com/syrmos/app/screen/PlanScreenRoute.kt`
- `/Users/peterdsp/git/Syrmos/composeApp/src/commonMain/kotlin/com/syrmos/app/screen/GoJourneyScreenRoute.kt`
- `/Users/peterdsp/git/Syrmos/composeApp/src/commonMain/kotlin/com/syrmos/app/journey/ActiveJourneyRepository.kt`
- `/Users/peterdsp/git/Syrmos/composeApp/src/commonMain/kotlin/com/syrmos/app/di/AppModule.kt`
- `/Users/peterdsp/git/Syrmos/feature/map/src/androidMain/kotlin/com/syrmos/feature/map/PlatformMapView.android.kt`
- `/Users/peterdsp/git/Syrmos/iosApp/iosApp/App/SyrmosApp.swift`
- `/Users/peterdsp/git/Syrmos/iosApp/iosApp/Views/Map/MapView.swift`
- `/Users/peterdsp/git/Syrmos/iosApp/iosApp/Features/Assistant/PlanFlow.swift`
- `/Users/peterdsp/git/Syrmos/iosApp/iosApp/Core/Persistence/SavedJourneyStore.swift`

The earlier Duo prompt and `docs/design/IPHONE-DUO-IPAD-READINESS.md` were not present during this inspection. Do not block on missing historical documents or claim their requirements are implemented. The checked Xcode project and `iosApp/project.yml` also reported different marketing versions; avoid blindly regenerating the project.

## 3. Art direction: a calm, beautiful travel workspace

Use the exact Syrmos identity, shared design tokens, meaningful line colors, strong typography, and quiet map styling. Retain native Android interaction conventions. Use glass sparingly for floating navigation or map controls, with readable opaque fallbacks. Content panels should have clear hierarchy, comfortable spacing, and restrained elevation.

On iOS, preserve existing system-material/glass work and use the operating system's navigation treatment. Do not introduce a new glass layer behind every content card. The visual character comes from typography, transit color semantics, clear selection, and a calm geographic canvas. Never redraw the shipped Syrmos or Ariadne marks.

The larger screen should reveal **related information that helps the current task**: route options beside their map, a station beside its departures, or the current GO instruction beside upcoming legs. Do not fill space with unrelated dashboard cards or stretch a phone form across the entire display.

At default text size, use an 8dp spacing rhythm with 16–24dp panel padding, 16–24dp corners for major surfaces, 48dp minimum Android touch targets, and content rows around 64–80dp minimum height. Use approximately 28–36sp for the main current-action headline and 15–17sp for body text; allow full font scaling and content growth. Preserve the canonical token generator for exported values.

Those measurements are Android design starting points. iOS uses native text styles and system metrics, with 44pt minimum custom touch targets as a Syrmos acceptance target. Prefer semantic spacing and readable content proposals to copying dp values into pt. Do not shrink type to force a pane to fit.

The map is a first-class pane. Station selection, route selection, and map highlighting must feel like one connected interaction. Keep destination names and Live/Scheduled/Estimated/Cached/Offline labels readable. Never replace factual status with a decorative green dot.

## 4. One hierarchy, deliberately adapted across postures

The table defines observable outcomes, not separate posture-specific root screens. Reuse the same feature state, content components, and actions. On Duo, native compact/regular behavior and reserved regions determine the arrangement; posture names are primarily test scenarios.

| Situation | Required experience |
| --- | --- |
| Ordinary phone or book-fold cover display | A focused single task, reachable navigation, readable endpoint fields, and an accessible map/detail switch. During GO, prioritize the current instruction and next action. |
| Fully open inner display | Expose a useful additional level of hierarchy or related task content when it fits. Android may use a rail; Duo uses native tab/sidebar/navigation behavior. Navigation and task/map pairing are separate decisions. |
| Vertical separating fold / book posture | For paired content, align regions to the reported division: list/planner content in one and map/detail in the other. Keep static instructions and actions comfortably clear. Preserve a continuous feed's scroll model; a division is not automatically an opaque hinge. |
| Horizontal separating fold / tabletop | For paired content, put the map, route overview, or large departure display above the fold; place journey steps, selections, and reachable controls below when both regions fit. Preserve continuous content where a split offers no benefit. |
| Fully open flip phone | Use ordinary phone layouts unless available space warrants more. Tabletop adaptation must also work here. Do not assume every foldable opens into a tablet. |
| Very small flip cover display | Support normal app windows only where the OS permits them. Do not promise arbitrary cover-screen execution or add OEM-specific launch hacks. Existing widgets are a separate surface. |
| Dual-screen device with an occluding hinge | Treat the hinge as unavailable space, including when flat. Fit panes to the actual two regions. No button, label, route instruction, dialog, or touch target may bridge the hinge. |
| Multi-window, desktop window, or tablet | Recompute from the current app window. Use two or three panes only when task content fits; do not infer layout from a device model or physical display. On Duo, test both sides of Split View and the shorter scene left beneath pinned video. |
| Keyboard, short window, or large text | Prioritize the focused task and reachable action. Collapse supporting content when necessary and provide an obvious way to reopen it. Preserve input and selection. |

No manual fold-mode switch should be necessary. An optional Show map / Focus journey control is useful; remember task intent without overriding physical occlusion rules.

Posture handling must use reported window and folding information. Read separating state, orientation, occlusion, and fold bounds; do not derive placement from a marketing name, screen diagonal, or guessed hinge angle. A flat nonseparating crease need not create a permanent empty stripe. Follow [Android's fold-aware guidance](https://developer.android.com/develop/adaptive-apps/guides/foldables/make-your-app-fold-aware).

When folding information is absent or unsupported, use current window geometry and ordinary content fitting. Reconcile new or removed region reports without losing the task or retaining a stale gap. Layout must remain useful before the first posture callback arrives.

## 5. Geometry and layout policy

Use **dp/sp on Android and pt on iOS**. All reference dimensions below are product test fixtures, not hardware specifications.

Separate three concepts: the current application window; system/navigation/keyboard occupancy; and the actual content regions available to each pane. Navigation policy may use the window size class, but content fitting must use the measured space after occupied areas are accounted for. Convert fold bounds into the same local coordinate system exactly once. Intersect them with the app window; a hinge outside the window is irrelevant. Never subtract an inset twice. Use [Android's window and component sizing guidance](https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-display-sizes).

Create one tested adaptive policy that consumes usable rectangles, reserved regions, font scale, current task, and active pane. It produces pane placement and visibility. Platform adapters supply geometry; screen components consume decisions. Keep Android SDK types out of shared domain code.

Share **semantic roles, continuity invariants, and test fixtures**, not a single forced frame algorithm across platforms. On iOS, native navigation and arrangement containers own their layout; the adapter informs genuinely custom controls and map padding. On Android, the tested policy can drive adaptive scaffold choices. Never override correct native behavior to make a Swift screenshot match a Compose screenshot pixel for pixel.

Extend the current policy deliberately. Preserve existing behavior for callers that have not adopted the new task-aware policy; do not silently change the web app through a shared-token or breakpoint edit. Add platform-neutral geometry/selection fixtures that the Swift implementation can also validate.

For an unobstructed **Android** content canvas `C` after native navigation and system occupancy, use these starting rules at default text size. These are not iOS breakpoints:

- **Below 592dp wide:** one primary task pane. A map may be the main surface with an adaptive context sheet, but there must be a fully readable content alternative.
- **592–687dp:** a lightweight station/list-plus-map layout may fit two 272dp panes with a 16dp gap and 16dp outer margins. Planner forms remain single-pane if their minimum usable widths do not fit.
- **688–839dp:** planner-plus-map may use two panes of at least 320dp, with a 16dp gap and 16dp outer margins. This lets narrower unfolded devices gain useful context before a conventional expanded breakpoint.
- **840–1279dp:** use a 340–400dp task pane, 24dp gap, 24dp outer margins, and a map/detail pane taking the remaining space.
- **1280dp and above:** optionally expose a third inspector for selected route/station details. Fit at least 320dp task + 480dp map + 280dp inspector, two 24dp gaps, and 32dp outer margins. Hide the inspector first when space shrinks.

These are fit policies, not mandatory column counts. Increase content minima for larger text. Validate translations and actual rendered controls. Keep single-column prose bounded around 680dp. Avoid broad empty margins around maps; cap text density, not the entire map surface.

Reported separating regions override generic Android column ratios. When two real regions are present, fit each independently. If a pane cannot fit its content, collapse secondary content or relocate the task; do not squeeze controls. Retain the current short-window safeguard for ordinary windows, but give tabletop a separate fit policy so its naturally short halves do not disable tabletop mode. As an Android starting point, require roughly 220dp for the upper overview and 280dp for the lower task region; increase these for font scale and IME needs. Apple's region and content-fit behavior is specified in section 9.

Allow an accessible divider adjustment on unobstructed large layouts if the selected scaffold supports it. Clamp it to minimum pane sizes and provide keyboard/accessibility alternatives. Do not allow dragging a dividing edge away from a real separating hinge.

## 6. Make Syrmos features use the extra space

Implement the following through the current app's real destinations and data. Preserve existing navigation labels and entry points unless a specific change is necessary; do not introduce a competing second app shell.

**Now/Home:** compact shows the useful departure answer, station, direction, and status first. Expanded adds a map with the selected station and nearby context. Tapping a map station updates the answer panel; selecting a row highlights the map. No duplicate station pickers across panes.

**Plan:** compact provides endpoints, timing preferences, options, and detail through a clear sequence. Expanded keeps the editable query and route options beside a preview map. Selecting an option updates the map and leg details without starting GO. On very wide windows, detail can become a third inspector. Start GO is a deliberate action for the selected real itinerary.

**GO:** compact prioritizes the current leg and next actionable instruction. Expanded shows the map with a leg timeline beside it. Tabletop puts the map and glanceable progress above the fold, with timeline and controls below. Always expose applicable recovery and end actions. Folding never starts, cancels, advances, reroutes, or confirms a journey.

**Explore and station details:** combine region/line/station selection with a map or station detail. Preserve filters and list position when details appear. Use canonical station identifiers: repeated names such as Syntagma must represent intentional separate contexts, not duplicate entities created by pane rendering.

**Departures and airport information:** keep station, direction, source status, and useful upcoming departures together. A supporting pane can show the selected service or airport connection context. Long schedules scroll inside a readable pane instead of becoming extremely wide rows.

**Fares:** retain a readable input form alongside the result and applicable notes where space permits. Use official ticket links; this layout task does not add purchases.

**Ariadne:** compact may use its existing presentation; expanded may dock it as a contextual pane when there is enough space. Keep the conversation and composer intact through posture changes. Opening a pane must not initialize a second assistant session, restart a model download, or resend a question. Present the real Ariadne artwork.

**Settings, onboarding, dialogs, errors, and empty states:** adapt these too. Use bounded forms and meaningful list/detail where useful. Custom presentations on devices with an opaque hinge must fit a usable region without hiding essential content or controls. On Duo, retain native presentation adaptation; do not treat every division as an opaque barrier. Keep the current task understandable even if the map is unavailable.

## 7. Android implementation and continuity contract

Inspect `/Users/peterdsp/git/Syrmos/gradle/libs.versions.toml` and module boundaries before choosing dependencies. The inspected baseline uses Kotlin 2.1.20, Compose Multiplatform 1.8.0, Voyager 1.1.0-beta03, and Android API 36 targeting. Add compatible AndroidX WindowManager and Material adaptive APIs through a narrow platform boundary. Verify the available API names against the selected library; current documentation and older dependencies may differ. Avoid broad unrelated dependency upgrades.

Use canonical supporting-pane and list/detail scaffolds where they fit. Preserve one navigation authority: either integrate pane navigation with Voyager or use non-navigating scaffold primitives; do not create independent back stacks for the same selection. Compact Back returns from detail to its list; expanded Back changes task history appropriately and must not close a visible pane merely because it exists. Validate predictive Back. See [list/detail scaffolds](https://developer.android.com/develop/ui/compose/layouts/adaptive/list-detail) and [supporting panes](https://developer.android.com/develop/ui/compose/layouts/adaptive/build-a-supporting-pane-layout).

Observe window geometry and posture for the active window with lifecycle-aware collection. Do not poll sensors continuously. Review activity resizing, orientations, edge-to-edge insets, multi-window behavior, and keyboard handling. Do not solve recreation bugs by locking orientation, forcing fullscreen, opting out of adaptive behavior, or indiscriminately adding `configChanges`. Follow the [device compatibility guidance](https://developer.android.com/guide/practices/device-compatibility-mode).

Keep screen state above presentation branches. Persist small restoration keys, selected IDs, query text, draft timing preferences, focused pane, list anchors, and relevant sheet state. Use existing state holders plus appropriate saveable/saved-state mechanisms; ViewModel or ScreenModel lifetime alone is not proof of process-death restoration. Store durable journey data in the existing repository and reconstruct derived data. Avoid placing large routes, maps, or datasets in instance-state bundles. Follow [Compose state saving](https://developer.android.com/develop/ui/compose/state-saving).

Preserve the visible list item and offset, not just the selected ID. Do not replace a continuous list with unrelated scroll states when posture changes. Adapt the viewport around a physically occluding Android hinge so rows and touch targets remain usable; preserving scrolling does not permit content to disappear beneath an opaque region.

Audit dependency scopes before composing two details simultaneously. The current dependency module registers map and some detail models as singletons to avoid duplicated polling. Independent pane selections must not overwrite each other's singleton state. Use one canonical workspace selection or explicitly scoped presentation models over shared repositories; do not blindly replace singleton registrations with factories that start extra polling or simulation pipelines.

Restore the active task, selected journey/leg, canonical station, map center/zoom/bearing, manual-versus-follow camera mode, and assistant draft where applicable. When a window shrinks, retain the pane the user is acting in. During GO, retain the current instruction as the default compact view; do not jump to Home. Keep the same selections when expanding again.

Use one live map instance per visible workspace where practical. Within one activity, keep a stable map owner while layout changes. Across unavoidable activity recreation, restore camera and selections into a properly recreated map. Audit osmdroid resume/pause/detach, location overlays, listeners, and coroutine lifetimes. Do not retain destroyed Activity references or leave hidden maps running. Recalculate visible camera padding around panels and occlusions without resetting to Athens or repeatedly fitting the route while the rider pans.

Do not remount the whole shell using posture or width as a key. Do not restart seeding, onboarding, data refresh, countdown clocks, or active-session side effects because a pane moved. Countdown values derive from timestamps rather than restarting timers. Restored live information retains its actual freshness; old positions must not become Live because the screen reopened.

## 8. Motion that makes folding feel natural

Use motion to explain continuity: the compact context sheet becomes a side pane while the map retains its geographic context; a selected station remains visibly selected; the current GO instruction moves to the reachable tabletop region.

Use approximately 180–320ms transitions for pane appearance and selection, up to 400ms for a major layout transition, following existing motion tokens where suitable. Prefer restrained fades, translations, and supported shared-element transitions for lightweight controls. Avoid snapshotting the entire live map or duplicating map instances for an effect.

Respond correctly during live window resize. Do not queue an animation for every reported pixel or hinge update. Update excluded areas immediately; animation must never carry interactive controls through an occluding region. Coalesce redundant layout decisions without delaying safety-critical information or creating oscillation near a fit threshold.

Respect system animation scale and reduced-motion preferences, including a valid zero-animation path. Do not add continuous decorative motion during navigation. Retain keyboard focus, text selection, screen-reader context, and touch targets when elements move. No posture-triggered sound or haptic is required.

## 9. Build an intentionally native iPhone Duo experience

### 9.1. Establish capabilities before choosing APIs

On September 17, Apple's readiness page still marked Xcode 27.1 beta as forthcoming, while this machine had Xcode 27.0. Recheck both the published tools and the installed SDK. Record the Xcode build, SDK, deployment target, available simulator runtimes, and exact declarations used. Rebuilding against the supporting SDK enables behaviors that merely running an older binary does not establish. [Apple readiness](https://developer.apple.com/iphone-duo/), [preparing the app](https://developer.apple.com/videos/play/tech-talks/111461/)

Maintain a capability table with three distinct cases:

| Capability | Implementation contract |
| --- | --- |
| Already supported by the project's SDK and minimum OS | Implement and validate now: scene-owned state, native navigation, reusable content, camera restoration, fit/scroll behavior, and presentation coordination. |
| Declared in the installed SDK, but newer than iOS 17 | Adopt only where useful, with the symbol's actual runtime availability and a working older-OS path. Some newer toolbar APIs may be available before the Duo layout APIs. Check each symbol separately. |
| Missing from the installed SDK, or lacking a matching test runtime | Document the precise missing integration/test. Complete independent work. Add real integration once the supporting SDK is present, then compile and exercise it. |

`#available` does not make an unknown SDK symbol compile. Do not add fake system types, string-based private API access, or unreachable sample code as evidence of support. Keep the iOS 17 deployment fallback unless a separate product decision changes it. Do not port the Android UI layer or introduce another cross-platform UI framework.

### 9.2. Give navigation and content arrangement separate jobs

Keep the existing destination hierarchy, navigation history, and selected entity across outer and inner displays. Work from the scene's proposed geometry, size classes, safe areas, and layout margins; do not branch on device names, `UIScreen.main`, or an orientation flag alone. If display scale is needed, obtain it from the active scene/view environment. Treat all four margins independently. [Apple preparation guidance](https://developer.apple.com/videos/play/tech-talks/111461/)

Use `NavigationStack` for a sequence and `NavigationSplitView` for a genuine list/detail hierarchy. Evaluate `TabView.defaultTabBarPlacement(.sidebar)` for information-dense navigation only when available and useful. A sidebar is not automatically the planner pane. Keep primary destinations discoverable without manufacturing extra destinations just to fill the inner display.

For paired task content, evaluate `ArrangementView`: split style can change axis with available shape; restricting its axis may collapse the arrangement. Overlay style serves content with an actual foreground/background relationship. Keep arrangements inside navigation and outside scrolling containers. Use `overlayArrangementZIndex` only if a real overlay composition needs to vary its content. Layout comes from native arrangements and reserved regions, not hinge-angle calculations. [Apple adaptive-layout APIs and examples](https://developer.apple.com/videos/play/tech-talks/111463/)

The following are Syrmos design decisions to implement with those containers:

| Feature | Compact presentation | Additional inner-display value | Collapse priority |
| --- | --- | --- | --- |
| Now/Home | Next useful departure and selected station | Station context with a linked map | Retain the departure answer and source status. |
| Plan | Endpoints → options → selected itinerary | Route options beside the preview map; query remains easy to edit | Keep the focused query when editing, otherwise the selected itinerary. |
| GO | Current instruction with reachable actions | Current progress and upcoming legs alongside the map | Retain the active instruction; provide an explicit map switch. |
| Explore | Region/line/station navigation | Hierarchical station browsing plus selected station context | Keep the current station detail; Back restores its list position. |
| Departures | Station, direction, and departure list | Selected service or geographic context | Keep station/direction and the readable timetable. |
| Ariadne | Existing conversation presentation | Optional contextual conversation beside the task | Keep the same conversation and composer; never start a second session. |

A three-column view must earn its space: navigation, working content, and a relevant inspector have different roles. Hide optional inspector content before compromising a readable task. Use measured content fit at the current Dynamic Type size. Do not hardcode a Duo pane ratio or transplant section 5's Android thresholds.

The visual centerpiece should be Plan and GO: one unmistakably selected itinerary, leg colors that agree with the map, clear transfer hierarchy, and a persistent current instruction. Design book and tabletop arrangements around this same content. Do not create pose-exclusive actions or duplicate the same instruction in two equally prominent cards.

### 9.3. Make native bars and contextual controls work together

Expect native vertical bars on the outer display and the landscape inner display, with horizontal bars on the portrait inner display. Their occupied edge is not always the same in multitasking. The hardware-aligned bar does not mirror with right-to-left content. Let navigation containers determine placement; a custom standalone toolbar does not gain these behaviors automatically. [Apple bar placement and container behavior](https://developer.apple.com/videos/play/tech-talks/111462/)

Define an action inventory for every Syrmos destination. For each action, identify its owner, availability, accessible title, symbol, grouping, and overflow priority. Implement these product priorities:

| Context | Keep prominent | Keep near its content | Overflow candidates |
| --- | --- | --- | --- |
| Browsing | Primary destinations and the selected context | Station search, direction, line filters | Secondary view preferences and infrequent utilities |
| Plan editing | Back/Close and the meaningful commit/search action | Endpoint fields, swap, timing preferences | Secondary itinerary utilities |
| Selected itinerary | Deliberate Start GO | Selected option and leg details | Existing secondary actions such as saving/sharing, where supported |
| Active GO | Current instruction and applicable recovery action | Follow/recenter in the map; step context in the timeline | Infrequent options; End remains clearly discoverable with the confirmation specified below |
| Ariadne composing | Send/Stop when applicable | Composer and its input accessories | Conversation utilities already supported |

Use native cancellation/prominent placements and semantic groups; avoid manual spacers. Provide title and symbol metadata even when the visible representation is an icon. Where supported, inspect `toolbarVerticalEdge`, `.visibilityPriority(...)`, `.toolbarVerticalCompressionBehavior(...)`, and `ToolbarOverflowMenu`. Preserve navigation during browsing and task controls during GO. Consolidate true overflow; retain distinct domain menus. Verify exact declarations before coding. [Apple toolbar customization](https://developer.apple.com/videos/play/tech-talks/111462/)

Do not assign high priority to everything. Do not put a long station selector, live countdown, or fare result into a skinny vertical control. Keep these readable with their content. A search field must search its adjacent list, and its suggestions must remain associated with that field. Do not move search to a distant global edge simply because room exists there.

Add a native confirmation before deliberately ending an unfinished GO journey; this is a new interaction requirement, not a description of the current iOS implementation. Distinguish abandoning an unfinished journey from acknowledging a successfully finished one. Folding, opening overflow, or dismissing a presentation must never trigger either action.

Audit `CompactTabHeader.swift`, custom map controls, and the Ariadne launcher against the native bar. Remove redundant chrome only where it duplicates a system responsibility; preserve meaningful titles and identity. Preserve the launcher's existing non-hit-testable backing behavior and Explore's separate Plan-control band until the new arrangement demonstrably replaces them correctly. Never create a second launcher or an invisible touch-blocking band per pane.

### 9.4. Distinguish layout division from actual occlusion

System safe areas, margins, division regions, and occlusion regions solve different problems. Apple's region APIs distinguish the fold division from dynamic occlusion; an inactive flat division does not justify a permanent blank stripe. `.includeInactive` is a structural hint, not a command to hide pixels. Preserve continuous scrolling content rather than dividing every list into separate feeds. [Apple reserved-region guidance](https://developer.apple.com/videos/play/tech-talks/111463/)

For custom geometry, inspect `GeometryProxy.reservedRegions(kind:)` with `.division` and `.occlusion` separately in the supporting SDK. Confirm the returned frames' coordinate space and availability; keep those native calls in the iOS adapter rather than manufacturing equivalent system regions from a guessed fold angle.

For Syrmos, require these geometry rules:

1. Normalize region frames into the actual content coordinate space. Keep the region kind and active state. Clip to the scene; do not subtract system occupancy again from a container that already respected it.
2. Let paired content use native arrangements. For genuinely custom overlays, find a usable placement and size for the whole control, including its hit target and label.
3. Keep active camera occlusions clear. Respond when they appear or disappear without relying on a fixed cutout size. Syrmos must not request camera access merely to manufacture this test; use supported simulator/system facilities where available.
4. Keep meaningful static instructions and action clusters comfortably away from an active division. A division is not an opaque Android hinge: retain scrolling continuity and system-managed presentation behavior.
5. Let appropriate map/background content extend behind system regions while keeping controls, attribution, selected-point visibility, and essential route information usable. Avoid applying `ignoresSafeArea` to the entire foreground hierarchy.
6. Compute map padding from the visible task panel and current occupied regions. Do not resolve a collision by resetting the camera, shrinking every control, or inserting a permanent screen-wide gutter.

Test asymmetric regions and nonzero origins. A left inset is not a proxy for the right inset. A division outside the scene must not split its content. When two usable areas cannot hold the intended panes, preserve the focused task and expose supporting content through a native presentation or an explicit switch.

### 9.5. Make sheets, focus, and the keyboard continuous

Prefer native sheets, alerts, menus, and popovers. Native sheet bars adapt differently on outer and inner displays; centered inner sheets normally retain horizontal controls, including in landscape. A narrowly justified single-action sheet can be evaluated for different bar behavior, but never disable vertical bars globally to simplify custom layout. [Apple presentation design](https://developer.apple.com/design/human-interface-guidelines/designing-for-iphone-duo)

Use an explicit presentation identity: station detail, vehicle detail, itinerary, rename draft, assistant, or GO session. Avoid several unrelated booleans that can present competing sheets. A sheet becoming an inline pane is the same task and selection, not dismissal followed by a new business event. Separate presentation closure from deleting a draft or ending a journey. Native modal boundaries must still hide background content from accessibility interaction.

Preserve endpoint text, cursor/selection where supported, the active field, rename text, and the Ariadne composer. Keep input accessories with the keyboard. When the keyboard reduces a pane, scroll the field and its relevant action into usable space; collapse supplementary map content if necessary. Never shrink the entire app to make every pane remain visible. Restore focus to the same semantic control, not an obsolete view instance.

Test the case where a selected vehicle or route expires while its detail is open: update or dismiss with an understandable explanation and retain the surrounding task. Do not restore an old snapshot as fresh live data. Do not automatically reopen a keyboard after genuine app relaunch merely because it was visible in a previous session.

### 9.6. Give state a lifetime longer than its arrangement

Reuse the current stores and services. Introduce only the missing ownership boundaries, using the existing project structure:

| Ownership | Required state | Lifetime rule |
| --- | --- | --- |
| Shared domain/session | Saved journeys, active GO session/progress, canonical entities, data freshness, session side-effect coordination | Independent of any sheet, pane, or hosting controller. Reuse `SavedJourneyStore` and the existing active-journey store. |
| Scene workspace | Selected destination and navigation path, Plan draft/results identity, station/vehicle selection, list anchors, map camera intent, Ariadne conversation identity | Survives outer/inner changes and `SceneDelegate` host replacement. Persist small restoration keys where appropriate. |
| Presentation | Which content is visible, focus target, transient animation, active drag | Derived from workspace and geometry; moving content cannot mutate journey progress. |

Suggested additions, only when existing types cannot cleanly own the responsibility: `App/SceneWorkspaceState.swift`, `DesignSystem/Adaptive/SyrmosLayoutEnvironment.swift`, and `Views/Map/MapCameraState.swift` beneath `iosApp/iosApp/`. These are proposed files, not existing APIs. Keep native framework types at the presentation boundary. Avoid a large global observable object that invalidates the entire map for every countdown tick.

Apply the contract at the actual repository seams:

- **`App/SyrmosApp.swift`:** inject surviving workspace state from `SceneDelegate` into each replacement host. Preserve the existing background/foreground rendering recovery. Verify recovery independently from layout-only updates.
- **`Features/Assistant/PlanFlow.swift`:** hoist endpoint/timing drafts and selected itinerary identity above arrangement branches. Replace index-only selection with stable route identity. Preserve rename/undo state. Fresh GO and resumed GO remain distinct intents in one session-backed presentation model.
- **`Views/Map/MapView.swift`:** coordinate station/vehicle/live-list selection. Add camera input/output with explicit manual, follow, and fit intents. A geometry update changes padding; only an appropriate camera intent changes geographic framing. Restore a replaced map without resetting to Athens.
- **`Features/Go/GoJourneyView.swift` and `GoJourneyViewModel.swift`:** give the active session a surviving owner. Make short-region instructions and controls scroll or fit. Rendering the same session in another pane must not run a new begin flow or reset alerted-leg bookkeeping.
- **`Core/Journey/GoJourneyActivityController.swift`:** retain its existing in-process start protection and verify session-keyed behavior through restoration. Duplicate Live Activities are a risk to test, not an established defect to assert.
- **`Features/Assistant/AriadneView.swift`:** retain conversation, unsent input, pending response, and model work across sheet/pane changes. No repeated request caused by view appearance.
- **`Features/Onboarding/OnboardingView.swift`:** preserve the page and permission progress. An OS prompt or fold must not replay onboarding or repeat a permission request.

Record session ID, selected entity ID, camera intent, and side-effect counts in test instrumentation without logging personal location or conversation content. For layout-only tests, freeze `SyrmosClock` and hold location, feed, assistant, and other external inputs constant after initial work settles. A layout transition then produces zero starts, ends, advances, reroutes, duplicate alerts, or assistant sends. Separately inject real domain events during transitions and confirm each is processed correctly once; legitimate progress must remain attributable to its event rather than the layout.

### 9.7. Refine motion and visual quality around the actual task

Use system container transitions before inventing custom choreography. Keep the selected route, its color, and current instruction visually traceable through an arrangement change. Animate lightweight relationships only when the geometry is stable enough; apply a newly excluded region immediately. Preserve manual map framing and never create two live maps just to crossfade them.

Treat section 8's timings as design starting points for custom transitions, not overrides for native animation. Under Reduce Motion, make the same task relationship clear with immediate layout and restrained opacity where appropriate. Under Reduce Transparency, maintain readable opaque control surfaces. Stop decorative work when the scene is inactive or content hidden; specifically review `OnboardingMeshBackground.swift` and its animation timeline.

Inspect real screenshots for clipped station names, inconsistent selected states, competing titles, cramped chips, inaccessible map attribution, empty panels, and action hierarchy. Match Syrmos's colors, content, and meaning across platforms while honoring native typography and controls. Do not claim visual parity by overlaying different platform navigation chrome pixel for pixel.

### 9.8. Support system multitasking without expanding the product's window model

Keep independent app scenes disabled for this scope. The current app can still need resizing for system Split View and a scene below pinned video. Test both Split View sides because native bar occupancy changes, and test constrained height separately from width. [Apple scenes and multitasking](https://developer.apple.com/videos/play/tech-talks/111464/)

If independent windows are later adopted, treat them as a separate feature: per-scene drafts/navigation, explicit active-journey ownership, scene-activation availability/error handling, and deduplicated background work. Apple's camera accessory is a purpose-specific camera surface, not permission to display an arbitrary second Syrmos dashboard on the other physical display. Layout must not depend on sensor polling or camera use.

## 10. Accessibility, content, and resource use

Support existing English, Greek, Albanian, and Italian coverage; verify each new label and long destination rather than clipping to fit. Keep text scaling, TalkBack/VoiceOver order, explicit pane headings, keyboard focus, and accessible pane-switch/divider controls. Avoid relying on color or hover. Background panes hidden by an overlay must not retain invisible focusable controls.

At the largest supported accessibility text sizes, let rows grow and collapse optional panes before clipping the current instruction or its action. Retain logical reading order after a pane moves, announce meaningful content changes rather than every geometry callback, and preserve focus by entity/action identity. Exercise right-to-left layout in a test configuration without claiming a new translated locale; native hardware-aligned Duo bars remain system-owned. Provide non-map access to the same station, route, and guidance information.

Test location denied, offline startup, cached information, unavailable maps, no routes, loading, errors, and a restored expired journey. This redesign must preserve those states honestly. It must not fabricate departure guarantees, platform numbers, accessibility data, or live tracking coverage.

Keep map and live-data work scoped to visibility and actual session needs. Pause unnecessary rendering in hidden windows and avoid duplicated collectors. Measure frame behavior during folding and map interaction, and inspect memory after repeated transitions. Do not claim smoothness or battery improvement without measurements. Preserve existing offline/cache behavior and permissions.

## 11. Execute in reviewable phases

1. **Baseline and product composition:** capture current Android and iOS compact/large-window screens; record current tools and state lifetimes. Define deterministic station/journey fixtures, the action inventory, and compact/open/book/tabletop compositions. Identify which content is navigation, working content, map, or inspector.
2. **Continuity on both platforms:** implement surviving task/session ownership, map camera intent, canonical selection, and presentation coordination. Validate a selected station and a running GO session through Android recreation and iOS host replacement before multiplying panes.
3. **Native geometry and navigation:** add compatible Android posture/scaffold integration and supported iOS navigation/content composition. Integrate real Duo arrangements, regions, and bars when the required SDK is installed. Verify API availability individually and keep a precise pending list for unavailable dependencies.
4. **Complete product flows:** finish Plan and GO first on both platforms, then Now, Explore, Departures, fares, Ariadne, settings, onboarding, and all presentations. Preserve feature access. Missing Duo tools do not block complete Android or supported-iOS slices.
5. **Interaction and visual refinement:** validate bars/overflow, keyboard, focus, typography, source labels, reduced motion/transparency, map padding, and continuity on running surfaces. Polish the actual screen captures and transitions against section 3's art direction.
6. **Regression and evidence:** run the applicable matrix, relevant builds/tests, and targeted performance checks. Deliver a capability report distinguishing supported-iOS foundations, compiled Duo integration, Duo runtime checks, and physical-device quality validation.

Complete every available part of each phase; carry explicitly gated items into the readiness report while continuing independent work. Do not stop after a visual proposal, a navigation-rail change, or adapter scaffolding. Preserve unrelated changes and keep commits/reviews scoped according to repository rules.

## 12. Acceptance matrix and required evidence

Create deterministic tests for policy fit, occlusion avoidance, selection restoration, and side-effect deduplication. Reuse existing GO/domain fixtures. Discover and run the relevant Gradle test tasks, Android debug build, changed-module checks, and iOS builds/tests supported by the environment. Do not replace failed checks with screenshot assertions or disable existing tests.

### 12.1. Cross-platform and Android coverage

Validate all of these on actual running surfaces where supported. Android dp fixtures are not iPhone hardware dimensions:

1. Narrow ordinary phone at 320/360dp logical widths, including long station names.
2. Book-fold cover display, both orientations supported by the OS.
3. Open inner display in portrait and landscape.
4. Narrow inner-display content below 840dp, proving useful panes appear when they fit.
5. Vertical half-open posture and horizontal tabletop posture.
6. Flip-phone tabletop with small upper/lower usable regions.
7. Separating full-occlusion hinge when flat and when partly open, using real or synthetic test geometry as explicitly labeled.
8. Off-center fold and a fold outside the app's multi-window bounds.
9. Split-screen widths on each side of navigation and pane-fit thresholds.
10. Keyboard open during endpoint editing, then fold/unfold/rotate.
11. Selected route, scrolled list, and manually panned map retained through twenty open/close cycles.
12. Running GO retained through those cycles with the same session and no duplicate side effects.
13. Background/foreground, activity recreation, and actual system-initiated process restoration. Activity recreation alone does not prove process-death recovery.
14. Assistant conversation and unsent draft preserved through resizing.
15. Dialogs, sheets, menus, and errors never straddling an occluding hinge.
16. Large font scales, TalkBack, keyboard navigation, and reduced/disabled animations.
17. All existing supported locales in light and dark themes.
18. Offline startup and connection loss during a fold, preserving truthful status labels.
19. Tablet/resizable large window with two panes, and sufficiently wide window with an optional inspector.
20. Current iPhone/iPad narrow and wide layouts, Dynamic Type, scene recovery, and map state.
21. Duo SDK compilation, real reserved-region behavior, outer/inner transitions, and scene behavior when the required toolchain/runtime is available. Otherwise mark this row pending with the exact missing dependency.
22. Predictive Back cancellation and completion from compact detail and expanded selections, preserving the intended destination and canonical selection.
23. Required actions reachable through accessible menus or pane switches with the keyboard open in the smallest supported tabletop region.
24. Cold launch without folding data, late arrival of regions, and removal of regions while editing; no stale excluded space or lost task.

### 12.2. Mandatory iPhone Duo scenarios

These are product acceptance cases, not a claim that today's environment can run them. Use real Duo simulator/device behavior for system bars and regions. Synthetic fixtures can establish policy correctness but cannot pass a native-Duo-runtime requirement. Group cases into efficient recordings while retaining an outcome for each ID.

| ID | Exercise | Required Syrmos outcome |
| --- | --- | --- |
| D01 | Cold launch on the outer display; open a saved itinerary | Native navigation fits; destination names and Start GO are usable; no duplicate title or navigation shell. |
| D02 | Open to the inner display in both orientations | The selected itinerary gains useful map context; native bars adapt; no selection reset or repeated startup. |
| D03 | Move the app to each side of system Split View | Both sets of asymmetric margins/bar occupancy are respected; map controls and attribution remain reachable. |
| D04 | Place supported pinned video above the app and vary the remaining height | The current task fits or scrolls; actions remain reachable; this is not falsely treated as tabletop. |
| D05 | Move slowly through flat → book → flat | Paired content adapts without oscillation; no permanent fold gutter remains; list anchor and selection survive. |
| D06 | Move Plan and active GO into tabletop; reduce usable space further | Overview and task controls use available regions sensibly; insufficient space collapses support content without hiding the current action. |
| D07 | Show and change a supported Live Activity/Dynamic Island presentation | Outer-display chrome does not cover Syrmos controls or relabel stale journey data as live. |
| D08 | Activate/deactivate a system-reported inner camera occlusion using supported test facilities | Custom controls respond to the region; no camera permission is added to Syrmos; unavailable test facilities are recorded explicitly. |
| D09 | Compress browsing bars with constrained height and keyboard | Primary destinations remain discoverable; each secondary action is reachable once through an accessible menu. |
| D10 | Compress active-GO bars | Applicable recovery remains easy to find; End remains discoverable with confirmation; no action fires as a result of compression. |
| D11 | Search stations, apply a line filter, then resize | Field, suggestions, filter state, and results stay connected; the map follows the canonical selection. |
| D12 | Open station detail, vehicle detail, itinerary, and Ariadne presentations in turn; fold/rotate | Each remains the same task; no competing sheets, duplicate sessions, or unintended dismiss-and-reopen loop. |
| D13 | Rename a saved journey and edit Plan endpoints with the keyboard visible; fold and reopen | Draft text and logical focus survive; relevant actions stay reachable; keyboard presentation does not repeatedly restart. |
| D14 | Scroll a long departures list through a division change | The same visible item/offset remains meaningful; no unrelated second feed appears. |
| D15 | Test manual pan/zoom/bearing, Follow, and explicit Fit separately through host and layout changes | Each camera intent is honored; no default Athens reset or repeated route fit after a manual pan. |
| D16 | Repeat twenty open/close cycles during riding, an approaching get-off event, and transfer recovery | One session continues; layout-only tests with clock and external inputs held constant produce no domain events; injected/live events produce only legitimate, nonduplicated progress/alerts. |
| D17 | Background/foreground, lock/unlock, and interrupt with an OS permission prompt | Host recovery preserves the workspace; no duplicate splash, GO begin, assistant request, or permission prompt. |
| D18 | Terminate/relaunch through supported restoration tests with a saved active journey | Durable state restores honestly; expired data is handled; a new process is distinguished from host replacement. |
| D19 | Resize Ariadne while a response or existing model operation is pending | Same conversation, draft, pending work, and scroll context; no resend or download restart caused by presentation. |
| D20 | Fold during onboarding, including a permission step | Same page and progress; readable content/action relationship; no duplicate onboarding tree. |
| D21 | Use the largest accessibility text sizes and long existing translations | Current instruction, statuses, buttons, and sheets remain readable; optional panes yield before essential text clips. |
| D22 | Use VoiceOver and hardware keyboard navigation through pane changes | Stable semantic focus, logical pane order, no hidden focus targets, and accessible alternatives to map-only information. |
| D23 | Enable Reduce Motion, Reduce Transparency, light/dark mode, and a test RTL layout | Clear controls and selected states; no compulsory decorative motion; native bar placement remains correct without manual mirroring. |
| D24 | Lose connectivity or invalidate a selected vehicle/itinerary while folding | Accurate cached/offline/expired status, understandable recovery, and no fabricated live position or duplicate entity. |

### 12.3. Evidence and completion gates

Include logical window dimensions, density/font scale, OS/runtime, posture source, and available content regions in the evidence. Distinguish physical-device tests, emulator/simulator tests, and synthetic geometry tests. Record measured startup, frame timing/jank, and memory observations for representative transitions, with a before/after baseline where meaningful.

Use the existing deterministic clock and canonical transit fixtures, with controlled location/feed/assistant inputs, so screenshots and state assertions describe the same journey. Record repository revision and tools with each result. Exercise view/layout replacement, host/activity recreation, and process restoration separately. A snapshot test does not prove session lifetime or native bar behavior.

For performance, capture comparable traces on named devices and report transition hitching, steady map interaction, retained map/model instances, and memory before/after the repeated-cycle test. There must be no accumulating duplicate listeners, maps, or session owners. Set device-appropriate budgets from the measured baseline before claiming an improvement; do not invent an FPS or battery result. Investigate meaningful regressions before calling a slice complete.

Maintain `docs/design/FOLDABLE-READINESS.md` as the implementation evidence record, creating it during implementation if absent. It must include the capability matrix, source/tool dates, relevant source seams, scenario results, artifact paths, and exact unresolved dependencies. Report these levels separately:

| Level | Evidence required | What it establishes |
| --- | --- | --- |
| Supported foundations | Builds and relevant tests on the available Android and ordinary iOS targets; state and fit scenarios pass | Usable implementation on those targets, with preparation for Duo |
| Duo SDK integration | Real Duo APIs compile with the recorded supporting SDK; older-OS fallback also builds | API integration, without a claim of runtime correctness |
| Duo runtime validation | Matching Duo simulator/device exercises the applicable D01–D24 cases; pending cases listed | Verified behavior only for the recorded runtime and scenarios |
| Physical-device quality | Actual device posture, reachability, accessibility, motion, and resource checks | Hardware-level quality for tested devices and configurations |

For each acceptance case, report Pass, Fail, or Pending with a short reason and evidence path. Missing tools are Pending, never Pass. Do not call Duo support complete while a mandatory native-runtime case is pending. Optional future independent-window work is outside this completion gate.

Deliver implementation files, a concise architecture note, screenshots of compact/open/book/tabletop layouts, a short recording of journey continuity through folding, test/build results, and the readiness record. Include Plan and active GO, with keyboard/large-text examples and at least one native overflow presentation. Every implementation-evidence screen must come from the running app; label any design-only sketch separately.

## 13. Source and evidence boundaries

Reviewed September 17, 2026. The HIG was read in its rendered page, including its examples; related official sessions supplied technical context and published API spellings. API references linked from the HIG are implementation entry points, not proof those declarations exist in the currently installed SDK. Recheck tool availability and reference signatures when implementation begins.

| Primary source | Use in this prompt |
| --- | --- |
| [Apple: Designing for iPhone Duo](https://developer.apple.com/design/human-interface-guidelines/designing-for-iphone-duo) | Hierarchy, control placement, presentation, and design principles |
| [Apple: Design for iPhone Duo](https://developer.apple.com/videos/play/tech-talks/111466/) | Design rationale and visual examples |
| [Apple: Prepare your app](https://developer.apple.com/videos/play/tech-talks/111461/) | Scene geometry, native containers, rebuilding, and adaptation |
| [Apple: Raise the bar](https://developer.apple.com/videos/play/tech-talks/111462/) | Bar ownership, actions, representation, and overflow |
| [Apple: Adaptive layouts](https://developer.apple.com/videos/play/tech-talks/111463/) | Arrangements and reserved-region semantics |
| [Apple: Multiple displays and scenes](https://developer.apple.com/videos/play/tech-talks/111464/) | Resizing, scene boundaries, and purpose-specific display capabilities |
| [Apple: Get ready for iPhone Duo](https://developer.apple.com/iphone-duo/) | Dated SDK and runtime availability checks |
| [Android: Make your app fold-aware](https://developer.android.com/develop/adaptive-apps/guides/foldables/make-your-app-fold-aware) | Reported fold geometry and posture |
| [Android: Adaptive sizing](https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-display-sizes) | Window and content fitting |
| [Android: State saving](https://developer.android.com/develop/ui/compose/state-saving) | Restoration and ownership mechanisms |

The pane priorities, timing suggestions, proposed files, and acceptance cases are Syrmos engineering/design requirements. They are not Apple or Android hardware specifications. This prompt revision changes documentation only; future implementation reports must establish their own build and runtime evidence.

Success means Syrmos feels intentionally designed for each posture: more capable when opened, easy to use when held, useful on a table, and continuous throughout the journey.
