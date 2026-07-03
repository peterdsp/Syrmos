import Foundation
import WatchConnectivity
import WidgetKit

/// Receives the current departures snapshot from the iPhone over
/// WatchConnectivity and publishes it to the Watch UI. Uses both
/// `updateApplicationContext` (latest-state, coalesced) and `sendMessage`
/// (immediate when reachable). Falls back to the bundled placeholder until the
/// first real payload arrives.
final class WatchConnectivityProvider: NSObject, ObservableObject, WCSessionDelegate {
    static let shared = WatchConnectivityProvider()

    @Published private(set) var snapshot: WatchSnapshot = .placeholder

    func activate() {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    private func apply(_ payload: [String: Any]) {
        guard let data = payload["snapshot"] as? Data,
              let decoded = try? JSONDecoder().decode(WatchSnapshot.self, from: data) else { return }
        // Persist for the complications and refresh their timelines.
        WatchComplicationStore.write(decoded)
        WidgetCenter.shared.reloadAllTimelines()
        DispatchQueue.main.async { self.snapshot = decoded }
    }

    // MARK: WCSessionDelegate

    func session(_ session: WCSession, activationDidCompleteWith state: WCSessionActivationState, error: Error?) {
        if let ctx = session.receivedApplicationContext as [String: Any]?, !ctx.isEmpty {
            apply(ctx)
        }
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        apply(applicationContext)
    }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        apply(message)
    }
}
