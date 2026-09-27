# Syrmos: exceptional on foldable phones

## Implementation mandate

Create a coherent, polished foldable transit experience, with **iPhone Duo as the primary design and verification target**, and full Android foldable support. Unfolding must make the current task easier: more route context, better comparisons, clearer transfers, and a useful map beside the decision. Closing the phone must retain the same journey, selection, and place in the task.

**The machine implementing this plan HAS SIMULATORS. Use them. Build the app, launch it, inspect the actual screen, interact with it, open and close the device, rotate it, change its posture, type with the keyboard, inspect accessibility, capture evidence, and fix what fails. Repeat until every applicable simulator-testable acceptance criterion below passes. A successful build, a nonblank image, or a layout calculation is not completion. Do not leave routine simulator verification as homework for the user.**

Use the iPhone Duo simulator's actual device and posture controls as well as deterministic tests. Test the installed app through its real scene, navigation, presentation, and persistence paths. Use Android foldable emulators for Android. If a particular tool fails, diagnose that tool and use another available supported route; do not turn a sandbox IPC failure into a claim that simulators do not exist.

Physical crease perception, outdoor legibility, real radio/GPS behavior, battery consumption, and physical ergonomics need hardware evidence. Record those separately and honestly. This distinction does not excuse skipping anything the simulators can verify.

This document is an implementation specification, **not a claim that the work is shipped or that its acceptance tests have passed**. Implement the complete sequence, retaining established routing, safety, offline, accessibility, and localization behavior. Do not publish a release, submit to stores, or deploy the website as part of this task.

## 1. Evidence and baseline

Prepared **26 September 2026** against `/Users/peterdsp/git/Syrmos`. Source review began at `064d09f7`; the checkout advanced to `8b60bc51` during preparation. The intervening changes include Android interchange grouping, What's New copy, and the dark GO snapshot. Preserve them. Recheck the working tree and current commit before implementation; other work can continue independently.

The review covered the adaptive policy and platform adapters, iOS application hosting and navigation, Plan and GO, maps and journey activities, Android presentation paths, the product/design specifications, existing foldable plans, **15 Duo snapshots available by final review**, and eight representative release screenshots. The release screenshots are historical visual references; current source and a fresh runtime reproduction take precedence over their old findings.

Concurrent working-tree changes appeared during final review: Departures pairing on iOS/Android, additional fixture tests and two Departures captures, and relocation of the Compose workspace adapter into `core/designsystem`. These were inspected and retained in this plan's references. Build on them rather than recreating them. The observations below identify the reviewed baseline; reproduce each gap against the implementation checkout before changing it. Concurrent work is not itself proof that its runtime acceptance criteria have passed.

### Verified tools available during preparation

| Item | Observed evidence | Implementation consequence |
| --- | --- | --- |
| Selected Xcode | Xcode 27.1, build 27A9269, `/Users/peterdsp/Downloads/Xcode_27.1.app/Contents/Developer` | Native Duo verification is a present requirement. Recheck the selected toolchain before building. |
| Duo runtime | XcodeBuildMCP listed a booted iPhone Duo on iOS 27.1, `92B2C61A-0B60-4A63-8B24-85BAC609AC8B`, plus another Duo | Use a currently available Duo destination; IDs are inventory evidence, not permanent configuration. |
| Regression devices | iOS 27.0 iPhone and iPad simulators were also listed | Test compact phone and tablet regression behavior. Inventory older supported runtimes separately. |
| Android | Local AVD definitions include `syrmos`, `syrmos_lite`, `syrmos_tablet`, `claude_pixel` | A foldable AVD was not established by this inventory. Inspect the AVDs and configure an official foldable/dual-screen profile when needed. |
| iOS project | `iosApp/Syrmos.xcodeproj`, scheme `Syrmos - Athens Rail Times`, tests `iosAppTests` | Use the actual scheme. Do not assume the helper script's `iosApp` scheme is correct. |
| Native SDK branch | `SYRMOS_DUO_SDK` plus runtime availability gates | Prove that the native branch was compiled and exercised. Running the fallback build on Duo does not prove native integration. |

The shell's `simctl` inventory encountered CoreSimulator IPC restrictions while the dedicated simulator tool successfully listed devices. This was a tooling-path limitation, not evidence of missing simulators. No app build or interactive acceptance run was performed while writing this plan.

### Source-of-truth order

1. Current product rules and actual user task; current source and reproducible runtime behavior.
2. Current official Apple/Android guidance and APIs available in the selected SDK/dependencies.
3. This plan's acceptance criteria and newly collected evidence.
4. Historical plans, screenshots, and readiness logs, interpreted with their dates.

This is the master plan for the next foldable implementation pass. It supersedes conflicting prescriptions in [the earlier cross-platform prompt](ANDROID-FOLDABLES-IPHONE-DUO-IMPLEMENTATION-PROMPT.md) and [the six-posture design prompt](IPHONE-DUO-SIX-POSTURES-AWARD-DESIGN-PROMPT.md). Preserve their useful test cases and history. [FOLDABLE-READINESS.md](../design/FOLDABLE-READINESS.md) contains both old unavailability statements and later runtime evidence; replace its current-status summary with a dated evidence matrix during implementation without rewriting historical results as new passes.

Resolve these specific conflicts:

- Do not require bottom tabs on every cover layout. Allow the native Duo navigation bars to use their supported placement.
- Do not force a native arrangement axis based on the policy's preferred axis. Observe and adapt to the actual native arrangement; prove that both roles remain visible.
- Do not use physical device dimensions or a device-name check as the production layout policy.
- Do not treat a blank map, a color-block arrangement test, or a nonblank-image assertion as proof of a usable GO screen.
- Do not carry forward the old claim that Android has no folding adapter or camera persistence. Both now have implementation to inspect and improve.
- Do not call a policy single-pane fallback safe until the renderer actually honors its safe rectangle.

## 2. What the screenshots show

Snapshot directory: [iosAppTests/__DuoSnapshots__](../../iosApp/iosAppTests/__DuoSnapshots__). These are rendered fixture views, not full device screenshots. Current fixture sizes include 951 × 669, 669 × 951, and 466 × 678 points rendered at 2×. They do not establish the real scene's available content bounds or safe areas.

