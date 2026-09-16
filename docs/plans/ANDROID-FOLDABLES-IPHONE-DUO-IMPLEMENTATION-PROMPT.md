# Syrmos — exceptional Android foldables and iPhone Duo foundations

Prepared 2026-09-14. This document is an implementation prompt, not evidence of implemented or released foldable support.

## 1. Mission and definition of success

Act as a senior Android/Compose engineer, adaptive-interface designer, and iOS architecture engineer. Implement a polished foldable experience for **Syrmos**, using the available screen space to make everyday travel easier. Give Android foldables a complete, tested implementation and prepare the native iOS architecture for **Apple iPhone Duo**.

The signature experience: a rider selects a journey on the cover screen, opens the device, and immediately sees that same journey beside its map. Partially folding the device creates useful book or tabletop arrangements. Closing it returns to the relevant compact screen with the same selection and active journey. No lost draft, restarted guidance, map reset, duplicate task, or repeated splash.

Work in `/Users/peterdsp/git/Syrmos`. Implement working product screens, motion, state continuity, and tests. Keep native Android Compose/Voyager and native iOS SwiftUI/MapKit. Preserve real branding, transit artwork, offline information, and current feature access. Do not release to stores, deploy the website, or change version numbers as a side effect.

Prioritize Android completion. Apple preparation must produce usable architecture and supported-SDK validation; Duo-specific integration is a separate, explicit gate when its SDK and runtime are available. A generic wide-window preview is not a verified Duo build.

## 2. Verify the current baseline before editing

Read repository instructions, inspect local changes, and record the commit and toolchains. Read `/Users/peterdsp/git/Syrmos/DESIGN_SYSTEM.md`, `/Users/peterdsp/git/Syrmos/docs/PRODUCT_PRINCIPLES.md`, and `/Users/peterdsp/git/Syrmos/docs/plans/3.0.0-IMPLEMENTATION-PROMPT.md`. Reconcile earlier proposals against current code. This task updates adaptive presentation and continuity; it is not a request to reimplement the entire Journeys roadmap.

Current inspection found these concrete starting points; recheck them:

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
| iOS tools | This inspection found Xcode 26.6 and iPhoneOS SDK 26.5. Recheck installed tools; simulator discovery was unavailable in the inspection environment. |

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

The larger screen should reveal **related information that helps the current task**: route options beside their map, a station beside its departures, or the current GO instruction beside upcoming legs. Do not fill space with unrelated dashboard cards or stretch a phone form across the entire display.

At default text size, use an 8dp spacing rhythm with 16–24dp panel padding, 16–24dp corners for major surfaces, 48dp minimum Android touch targets, and content rows around 64–80dp minimum height. Use approximately 28–36sp for the main current-action headline and 15–17sp for body text; allow full font scaling and content growth. Preserve the canonical token generator for exported values.

The map is a first-class pane. Station selection, route selection, and map highlighting must feel like one connected interaction. Keep destination names and Live/Scheduled/Estimated/Cached/Offline labels readable. Never replace factual status with a decorative green dot.

## 4. Design every posture deliberately

| Situation | Required experience |
| --- | --- |
| Ordinary phone or book-fold cover display | A focused single task, reachable navigation, readable endpoint fields, and an accessible map/detail switch. During GO, prioritize the current instruction and next action. |
| Fully open inner display | Related content in two panes whenever it genuinely fits: task/list pane plus map/detail pane. Use a rail when appropriate. Select a station or journey once and update both panes. |
| Vertical separating fold / book posture | Align the two content regions to the reported fold. Put list/planner content in one region and map/detail in the other. Keep actions and important text clear of the fold. |
| Horizontal separating fold / tabletop | Put the map, route overview, or large departure display above the fold; place journey steps, selections, and reachable controls below. Scroll each content region independently as needed. |
| Fully open flip phone | Use ordinary phone layouts unless available space warrants more. Tabletop adaptation must also work here. Do not assume every foldable opens into a tablet. |
| Very small flip cover display | Support normal app windows only where the OS permits them. Do not promise arbitrary cover-screen execution or add OEM-specific launch hacks. Existing widgets are a separate surface. |
| Dual-screen device with an occluding hinge | Treat the hinge as unavailable space, including when flat. Fit panes to the actual two regions. No button, label, route instruction, dialog, or touch target may bridge the hinge. |
| Multi-window, desktop window, or tablet | Recompute from the current app window. Use two or three panes only when task content fits; do not infer layout from a device model or physical display. |
| Keyboard, short window, or large text | Prioritize the focused task and reachable action. Collapse supporting content when necessary and provide an obvious way to reopen it. Preserve input and selection. |

