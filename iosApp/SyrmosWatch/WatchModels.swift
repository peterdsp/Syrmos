import SwiftUI

/// Self-contained line tokens for the Watch target, mirroring the iOS
/// `SyrmosLineTokens` values. The Watch app is its own target, so it carries its
/// own copy rather than depending on the app's design-system module.
enum WatchLineTokens {
    static func color(for lineId: String) -> Color {
        switch normalize(lineId) {
        case "M1": return Color(red: 0.19, green: 0.62, blue: 0.31)
        case "M2": return Color(red: 0.85, green: 0.20, blue: 0.20)
        case "M3": return Color(red: 0.10, green: 0.36, blue: 0.72)
        case "T6", "T7": return Color(red: 0.95, green: 0.55, blue: 0.11)
        default: return Color(red: 0.42, green: 0.30, blue: 0.66)
        }
    }
    static func label(for lineId: String) -> String { normalize(lineId) }
    static func normalize(_ id: String) -> String { id.hasPrefix("M3") ? "M3" : id }
}

/// A single departure row shared across the Watch views and complications.
/// Encoded/decoded as the WatchConnectivity payload the phone sends.
struct WatchDeparture: Codable, Identifiable, Hashable {
    var id: String { "\(lineId)-\(time)-\(destination)" }
    let lineId: String
    let destination: String
    /// Minutes away at the moment the phone built the snapshot. The Watch
    /// recomputes live from `targetEpoch`, so this is only a fallback for
    /// older payloads that carry no absolute target.
    let minutes: Int
    let time: String
    /// Absolute Unix epoch seconds of the departure. The Watch ticks its own
    /// countdown against this every second (see `TimelineView`) so the rows
    /// stay live without waiting for a phone push. Optional so a snapshot
    /// encoded before this field existed still decodes.
    let targetEpoch: TimeInterval?

    init(lineId: String, destination: String, minutes: Int, time: String, targetEpoch: TimeInterval? = nil) {
        self.lineId = lineId
        self.destination = destination
        self.minutes = minutes
        self.time = time
        self.targetEpoch = targetEpoch
    }

    /// Live minutes away as of `now`, computed from the absolute target when
    /// present. Falls back to the snapshot's `minutes` for legacy payloads.
    /// Rounds up so a train 2m10s out reads "3m", matching the projector.
    func liveMinutes(now: TimeInterval) -> Int {
        guard let targetEpoch else { return minutes }
        let secs = targetEpoch - now
        if secs <= 0 { return 0 }
        return max(0, Int(ceil(secs / 60)))
    }
}

struct WatchSnapshot: Codable, Equatable {
    let stationName: String
    let departures: [WatchDeparture]
    let updatedEpoch: Double
    let language: String?
    let liveTrainCount: Int?

    var isLiveDataFresh: Bool {
        guard let count = liveTrainCount, count > 0 else { return false }
        return (Date().timeIntervalSince1970 - updatedEpoch) < 300
    }

    init(stationName: String, departures: [WatchDeparture], updatedEpoch: Double, language: String? = nil, liveTrainCount: Int? = nil) {
        self.stationName = stationName
        self.departures = departures
        self.updatedEpoch = updatedEpoch
        self.language = language
        self.liveTrainCount = liveTrainCount
    }

    static let placeholder: WatchSnapshot = {
        let now = Date().timeIntervalSince1970
        return WatchSnapshot(
            stationName: "Syntagma",
            departures: [
                WatchDeparture(lineId: "M3", destination: "Doukissis Plakentias", minutes: 3, time: "17:47", targetEpoch: now + 3 * 60),
                WatchDeparture(lineId: "M3", destination: "Airport", minutes: 11, time: "17:55", targetEpoch: now + 11 * 60),
                WatchDeparture(lineId: "M2", destination: "Elliniko", minutes: 4, time: "17:48", targetEpoch: now + 4 * 60),
            ],
            updatedEpoch: now,
            language: "en",
            liveTrainCount: 28
        )
    }()
}

/// Shared snapshot store persisted to the watch App Group, written by the watch
/// app's WatchConnectivityProvider and read by the complications. In
/// WatchModels (a member of both watch targets) so both sides see it.
enum WatchComplicationStore {
    static let suite = "group.com.syrmosApp.watch"
    static func read() -> WatchSnapshot? {
        guard let d = UserDefaults(suiteName: suite)?.data(forKey: "snapshot") else { return nil }
        return try? JSONDecoder().decode(WatchSnapshot.self, from: d)
    }
    static func write(_ snapshot: WatchSnapshot) {
        guard let d = try? JSONEncoder().encode(snapshot) else { return }
        UserDefaults(suiteName: suite)?.set(d, forKey: "snapshot")
    }
}