| Snapshot(s) | Observed result | Required response |
| --- | --- | --- |
| `arrangement-duo-inner.png`, `arrangement-duo-cover.png` | Colored role blocks demonstrate paired and single composition. | Retain as structural tests; add semantic, interaction, and actual-scene verification. |
| `arrangement-duo-inner-portrait-go.png`, `arrangement-duo-inner-portrait-plan.png` | GO stacks roles; Plan pairs them horizontally in these fixtures. | Validate readable content minima and actual native role placement, not just block color. |
| `arrangement-duo-inner-landscape-hinge.png` | A white separation band exists between colored roles. | Assert exact nonintersection with every active occlusion and that real controls remain reachable. |
| [GO landscape](../../iosApp/iosAppTests/__DuoSnapshots__/go-duo-inner-landscape.png) | Instruction at left, map and journey at right; substantial header space; the multileg route uses one green map stroke. | Tighten hierarchy, use real per-leg line colors, and give the map and upcoming transfer deliberate space. |
| [GO portrait](../../iosApp/iosAppTests/__DuoSnapshots__/go-duo-inner-portrait.png) | Map and timeline are nested into the upper portion; very little timeline is visible before the lower instruction area. | Compose map, current instruction, and timeline independently. Make the next transfer/current and next leg discoverable in the first viewport. |
| [GO cover](../../iosApp/iosAppTests/__DuoSnapshots__/go-duo-cover.png) | The instruction-first compact composition is promising; timeline continues below. | Preserve compact clarity; test a real persisted journey including End/Finish, rather than only this store-free demo. |
| [GO dark portrait](../../iosApp/iosAppTests/__DuoSnapshots__/go-duo-inner-portrait-dark.png) | Map grid without geographic tiles; green-on-dark tinted treatments warrant contrast checking. | Separate map readiness/offline evidence from layout; measure text/control contrast and inspect a loaded-map capture. Do not guess the map-provider failure cause from a PNG. |
| [GO with horizontal hinge](../../iosApp/iosAppTests/__DuoSnapshots__/go-duo-inner-landscape-hinge.png) | Map card appears blank except attribution; lower task controls are clipped in the captured viewport. | Reproduce in the actual scene. This image does not establish a successful hinge layout. Capture a usable map or an explicit useful fallback and reachable controls. |
| [Plan landscape](../../iosApp/iosAppTests/__DuoSnapshots__/plan-duo-inner-landscape.png) | Query/saved content and an empty Routes pane occupy separate columns. | Test populated alternatives, selected detail, route preview, errors, and keyboard—not only the empty state. |
| [Plan portrait](../../iosApp/iosAppTests/__DuoSnapshots__/plan-duo-inner-portrait.png) | Narrow side-by-side regions and considerable unused vertical space. | Base pairing on actual padded text/control minima; use a purposeful route preview/detail when a route exists. |
| [Plan cover](../../iosApp/iosAppTests/__DuoSnapshots__/plan-duo-cover.png) | A compact query form is visible. | Preserve query, picker, scroll, and selected itinerary when opening and closing the phone. |
| [Departures landscape](../../iosApp/iosAppTests/__DuoSnapshots__/departures-duo-inner-landscape.png) | Newly paired planning cards at left and service board at right make the expanded display useful. | Preserve this direction; verify independent scroll restoration, calendar/route interaction, hinge geometry, native bars, and large text. |
| [Departures cover](../../iosApp/iosAppTests/__DuoSnapshots__/departures-duo-cover.png) | The compact airport hero, calendar, and route selector remain in one scroll; the service board lies below the first viewport. | Verify that next-service access remains efficient and task selection survives opening; do not mistake an offscreen board for missing data. |

Eight additional references were visually inspected under [release-3.0.0/ios](../screenshots/release-3.0.0/ios): Home, Explore, Map, Airport, journey detail, connection risk, Ariadne, and More/settings. They establish Syrmos's real cards, line identity, navigation, and content density. Their September 16 fixtures predate subsequent fixes; do not reopen a resolved issue solely because it appears in an old image.

### Visual intent

Follow [PRODUCT_PRINCIPLES.md](../PRODUCT_PRINCIPLES.md) and [DESIGN_SYSTEM.md](../../DESIGN_SYSTEM.md): answer first, then trust, action, context, and detail. The next useful action remains dominant. Extra space exposes meaningful context, not repeated titles, decorative metrics, or a larger empty card.

Keep the actual Syrmos branding and Ariadne artwork. Use warm station-white and Aegean blue, semantic light/dark surfaces, and the exact transit line colors from data. Preserve semantic typography, Dynamic Type, localized text, established spacing/radii, and restrained motion. Glass belongs to appropriate native navigation and overlay surfaces; do not turn every journey card into glass.

## 3. Product experience by posture

Posture names below describe test scenarios and design intent. Production code derives layout from usable geometry, system regions, content requirements, and task—not an enum selected by device name. Every essential capability remains available in compact layouts.

| Scenario | Desired experience | Nonnegotiable behavior |
| --- | --- | --- |
| P1: closed / cover | One clear next action; concise journey summary; progressive disclosure of detail. | Same route, draft, active session, and meaningful scroll anchor as before closing. Honor native bar placement and safe areas. |
| P2: supported glance / tent use | An optional glance presentation emphasizes current line/direction, next stop, transfer, and an honest status. | Do not infer which half faces the user. Do not assume an angle API exists. Make glance mode explicitly accessible in other postures too; retain exit and journey controls. |
| P3: open flat landscape | A task area and useful route/network context side by side when they fit. | Keep readable task width; navigation outside the content arrangement; no duplicated toolbars. |
| P4: book | Two coherent reading regions when reported geometry supports them: task/selection and map/detail. | Essential controls and text avoid active obstructions. A visible crease without occlusion is not automatically an opaque dead strip. |
| P5: open flat portrait | A map overview above an instruction-and-timeline workspace for GO; Plan chooses readable pairing or a purposeful stack. | Do not squeeze the entire landscape companion into a short upper pane. Avoid equal halves as a universal rule. |
| P6: laptop / tabletop | Overview in one usable region and active controls in the other, responding to the actual system arrangement. | Keyboard, focus, and controls remain reachable; map remains useful; when minimum heights fail, use the safe single-region fallback. |
| Opaque dual-screen hinge | Independent useful regions separated by the physical gap. | No text, hit target, annotation requiring touch, or route-control affordance disappears into the gap. |
| Flip phone / short landscape / narrow window | Compact task with sensible scrolling and optional context disclosure. | Never force two panes merely because a fold is present. |
| iPad / resized window / external display | The same task adapts to that scene's usable space. | No `UIScreen.main` assumption, global orientation assumption, or device-family shortcut. |