No manual fold-mode switch should be necessary. An optional Show map / Focus journey control is useful; remember task intent without overriding physical occlusion rules.

Posture handling must use reported window and folding information. Read separating state, orientation, occlusion, and fold bounds; do not derive placement from a marketing name, screen diagonal, or guessed hinge angle. A flat nonseparating crease need not create a permanent empty stripe. Follow [Android's fold-aware guidance](https://developer.android.com/develop/adaptive-apps/guides/foldables/make-your-app-fold-aware).

## 5. Geometry and layout policy

Use **dp/sp on Android and pt on iOS**. All reference dimensions below are product test fixtures, not hardware specifications.

Separate three concepts: the current application window; system/navigation/keyboard occupancy; and the actual content regions available to each pane. Navigation policy may use the window size class, but content fitting must use the measured space after occupied areas are accounted for. Convert fold bounds into the same local coordinate system exactly once. Intersect them with the app window; a hinge outside the window is irrelevant. Never subtract an inset twice. Use [Android's window and component sizing guidance](https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-display-sizes).

Create one tested adaptive policy that consumes usable rectangles, reserved regions, font scale, current task, and active pane. It produces pane placement and visibility. Platform adapters supply geometry; screen components consume decisions. Keep Android SDK types out of shared domain code.

Extend the current policy deliberately. Preserve existing behavior for callers that have not adopted the new task-aware policy; do not silently change the web app through a shared-token or breakpoint edit. Add platform-neutral geometry/selection fixtures that the Swift implementation can also validate.

For an unobstructed content canvas `C` after native navigation and system occupancy, use these starting rules at default text size:

- **Below 592dp wide:** one primary task pane. A map may be the main surface with an adaptive context sheet, but there must be a fully readable content alternative.
- **592–687dp:** a lightweight station/list-plus-map layout may fit two 272dp panes with a 16dp gap and 16dp outer margins. Planner forms remain single-pane if their minimum usable widths do not fit.
- **688–839dp:** planner-plus-map may use two panes of at least 320dp, with a 16dp gap and 16dp outer margins. This lets narrower unfolded devices gain useful context before a conventional expanded breakpoint.
- **840–1279dp:** use a 340–400dp task pane, 24dp gap, 24dp outer margins, and a map/detail pane taking the remaining space.
- **1280dp and above:** optionally expose a third inspector for selected route/station details. Fit at least 320dp task + 480dp map + 280dp inspector, two 24dp gaps, and 32dp outer margins. Hide the inspector first when space shrinks.

These are fit policies, not mandatory column counts. Increase content minima for larger text. Validate translations and actual rendered controls. Keep single-column prose bounded around 680dp. Avoid broad empty margins around maps; cap text density, not the entire map surface.

Reported separating regions override generic column ratios. When two real regions are present, fit each independently. If a pane cannot fit its content, collapse secondary content or relocate the task; do not squeeze controls. Retain the current short-window safeguard for ordinary windows, but give tabletop a separate fit policy so its naturally short halves do not disable tabletop mode. As a starting point, require roughly 220dp for the upper overview and 280dp for the lower task region; increase these for font scale and IME needs.

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

**Settings, onboarding, dialogs, errors, and empty states:** adapt these too. Use bounded forms and meaningful list/detail where useful. Popovers, menus, sheets, and confirmation controls must remain fully visible in one usable region. Keep the current task understandable even if the map is unavailable.

## 7. Android implementation and continuity contract

Inspect `/Users/peterdsp/git/Syrmos/gradle/libs.versions.toml` and module boundaries before choosing dependencies. The inspected baseline uses Kotlin 2.1.20, Compose Multiplatform 1.8.0, Voyager 1.1.0-beta03, and Android API 36 targeting. Add compatible AndroidX WindowManager and Material adaptive APIs through a narrow platform boundary. Verify the available API names against the selected library; current documentation and older dependencies may differ. Avoid broad unrelated dependency upgrades.

Use canonical supporting-pane and list/detail scaffolds where they fit. Preserve one navigation authority: either integrate pane navigation with Voyager or use non-navigating scaffold primitives; do not create independent back stacks for the same selection. Compact Back returns from detail to its list; expanded Back changes task history appropriately and must not close a visible pane merely because it exists. Validate predictive Back. See [list/detail scaffolds](https://developer.android.com/develop/ui/compose/layouts/adaptive/list-detail) and [supporting panes](https://developer.android.com/develop/ui/compose/layouts/adaptive/build-a-supporting-pane-layout).

Observe window geometry and posture for the active window with lifecycle-aware collection. Do not poll sensors continuously. Review activity resizing, orientations, edge-to-edge insets, multi-window behavior, and keyboard handling. Do not solve recreation bugs by locking orientation, forcing fullscreen, opting out of adaptive behavior, or indiscriminately adding `configChanges`. Follow the [device compatibility guidance](https://developer.android.com/guide/practices/device-compatibility-mode).

Keep screen state above presentation branches. Persist small restoration keys, selected IDs, query text, draft timing preferences, focused pane, list anchors, and relevant sheet state. Use existing state holders plus appropriate saveable/saved-state mechanisms; ViewModel or ScreenModel lifetime alone is not proof of process-death restoration. Store durable journey data in the existing repository and reconstruct derived data. Avoid placing large routes, maps, or datasets in instance-state bundles. Follow [Compose state saving](https://developer.android.com/develop/ui/compose/state-saving).

Audit dependency scopes before composing two details simultaneously. The current dependency module registers map and some detail models as singletons to avoid duplicated polling. Independent pane selections must not overwrite each other's singleton state. Use one canonical workspace selection or explicitly scoped presentation models over shared repositories; do not blindly replace singleton registrations with factories that start extra polling or simulation pipelines.

Restore the active task, selected journey/leg, canonical station, map center/zoom/bearing, manual-versus-follow camera mode, and assistant draft where applicable. When a window shrinks, retain the pane the user is acting in. During GO, retain the current instruction as the default compact view; do not jump to Home. Keep the same selections when expanding again.

Use one live map instance per visible workspace where practical. Within one activity, keep a stable map owner while layout changes. Across unavoidable activity recreation, restore camera and selections into a properly recreated map. Audit osmdroid resume/pause/detach, location overlays, listeners, and coroutine lifetimes. Do not retain destroyed Activity references or leave hidden maps running. Recalculate visible camera padding around panels and occlusions without resetting to Athens or repeatedly fitting the route while the rider pans.

Do not remount the whole shell using posture or width as a key. Do not restart seeding, onboarding, data refresh, countdown clocks, or active-session side effects because a pane moved. Countdown values derive from timestamps rather than restarting timers. Restored live information retains its actual freshness; old positions must not become Live because the screen reopened.

## 8. Motion that makes folding feel natural

Use motion to explain continuity: the compact context sheet becomes a side pane while the map retains its geographic context; a selected station remains visibly selected; the current GO instruction moves to the reachable tabletop region.

Use approximately 180–320ms transitions for pane appearance and selection, up to 400ms for a major layout transition, following existing motion tokens where suitable. Prefer restrained fades, translations, and supported shared-element transitions for lightweight controls. Avoid snapshotting the entire live map or duplicating map instances for an effect.

Respond correctly during live window resize. Do not queue an animation for every reported pixel or hinge update. Update excluded areas immediately; animation must never carry interactive controls through an occluding region. Coalesce redundant layout decisions without delaying safety-critical information or creating oscillation near a fit threshold.

Respect system animation scale and reduced-motion preferences, including a valid zero-animation path. Do not add continuous decorative motion during navigation. Retain keyboard focus, text selection, screen-reader context, and touch targets when elements move. No posture-triggered sound or haptic is required.

## 9. Prepare the native iOS app for iPhone Duo

Apple has published Duo-specific guidance; use it directly instead of guessed dimensions or a generic tablet assumption. During this prompt's preparation, Apple's readiness page listed Xcode 27.1 beta as coming later that month. Recheck before implementation. Preserve the current iOS 17 deployment fallback. [Apple readiness and tooling](https://developer.apple.com/iphone-duo/)

**Implement now with the installed supported SDK:** separate reusable task content from its presentation container; hoist journey drafts, navigation selections, map camera state, and active-session ownership outside host views that can be replaced; introduce a small layout-environment adapter for usable geometry and reserved regions; and validate one/two-pane composition with existing SwiftUI containers on supported iPhone/iPad windows. Reuse shared semantic fixtures and tokens, with native Swift layout behavior. Do not port the Android UI layer or add a generic cross-platform UI framework.

**Integrate when the supporting SDK is available:** evaluate `ArrangementView` for connected task/map content and `GeometryProxy.reservedRegions(kind:)` for division and occlusion regions. Use split arrangements for content that should remain side by side and overlay arrangements where a real foreground/background relationship exists. Arrangement containers do not own navigation. Use system-provided region geometry rather than inferring layout from hinge angles. Verify exact declarations and availability in the installed SDK. [Apple adaptive layouts](https://developer.apple.com/videos/play/tech-talks/111463/)

Prefer navigation and toolbar containers that can adapt to Apple's changing bar placement; audit custom headers, Ariadne controls, map controls, and modal presentation against their actual content region. Prioritize meaningful actions and provide overflow when space is constrained. Preserve the Syrmos identity without copying an Android rail into iOS. [Apple bars and toolbars](https://developer.apple.com/videos/play/tech-talks/111462/)

Prepare scene ownership deliberately: navigation and drafts belong to their window; shared saved data and active journey side effects need an explicit common owner. Two scene views of a journey must not double-start tracking, Live Activities, or notifications. Keep background/foreground host recovery and restoration working. The current app disables multiple scenes; retain that setting during groundwork. Resizable layouts do not require adding independent windows. Document and test additional scene support separately if later adopted. Do not infer that any app can render arbitrary transit content on both physical displays simultaneously; Apple's documented camera accessory is purpose-specific. [Apple scenes and displays](https://developer.apple.com/videos/play/tech-talks/111464/)

New SDK symbols cannot be made compilable in an old SDK by `#available` alone. If tools are unavailable, complete supported foundations and document the exact adapter integration and validation still pending. Do not introduce fake shims, inactive pseudocode presented as implemented support, or a build that depends on missing SDK symbols. Verify older-OS fallbacks after any adoption.

## 10. Accessibility, content, and resource use

Support existing English, Greek, Albanian, and Italian coverage; verify each new label and long destination rather than clipping to fit. Keep text scaling, TalkBack/VoiceOver order, explicit pane headings, keyboard focus, and accessible pane-switch/divider controls. Avoid relying on color or hover. Background panes hidden by an overlay must not retain invisible focusable controls.

Test location denied, offline startup, cached information, unavailable maps, no routes, loading, errors, and a restored expired journey. This redesign must preserve those states honestly. It must not fabricate departure guarantees, platform numbers, accessibility data, or live tracking coverage.

Keep map and live-data work scoped to visibility and actual session needs. Pause unnecessary rendering in hidden windows and avoid duplicated collectors. Measure frame behavior during folding and map interaction, and inspect memory after repeated transitions. Do not claim smoothness or battery improvement without measurements. Preserve existing offline/cache behavior and permissions.

## 11. Execute in reviewable phases

1. **Baseline and design:** capture current Android compact/large-window screens; record navigation/state/map issues; define the posture matrix and deterministic station/journey fixtures. Produce concrete layouts for compact, open, book, and tabletop modes.
2. **Policy and lifecycle:** add compatible posture/window inputs, tested geometry decisions, and restoration ownership. Verify a simple station-selection flow survives recreation before expanding the UI work.
3. **Android product layouts:** implement Now, Plan, GO, and map as complete vertical slices, then bring Explore, Departures, fares, Ariadne, settings, and all presentations to the same standard. Preserve current feature access.
4. **Motion and accessibility:** refine transitions, typography, focus, insets, pane switching, large text, keyboard, and map camera continuity on running devices/emulators.
5. **iOS foundations:** implement supported scene/state/layout seams and validate current iPhone/iPad behavior. Add the real Duo adapter only with a supporting SDK. Record precisely what remains gated.
6. **Regression and delivery:** run relevant tests and builds, capture the actual experiences, and report implementation, measurements, device coverage, and remaining limitations separately.

Complete each phase's authorized work before moving on; do not stop after a visual proposal or a navigation-rail change. Preserve unrelated changes and keep commits/reviews scoped according to repository rules.

## 12. Acceptance matrix and required evidence

Create deterministic tests for policy fit, occlusion avoidance, selection restoration, and side-effect deduplication. Reuse existing GO/domain fixtures. Discover and run the relevant Gradle test tasks, Android debug build, changed-module checks, and iOS builds/tests supported by the environment. Do not replace failed checks with screenshot assertions or disable existing tests.

Validate all of these on actual running surfaces where supported:

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
11. Selected route, scrolled list, and manually panned map retained through ten open/close cycles.
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

Include logical window dimensions, density/font scale, OS/runtime, posture source, and available content regions in the evidence. Distinguish physical-device tests, emulator/simulator tests, and synthetic geometry tests. Record measured startup, frame timing/jank, and memory observations for representative transitions, with a before/after baseline where meaningful.

Deliver implementation files, a concise architecture note, screenshots of compact/open/book/tabletop layouts, a short recording of journey continuity through folding, test/build results, and a specific iPhone Duo readiness checklist. Every shown screen must come from the running app. Do not present mockups or generic width tests as hardware validation.

Success means Syrmos feels intentionally designed for each posture: more capable when opened, easy to use when held, useful on a table, and continuous throughout the journey.
