package com.syrmos.core.domain.usecase

import com.syrmos.core.model.transit.Direction

/**
 * Computes tonight's final departure for a line at a given station.
 *
 * This is the inversion of [GetNextDeparturesUseCase]: instead of "the next
 * few trains from now", it answers "what's the last train I can still catch
 * on this line tonight, and when does it pass this station." Single line, no
 * transfers, so no routing is involved.
 *
 * Implementation reuses [ComputeDeparturesFromBandsUseCase] with a wide limit
 * so it enumerates every remaining slot for today's service window, then takes
 * the latest one. Returns null when no more trains run tonight (the caller then
 * shows nothing, or a "service over" line).
 */
class GetLastTrainUseCase(
    private val bandProjector: ComputeDeparturesFromBandsUseCase,
) {
    data class LastTrain(
        val time: String,
        val minutesAway: Int,
        val direction: Direction,
        val lineId: String,
    )

    /**
     * Last train on [lineId] in [direction] passing [stationId] tonight, or
     * null if service is over. Pass [maxLookaheadMinutes] to bound how far the
     * "tonight" window reaches; the default 12h keeps the M3_AIR look-ahead
     * row (which can scan up to a week out) from masquerading as tonight's
     * last airport train.
     */
    operator fun invoke(
        stationId: String,
        lineId: String,
        direction: Direction,
        maxLookaheadMinutes: Int = 12 * 60,
    ): LastTrain? {
        val lineIds = if (lineId == "M3") listOf("M3", "M3_AIR") else listOf(lineId)
        val projected = bandProjector.invoke(
            lineIds = lineIds,
            direction = direction,
            limit = 400,
            stationId = stationId,
        )
        return selectLastTrain(projected, maxLookaheadMinutes)
            ?.let { LastTrain(it.time, it.minutesAway, it.direction, it.lineId) }
    }

    /**
     * Convenience for the home teaser: the later of the two directions, so the
     * line shows the genuine last chance to ride it tonight regardless of which
     * way the final train runs.
     */
    fun latestEitherDirection(stationId: String, lineId: String): LastTrain? {
        val outbound = invoke(stationId, lineId, Direction.OUTBOUND)
        val inbound = invoke(stationId, lineId, Direction.INBOUND)
        return listOfNotNull(outbound, inbound).maxByOrNull { it.minutesAway }
    }

    companion object {
        /**
         * The latest departure still running tonight, within
         * [maxLookaheadMinutes]. Extracted so the selection rule is unit-tested
         * directly: it must take the single max-minutesAway slot inside the
         * window and ignore any look-ahead row (e.g. tomorrow's first M3_AIR
         * train) that sits beyond it.
         */
        internal fun selectLastTrain(
            departures: List<UpcomingDeparture>,
            maxLookaheadMinutes: Int,
        ): UpcomingDeparture? =
            departures
                .filter { it.minutesAway in 0..maxLookaheadMinutes }
                .maxByOrNull { it.minutesAway }
    }
}