Progressive disclosure must be predictable: expanding reveals context around the existing selection; collapsing keeps the active task. A user looking at a selected transfer should return to that transfer, not the top of a newly created itinerary.

## 4. Screen-by-screen design contract

### Home: see the station and the journey

- Compact: retain the next useful departure, truthful source/freshness, disruption context, and active-journey resume.
- Expanded: make the direction/interchange board the main task; use companion space for the selected station/network context or active journey. Retain the newly implemented interchange grouping; never show only one line because the nearest coordinate belongs to that platform.
- Keep the selected station, direction, favorites, and scroll anchor while folding. Avoid duplicate polling or geolocation acquisition from creating a second pane.
- Default to useful real data. Empty favorites, denied location, cached departures, and unavailable live data get explicit states; do not invent departure times to fill space.

### Plan: compare with confidence

- Preserve From/To, search text, picker selection, Now/Arrive by/Last connection, date/time, accessibility preferences, saved journey selection, route results, and selected itinerary identity.
- Expanded flow: editable query and route alternatives in the task region; selected itinerary with map/transfer context in the companion region. At widths that cannot support both, keep the task readable and open detail through the existing navigation flow.
- Empty Plan should explain what to do next in a modest area. Once results exist, replace the empty companion with actual selected-route context. Do not add unrelated filler to occupy the screen.
- For portrait/tabletop, choose a stack only when both regions remain useful. A keyboard must not strand the station results, hide the confirm action, or push the selected route into an inaccessible sliver.
- Compare actual meaningful differences: changes, walking/transfer detail where supported, arrival/leave-by, feasibility, disruption, and source certainty. Preserve comfortable-first ranking and existing no-service behavior.
- Use a stable itinerary key derived from the itinerary's actual legs and relevant schedule identity; a selected array index or line-chain-only identity is insufficient when alternatives update.
- Distinguish intentional refresh due to time/feed changes from a pure layout change. Opening the device must not itself rerun routing, change the selected option, or silently start GO.

### GO: the flagship foldable experience

Refactor the composition into reusable **current instruction**, **journey timeline**, **route map**, **journey controls**, and **source/status** components. Share one authoritative session. Do not maintain separate portrait and landscape journey models.

| Region | Content and behavior |
| --- | --- |
| Current instruction | Clear action verb; correct line badge and direction; next stop or transfer; relevant stop count with its precise meaning. Keep the current instruction readable without hunting. |
| Timeline | Current and next leg/transfer get priority; completed legs can collapse with accessible history. Keep manual browsing stable rather than repeatedly snapping the user back. Offer an explicit return-to-current action. |
| Map | Show the selected journey using each leg's real line color, transfer markers, and an unmistakable current/confirmed-position treatment. Keep attribution visible. Provide fit-route and return-to-follow controls as appropriate. |
| Controls | Manual progress/live guidance state, relevant next action, and End/Finish remain reachable in every valid layout and with large text. Do not duplicate destructive controls across panes. |
| Trust | Make estimated, cached, live, unavailable, and manually confirmed information distinguishable. A confirmed station is not automatically a live GPS fix. |

- Landscape: give the map enough area for geographic context; arrange the current instruction and upcoming timeline so both help the next decision. Remove excessive blank header space and repeated route headings.
- Portrait: map overview above; current instruction plus upcoming timeline below, or another verified native arrangement meeting the same priorities. Do not put a complete map-plus-timeline column inside the upper fraction and thereby hide the timeline.
- Tabletop: readable overview in one region, current task and reachable actions in the other. If the keyboard or region height prevents a useful pair, simplify deliberately.
- Cover: instruction first, compact progress/next transfer, controls, then timeline. Map remains accessible even if it is not permanently embedded.
- Ending an unfinished journey requires confirmation; cancellation leaves the journey untouched. Arrival can offer Finish. Verify the actual persisted journey path because the existing snapshot demo omits the store and therefore omits its End behavior.
- Folding, rotating, sheet changes, and host replacement must not call begin/advance/end again, create a second location subscription, restart a Live Activity, or duplicate a notification.
- Make route rendering survive loading and offline conditions. A blank tile surface with only attribution is not a useful finished state: preserve the route when possible and show a clear fallback when geography is unavailable.
- Map camera has explicit intent: fit route, follow, or manual exploration. Respect manual pan/zoom/bearing/pitch across reflow. Reframe for new geometry only when the active camera intent calls for it; do not recenter on every SwiftUI update or identical coordinate delivery.

### Network Map

- Compact keeps the map primary with accessible search, filters, station selection, and details.
- Expanded pairs map with the selected station/line detail, departures, and relevant actions. An empty inspector should be restrained and informative, not a large blank form.
- Preserve camera and selection when details move between a sheet and a pane. Avoid multiple map instances competing for selection or location updates.
- Compute annotation/control visibility against actual overlays and obstructions. Do not subtract a companion pane from map padding if the map's bounds already exclude that pane.

### Explore, lines, station detail, and network/community content

- Pair the line/station list with the selected detail or map at useful widths; retain search, region/type filters, and selected line/station.
- Keep the Explore Plan affordance, Ariadne launcher, tabs/rail, bottom/side native bars, and detail presentation from overlapping. Remove duplicate safe-area bands rather than masking them with more padding.
- Preserve the distinction between station presentation grouping and routing IDs; do not undo the Kifisias deduplication work.
- Keep Rail Pulse/community content subordinate to the selected task. Retain its existing capabilities; do not invent live observations or participation counts.
- A sheet that becomes a pane is still one presentation with one identity. Folding must not dismiss it, open a duplicate, or lose its navigation depth.

### Departures / Airport

- Preserve current product naming and navigation; the internal departures case is not authorization to rename the Airport experience.
- Compact prioritizes direction, next usable option, selected day/time, and service status.
- Expanded can pair the service/direction/day selector and departures with route/station context and supported airport alternatives. Avoid stretching the airport hero across the whole inner display.
- Preserve selected service, route, direction, date, airport bus/rail selection, scroll anchor, and source freshness. Explain the actual validity of offline/cached data.
- Extend the paired `planningCards`/`boardCards` work now present on iOS and its Android equivalent. Verify its geometry and continuity instead of starting a separate competing composition.

### Saved journeys, saved departures, and fares

