# Syrmos on iPhone Duo: six postures, one journey, award-level craft

An implementation prompt for the agent that will take the shipped Syrmos design
and make it feel designed for the iPhone Duo in every physical posture, end to
end, at the quality bar of an Apple Design Award finalist. It builds on, and does
not replace, `docs/plans/ANDROID-FOLDABLES-IPHONE-DUO-IMPLEMENTATION-PROMPT.md`
and the evidence record `docs/design/FOLDABLE-READINESS.md`.

The reference sheet for this prompt is a six-icon posture strip: a closed phone
with a camera dot, a tent, a flat open landscape slab, a half-open book, a flat
open portrait slab, and a laptop. Every posture in that strip is a scene Syrmos
must be beautiful and correct in, and every transition between two of them is a
moment the rider will feel.

---

## 0. How to use this prompt

You are the designer and the engineer. Deliver both the design decisions and the
working, tested code that realises them, in small reviewable pull requests.

Read, in this order, before touching anything:

1. `docs/plans/ANDROID-FOLDABLES-IPHONE-DUO-IMPLEMENTATION-PROMPT.md` (the
   parent contract: hierarchy, geometry policy, continuity, D01 to D24).
2. `docs/design/FOLDABLE-READINESS.md` (what has landed, what is gated, and the
   evidence levels; you will extend this file).
3. `docs/design/REDESIGN_DESCRIPTION.md` and `docs/design/syrmos-brand-board.svg`
   (the Calm Signal design language you are refining, not replacing).
4. `iosApp/iosApp/Features/Assistant/PlanFlow.swift` (the `SyrmosArrangement`
   two-pane container, `minPairWidth = 640`, and the `SYRMOS_DUO_SDK` gate).
5. `iosApp/iosApp/Features/Go/GoJourneyView.swift` (GO two-pane with the live
   route-map companion).
6. `iosApp/iosAppTests/DuoSnapshotTests.swift` and
   `iosApp/iosAppTests/__DuoSnapshots__/` (the render recipe that works on the
   Duo, and the five committed reference images).
7. `core/common/src/commonMain/kotlin/com/syrmos/core/common/layout/AdaptiveWorkspace.kt`
   and its test `AdaptiveWorkspaceTest.kt` (the shared, platform-neutral policy
   and the cross-platform fixtures, including the three Duo geometry cases).
8. `composeApp/src/commonMain/kotlin/com/syrmos/app/layout/AdaptiveWorkspaceCompose.kt`,
   `composeApp/src/commonMain/kotlin/com/syrmos/app/platform/ReservedRegions.kt`,
   `composeApp/src/commonMain/kotlin/com/syrmos/app/screen/PlanScreenRoute.kt`
   (the Android side of the same policy; keep the two in lockstep).

Precedence: this prompt decides posture design and the award bar. The parent
prompt decides the engineering contracts (geometry policy, continuity, evidence
gates) unless a rule here explicitly tightens it. The readiness record is the
only place you claim evidence.

Non-negotiable working rules:

- Never use an em dash or an en dash anywhere: not in prose, code comments,
  commit messages, or PR text. Use a comma, a period, a colon, or "to" for ranges.
- Every change ships with tests and the tests are run. A build that compiles is
  not verification. State honestly which evidence tier each claim sits on.
- Never fake a gated API. If the system does not report something (a hinge
  angle, which half faces the rider in tent), do not synthesise it from a
  device name, a diagonal, or an accelerometer guess. Design so the outcome is
  right without that information, and record the gap.
- Commits carry no AI trailers. The repository owner is the only author.
- The web app is desktop only and is out of scope for pane work.
- Do not redraw the Syrmos or Ariadne marks, do not add purchases, do not add
  camera permission, do not add a second app shell or a posture switcher.

---

## 1. The bar: what "award-winning" means for this work

Adjectives are not a bar. Each criterion below has an observable gate. You pass
the criterion only when the gate has evidence in the readiness record.

