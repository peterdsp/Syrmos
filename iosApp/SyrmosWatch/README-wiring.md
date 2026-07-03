# Apple Watch target wiring (B4)

The Watch app source in this folder is complete and **typechecks against the
watchOS SDK** (`xcrun --sdk watchsimulator swiftc -typecheck …` passes). The one
step that cannot be done headlessly — and that the repo already does in Xcode for
the widget extension — is creating the watchOS target in the Xcode project. Do it
once in Xcode; after that the normal scheme build embeds it.

## Files (already written)

- `SyrmosWatchApp.swift` — `@main` watchOS app (single-target `WKApplication`).
- `WatchContentView.swift` — next-three departures for the pinned station,
  mirroring the iOS Live Departures large widget at Watch scale.
- `WatchConnectivityProvider.swift` — receives the departures snapshot from the
  phone (`updateApplicationContext` + `sendMessage`), persists it for the
  complications, and reloads their timelines.
- `WatchModels.swift` — `WatchSnapshot` / `WatchDeparture` (the JSON shape the
  phone sends) and `WatchLineTokens` (line colors, mirroring `SyrmosLineTokens`).
- `SyrmosWatchComplications.swift` — corner (minutes), circular (line-color dot),
  rectangular (pill + minutes + destination) accessory complications.
- `Info.plist` — `WKApplication=YES`, `WKCompanionAppBundleIdentifier=com.syrmosApp.ios`.

The **iPhone side is already wired and building**: `WatchSessionManager` (app
target) activates a `WCSession` lazily when the user starts tracking a train and
pushes the next three trains on the tracked line; `DepartureTracking` calls it on
`track()` and on each minute change.

## Xcode steps (once)

1. File → New → Target → **watchOS → App**. Product name `SyrmosWatch`, bundle id
   `com.syrmosApp.ios.watchkitapp`, interface SwiftUI, language Swift. Uncheck the
   auto-created tests. When asked, set it as a companion to the iOS app.
2. Delete the auto-generated `ContentView.swift` / `App.swift` Xcode adds, then
   add every file in this folder to the new `SyrmosWatch` target
   (File Inspector → Target Membership). Point the target's `INFOPLIST_FILE` at
   this folder's `Info.plist`.
3. Add a **Widget Extension** target (watchOS) named `SyrmosWatchComplications`;
   move `SyrmosWatchComplications.swift`, `WatchModels.swift` into it (WatchModels
   is shared by both — tick both memberships). The complication reads the shared
   suite `group.com.syrmosApp.watch`; add that **App Group** to both watch targets.
4. Add the same App Group `group.com.syrmosApp.watch` capability so the app and
   complication share the persisted snapshot. (WatchConnectivity does not need an
   App Group; the group is only for handing the snapshot to the complication.)
5. Build the `SyrmosWatch` scheme, then the iOS scheme (which now embeds the watch
   app). Run on a paired Apple Watch: start tracking a train on the phone and the
   watch app + complications update within a second when reachable.

## Verification done here

- `swiftc -typecheck` of all `SyrmosWatch/*.swift` against WatchSimulator SDK: passes.
- iOS app (with `WatchSessionManager` + the `DepartureTracking` hook) builds green.