- Saved items can use list/detail; selection, editing, and deletion confirmation survive posture changes. Resume connects to the existing active session.
- Fare selection can pair the readable form with a summary/explanation when useful. Preserve selected endpoints and picker draft; never fabricate prices or validity rules.
- Keep forms and explanatory prose at readable widths. Full-width availability does not require full-width paragraphs.

### Ariadne

- Compact uses the established conversation presentation. Expanded may dock the same conversation beside the relevant Plan/map context when there is sufficient room.
- Preserve conversation ID, messages, draft, focus, response progress, attachments/context already supported, download state, and scroll position. Moving between sheet and pane must not resend a prompt or initialize another model/download.
- Keyboard handling follows actual system geometry. Ensure the composer, send/cancel action, and current response remain reachable in all postures.
- Preserve existing privacy and model/network disclosures. Foldability is not permission to silently change assistant behavior or data sharing.

### More, settings, onboarding, alerts, and system surfaces

- Keep native readable forms. Use list/detail only where it serves selection; do not split a short settings form merely to fill the display.
- Preserve setting drafts, permission explanation, onboarding step, consent, language choice, and dismissal state. Folding must not replay onboarding or What's New.
- Check disruption detail, connection-risk notices, confirmation dialogs, pickers, share sheets, error recovery, and empty states—not just the main tab roots.
- Keep Live Activity/widget content concise and bound to the same active journey. Validate supported simulator surfaces and transitions without inventing a custom simultaneous-cover-display feature.

## 5. Architecture: geometry and state before polish

### A. One layout contract, native rendering

Retain the shared concepts of task, companion, and optional inspector. The shared policy can express preferred roles and minimum space; native containers decide platform-specific presentation. iOS and Android must agree on semantic invariants, not necessarily produce identical pixel geometry.

The layout inputs must include the **actual usable content bounds**, font scaling/content minima, task needs, and all relevant system regions. Output must describe the safe task/companion rectangles, any real separation, and when to fall back. Do not add a generic three-column dashboard just because an inspector role exists.

Required geometry rules:

1. Establish the coordinate space explicitly: scene/window, container, then pane. Convert once at each boundary using measured origins and density.
2. Intersect raw region frames with the current container before reasoning about their spans. A region beginning outside the container can still overlap it.
3. Preserve all relevant regions, including multiple occlusions, divisions, and cutouts. Selecting the first region is not sufficient.
4. Distinguish separation from occlusion and active from inactive regions. A non-occluding division can permit continuous imagery, while critical stationary controls must remain usable.
5. Apply navigation, safe areas, and keyboard geometry once. Account for rail/top bar/padding before deciding content minima.
6. Evaluate both panes after their internal padding and text/control requirements. A physical division must not override a minimum readable width or accessibility height.
7. A single-pane result must be rendered inside its safe rectangle. Merely returning that rectangle from policy while drawing full-size content is a bug.
8. Render paired rectangles and separation exactly. Do not add a second arbitrary gap/divider on top of the policy's geometry.
9. If content cannot fit, use a deliberate single-region arrangement with accessible context disclosure and scrolling. Never silently clip actions.
10. Scope injected test regions to their coordinate space. Do not inherit a root-local test frame into a nested pane as though it were pane-local.

Do not animate through an opaque hinge. Use system transitions where appropriate and modest content transitions elsewhere; honor Reduce Motion and preserve focus. Avoid visible intermediate blank frames or duplicated pane content during reparenting.

### B. iOS implementation map

| Files / area | Required work |
| --- | --- |
| `iosApp/iosApp/App/SyrmosApp.swift` | Establish scene-owned durable task state above replaceable hosts; keep native primary navigation outside content arrangements; audit ReadableTabContent, Ariadne and Plan inset bands. |
| `iosApp/iosApp/Features/Assistant/PlanFlow.swift` | Extract the reusable `SyrmosArrangement` into DesignSystem; preserve a single Plan state and presentation identity; implement populated task/detail/map composition. |
| `iosApp/iosApp/DesignSystem/AdaptiveWorkspacePolicy.swift` | Enforce readable minima for every region path, multiple-region handling, and safe single-region behavior in conjunction with the renderer. |
| `iosApp/iosApp/DesignSystem/ReservedRegionAdapter.swift` | Verify real-scene coordinate assumptions; preserve region semantics; scope injected geometry; test map padding and actual intersections. |
| `iosApp/iosApp/Features/Go/GoJourneyView.swift` | Decompose GO regions; apply safe rectangles; fix per-leg map color, camera intent, and End confirmation; avoid view-owned session restart. |
| `iosApp/iosApp/Features/Go/GoJourneyViewModel.swift` | Keep journey progression and subscriptions stable across presentation changes; expose reusable region state without multiplying models. |
| `iosApp/iosApp/Core/Persistence/SavedJourneyStore.swift` | Inspect `GoActiveJourneyStore` ownership and restoration; preserve one authoritative active journey and stable identity. |
| `iosApp/iosApp/Core/Journey/GoJourneyActivityController.swift` | Reconcile activity ownership with the persisted session, including process recreation; verify idempotence rather than relying solely on an in-process guard. |
| `iosApp/iosApp/Views/Home/HomeView.swift` | Adapt direction/interchange board and active journey context without duplicate data work. |
| `iosApp/iosApp/Views/Lines/LinesView.swift`, `ExploreRailPulseView.swift` | Preserve filters/selection; unify sheet/full-screen/pane presentation identity. |
| `iosApp/iosApp/Views/Map/MapView.swift` | Preserve map intent/selection and adapt station/line detail. |
| `iosApp/iosApp/Features/Timetables/TimetablesView.swift` | Adaptive Airport/departures composition with retained service/day/direction. |
| `iosApp/iosApp/Features/Fares/FaresView.swift` | Retain form/picker state and readable widths. |
| `iosApp/iosApp/Features/Assistant/AriadneView.swift` | Own conversation independently of dock/sheet composition; retain draft, focus, and in-flight work. |
| `iosApp/iosApp/Views/Settings/SavedDeparturesBoardView.swift` | Saved selection/detail continuity and adaptive presentation. |
| `iosApp/SyrmosWidget/SyrmosGoJourneyLiveActivity.swift` | Check accurate, stable journey projection during fold/background/foreground transitions. |

