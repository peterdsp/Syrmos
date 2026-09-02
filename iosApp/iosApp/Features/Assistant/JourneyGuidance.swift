import Foundation

// Syrmos GO -- live trip-guidance engine (iOS reference implementation).
//
// GO is the 3.0 "Journeys" spine: once a rider is on a planned journey, tell them
// the one thing that matters right now -- board, ride, get off next, change here,
// arrived -- so they never have to watch for their stop. This is the pure,
// deterministic core: no UIKit, no network, no clock. It works fully offline from
// a journey's static stop sequence; live positions only advance `position` faster.
//
// It mirrors the web reference (`web-go.js`) and the server engine
// (`go_guidance.py`) and is validated against the same cross-client contract in
// `fixtures/go-guidance/cases.json`, so GO guidance cannot drift between the web,
// iOS, Android and server implementations.
//
// A `GuidanceLeg`'s `stops` are ordered from its board stop to its alight stop
// inclusive; `towards` is the direction shown to the rider. A `GuidancePosition`
// { legIndex, stopIndex } means the rider is AT `legs[legIndex].stops[stopIndex]`.

struct GuidanceStop: Equatable, Sendable {
    let id: String
    let name: String
}

struct GuidanceLeg: Equatable, Sendable {
    let lineId: String
    let towards: String
    let stops: [GuidanceStop]
}

struct GuidanceJourney: Equatable, Sendable {
    let legs: [GuidanceLeg]
}

struct GuidancePosition: Equatable, Sendable {
    let legIndex: Int
    let stopIndex: Int
}

/// The rider-facing instruction for a position on a journey.
enum JourneyGuidance: Equatable, Sendable {
    case board(lineId: String, towards: String, stopsRemaining: Int, nextStation: String)
    case ride(lineId: String, towards: String, stopsRemaining: Int, nextStation: String)
    case getOffNext(nextStation: String, isDestination: Bool, transferTo: String?)
    case transfer(atStation: String, toLineId: String, towards: String)
    case arrived(station: String)

    enum GuidanceError: Error, Equatable { case positionOutOfRange }

    /// The instruction for `position`. Throws `positionOutOfRange` for a position
    /// that does not name a real stop (a caller bug, not a rider state).
    static func at(_ journey: GuidanceJourney, _ position: GuidancePosition) throws -> JourneyGuidance {
        guard journey.legs.indices.contains(position.legIndex) else { throw GuidanceError.positionOutOfRange }
        let leg = journey.legs[position.legIndex]
        guard leg.stops.indices.contains(position.stopIndex) else { throw GuidanceError.positionOutOfRange }

        let lastLeg = position.legIndex == journey.legs.count - 1
        let lastStop = leg.stops.count - 1
        let remaining = lastStop - position.stopIndex
        let here = leg.stops[position.stopIndex]

        if lastLeg && remaining == 0 { return .arrived(station: here.name) }

        if remaining == 0 {
            let next = journey.legs[position.legIndex + 1]
            return .transfer(atStation: here.name, toLineId: next.lineId, towards: next.towards)
        }

        if position.stopIndex == 0 {
            return .board(lineId: leg.lineId, towards: leg.towards,
                          stopsRemaining: remaining, nextStation: leg.stops[1].name)
        }

        if remaining == 1 {
            let next = lastLeg ? nil : journey.legs[position.legIndex + 1]
            return .getOffNext(nextStation: leg.stops[lastStop].name,
                               isDestination: lastLeg, transferTo: next?.lineId)
        }

        return .ride(lineId: leg.lineId, towards: leg.towards,
                     stopsRemaining: remaining, nextStation: leg.stops[position.stopIndex + 1].name)
    }

    /// Whether a get-off notification should fire now (rider one stop from a leg's
    /// alight point). Independent of the display case so a caller drives the local
    /// notification / Live Activity off one predicate; true even on a 2-stop leg.
    static func shouldAlertGetOff(_ journey: GuidanceJourney, _ position: GuidancePosition) -> Bool {
        guard journey.legs.indices.contains(position.legIndex) else { return false }
        let leg = journey.legs[position.legIndex]
        let remaining = leg.stops.count - 1 - position.stopIndex
        return remaining == 1
    }

    /// Advance one stop, rolling a leg's alight stop onto the next leg's board
    /// stop. Returns the same position when already at the destination.
    static func advance(_ journey: GuidanceJourney, _ position: GuidancePosition) -> GuidancePosition {
        guard journey.legs.indices.contains(position.legIndex) else { return position }
        let leg = journey.legs[position.legIndex]
        let lastLeg = position.legIndex == journey.legs.count - 1
        let atLegEnd = position.stopIndex >= leg.stops.count - 1
        if atLegEnd {
            if lastLeg { return position }
            return GuidancePosition(legIndex: position.legIndex + 1, stopIndex: 0)
        }
        return GuidancePosition(legIndex: position.legIndex, stopIndex: position.stopIndex + 1)
    }

    static func isArrived(_ journey: GuidanceJourney, _ position: GuidancePosition) -> Bool {
        let lastLeg = position.legIndex == journey.legs.count - 1
        guard journey.legs.indices.contains(position.legIndex) else { return false }
        let leg = journey.legs[position.legIndex]
        return lastLeg && position.stopIndex == leg.stops.count - 1
    }
}
