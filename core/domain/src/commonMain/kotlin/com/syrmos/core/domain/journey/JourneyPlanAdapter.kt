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
        // Optional schedule upgrade: when a timetable + requested instant are given,
        // the estimated option is turned into a scheduled one (real feasibility).
        timetable: SchedulePlanner.Timetable? = null,
        requestedInstant: kotlinx.datetime.Instant? = null,
        // Backward (arrive-by) target; when set, the schedule pass assigns the
        // LATEST feasible departures to arrive by it. `lastConnection = true`
        // ignores any target and takes the latest available departure (last train).
        arriveByInstant: kotlinx.datetime.Instant? = null,
        lastConnection: Boolean = false,
    ): List<JourneyOption> {
        val option = buildOption(result, serviceDate, timetable, requestedInstant, arriveByInstant, lastConnection)
            ?: return emptyList()
        return JourneyRanker.rank(listOf(option), ranking)
    }

    /**
     * Rank several candidate routes together (k-shortest via line-banning): each
     * result is converted + scheduled with its OWN timetable, then the shared
     * ranker dedups identical leg sequences, orders by objective, and caps at 3.
     */
    fun rankCandidates(
        results: List<JourneyResult?>,
        ranking: Ranking,
        serviceDate: LocalDate,
        requestedInstant: kotlinx.datetime.Instant? = null,
        arriveByInstant: kotlinx.datetime.Instant? = null,
        lastConnection: Boolean = false,
        timetableFor: (JourneyResult) -> SchedulePlanner.Timetable? = { null },
    ): List<JourneyOption> {
        val options = results.mapNotNull { r ->
            r?.let { buildOption(it, serviceDate, timetableFor(it), requestedInstant, arriveByInstant, lastConnection) }
        }
        return JourneyRanker.rank(options, ranking)
    }

    /** Convert one topology result into a single (scheduled) option; no ranking. */
    private fun buildOption(
        result: JourneyResult?,
        serviceDate: LocalDate,
        timetable: SchedulePlanner.Timetable?,
        requestedInstant: kotlinx.datetime.Instant?,
        arriveByInstant: kotlinx.datetime.Instant?,
        lastConnection: Boolean,
    ): JourneyOption? {
        if (result == null || result.segments.isEmpty()) return null

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

        // Stable id from the ride-line chain so distinct candidates sort/dedup well.
        val chainId = result.segments.joinToString("_") { it.lineId + ":" + it.fromStationId + ">" + it.toStationId }
        var option = JourneyOption(
            id = "opt-$chainId",
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
        if (timetable != null) {
            option = when {
                lastConnection -> SchedulePlanner.lastConnection(option, timetable)
                arriveByInstant != null -> SchedulePlanner.assignScheduleArriveBy(option, arriveByInstant, timetable)
                requestedInstant != null -> SchedulePlanner.assignSchedule(option, requestedInstant, timetable)
                else -> option
            }
        }
        return option.copy(feasibility = FeasibilityCalculator.forOption(option))
    }
}