**Host reconstruction is a first-class continuity case.** `SceneDelegate` currently replaces the root hosting controller after a genuine background-to-foreground transition as a rendering recovery measure. Selected tab persistence does not preserve the other view-local drafts, navigation, map camera, and presentations. Keep the recovery behavior until its underlying issue has a verified alternative. Move the necessary durable state above the replaceable host, namespace per-scene navigation state, and reconnect to the single shared active journey. Do not solve continuity by deleting the workaround without reproducing its original failure case.

Use the smallest appropriate ownership mechanism and the project's SwiftUI conventions. Do not introduce a large view-model framework solely to rearrange views. Persist small restoration values and stable IDs, not transient framework objects, map views, raw UI trees, or unnecessary location history.

**Native Duo integration:** compile with the supporting SDK and the intended compilation flag, retain runtime availability checks, and keep fallback symbols isolated for older compilers. Confirm native branch activation in test evidence. Preserve inherited build conditions, especially `DEBUG`; do not replace the whole configuration with one flag. Decide and document how the intended release configuration enables the native path without relying on a developer's ad hoc command.

The current native `.split` implementation leaves axis selection to the system after an earlier forced-axis approach hid content. Observe the actual arrangement through supported APIs available in the SDK and adapt the child composition accordingly. Do not assume a preferred horizontal policy result means the native arrangement rendered horizontally. Probe this inside the real application scene: the bare-window snapshot host is insufficient evidence for system region delivery.

Use system navigation, toolbars, sheet positioning, and safe areas. Give toolbar actions appropriate titles and symbols, set meaningful priorities, and inspect system overflow behavior. Do not duplicate system navigation into an arrangement or manually mirror hardware-aligned bars for RTL. Keep geometry local to the current scene.

### C. Android implementation map

| Files / area | Required work |
| --- | --- |
| `core/common/src/commonMain/kotlin/com/syrmos/core/common/layout/AdaptiveWorkspace.kt` | Multiple-region/clipping/minimum-size correctness, useful single-region output, shared task semantics. |
| `core/common/src/commonMain/kotlin/com/syrmos/core/common/layout/ContentBreakpoint.kt` | Preserve existing non-adopter behavior; use post-inset bounds where appropriate. |
| `composeApp/src/androidMain/kotlin/com/syrmos/app/platform/ReservedRegions.android.kt` | Preserve enough FoldingFeature geometry and semantics; collect with appropriate lifecycle; do not discard information needed for clipping/multiple regions. |
| `core/designsystem/src/commonMain/kotlin/com/syrmos/core/designsystem/layout/AdaptiveWorkspaceCompose.kt` | Adapter relocated here during review. Derive the actual container's window origin, correct density, clipping, and region conversion; preserve feature-module reuse. |
| `composeApp/src/commonMain/kotlin/com/syrmos/app/screen/PlanScreenRoute.kt` | Support meaningful stacked composition; measure origin; render policy rectangles without extra arbitrary gaps; retain stable selected itinerary. |
| `composeApp/src/commonMain/kotlin/com/syrmos/app/screen/GoJourneyScreenRoute.kt` | Respect hinge gap and single-region bounds; provide useful route-map context alongside instruction/timeline; preserve repository-owned session. |
| `composeApp/src/commonMain/kotlin/com/syrmos/app/SyrmosApp.kt` | Navigation rail/inset/scene geometry integration and stable presentation. |
| `feature/map/src/androidMain/kotlin/com/syrmos/feature/map/PlatformMapView.android.kt` | Preserve the existing saveable camera behavior, explicit camera intent, selection, listener ownership, and cleanup. |
| `feature/home/src/commonMain/kotlin/com/syrmos/feature/home/HomeScreen.kt` | Interchange/direction workspace and continuity. |
| `feature/lines/src/commonMain/kotlin/com/syrmos/feature/lines/LinesScreen.kt` | List/detail/map behavior with stable search and selection. |
| `feature/schedule/src/commonMain/kotlin/com/syrmos/feature/schedule/AirportHubScreen.kt` | Extend current planning/board pairing; verify region offsets, gap/safe-rectangle rendering, keyboard, and restoration. |
| `feature/settings/src/commonMain/kotlin/com/syrmos/feature/settings/` | Readable settings/fares and stable form state. |

Current concrete gaps to reproduce and fix:

- Plan and GO callers do not supply the measured container offsets expected by the geometry adapter. Rails, top bars, and scaffold padding can therefore displace hinge calculations.
- Plan handles side-by-side versus single content without a complete stacked rendering path.
- Extra outer padding, fixed spacing, and dividers can disagree with the policy's rectangles.
- GO's paired companion is currently timeline content, without an equivalent dedicated route-map workspace; its renderer does not faithfully reserve the returned hinge gap.
- Single-pane rendering can still fill the entire container even when policy chooses a safer subregion.
- The common policy picks a first region and some paired-region paths bypass font-scaled content minima.

Inspect the actual dependency versions before adopting new APIs. At review, Kotlin is 2.1.20, Compose Multiplatform 1.8.0, Voyager 1.1.0-beta03, and Android Window 1.3.0. Current documentation can describe APIs absent from those versions. Reuse a correct adapter or make a focused compatible upgrade with build/runtime verification; do not paste newer API examples and assume availability. Keep routing and active-session semantics shared, but respect native Android navigation and lifecycle.

### D. State and side-effect ledger

Make the following restoration contract explicit in tests:

| State | Owner / restoration requirement | Forbidden layout side effect |
| --- | --- | --- |
| Tab, navigation depth, selected item | Stable scene/navigation state | Returning to Home or dismissing detail during a fold |
| Plan query, mode, picker, results | One task state with stable result identity | Replanning or selecting a different option solely because bounds change |
| Active GO | One persisted journey/session authority | New session, changed leg/stop, ended journey, duplicate location work |
| Map | Camera intent, center/zoom/bearing/pitch, selection | Forced recenter during manual exploration |
| Ariadne | One conversation/request owner | Resend, duplicate response/download, lost draft |
| Forms and presentation | Stable draft and presentation identity | Lost input, duplicate sheet, vanished confirmation |
| Timeline/list | Stable item anchor plus meaningful position | Jump to the beginning on reflow |
| Focus | Logical focused control with valid destination | Focus on a removed/hidden duplicate control |
| Live Activity/notifications | Session-linked idempotent coordinator | Restart or repeated alert during layout transitions |

For layout-only continuity tests, freeze or replay **clock, location, transit feed, and assistant events**. Count routing, session, location, notification, activity, and request side effects. A changing data source is not a valid explanation for a layout regression unless separately demonstrated.

## 6. Accessibility, language, resilience, and performance

