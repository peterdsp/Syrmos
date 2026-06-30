import Foundation
#if canImport(ActivityKit)
import ActivityKit

/// Live Activity payload for the tracked departure. This file is a member of
/// BOTH the app target (which starts/updates/ends the activity) and the
/// SyrmosWidgetExtension target (which renders the Lock Screen + Dynamic Island
/// UI), so the type matches across the process boundary, exactly as Apple's
/// Live Activity template shares its ActivityAttributes file.
@available(iOS 16.2, *)
struct SyrmosTrackingAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        var minutesRemaining: Int
        var scheduledTime: String
        var isDue: Bool
    }

    var lineId: String
    var stationName: String
    var destination: String
}
#endif
