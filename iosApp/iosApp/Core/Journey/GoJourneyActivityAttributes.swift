import Foundation
#if canImport(ActivityKit)
import ActivityKit

/// Phase N J08 Live Activity payload for an explicitly started GO journey. Member
/// of BOTH the app target (which starts/updates/ends the activity) and the
/// SyrmosWidgetExtension target (which renders the Lock Screen + Dynamic Island),
/// mirroring `SyrmosTrackingAttributes`. Every ContentState field is optional-safe
/// so an older in-flight activity still decodes.
@available(iOS 16.2, *)
struct GoJourneyActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        /// Short state word: Board / Riding / Get off next / Transfer / Arrived.
        var stateLabel: String
        /// The one instruction that matters now, e.g. "Board M3 toward Syntagma".
        var instruction: String
        /// Secondary context, e.g. "Next stop: Maniatika" / "8 stops". Optional.
        var context: String? = nil
        /// 0.0 at the start of the journey, 1.0 on arrival.
        var progress: Double = 0
        /// Current leg line id for tinting, when riding. Optional.
        var lineId: String? = nil
        var arrived: Bool = false
    }

    var origin: String
    var destination: String
}
#endif
