import Foundation
#if canImport(WatchConnectivity)
import WatchConnectivity
#endif

/// iPhone side of the Syrmos Apple Watch bridge. Sends the current departures
/// snapshot to the Watch over WatchConnectivity so the Watch app and its
/// complications mirror the phone. Activates lazily the first time we actually
/// have something to push (when the user starts tracking a train), so the
/// shipping app carries no always-on session when the Watch app isn't in use.
///
/// The payload is a plain JSON dictionary under the "snapshot" key, whose shape
/// matches the Watch target's `WatchSnapshot` / `WatchDeparture` (same field
/// names), so the two decode each other without a shared module.
final class WatchSessionManager: NSObject, @unchecked Sendable {
    static let shared = WatchSessionManager()

    private struct Payload: Codable {
        struct Dep: Codable {
            let lineId: String
            let destination: String
            let minutes: Int
            let time: String
        }
        let stationName: String
        let departures: [Dep]
        let updatedEpoch: Double
    }

    #if canImport(WatchConnectivity)
    private var didActivate = false

    private func activateIfNeeded() {
        guard WCSession.isSupported(), !didActivate else { return }
        let session = WCSession.default
        session.delegate = self
        session.activate()
        didActivate = true
    }
    #endif

    /// Push the latest departures to the Watch. No-op on devices without
    /// WatchConnectivity or without a paired, installed Watch app.
    func push(stationName: String, departures: [(lineId: String, destination: String, minutes: Int, time: String)]) {
        #if canImport(WatchConnectivity)
        activateIfNeeded()
        let session = WCSession.default
        guard session.activationState == .activated else { return }
        let payload = Payload(
            stationName: stationName,
            departures: departures.map { .init(lineId: $0.lineId, destination: $0.destination, minutes: $0.minutes, time: $0.time) },
            updatedEpoch: Date().timeIntervalSince1970
        )
        guard let data = try? JSONEncoder().encode(payload) else { return }
        let dict: [String: Any] = ["snapshot": data]
        // Latest-state, coalesced by the system.
        try? session.updateApplicationContext(dict)
        // Immediate delivery when the Watch is reachable.
        if session.isReachable {
            session.sendMessage(dict, replyHandler: nil, errorHandler: nil)
        }
        #endif
    }
}

#if canImport(WatchConnectivity)
extension WatchSessionManager: WCSessionDelegate {
    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {}
    #if os(iOS)
    func sessionDidBecomeInactive(_ session: WCSession) {}
    func sessionDidDeactivate(_ session: WCSession) {
        // Re-activate for a newly paired Watch.
        WCSession.default.activate()
    }
    #endif
}
#endif
