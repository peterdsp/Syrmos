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
 * planner, then re-anchors the origin leg to the LATEST real departure
 * that still reaches the destination on time.
 *
 * This is a step up from "subtract totalMinutes and call it a day": the
 * first leg is grounded in an actual scheduled departure, so the answer
 * says "leave Syntagma on the 21:04 M3" rather than "leave by 21:04ish".
 * Interior transfers still lean on the planner's estimated inter-station
 * times (a proper backward walk on all legs is a follow-up).
 */
class PlanByArrivalUseCase(
    private val planJourney: PlanJourneyUseCase,
    private val getNextDepartures: GetNextDeparturesUseCase,
) {
    /**
     * Result carries the base [route] plus a concrete departure for the
     * first leg. Callers use [firstLegDepartureTime] as the answer's
     * "leave by X" anchor. Null when either no route exists or nothing
     * on the origin line's schedule can reach the destination in time.
     */
    data class Solution(
        val route: JourneyResult,
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
        val boardable = route.segments.firstOrNull { !it.isTransfer } ?: return null

        // Latest wall-clock minute the user could still be boarding the
        // first non-transfer leg and reach the destination in time.
        val boardBy = arriveByAthensMinutes - route.totalMinutes + boardable.estimatedMinutes

        // Pull a wide window of upcoming departures on the boarding line at
        // the boarding station in each direction; keep the one whose clock
        // time is the highest that is still <= boardBy.
        val candidates = buildList {
            for (dir in Direction.entries) {
                val list = runCatching {
                    getNextDepartures.invoke(
                        stationId = boardable.fromStationId,
                        lineId = boardable.lineId,
                        direction = dir,
                        limit = 48,
                    ).first()
                }.getOrNull() ?: continue
                addAll(list)
            }
        }
        if (candidates.isEmpty()) return null

        val latestBefore = candidates
            .mapNotNull { dep ->
                val minutes = clockToMinutes(dep.time) ?: return@mapNotNull null
                if (minutes <= boardBy) minutes to dep else null
            }
            .maxByOrNull { it.first }
            ?: return null

        val projectedArrival = latestBefore.first + route.totalMinutes - boardable.estimatedMinutes
        return Solution(
            route = route,
            firstLegDepartureTime = latestBefore.second.time,
            firstLegDepartureMinutes = latestBefore.first,
            projectedArrivalMinutes = projectedArrival,
            slackMinutes = arriveByAthensMinutes - projectedArrival,
        )
    }

    private fun clockToMinutes(hhmm: String): Int? {
        val parsed = runCatching { parseTime(hhmm) }.getOrNull() ?: return null
        return parsed.hour * 60 + parsed.minute
    }

    /** Handy for callers that want "now" in the same Athens frame. */
    fun currentAthensMinutes(): Int {
        val t = currentAthensTime()
        return t.hour * 60 + t.minute
    }
}
