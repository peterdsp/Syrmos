import SwiftUI

/// The Syrmos Apple Watch app (1.2 "Widgets Everywhere"). A glanceable
/// next-three-departures view for the pinned station, mirroring the iOS Live
/// Departures large widget at Watch scale. Data arrives from the iPhone over
/// WatchConnectivity (`WatchConnectivityProvider`) and falls back to a bundled
/// snapshot when the phone is unreachable.
///
/// TARGET WIRING (must be done once in Xcode, cannot be created headlessly —
/// same as the widget extension target). See SyrmosWatch/README-wiring.md.
@main
struct SyrmosWatchApp: App {
    @WKApplicationDelegateAdaptor private var delegate: WatchAppDelegate

    var body: some Scene {
        WindowGroup {
            WatchContentView()
                .environmentObject(WatchConnectivityProvider.shared)
        }
    }
}

/// Activates the WatchConnectivity session as soon as the app launches so the
/// phone can push the tracked departure and departures snapshot.
final class WatchAppDelegate: NSObject, WKApplicationDelegate {
    func applicationDidFinishLaunching() {
        WatchConnectivityProvider.shared.activate()
    }
}