| Criterion | What a judge is looking for | Syrmos gate |
| --- | --- | --- |
| Delight | One moment people remember | The unfold moment: a journey chosen on the cover display gains its live map on the inner display within one frame of layout settling, with the same selection, scroll anchor, and status. Recorded as the T1 transition (section 4). |
| Interaction | Controls where hands are | In every posture, primary actions sit in the reachable region for that posture (bottom half in laptop and portrait, near the thumb on the cover, never under the division). Verified per posture in the P-matrix (section 11). |
| Inclusivity | Works at AX5 text, VoiceOver, RTL | Every posture layout survives the largest accessibility text size by yielding optional panes before clipping essential text. VoiceOver reads panes in a logical order in every posture. D21 to D23 pass on the Duo runtime. |
| Visuals | One design language at every size | Calm Signal tokens, line colours, and status vocabulary are identical on the cover and inner displays. No pane is a stretched phone screen. No seam, shadow, or blur bridges the division. Snapshot references per posture. |
| Innovation | Uses the hardware honestly | The horizontal division is used as a real design surface (tray table GO, glance mode) driven by reported regions, not by posture names. The cover display and Live Activity behave as one continuous story with the inner display. |
| Trust | Transit truth is never decorated | Live, Scheduled, Estimated, Cached, Offline labels stay readable in every pane. Folding never changes a status word, restarts a fetch, or creates a phantom vehicle. D24 passes. |
| Performance | Folding feels physical | Layout settles within one animation (target 250 ms, no oscillation) across every transition; no dropped frames on a fold with an active map. Instruments trace attached. |
| Craft under constraint | Right on iOS 17 and on 27.1 | The native `ArrangementView` path and the `HStack` fallback produce the same information architecture. CI (Xcode 26.6) stays green. |

You may add criteria. You may not remove one.

---

## 2. Ground truth you build on (verified, do not re-derive)

Device geometry, measured on the booted Duo simulator:

| Display | Pixels @3x | Points | Role |
| --- | --- | --- | --- |
| Cover (closed) | 1398 x 2034 | 466 x 678 | Single column, one hand |
| Inner (open), portrait | 2007 x 2853 | 669 x 951 | Pairs today by width |
| Inner (open), landscape | 2853 x 2007 | 951 x 669 | Pairs today by width |

