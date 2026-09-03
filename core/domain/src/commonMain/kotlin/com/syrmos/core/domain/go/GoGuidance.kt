package com.syrmos.core.domain.go

/**
 * Syrmos GO -- live trip-guidance engine (Android / KMP reference implementation).
 *
 * GO is the 3.0 "Journeys" spine: once a rider is on a planned journey, tell them
 * the one thing that matters right now -- board, ride, get off next, change here,
 * arrived -- so they never have to watch for their stop. This is the pure,
 * deterministic core: no Android, no network, no clock. It works fully offline
 * from a journey's static stop sequence; live positions only advance [GuidancePosition]
 * faster.
 *
 * It mirrors the web reference (`web-go.js`), the server engine (`go_guidance.py`)
 * and the iOS engine (`JourneyGuidance.swift`), and is validated against the same
 * cross-client contract in `fixtures/go-guidance/cases.json`, so GO guidance
 * cannot drift between the web, iOS, Android and server implementations.
 *
 * A [GuidanceLeg]'s [GuidanceLeg.stops] are ordered from its board stop to its
 * alight stop inclusive; [GuidanceLeg.towards] is the direction shown to the
 * rider. A [GuidancePosition] (legIndex, stopIndex) means the rider is AT
 * `legs[legIndex].stops[stopIndex]`.
 */
data class GuidanceStop(val id: String, val name: String)

data class GuidanceLeg(val lineId: String, val towards: String, val stops: List<GuidanceStop>)

data class GuidanceJourney(val legs: List<GuidanceLeg>)

data class GuidancePosition(val legIndex: Int, val stopIndex: Int)

/** The rider-facing instruction for a position on a journey. */
sealed interface JourneyGuidance {
    data class Board(
        val lineId: String, val towards: String, val stopsRemaining: Int, val nextStation: String,
    ) : JourneyGuidance

    data class Ride(
        val lineId: String, val towards: String, val stopsRemaining: Int, val nextStation: String,
    ) : JourneyGuidance

    data class GetOffNext(
        val nextStation: String, val isDestination: Boolean, val transferTo: String?,
    ) : JourneyGuidance

    data class Transfer(val atStation: String, val toLineId: String, val towards: String) : JourneyGuidance

    data class Arrived(val station: String) : JourneyGuidance
}

object GoGuidance {

    /**
     * The instruction for [position]. Throws [IllegalArgumentException] for a
     * position that does not name a real stop (a caller bug, not a rider state).
     */
    fun guidance(journey: GuidanceJourney, position: GuidancePosition): JourneyGuidance {
        require(position.legIndex in journey.legs.indices) { "legIndex out of range" }
        val leg = journey.legs[position.legIndex]
        require(position.stopIndex in leg.stops.indices) { "stopIndex out of range" }

        val lastLeg = position.legIndex == journey.legs.lastIndex
        val lastStop = leg.stops.lastIndex
        val remaining = lastStop - position.stopIndex
        val here = leg.stops[position.stopIndex]

        if (lastLeg && remaining == 0) return JourneyGuidance.Arrived(here.name)

        if (remaining == 0) {
            val next = journey.legs[position.legIndex + 1]
            return JourneyGuidance.Transfer(here.name, next.lineId, next.towards)
        }

        if (position.stopIndex == 0) {
            return JourneyGuidance.Board(leg.lineId, leg.towards, remaining, leg.stops[1].name)
        }

        if (remaining == 1) {
            val next = if (lastLeg) null else journey.legs[position.legIndex + 1]
            return JourneyGuidance.GetOffNext(leg.stops[lastStop].name, lastLeg, next?.lineId)
        }

        return JourneyGuidance.Ride(
            leg.lineId, leg.towards, remaining, leg.stops[position.stopIndex + 1].name,
        )
    }

    /**
     * Whether a get-off notification should fire now (rider one stop from a leg's
     * alight point). Independent of the display type so a caller can drive the
     * notification / Live Update off one predicate; true even on a 2-stop leg.
     */
    fun shouldAlertGetOff(journey: GuidanceJourney, position: GuidancePosition): Boolean {
        val leg = journey.legs.getOrNull(position.legIndex) ?: return false
        val remaining = leg.stops.lastIndex - position.stopIndex
        return remaining == 1
    }

    /**
     * Advance one stop, rolling a leg's alight stop onto the next leg's board stop.
     * Returns the same position when already at the destination.
     */
    fun advance(journey: GuidanceJourney, position: GuidancePosition): GuidancePosition {
        val leg = journey.legs.getOrNull(position.legIndex) ?: return position
        val lastLeg = position.legIndex == journey.legs.lastIndex
        val atLegEnd = position.stopIndex >= leg.stops.lastIndex
        return when {
            atLegEnd && lastLeg -> position
            atLegEnd -> GuidancePosition(position.legIndex + 1, 0)
            else -> GuidancePosition(position.legIndex, position.stopIndex + 1)
        }
    }

    fun isArrived(journey: GuidanceJourney, position: GuidancePosition): Boolean {
        val leg = journey.legs.getOrNull(position.legIndex) ?: return false
        val lastLeg = position.legIndex == journey.legs.lastIndex
        return lastLeg && position.stopIndex == leg.stops.lastIndex
    }
}