- Test English, Greek, Albanian, and Italian with representative long labels. Preserve existing localization; do not ship English literals in new controls.
- Use semantic text sizes; test the largest supported accessibility sizes. Reflow, collapse optional context, and scroll before reducing type or clipping essential text.
- Target at least 4.5:1 for normal text, 3:1 for large text and essential control boundaries/graphics where applicable. Measure actual foreground/background combinations, including tinted dark GO cards. Preserve transit identity with badges/shapes/labels when exact line colors need a contrasting text treatment.
- Maintain platform-appropriate minimum hit targets, VoiceOver/TalkBack reading order, meaningful map alternatives, keyboard focus order, and Switch Control access where available. A pane reorder must not read the same instruction twice.
- Test Reduce Motion, increased contrast, light/dark, keyboard open, software keyboard dismissal, and external keyboard navigation where supported.
- Verify all permission states: location undecided/denied/approximate/available as applicable. User-controlled progress remains possible without pretending a station confirmation is a GPS fix.
- Test cached data, offline startup, no route/no service, disruption, stale data, and interrupted assistant/model work. Show useful real fallback content.
- Profile actual simulator interaction for layout thrashing, repeated model initialization, map recreation, subscription growth, and long main-thread work. Record tool/device conditions and compare the same scripted journey before/after. Simulator timing is useful comparative evidence, not a hardware battery or frame-rate guarantee.
- Use at least ten consecutive open/close/rotate cycles in a populated Plan and an active GO session. After expected caches settle, verify that subscriptions, activity count, retained task owners, and memory do not grow monotonically from transitions.

## 7. Verification that proves the experience

### Layer 1: policy and restoration tests

Extend the existing common and Swift policy/fixture suites instead of replacing them with screenshots alone:

- `core/common/src/commonTest/kotlin/com/syrmos/core/common/layout/AdaptiveWorkspaceTest.kt`
- `core/common/src/commonTest/kotlin/com/syrmos/core/common/layout/DuoPostureFixturesTest.kt`
- Their existing Swift counterparts in `iosApp/iosAppTests`.
- GO model/store, route/timeline projection, map state, and presentation restoration tests relevant to the changes.

Cover zero-size/short windows, partially intersecting regions, multiple cutouts/hinges, inactive regions, zero-width dividing folds, opaque hinges, font-scaled minima, container offsets, density conversion, and safe single-region fallbacks. Assert bounds and semantic invariants, not just a layout enum. Preserve routing IDs, ranking, stop-count meanings, and active-journey behavior.

### Layer 2: deterministic visual regression

Improve `iosApp/iosAppTests/DuoSnapshotTests.swift` before treating its images as approval evidence:

- Existing tests render a bare `UIWindow`, ignore safe areas, wait briefly, and mainly assert sampled image variance. Keep such tests labeled as composition tests.
- Add scene-hosted/system-container coverage for real safe areas, system regions, navigation bars, and presented UI.
- Store candidate output separately from approved baselines. Separate native-Duo and fallback builds, SDK/runtime versions, fixture configuration, and appearance so one run cannot silently overwrite another's baseline.
- Wait for explicit map/render readiness or capture a deliberately labeled offline fixture. Do not treat a fixed short delay as proof that a map is ready.
- Capture populated Plan, selected route detail, active persisted GO, current transfer, arrival, confirmation, map interaction, and assistant keyboard states. Empty forms and store-free GO are insufficient.
- Inspect every changed capture at full aspect ratio. Check truncation, clipping, overlap, line colors, selected state, contrast, map availability, meaningful whitespace, and first-viewport hierarchy.
- Add targeted assertions for critical controls/text, safe-region bounds, and accessibility structure. A nonblank image is only a smoke check.

### Layer 3: actual app and device transitions

Launch the real app in iPhone Duo and use supported device controls for open/close/rotate/fold. Capture screenshots and short transition recordings where available. Also exercise the Android app on a folding phone and a dual-screen/opaque-hinge configuration; include a compact phone and tablet regression pass.

Record the actual scene/content dimensions, safe areas, reported regions, active native/fallback path, font scale, selected task/session IDs, and side-effect counters in development-only diagnostics. Avoid user content or sensitive location in logs. Do not substitute injected regions for actual native posture tests; use injection for additional edge cases alongside them.

### Required scenario matrix

Each row needs a pass/fail result and evidence. Existing D/T cases in the earlier plans remain applicable when they express the same invariants. Link them rather than losing coverage during renaming.

