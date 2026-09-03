import Foundation
import SwiftUI

// Drives the GO live-guidance screen over a planned journey. Holds the rider's
// position and derives the current instruction from the pure `JourneyGuidance`
// engine. Advancing is manual here (step through your journey); a later phase
// advances `position` from GPS proximity / live train position. Pure UI state on
// top of the engine — no network, no clock.
@MainActor
final class GoJourneyViewModel: ObservableObject {
    let journey: GuidanceJourney
    @Published private(set) var position: GuidancePosition

    init(journey: GuidanceJourney) {
        self.journey = journey
        self.position = GuidancePosition(legIndex: 0, stopIndex: 0)
    }

    // MARK: Derived state

    var current: JourneyGuidance {
        (try? JourneyGuidance.at(journey, position)) ?? .arrived(station: journey.legs.last?.stops.last?.name ?? "")
    }

    var isArrived: Bool { JourneyGuidance.isArrived(journey, position) }

    /// True when the rider is one stop from a leg's alight point (fire the get-off
    /// cue). Keyed independently of the display case, per the GO contract.
    var shouldAlert: Bool { JourneyGuidance.shouldAlertGetOff(journey, position) }

    var canAdvance: Bool { !isArrived }
    var canGoBack: Bool { position.legIndex > 0 || position.stopIndex > 0 }

    /// 0...1 progress across the whole journey by stops visited.
    var progress: Double {
        let total = max(1, journey.legs.reduce(0) { $0 + max(0, $1.stops.count - 1) })
        var done = 0
        for i in 0..<position.legIndex { done += max(0, journey.legs[i].stops.count - 1) }
        done += position.stopIndex
        return min(1, Double(done) / Double(total))
    }

    /// The line id for the current leg (for tint), nil once arrived at the end.
    var currentLineId: String? {
        guard journey.legs.indices.contains(position.legIndex) else { return nil }
        return journey.legs[position.legIndex].lineId
    }

    // MARK: Actions

    func advance() {
        guard canAdvance else { return }
        position = JourneyGuidance.advance(journey, position)
    }

    func back() {
        if position.stopIndex > 0 {
            position = GuidancePosition(legIndex: position.legIndex, stopIndex: position.stopIndex - 1)
        } else if position.legIndex > 0 {
            let prev = position.legIndex - 1
            position = GuidancePosition(legIndex: prev, stopIndex: journey.legs[prev].stops.count - 1)
        }
    }

    func reset() { position = GuidancePosition(legIndex: 0, stopIndex: 0) }
}
