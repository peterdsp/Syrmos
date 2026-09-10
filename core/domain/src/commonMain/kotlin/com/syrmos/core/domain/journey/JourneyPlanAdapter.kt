package com.syrmos.core.domain.journey

import com.syrmos.core.model.journey.Feasibility
import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.Leg
import com.syrmos.core.model.journey.LegAccessibility
import com.syrmos.core.model.journey.LegKind
import com.syrmos.core.model.journey.Ranking
import com.syrmos.core.model.journey.TimingKind
import com.syrmos.core.model.planner.JourneyResult
import kotlinx.datetime.LocalDate

/**
 * Composes the existing topology planner output (`JourneyResult`) into the 3.0
 * ranked `JourneyOption` contract, mirroring web `web-journey-plan.js`.
 *
 * Truthfulness (same rules as web): the topology planner has no schedule, so this
 * attaches NO absolute clock times. Every ride leg is `TimingKind.ESTIMATED`,
 * instants stay null, `durationSeconds` is a disclosed estimate, and feasibility
 * therefore reads `unknown` for any transfer and `direct`/comfortable for a single
 * ride. Feasibility and ranking come from the shared calculators, so the honest
 * output matches web.
 */
object JourneyPlanAdapter {
    /** Disclosed estimate for an interchange with no scheduled minimum (matches web). */
    const val TRANSFER_ESTIMATE_MINUTES = 3

    /**
     * @param serviceDate the operator service date the estimate is anchored to
     *   (required by the Leg model; the times themselves remain estimated).
     */
    fun toOptions(
        result: JourneyResult?,
        ranking: Ranking,
        serviceDate: LocalDate,
    ): List<JourneyOption> {
        if (result == null || result.segments.isEmpty()) return emptyList()

        val fromId = result.segments.first().fromStationId
        val toId = result.segments.last().toStationId
        val legs = mutableListOf<Leg>()

        result.segments.forEachIndexed { i, seg ->
            if (i > 0) {
                val prevAlight = legs.last().toId
                legs += Leg(
                    id = "transfer-$i",
                    kind = LegKind.TRANSFER,
                    fromId = prevAlight,
                    toId = seg.fromStationId,
                    orderedStopIds = listOf(prevAlight, seg.fromStationId),
                    serviceDate = serviceDate,
                    timingKind = TimingKind.ESTIMATED,
                    accessibility = LegAccessibility.UNKNOWN,
                )
            }
            legs += Leg(
                id = "ride-$i",
                kind = LegKind.RIDE,
                fromId = seg.fromStationId,
                toId = seg.toStationId,
                lineId = seg.lineId,
                orderedStopIds = listOf(seg.fromStationId, seg.toStationId),
                serviceDate = serviceDate,
                timingKind = TimingKind.ESTIMATED,
                accessibility = LegAccessibility.UNKNOWN,
            )
        }

        val transferCount = result.transferCount
        val durationSeconds = (result.totalMinutes + transferCount * TRANSFER_ESTIMATE_MINUTES) * 60

        var option = JourneyOption(
            id = "plan-$fromId-$toId",
            requestId = "req-$fromId-$toId",
            legs = legs,
            departureInstant = null,
            arrivalInstant = null,
            durationSeconds = durationSeconds,
            transferCount = transferCount,
            walkingSeconds = null, // unknown, never invented
            feasibility = Feasibility(com.syrmos.core.model.journey.FeasibilityStatus.UNKNOWN, explanationCode = "pending"),
            rankingBadge = null,
        )
        option = option.copy(feasibility = FeasibilityCalculator.forOption(option))
        return JourneyRanker.rank(listOf(option), ranking)
    }
}