| ID | Scenario | Pass condition |
| --- | --- | --- |
| F01 | Cold launch closed, then open | Correct native navigation; same selected task; no repeated onboarding. |
| F02 | Cold launch open portrait and landscape | Useful populated composition; no startup clipping or blank role. |
| F03 | Cover → open → cover on every primary tab | Tab, selection, navigation depth, and meaningful scroll remain. |
| F04 | P3/P4/P5/P6 with populated Plan | Query, selected route, readable controls, and useful companion persist. |
| F05 | Fold while station search keyboard is open | Search text, focus, results, and selection action survive. |
| F06 | Fold during date/time or mode selection | Draft and confirmation remain reachable; no duplicate picker. |
| F07 | Reflow selected route detail | Same itinerary; map/detail references the same legs. |
| F08 | Arrive-by, last connection, no-service, disruption | Correct ranking/trust/error behavior in compact and paired layouts. |
| F09 | Start persisted GO, then all six scenarios | Same session and current instruction; no duplicate begin/activity. |
| F10 | Advance manually, fold, resume | Exactly one intended advance; consistent count/timeline/map. |
| F11 | Replay location guidance while folding | No duplicate subscription or extra progression from layout. |
| F12 | Transfer instruction on cover and inner display | Correct line/direction/transfer visible; no ambiguous count. |
| F13 | End confirmation across a fold | Cancel preserves session; confirm ends exactly once. |
| F14 | Arrival and Finish across a fold | Accurate terminal state; no accidental unfinished termination. |
| F15 | GO portrait first viewport | Instruction and upcoming journey context readable; timeline not reduced to a title. |
| F16 | GO tabletop / short-height fallback | Map or honest fallback useful; every essential action reachable. |
| F17 | Manual map pan/zoom/rotation, then fold | Camera intent and selection survive without forced recenter. |
| F18 | Fit-route/follow after bounds change | Deliberate framing avoids actual obstructions and controls. |
| F19 | Multiline route and transfers | Correct per-leg colors and distinct transfer/current markers. |
| F20 | Offline or delayed map imagery | Clear usable fallback; no unlabeled blank-map acceptance. |
| F21 | Home interchange and selected direction | Correct grouped lines/directions; no duplicate polling. |
| F22 | Explore search/filter/detail and Plan entry | State retained; Plan/Ariadne/navigation controls do not overlap. |
| F23 | Airport service/day/direction and alternatives | Same selection and truthful source data across reflow. |
| F24 | Saved journey/departure edit and resume | Correct selected item, confirmation, and existing session. |
| F25 | Fare form/picker during fold | Endpoints/draft retained; summary remains readable. |
| F26 | Ariadne typing and in-flight response | Draft/conversation/focus retained; request count unchanged. |
| F27 | Settings/onboarding/What's New/dialog | Current step/state preserved; no unsolicited replay. |
| F28 | Background/foreground host reconstruction | Full task state restored; original rendering recovery still works. |
| F29 | Process termination and relaunch with GO | Restore intended session; reconcile activity; no stale duplicate. |
| F30 | Notification/deep-link entry during active task | Correct destination and session identity; no duplicate navigation stack. |
| F31 | Largest text + each posture | No clipped essential labels/actions; sensible single-pane fallback. |
| F32 | EN/EL/SQ/IT representative long content | Correct translation, wrapping, semantics, and selection. |
| F33 | VoiceOver/TalkBack and keyboard | Logical order and focus, no hidden duplicates, usable actions. |
| F34 | Dark/light, contrast, Reduce Motion | Measured contrast; no essential meaning depends on color/motion. |
| F35 | Native regions vs equivalent injected fixtures | Correct coordinate handling; native path actually exercised. |
| F36 | Multiple/partial occlusions and cutouts | Every essential control/text avoids obstruction; safe single rect honored. |
| F37 | Android rail/insets/density + fold | Correct measured hinge location; no duplicate gap or unsafe content. |
| F38 | Android opaque hinge and non-occluding fold | Correct separation/occlusion semantics in flat and half-open states. |
| F39 | Ordinary phone, tablet, resized scene | Existing capabilities and sensible navigation retained. |
| F40 | Ten transition cycles, Plan and GO | Stable owners/subscriptions/session; no accumulating layout damage. |

Run the six posture scenarios across the main tasks. Use risk-based combinations for the full language/appearance/text-scale cross-product, but explicitly cover the highest-risk combinations: large Greek labels with keyboard, dark GO portrait, horizontal occlusion with GO controls, and Android rail plus hinge. Record the combinations actually tested; do not label an unexecuted permutation passed.

### Evidence package

Create `docs/verification/foldables/<date>/` with:

- `RESULTS.md`: commit, toolchain, build configuration/native flag, destinations, scenario matrix, exact results, failures repaired, and any genuine remaining limitations.
- `captures/`: named by platform, feature/state, posture, language, appearance, and text size; full aspect ratio, no misleading crop.
- `transitions/`: available short recordings demonstrating continuity and absence of blank/duplicated content.
- `logs/`: relevant test/build logs and privacy-safe state/side-effect assertions.
- `PERFORMANCE.md`: repeatable before/after conditions and measured observations, including simulator limitations.

Do not copy historical pass counts into this package as current results. Do not silently replace a failed capture with an empty state. If a scenario is blocked, identify the exact unavailable capability, attempted remedies, and affected criterion; continue the independent work. Only a real blocker justifies an incomplete result.

## 8. Ordered implementation sequence

### Phase 0 — establish reproducible evidence

1. Read this plan, current source, product/design rules, and applicable repository instructions. Recheck git status and preserve other changes.
2. Inventory Xcode/SDK/runtime/destinations, actual scheme, Android toolchain/AVDs, and current dependency versions. Inspect simulator-tool session defaults before using build/run/test operations.
3. Build baseline native and fallback iOS configurations with distinct derived-data/output locations; build Android. Record the native branch activation and any pre-existing failures.
4. Reproduce the screenshot issues in the actual app, especially portrait GO, horizontal hinge, populated Plan, native bars, and background host replacement. Establish candidate/approved snapshot separation before running an auto-writing test suite.
5. Freeze/replay data inputs and add focused diagnostics needed for geometry and continuity verification.

**Exit:** repeatable real-app fixtures, known build paths, baseline captures, and an honest current-status matrix.

### Phase 1 — geometry, native shell, and continuity

1. Fix multiple-region/clipping/minimum-size policy and platform coordinate conversion.
2. Render returned safe rectangles correctly in single, paired, and stacked cases; remove duplicate gap/inset calculations.
3. Stabilize scene/task/session/presentation ownership, including iOS host replacement and Android lifecycle restoration.
4. Verify native iOS arrangement behavior and system navigation in actual scenes; retain a tested fallback.
5. Add focused policy/restoration tests and prove side-effect invariants with frozen inputs.

**Exit:** correct geometry and no lost task/session across transitions; no new visual composition built on an unsafe foundation.

### Phase 2 — GO excellence

1. Decompose instruction, timeline, map, controls, and trust presentation.
2. Implement cover, landscape, portrait, book, and tabletop compositions using actual available regions.
3. Fix per-leg map colors, camera intent, marker semantics, readiness/fallback, and incomplete-journey confirmation.
4. Reconcile persisted journey, location, notification, and activity ownership across reflow/background/process restoration.
5. Run F09–F20, F28–F29, and relevant accessibility/region cases; visually inspect and repair captures.

**Exit:** a rider can immediately understand the next action and its route context in every supported posture.

### Phase 3 — Plan and the map/detail system

1. Implement populated route comparison and selected-route context with stable identity.
2. Make picker/keyboard, sheet/pane, and selection transitions continuous.
3. Apply shared map/detail conventions to network Map and Explore, keeping routing semantics unchanged.
4. Run F04–F08, F17–F18, F22, and keyboard/large-text cases.

**Exit:** unfolding improves the actual route decision; no lost query, selection, or map exploration.

### Phase 4 — complete the app

1. Adapt Home, Airport/departures, saved items, fares, Ariadne, settings, onboarding, and secondary presentations according to their screen contracts.
2. Preserve actual branding, native controls, line data, offline truth, and all four languages.
3. Complete corresponding F21–F27 cases and every primary-tab posture transition.

**Exit:** the experience is coherent beyond the demonstration screens.

### Phase 5 — Android parity and platform polish

