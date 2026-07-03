package com.syrmos.core.domain.usecase

import com.syrmos.core.common.extensions.currentAthensTime
import com.syrmos.core.common.extensions.parseTime
import com.syrmos.core.model.planner.JourneyResult
import com.syrmos.core.model.planner.JourneySegment
import com.syrmos.core.model.transit.Direction
import kotlinx.coroutines.flow.first

/**
 * Backward-walking wrapper around [PlanJourneyUseCase]. Given a target
 * arrival time, it plans the route with the standard shortest-path
 * planner, then re-anchors EVERY non-transfer leg to the LATEST real
 * scheduled departure that still connects to the next leg's boarding
 * (or the trip's target arrival, for the final leg).
 *
 * Walking every leg rather than only the first means multi-transfer
 * answers name real trains at each connection — "Board the 21:04 M3 at
 * Syntagma, transfer to the 21:19 M2 at Monastiraki, arrive Piraeus
 * 21:28" instead of "leave by 21:04 roughly". Transfer time is baked
 * into the segments already (JourneySegment.isTransfer == true), so we
 * treat those as slack windows between two boardable legs.
 */
class PlanByArrivalUseCase(
    private val planJourney: PlanJourneyUseCase,
    private val getNextDepartures: GetNextDeparturesUseCase,
) {
    /**
     * Per-leg concrete departure. The list mirrors the boardable legs in
     * [Solution.route] (transfer segments are not included here).
     */
    data class LegDeparture(
        val lineId: String,
        val fromStationName: String,
        val toStationName: String,
        val departureTime: String,        // HH:MM at fromStation
        val arrivalTime: String,          // HH:MM at toStation
        val departureMinutes: Int,        // Athens minutes-from-midnight
        val arrivalMinutes: Int,          // Athens minutes-from-midnight
    )

    /**
     * Result carries the base [route] plus a concrete departure for each
     * non-transfer leg. Callers use [firstLegDepartureTime] as the
     * headline "leave X by Y" anchor; multi-leg answers can additionally
     * list [legDepartures] to name the transfer trains.
     */
    data class Solution(
        val route: JourneyResult,
        val legDepartures: List<LegDeparture>,
        val firstLegDepartureTime: String,     // HH:MM
        val firstLegDepartureMinutes: Int,     // Athens minutes-from-midnight
        val projectedArrivalMinutes: Int,      // Athens minutes-from-midnight
        val slackMinutes: Int,                 // arriveBy - projectedArrival
    )

    suspend operator fun invoke(
        fromStationId: String,
        toStationId: String,
        arriveByAthensMinutes: Int,
    ): Solution? {
        val route = planJourney.invoke(fromStationId, toStationId).first() ?: return null
        val boardable = route.segments.filter { !it.isTransfer }
        if (boardable.isEmpty()) return null

        // Walk backward from the final leg. For leg N (0-indexed from
        // origin), the latest arrival minute at its destination is
        // determined by leg N+1's departure minute minus the transfer
        // time between them. The final leg's constraint is the trip's
        // target arrival.
        val nextLegBoardingByIndex = IntArray(boardable.size + 1)
        nextLegBoardingByIndex[boardable.size] = arriveByAthensMinutes

        val perLeg = ArrayList<LegDeparture?>(boardable.size)
        repeat(boardable.size) { perLeg.add(null) }

        for (idx in boardable.indices.reversed()) {
            val leg = boardable[idx]
            val transferAfter = if (idx == boardable.size - 1) 0 else transferMinutesBetween(route.segments, leg, boardable[idx + 1])
            val arriveDeadline = nextLegBoardingByIndex[idx + 1] - transferAfter
            val boardDeadline = arriveDeadline - leg.estimatedMinutes

            val latest = latestDepartureBefore(leg, boardDeadline) ?: return null
            val depMin = latest.first
            val arrMin = depMin + leg.estimatedMinutes
            perLeg[idx] = LegDeparture(
                lineId = leg.lineId,
                fromStationName = leg.fromStationName,
                toStationName = leg.toStationName,
                departureTime = formatClock(depMin),
                arrivalTime = formatClock(arrMin),
                departureMinutes = depMin,
                arrivalMinutes = arrMin,
            )
            nextLegBoardingByIndex[idx] = depMin
        }

        val legDepartures = perLeg.filterNotNull()
        val firstLeg = legDepartures.first()
        val lastLeg = legDepartures.last()
        return Solution(
            route = route,
            legDepartures = legDepartures,
            firstLegDepartureTime = firstLeg.departureTime,
            firstLegDepartureMinutes = firstLeg.departureMinutes,
            projectedArrivalMinutes = lastLeg.arrivalMinutes,
            slackMinutes = arriveByAthensMinutes - lastLeg.arrivalMinutes,
        )
    }

    private suspend fun latestDepartureBefore(
        leg: JourneySegment,
        boardBy: Int,
    ): Pair<Int, UpcomingDeparture>? {
        val candidates = buildList {
            for (dir in Direction.entries) {
                val list = runCatching {
                    getNextDepartures.invoke(
                        stationId = leg.fromStationId,
                        lineId = leg.lineId,
                        direction = dir,
                        limit = 48,
                    ).first()
                }.getOrNull() ?: continue
                addAll(list)
            }
        }
        if (candidates.isEmpty()) return null
        return candidates
            .mapNotNull { dep ->
                val minutes = clockToMinutes(dep.time) ?: return@mapNotNull null
                if (minutes <= boardBy) minutes to dep else null
            }
            .maxByOrNull { it.first }
    }

    /**
     * Total transfer-segment minutes between two boardable legs. In the
     * current planner, transfer edges sit between the segments and carry
     * their walking-time weight, so we sum up any transfer segments
     * whose from-station matches [before]'s destination.
     */
    private fun transferMinutesBetween(
        segments: List<JourneySegment>,
        before: JourneySegment,
        after: JourneySegment,
    ): Int {
        val start = segments.indexOf(before)
        val end = segments.indexOf(after)
        if (start < 0 || end < 0 || end <= start) return 0
        var sum = 0
        for (i in (start + 1) until end) {
            val seg = segments[i]
            if (seg.isTransfer) sum += seg.estimatedMinutes
        }
        return sum
    }

    private fun clockToMinutes(hhmm: String): Int? {
        val parsed = runCatching { parseTime(hhmm) }.getOrNull() ?: return null
        return parsed.hour * 60 + parsed.minute
    }

    private fun formatClock(minutes: Int): String {
        val safe = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60)
        val h = (safe / 60) % 24
        val m = safe % 60
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }

    /** Handy for callers that want "now" in the same Athens frame. */
    fun currentAthensMinutes(): Int {
        val t = currentAthensTime()
        return t.hour * 60 + t.minute
    }
}