Already landed and merged to `master` (PR #191, #192, #193):

- Shared pure policy `AdaptiveWorkspacePolicy.resolve(...)` producing SINGLE,
  SIDE_BY_SIDE, or STACKED, pane rects, hinge clearance, and an adjustable
  divider. 23 tests, including the three Duo fixtures.
- iOS `SyrmosArrangement`: width-driven two-pane, native `ArrangementView` on
  iOS 27.1 behind `#if SYRMOS_DUO_SDK`, `HStack` fallback otherwise, single
  column below 640 pt. Adopted in Plan (query and saved | results) and GO
  (instruction | live route map plus timeline).
- iOS Duo snapshot tests rendering the real views through `UIHostingController`
  at the exact cover and inner sizes, light appearance pinned.
- Android: FoldingFeature adapter, Compose workspace, Plan two-pane, and
  continuity of Plan draft, GO session, map camera, and browse and detail state
  across recreation and process death.

Known constraints you must design around:

- On the real 27.1 Duo, `ArrangementView(...).arrangementViewStyle(.split)`
  splits the panes vertically by default and reserves a region on one edge.
  Control the axis with `SplitArrangementViewStyle.axes(_:)` and read
  `\.splitArrangementAxis`. The pre-27.1 fallback splits horizontally. Section 9
  makes the axis a per-task decision so both paths agree.
- `GeometryProxy.reservedRegions(kind:options:layoutDirectionBehavior:)` exposes
  `.division` and `.occlusion` regions with an active state and
  `.includeInactive`. There is no public hinge-angle or posture API. Confirm this
  again on the SDK you build with and write the result in the readiness record.
- `ArrangementView` and its modifiers are iOS 27.1 SDK symbols. `#available`
  gates runtime only. CI runs Xcode 26.6. Everything native stays behind
  `SYRMOS_DUO_SDK` and the CI-equivalent build with the 27.0 SDK must pass
  before every push (command in `docs/design/FOLDABLE-READINESS.md`).
- The Duo is a multi-display device. `simctl io screenshot` and the simulator
  MCP fail on it. Visual evidence comes from XCTest renders
  (`UIHostingController` to `UIImage`), which is the committed recipe.
- No Duo hardware in this environment. Physical-quality claims stay Pending.

App facts: iOS tabs are Home, Explore, Map, Departures, More. Languages are
English, Greek, Albanian, Italian. Deployment target is iOS 17.0. Plan and GO
are presented full screen on regular width and as sheets on compact width,
both bound through one `showPlan` flag in `LinesView`.

---

## 3. The six postures, read from the reference strip

The code never branches on a posture name. It consumes the app window size,
orientation, size class, safe areas, keyboard, and the reported reserved regions
(kind, active state, frame). Posture names label design intent and test
fixtures. Where the same reported geometry can come from two postures, the
design must be right for both.

| # | Icon | Physical posture | What the system reports (confirm each on the Duo) | Syrmos scene name | Arrangement |
| --- | --- | --- | --- | --- | --- |
| P1 | Closed phone, camera dot | Folded shut, cover display active, one hand | 466 x 678 pt window, compact width, no regions | Pocket | Single column |
| P2 | Tent | Half folded, standing on its long edges, hinge on top, one inner half faces the rider | Inner window, horizontal division active; which half faces the rider is not reported | Platform glance | One self-sufficient glance region; the other region carries nothing essential |
| P3 | Flat open landscape | Fully open, held or laid wide | 951 x 669 pt, regular width, division inactive or absent | Atlas | Side by side, task pane left of the map |
| P4 | Book | Half open like a book, vertical division, held in two hands | Inner window, vertical division active | Timetable book | Side by side, panes aligned to the division, nothing bridges it |
| P5 | Flat open portrait | Fully open, held tall | 669 x 951 pt, regular width, division inactive or absent | Tall canvas | Stacked (map above, list below) by default; side by side only where both panes meet their minima |
| P6 | Laptop | Half open, lower half flat on a surface, upper half upright, horizontal division | Inner window, horizontal division active, both halves visible | Tray table | Stacked to the division: display above, hands below |

Transitions that are scenes in their own right:

| Id | From | To | The moment | Must hold |
| --- | --- | --- | --- | --- |
| T1 | P1 | P5 or P3 | The unfold: the chosen journey gains its map | Same selection, scroll anchor, status, GO position; map appears beside, not instead |
| T2 | P5 or P3 | P1 | The close: back to the pocket | The task continues in single column; GO continues in the Live Activity and Dynamic Island on the cover |
| T3 | P3 | P4 | Landscape flattens into a book | Panes snap to the division without oscillation; no leftover gutter when it flattens again |
| T4 | P5 | P6 | Portrait bends into a laptop | Display content rises above the division, controls and input settle below; the keyboard belongs to the lower half |
| T5 | P6 | P2 | The laptop is stood up as a tent | Only the glance region matters; nothing essential is lost when the far half is out of sight |
| T6 | Any | Any, with Split View or pinned video | The app shrinks with the system | Recompute from the window; never treat a short window as tabletop |

---

## 4. The signature journey: one demo, six postures

Design against this script. Record it as the acceptance video. It is the story
the award submission tells.

1. Pocket (P1). On the platform at Syntagma, one hand. Home shows the next
   departure toward the Airport with its status word. The rider taps Plan, sets
   Airport as the destination, and the recommended itinerary appears. Start GO.
2. The unfold (T1 into P5). On the train, the rider opens the phone. The same
   itinerary is now the left or upper pane and the live route map with the
   current-stop dot is the other. Nothing resets. The instruction is the same
   sentence, in the same language, with the same status.
3. Atlas (P3). Turned wide, the map takes the larger share; the leg timeline
   sits beside it. Tapping a stop on the map highlights it in the timeline and
   vice versa. The map padding respects the timeline pane so the current stop
   is never hidden under it.
4. Timetable book (P4). Half folded in two hands on a moving train: instruction
   and next action in one half, the map in the other, the division kept clear.
   Scrolling the timeline is continuous; there is no second feed.
5. Tray table (P6). At the airport express, the phone stands on the tray table.
   The map and the large next-stop countdown are upright above the division; the
   timeline and the End and recovery controls lie flat below, within reach.
6. Platform glance (P2). Waiting for the connection, the phone stands as a tent
   on a bench. A single glance region shows the next departure, its countdown,
   and its status word in large type readable from a metre away.
7. The close (T2 into P1). Pocketed again, GO continues. The cover shows the
   Live Activity with the same stop and the same status. Nothing was started
   twice, nothing was lost.

Every step above maps to a P-case or a T-case in section 11. If a step cannot be
recorded in this environment, the readiness record says so by id.

---

## 5. Per-posture design specification

Shared rules first:

- One hierarchy. Every posture renders the same feature state through the same
  components. Posture changes what is beside what, never what the app is.
- Pane minima at default Dynamic Type: task pane 300 pt, map or companion pane
  320 pt, timeline or list pane 280 pt. Raise minima with text size. If two panes
  cannot both meet their minima, stack; if stacking cannot meet them, single
  column with an explicit Show map or Focus journey control that remembers the
  rider's intent.
- Nothing bridges an active division: no control, label, divider handle, sheet,
  dialog, map attribution, or selected-station callout. An inactive division on a
  flat device leaves no permanent stripe.
- The map is a first-class pane. Its camera intent (manual pan, Follow, Fit) is
  owned by state, not by layout. A fold never resets the camera.
- Status words (Live, Scheduled, Estimated, Cached, Offline) are text, never a
  dot, in every pane and every posture.

### P1 Pocket (cover, 466 x 678 pt)

- Single column. The thumb zone (lower 40 percent) holds the primary action:
  Start GO, Next stop acknowledgement, Search, Plan.
- Home leads with the answer: next departure, station, direction, status. One
  screenful, no scrolling needed to see the answer.
- GO shows the current instruction as the headline (large title scale), the
  next action beneath, recovery and End reachable in the bottom bar.
- Live Activity and Dynamic Island present the same stop and status as the app,
  never stale data labelled Live.
- Navigation is the native tab bar. No rail, no sidebar.

### P2 Platform glance (tent, horizontal division, one visible half)

- Design one glance region that is complete on its own: the next departure or
  the current GO stop, a countdown in display-scale type, the status word, and
  the line chip in its line colour. No controls the rider would need to reach
  around the device for.
- Because the system does not report which half faces the rider, the glance
  region is the content region the arrangement designates as primary, and the
  secondary region carries only non-essential context (a muted map or nothing).
  Observe on the Duo which half the system keeps active and record it. If it
  cannot be observed here, mark P2 Pending with that reason; do not guess.
- Respect auto-dim and Reduce Motion: the countdown updates by text, not by a
  pulsing animation.

### P3 Atlas (flat landscape, 951 x 669 pt)

- Side by side. Task pane on the leading edge (Plan query and saved journeys,
  Explore list, Departures board, GO instruction and timeline), map or detail on
  the trailing edge taking the remaining width. Ratio 0.36 to 0.42 for the task
  pane; the map never falls below 480 pt here.
- A third inspector is not used at this width. Selected-route detail appears
  inside the task pane or as a native presentation anchored to it.
- The map's padding equals the task pane's visible width plus the pane gap so
  the selection and the route fit stay in the visible map, not under the pane.
- Toolbar: primary actions inline, secondary actions through the native
  overflow menu (`ToolbarOverflowMenu`, `visibilityPriority`) with runtime
  availability guards.

### P4 Timetable book (vertical division, two halves)

- Side by side, but the divider is the division: each pane fits its own half
  independently. No adjustable divider handle; dragging a pane edge across a
  real division is not offered.
- Static instructions and action clusters keep 16 pt clear of the division on
  both sides. Continuous content (a long departures list, the GO timeline) may
  scroll under an inactive division and must not be split into two feeds.
- When the book flattens (T3), the panes return to the P3 ratio without a
  leftover gutter and with the same list anchor and selection.

### P5 Tall canvas (flat portrait, 669 x 951 pt)

- Default is stacked: map or companion above, list or task below, split at
  about 0.45 with the map at least 360 pt tall. This is the reading posture and
  the rider's hands are at the bottom.
- Side by side is allowed only for tasks where both panes meet their minima at
  the current text size (669 pt gives two panes of 334 pt at 0.5). Plan qualifies
  at default type; GO and Explore prefer stacked. Make this a per-task decision
  in one place (section 9), with fixtures.
- Today the shared policy pairs the inner display in both orientations by
  width. Extend it so orientation and task select the axis; keep the existing
  fixtures green and add the new ones.

### P6 Tray table (laptop, horizontal division, both halves visible)

- Stacked to the division. Upper half: the thing you look at (map, route
  overview, large countdown, current instruction). Lower half: the things you
  touch (timeline, controls, query fields, keyboard).
- Plan in this posture: results and preview map above, the editable query and
  the keyboard below. The keyboard belongs to the lower half and must not push
  the map out of the upper half.
- GO in this posture: map and next-stop countdown above; timeline, recovery, and
  End below. End keeps its confirmation. No action fires because the device was
  folded.
- If the usable lower region becomes too short (system bars, keyboard), collapse
  the supporting content first and keep the current action visible.

---

## 6. Features across the postures

Implement through the real destinations and data. No competing shell.

- Home (Now). Pocket: the answer first. Atlas and Tall canvas: the answer beside
  a map with the selected station and nearby context; tapping a map station
  updates the answer, selecting a row highlights the map. Glance: the answer at
  display scale. No duplicate station pickers across panes.
- Plan. Pocket: the existing sequence. Atlas: query and saved | results with
  preview map. Tall canvas: side by side at 0.5 if minima hold, else stacked.
  Tray table: results above, query and keyboard below. Selecting an option
  updates the map and leg detail without starting GO. Start GO stays deliberate.
- GO. Pocket: instruction and next action. Atlas and Book: instruction and
  timeline | live route map. Tall canvas and Tray table: map above, timeline and
  controls below. Glance: current stop, countdown, status. Folding never begins,
  advances, reroutes, cancels, or confirms a journey (D16).
- Map. The camera is state. Follow, Fit, and manual intents survive every
  transition (D15). Controls and attribution stay in the visible, non-divided
  area. The Esri gray overlay and line-colour polylines are shared with GO.
- Explore and station or line detail. List | map or detail on wide; stacked on
  Tall canvas; filters and list position survive every transition. Canonical
  station ids only; repeated names such as Syntagma are separate intentional
  contexts.
- Departures and Airport. Board | selected service or airport connection
  context. Long boards scroll inside a readable pane. The Airport hub keeps its
  live OASA bus ETAs and never shows a placeholder for an absent route.
- Fares. Form beside result and notes where it fits; official links only.
- Ariadne. Compact keeps its presentation; wide may dock it as a contextual
  pane. One session, one composer draft, no resend or model restart on a fold
  (D19).
- More, settings, onboarding, dialogs, errors, empty states. Bounded forms,
  native presentation adaptation, nothing essential under a division (D20).
- Live Activity and Dynamic Island. The cover-display continuation of GO: same
  stop, same status vocabulary, same language (D07).

---

## 7. Visual system on the inner display

Calm Signal, unchanged in spirit, tuned for 669 and 951 pt.

- Typography. Native text styles with Dynamic Type. Current-action headline at
  Large Title in panes wider than 480 pt and at Title 1 otherwise; glance
  countdown uses a display scale (about 64 pt at default type) with monospaced
  digits. Body at Body, never smaller than Subheadline for status words.
- Spacing. 8 pt rhythm, 16 to 24 pt pane padding, 16 pt pane gap on a plain
  window, division-width gap on a divided one. Corners 16 to 24 pt on major
  surfaces. Custom touch targets 44 pt minimum.
- Panes. A pane has one title, one scroll model, and one set of actions. Two
  panes never show the same title or the same navigation shell (D01).
- Division treatment. No glass, blur, shadow, or gradient crosses an active
  division. Backgrounds may be continuous under an inactive one.
- Map. Quiet base, line-colour semantics, an emphasised current-stop dot,
  padding computed from visible panes and occupied regions. Attribution always
  visible and never under the division.
- Colour and appearance. Light and dark from the shared tokens, Reduce
  Transparency fallbacks opaque, Increase Contrast honoured, RTL correct without
  manual mirroring (D23).
- Content. Existing localisation for all four languages; the longest Greek and
  Albanian strings are the layout fixtures, not English.

---

## 8. Motion choreography

- Unfold and close. Content that exists in both postures moves, content that
  is new appears beside it. Use matched geometry for the itinerary card and the
  current-stop marker where practical. One animation, about 250 ms, settled
  before the next region report; no second relayout.
- Division appearing or disappearing. Apply hysteresis so a slow move through
  flat, book, flat (D05) does not oscillate: react to the active state change,
  not to intermediate frames.
- Keyboard. On Tray table the keyboard rises in the lower half only; the upper
  half does not animate.
- Reduce Motion. Cross-fade instead of movement; no decorative motion ever
  required to understand state.
- Budget. No dropped frames during a fold with a live map; profile with
  Instruments and attach the trace.

---

## 9. iOS engineering contract

Evolve what exists; do not add a parallel system.

1. `SyrmosArrangement` grows an explicit axis decision. Input: available size,
   orientation, task kind (plan, go, explore, departures, home, assistant),
   reported regions, Dynamic Type category. Output: single, side by side, or
   stacked, plus the ratio. The decision is a pure Swift function with tests,
   and it mirrors the shared KMP policy's fixtures (same numbers, same
   outcomes). On 27.1 it drives `SplitArrangementViewStyle.axes(_:)` and
   `splitArrangementLayoutRatio`; on the fallback it drives `HStack` or
   `VStack`.
