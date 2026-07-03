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
        // 0.0 at the moment tracking started, 1.0 when the train is due.
        // The Lock Screen renders this as a progress bar under the countdown
        // so the widget shows visible movement between minute-boundary
        // updates. Optional so decoding still works if an in-flight activity
        // pushed an older state shape.
        var progress: Double? = 0
        // Route strip: up to a handful of station names ordered in the
        // direction of travel, target last. The widget draws these as dots
        // with a train marker interpolated by progress, mirroring the
        // in-app TrackingCard. Optional + defaulted so a state pushed by
        // an older build still decodes.
        var routeStations: [String]? = []
        // Unix epoch second the train is expected. The widget uses this to
        // render Text(timerInterval:countsDown:) so the countdown ticks
        // between state pushes instead of freezing on the last minute
        // value. Optional so decoding of older shapes still succeeds.
        var targetEpoch: Double? = nil
    }

    var lineId: String
    var stationName: String
    var destination: String
}
#endif
