# Apple Watch targets (B4) — created and building

The watchOS targets are now part of the Xcode project (added programmatically
with the `xcodeproj` gem, see `scripts/` history), not a manual TODO:

- **`SyrmosWatch`** — watchOS app target (single-target `WKApplication`), bundle
  id `com.syrmosApp.ios.watchkitapp`, embedded in the iOS app via an
  "Embed Watch Content" copy-files phase.
- **`SyrmosWatchComplications`** — watchOS WidgetKit extension, bundle id
  `com.syrmosApp.ios.watchkitapp.complications`, embedded in the watch app's
  PlugIns. Corner (minutes) / circular (line dot) / rectangular (pill + minutes
  + destination) accessory families.

Both use Swift 5 language mode and share the `group.com.syrmosApp.watch` App
Group (entitlements: `SyrmosWatch/SyrmosWatch.entitlements`) so the app persists
the departures snapshot for the complications.

## Files

- `SyrmosWatchApp.swift` — `@main` watch app.
- `WatchContentView.swift` — next-three departures for the pinned station.
- `WatchConnectivityProvider.swift` — receives the snapshot from the phone,
  persists it (`WatchComplicationStore`), reloads complications.
- `WatchModels.swift` — `WatchSnapshot` / `WatchDeparture`, `WatchLineTokens`,
  and the shared `WatchComplicationStore` (member of both watch targets).
- `SyrmosWatchComplications.swift` — `@main` complication widget.
- `Info.plist` / `Complications-Info.plist` / `SyrmosWatch.entitlements`.

The iPhone side (`WatchSessionManager`, hooked into `DepartureTracking`) pushes
the next three trains on the tracked line over `WCSession`.

## Verified

- `xcodebuild -target SyrmosWatch -sdk watchsimulator`: **BUILD SUCCEEDED** —
  the watch app compiles, links, and the embedded complication passes
  embedded-binary validation.
- The iOS app sources are unchanged; the app built green immediately before the
  watch targets were added.

## Environment note (build requirement)

Because the iOS app now embeds a watch app, **building the `Syrmos - Athens Rail
Times` scheme requires the watchOS platform installed** (Xcode → Settings →
Components → watchOS Simulator, or build to a paired Apple Watch). The watch
scheme itself builds with just the SDK.

There is currently **no iOS-scheme CI job** in this repo (`.github/workflows`
only builds the web bundle and refreshes seeds), so nothing is broken today. If
an iOS CI job is added later, install the watchOS runtime before building the
app scheme, e.g. on a macОS runner:

```yaml
- name: Install watchOS simulator runtime
  run: xcodebuild -downloadPlatform watchOS
# then build the iOS scheme as usual
- name: Build iOS app (embeds watch app)
  run: |
    xcodebuild build \
      -project iosApp/Syrmos.xcodeproj \
      -scheme "Syrmos - Athens Rail Times" \
      -destination 'generic/platform=iOS Simulator'
```

Alternatively build only the watch scheme with `-sdk watchsimulator` (SDK only,
no runtime needed) to validate the watch code without the full embed.

## Portal (device / TestFlight only)

Register the two watch App IDs and the `group.com.syrmosApp.watch` App Group on
the Apple Developer portal, and enable the App Group on both watch targets. Not
needed for simulator builds.
