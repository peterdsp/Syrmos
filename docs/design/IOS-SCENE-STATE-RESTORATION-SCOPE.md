# iOS scene state restoration: scope and design

Prepared 2026-09-28 against `/Users/peterdsp/git/Syrmos`. This is a scoping and
risk document for readiness item 12.1 #20 ("iPhone/iPad scene recovery and map
state"), currently Pending: "The iOS scene host replacement is not implemented;
GO restores from its persisted session on relaunch, other tabs do not." It is a
design proposal for approval, not a claim that any of this is built.

## 1. How the app hosts its scene today

`iosApp/iosApp/App/SyrmosApp.swift` deliberately abandons SwiftUI's `WindowGroup`.
`AppDelegate` vends a `SceneDelegate`; `SceneDelegate` owns the `UIWindow` and
hosts the SwiftUI tree in a `UIHostingController`. On every real background to
foreground edge (`sceneDidBecomeActive` after a prior `sceneDidEnterBackground`)
it **replaces `window.rootViewController` with a brand new hosting controller**
that mounts a fresh `RootView` and `ContentView`.

The reason is documented in the file: on iOS 26 the `WindowGroup` UIWindow's
`CAMetalLayer` can end up unrenderable after a lock/unlock, screenshot, control
center swipe, or app switcher cycle, showing a solid black or white screen.
Rebuilding the hosting controller forces a fresh view hierarchy on a healthy
layer. This rebuild is intentional and should be preserved.

Consequence for state: a cold launch and a background return both mount a fresh
`ContentView`. Anything that lives only in SwiftUI `@State` inside a tab is
discarded on that mount. Anything in `@AppStorage` / UserDefaults survives.

## 2. What survives today, and what does not

**Survives (UserDefaults / `@AppStorage`):**

- The selected tab: `@AppStorage("syrmos.selectedTab")` in `ContentView`.
- GO's active session: `GoJourneyViewModel.begin(resuming:)` restores position
  from `GoActiveJourneyStore` (UserDefaults backed via `GoActiveJourneyContract`).
- Saved journeys: `SavedJourneysStore` (UserDefaults, `SavedJourneyContract`).
- GO's `showCompactMap` flag.

**Discarded on every rebuild / cold launch (in-view `@State`):**

- Home (`Views/Home/HomeView.swift`): `navigationPath: NavigationPath`,
  `selectedNearbyId`.
- Explore (`Views/Lines/LinesView.swift`): `selectedRegion`, `selectedType`,
  `selectedLine`, `railPulseDestination`.
- Map (`Views/Map/MapView.swift`): `selectedFilter`, camera position, selected
  station.
- Departures (`Features/Timetables/TimetablesView.swift`): `dayOffset`,
  `selectedCity`, `selectedRoute`, `flightTime`, plus loaded departure arrays
  (these are recomputable, not state to persist).

So "other tabs do not restore" means: the user returns to each tab's root with
default selection and an empty navigation stack, even though the tab itself is
correct. GO is the exception only because it persists its session separately.

## 3. Why not `@SceneStorage`

`@SceneStorage` is the SwiftUI-native answer, but it is tied to UIKit scene state
restoration under the standard `WindowGroup` lifecycle, keyed to a view's place
in the scene hierarchy. This app replaces the hosting controller by hand and does
not use `WindowGroup`, so restoration keyed to the scene hierarchy is unreliable
here, and `@SceneStorage` does not cover a true cold launch or reboot for every
type. The mechanism already proven to work across this app's rebuild is explicit
UserDefaults persistence (selected tab and GO both rely on it). The design below
uses that, not `@SceneStorage`.

## 4. Proposed design

Give each tab the same shape GO already uses: a pure, unit-tested contract plus a
small persist-on-change / restore-on-mount step, backed by UserDefaults.

