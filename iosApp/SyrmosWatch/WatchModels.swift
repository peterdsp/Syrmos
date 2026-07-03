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
    let minutes: Int
    let time: String
}

struct WatchSnapshot: Codable, Equatable {
    let stationName: String
    let departures: [WatchDeparture]
    /// Epoch seconds the snapshot was produced, so the Watch can age it.
    let updatedEpoch: Double

    static let placeholder = WatchSnapshot(
        stationName: "Syntagma",
        departures: [
            WatchDeparture(lineId: "M3", destination: "Doukissis Plakentias", minutes: 3, time: "17:47"),
            WatchDeparture(lineId: "M3", destination: "Airport", minutes: 11, time: "17:55"),
            WatchDeparture(lineId: "M2", destination: "Elliniko", minutes: 4, time: "17:48"),
        ],
        updatedEpoch: 0
    )
}