2. A reserved-region adapter reads `GeometryProxy.reservedRegions` for
   `.division` and `.occlusion` separately, normalises frames into the content
   coordinate space once, keeps kind and active state, and feeds custom
   overlays and map padding. Native containers keep owning paired content.
   Everything 27.1 stays behind `#if SYRMOS_DUO_SDK` and `if #available(iOS 27.1, *)`.
3. Navigation and arrangement are separate jobs. Tabs stay tabs. Evaluate
   `NavigationSplitView` only for hierarchy (Explore list and detail), never as a
   substitute for paired content.
4. Toolbars adopt `ToolbarOverflowMenu` and `visibilityPriority` with runtime
   guards; each secondary action reachable exactly once (D09, D10).
5. Continuity ownership. Plan draft and selection, GO session and position, map
   camera intent, Explore filters and scroll, Ariadne conversation and draft all
   live in state that outlives the arrangement and the scene host. Cover the
   `SceneDelegate` host-replacement path (D17, D18).
6. Presentation. Plan and GO stay full screen on regular width and sheets on
   compact width through the single `showPlan` flag. Sheets on a divided window
   fit a usable region and never bridge the division (D12).
7. Gates. Before every push: the CI-equivalent 27.0 SDK build succeeds, the full
   `iosAppTests` suite passes, and the 27.1 build with
   `SWIFT_ACTIVE_COMPILATION_CONDITIONS="SYRMOS_DUO_SDK"` passes on the booted
   Duo UDID. Record the numbers.