Implement the Android geometry bridge and screen paths alongside the earlier phases where practical; this phase is the explicit parity gate, not permission to defer Android indefinitely. Verify foldable and opaque-hinge devices, stacked Plan, GO route context, navigation rails, keyboard, state restoration, and font scaling. Preserve native platform conventions and existing Android interchange/map improvements.

**Exit:** Android meets the same task and safety invariants with platform-appropriate layouts.

### Phase 6 — final verification and handoff

1. Run relevant unit/build checks, complete the actual-app matrix, inspect all final captures, and repair remaining failures.
2. Run accessibility, language, appearance, offline, lifecycle, and repeated-transition passes.
3. Profile identified problem flows, repair regressions, and rerun affected checks. Avoid unrelated test expansion once the relevant evidence is sound.
4. Update the readiness summary and evidence package. Summarize actual changes, tested configurations, and residual hardware-only checks.
5. Deliver the working changes and evidence without release/deployment actions.

**Exit:** every applicable simulator-testable criterion is verified; any genuine unresolved blocker is explicit and the result is labeled incomplete rather than finished.

## 9. Build and test starting points

These are starting points to verify against the current checkout, not claims of successful execution. Prefer the configured simulator tools when shell CoreSimulator access is restricted. Do not change the user's global Xcode selection merely to run a test; scope toolchain selection to the invocation when needed.

```sh
xcodebuild -version
xcodebuild -list -project iosApp/Syrmos.xcodeproj
xcodebuild -showsdks
xcrun simctl list devices available
```

Use the verified scheme and an available destination, for example after confirming the destination still exists:

```sh
xcodebuild build \
  -project iosApp/Syrmos.xcodeproj \
  -scheme 'Syrmos - Athens Rail Times' \
  -configuration Debug \
  -destination 'platform=iOS Simulator,id=92B2C61A-0B60-4A63-8B24-85BAC609AC8B' \
  -derivedDataPath /tmp/syrmos-foldables-native \
  'SWIFT_ACTIVE_COMPILATION_CONDITIONS=$(inherited) DEBUG SYRMOS_DUO_SDK'
```

Inspect effective build settings to confirm preservation of existing flags. Run focused test selections first, then the relevant regression suite. **Before running `DuoSnapshotTests`, separate candidate output from source baselines**, because the current helper writes image files directly. Build the fallback separately without the Duo flag, using a compatible toolchain/runtime, and label its evidence accordingly. Do not regenerate the Xcode project from stale `project.yml` metadata without reconciling it with the current project.

For Kotlin/Android, inspect actual tasks and installed emulator profiles:

```sh
./gradlew :core:common:tasks --all
./gradlew :composeApp:tasks --all
adb devices -l
emulator -list-avds
```

Select the existing common-policy, domain, Android unit, and compile/build tasks from that inventory. Use actual emulator interaction for posture/lifecycle/navigation verification. A common test JVM/host run alone cannot prove Android hinge integration or map behavior.

## 10. Definition of done

- [ ] All main screens follow the screen contracts, with useful added context on expanded displays.
- [ ] Native iPhone Duo code is built and exercised; fallback and ordinary phone/tablet behavior remain correct.
- [ ] Actual scene geometry and every relevant active obstruction are handled without clipped essential UI.
- [ ] GO's current instruction, next transfer, controls, line colors, source truth, and map/fallback are clear.
- [ ] Plan supports populated comparison/detail/map, keyboards, and preserved selection across transitions.
- [ ] Navigation, drafts, scroll anchors, map camera, Ariadne work, and presentations survive reflow and documented restoration paths.
- [ ] Exactly one active journey, location ownership path, activity, and intended notification/request action exists.
- [ ] Android folding/dual-screen, rail/inset, stacked, and lifecycle cases pass on emulators.
- [ ] Accessibility, all supported languages, light/dark, offline, and permission states are verified.
- [ ] Every changed final screenshot was visually inspected; failures were repaired and affected tests rerun.
- [ ] Readiness documentation links to fresh evidence and distinguishes composition tests, native runtime tests, and hardware-only checks.
- [ ] No invented data, fabricated passes, unreviewed baseline replacement, or silent release/deployment occurred.

The intended result is a transit app that feels deliberately designed for both surfaces: glanceable when closed, informative when open, trustworthy during a journey, and continuous throughout the change.

## 11. Current primary references

Consult these again during implementation if APIs or SDKs change. Product requirements above are Syrmos-specific design decisions; platform APIs must be verified against the installed SDK/dependencies.

- [Apple: iPhone Duo developer overview](https://developer.apple.com/iphone-duo/) — current platform entry point and supporting toolchain material.
- [Apple HIG: Designing for iPhone Duo](https://developer.apple.com/design/human-interface-guidelines/designing-for-iphone-duo) — reviewed in its rendered page; informs adaptive native navigation, safe areas, and content continuity.
- [Apple: Design for iPhone Duo](https://developer.apple.com/videos/play/tech-talks/111466/) — native surfaces, flexible layouts, and posture-aware composition.
- [Apple: Prepare your app for iPhone Duo](https://developer.apple.com/videos/play/tech-talks/111461/) — simulator device controls, scene-based geometry, and adapting existing apps.
- [Apple: Raise the bar on iPhone Duo](https://developer.apple.com/videos/play/tech-talks/111462/) — native bars/toolbars, action presentation, and system placement.
- [Apple: Strike a pose with your app on iPhone Duo](https://developer.apple.com/videos/play/tech-talks/111463/) — arrangement and reserved-region reference. Discovery excerpts and current repository integration were reviewed; verify exact SDK signatures locally before editing.
- [Apple: Expand your app across displays and scenes](https://developer.apple.com/videos/play/tech-talks/111464/) — scene/display context and continuity; do not infer unsupported simultaneous-display capabilities.
- [Android: Make your app fold-aware](https://developer.android.com/develop/adaptive-apps/guides/foldables/make-your-app-fold-aware) — FoldingFeature state, bounds, orientation, separation, and occlusion; check dependency availability for newer convenience APIs.
- [Android: Get started with adaptive apps](https://developer.android.com/develop/adaptive-apps/guides/get-started-with-adaptive-apps) — adaptive navigation and pane patterns.
- [Android: Adaptive app quality guidelines](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality) — relevant device, resizing, accessibility, and interaction verification guidance.
- [Android: Configuration changes and continuity](https://developer.android.com/guide/topics/large-screens/configuration-and-continuity) — retain task state through configuration and window changes.
