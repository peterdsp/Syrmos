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
    @Published private(set) var lastRailPulseSignal: String?

    func activate() {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    func submitRailPulse(_ signal: String) {
        lastRailPulseSignal = signal
        let session = WCSession.default
        guard session.activationState == .activated else { return }
        let payload: [String: Any] = [
            "railpulse_signal": signal,
            "railpulse_epoch": Date().timeIntervalSince1970,
        ]
        if session.isReachable {
            session.sendMessage(payload, replyHandler: nil, errorHandler: nil)
        } else {
            session.transferUserInfo(payload)
        }
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