1. **Per-tab restoration contract (pure, testable).** For each tab, a small
   `Codable` struct capturing only its meaningful "place":
   - Home: `selectedNearbyId`, and at most one pushed destination (station or
     line id).
   - Explore: `selectedRegion`, `selectedType`, `selectedLine` id, and at most
     one pushed destination.
   - Departures: `selectedCity`, `selectedRoute`, `dayOffset`, `flightTime`.
   - Map: deferred (see scope boundaries) except the already-persisted GO camera.
   Each contract encodes/decodes to a single UserDefaults string, mirroring
   `GoActiveJourneyContract` / `SavedJourneyContract`. These get unit tests the
   same way (round-trip, invalid-input, validation), which is the part CI can
   verify without a device.

2. **Restore on mount, persist on change.** Each tab reads its contract in
   `init` / `.task` and writes it in `.onChange` of the relevant selection. Writes
   happen only on user-driven selection changes, never per frame, so the cost is
   a handful of UserDefaults writes.

3. **Validate on restore.** A restored id (station, line, pushed destination) is
   checked against the current seed before it is applied; if it no longer exists
   (seed changed, line removed) the tab falls back to its root. This prevents
   restoring onto a dead screen.

4. **Deep links win.** `DeepLinkRouter` already forces `selectedTab = .home` and a
   destination on an incoming link. Restoration must run before, and yield to, a
   pending deep link so a notification tap is never overridden by restored state.

5. **Freshness policy (product decision needed).** Restoring a deep push after the
   user has been away for hours can feel wrong (they may expect a fresh Home).
   Proposed default: always restore tab selection and per-tab selection, but only
   restore a pushed navigation destination when the app was backgrounded less
   than a chosen window (for example 30 minutes), recorded via a timestamp at
   `sceneDidEnterBackground`. This needs your call on the window and on whether
   deep pushes should restore at all.

## 5. Scope boundaries (explicitly out of this first pass)

- Transient scroll offsets, in-flight network results, ephemeral sheet/alert
  presentation state.
- Map camera and selected-station restoration on the Map tab. The map is the
  heaviest surface (live camera, MapLibre lifecycle) and deserves its own follow
  up; GO already restores its own compact-map camera.
- Arbitrary multi-level `NavigationPath` restoration. Restoring one meaningful
  pushed destination is robust; replaying a full arbitrary stack is fragile and
  low value.

## 6. Risks

1. **Stale restore onto an invalid screen.** Mitigation: id validation against the
   current seed on restore, fall back to root. Severity low with validation.
2. **Restoring feels intrusive.** Mitigation: the freshness policy in 4.5. Needs
   a product decision, not just code.
3. **Write cost on the hot rebuild path.** Mitigation: persist only on selection
   change, not in the rebuild path itself. The rebuild only reads.
4. **Conflict with deep links.** Mitigation: deep link takes precedence (4.4).
5. **Verification is partly device-gated.** The rebuild fires only on a real
   background edge, so end-to-end proof needs a simulator background/foreground
   cycle (doable on the booted iPhone Duo) plus a cold relaunch. The contracts
   themselves are fully unit-testable in CI; the wiring is verified by a manual
   simulator pass, recorded honestly in the readiness doc.
6. **Regression into the Metal-bug workaround.** The rebuild must not be weakened
   to make restoration easier. Restoration is layered on top of it, never in place
   of it.

## 7. Recommendation and phasing

Use explicit UserDefaults persistence, mirroring GO's contract plus store plus
unit-test pattern. Do not use `@SceneStorage` given the non-standard hosting.

Suggested phases, each shippable and independently verifiable:

- **A.** Restoration contracts for Home, Explore, Departures (pure, unit-tested).
  No behaviour change yet. CI-verifiable.
- **B.** Wire Departures (`selectedCity` / `selectedRoute` / `dayOffset`) and
  Explore selection. Lowest-risk, no navigation stack involved.
- **C.** Wire Home selection and single-level nav restore with id validation and
  the freshness gate. Simulator background/foreground + cold-launch verification.
- **D.** (Separate follow-up) Map camera and selected-station restoration.

Open decisions for you before Phase C:

1. Should a pushed navigation destination restore at all, or only tab plus
   in-tab selection?
2. If it restores, what background window (proposed 30 minutes)?
