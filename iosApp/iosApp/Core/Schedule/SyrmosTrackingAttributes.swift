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
    /// One upcoming train on the tracked line, for the Dynamic Island expanded
    /// tri-lane. Optional in ContentState so older in-flight states still decode.
    public struct UpcomingTrain: Codable, Hashable {
        var lineId: String
        var minutes: Int
        var destination: String
    }

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
        // Next few trains on the tracked line, for the Dynamic Island expanded
        // tri-lane. Optional + defaulted so older state shapes still decode; the
        // expanded view falls back to the route strip when this is empty.
        var upcoming: [UpcomingTrain]? = nil
        // Tonight's last train on the tracked line ("01:23"), for the Lock
        // Screen footer. Optional so older shapes decode.
        var lastTrain: String? = nil
        var currentStation: String? = nil
        var communityStatus: String? = nil
        var communityDetail: String? = nil
        var communityConfirmations: Int? = nil
        var communityUpdatedSeconds: Int? = nil
        var unexpectedStop: Bool? = false
    }

    var lineId: String
    var stationName: String
    var destination: String
}
#endif