---

## 10. Android and shared policy parity

- The shared policy in `core/common` remains the source of the fixtures. Add
  posture fixtures P1 to P6 and transitions T1 to T6 there first (geometry,
  regions, task, font scale, expected arrangement), then make the Swift decision
  reproduce them. Run `:core:common:testDebugUnitTest`.
- Android foldables map to the same scenes: cover, book, tabletop (laptop),
  flat. Extend `PlanScreenRoute.kt` and the GO route to the stacked axis where
  the policy says so. No Android snapshot framework exists; the policy contract
  is the coverage there, and it must say so.
- Never change the web app through a shared token or breakpoint edit.

---

## 11. Verification and evidence

Extend `docs/design/FOLDABLE-READINESS.md` with a posture matrix. Each row has
an id, the exercise, the required outcome, Pass, Fail, or Pending, and the
artifact path. Synthetic fixtures satisfy policy rows only; native-runtime rows
need the Duo runtime; physical rows stay Pending here.

Snapshot references (XCTest `UIHostingController` renders, light appearance,
committed under `iosApp/iosAppTests/__DuoSnapshots__/`):

| Case | Size (pt) | Regions | Screens |
| --- | --- | --- | --- |
| P1 | 466 x 678 | none | Home, Plan, GO |
| P2 | 669 x 951 and 951 x 669 | horizontal division, active | Home glance, GO glance |
| P3 | 951 x 669 | none or inactive division | Plan, GO, Explore, Departures |
| P4 | 951 x 669 | vertical division, active | Plan, GO, Departures |
| P5 | 669 x 951 | none or inactive division | Home, Plan, GO, Explore |
| P6 | 669 x 951 | horizontal division, active | Plan with keyboard, GO |
| AX | each of the above | same | Largest accessibility text, longest Greek and Albanian strings |

Also required:

- Policy fixtures for every row above in both Kotlin and Swift.
- The D01 to D24 matrix re-run on the Duo runtime where the environment allows,
  with the T1 to T6 transitions recorded as sequences of renders.
- An Instruments trace for T1 and T3 with an active map.
- A VoiceOver pane-order audit per posture (D22) and a Reduce Motion run (D23).
- The CI-equivalent build log and the full test counts, quoted in the record.

Never mark Pass on a claim that was only compiled. If the Duo simulator cannot
render a posture (tent is the likely case), say so by id.

---

## 12. Delivery plan

Small PRs, each with tests, each leaving `master` shippable:

1. Policy: posture and transition fixtures in `core/common`, the Swift axis
   decision, parity tests. No UI change.
2. iOS reserved-region adapter and map padding, behind the gate, with renders.
3. Tall canvas and Tray table: stacked axis for GO and Explore; Plan keyboard
   placement in the lower half.
4. Timetable book: division-aligned panes, gutter-free flatten, hysteresis.
5. Platform glance: the glance region for Home and GO, countdown typography,
   Reduce Motion behaviour.
6. Cover continuity: Live Activity and Dynamic Island parity with the app,
   T1 and T2 continuity tests.
7. Toolbar overflow, VoiceOver order, AX renders, RTL check.
8. Android parity for the stacked axis in Plan and GO.
9. Readiness record update, evidence, demo recording of the signature journey.

---

## 13. The award package (deliverables beyond code)

- The signature journey recorded as a sequence of renders or a screen capture,
  one file per step, named by step and posture.
- A one-page narrative in `docs/design/` explaining the design decisions per
  posture in plain language, with the six-icon strip as its figure.
- The posture matrix with honest evidence tiers.
- Before and after renders for each posture.

---

## 14. Boundaries

- No hinge-angle heuristics, no accelerometer posture guessing, no device-name
  branching.
- No camera permission to exercise occlusion; use system test facilities or
  record D08 as Pending.
- No new glass layer behind content cards; no redrawn logos; no purchases.
- No web changes; no second navigation shell; no manual fold-mode switch beyond
  the optional Show map or Focus journey intent.
- No em dash, no en dash, anywhere.
