package com.syrmos.core.model.planner

import com.syrmos.core.model.transit.Direction

data class JourneySegment(
    val lineId: String,
    val lineName: String,
    val fromStationId: String,
    val fromStationName: String,
    val toStationId: String,
    val toStationName: String,
    val stationCount: Int,
    val estimatedMinutes: Int,
    val isTransfer: Boolean = false,
    /**
     * The direction of travel on this line, derived from the line's canonical
     * station order (OUTBOUND = toward the natural last station). Null when the
     * planner could not resolve it. Lets a schedule-grounded consumer pick
     * departures heading the right way instead of merging both directions.
     */
    val direction: Direction? = null,
    /**
     * Every station the ride really calls at, in order, from [fromStationId] to
     * [toStationId] inclusive. Empty means unknown (a caller that built the
     * segment without a path), never "no intermediate stops": consumers fall back
     * to the two endpoints. Web and iOS already carry the full sequence, so this
     * keeps the shared contract at parity instead of collapsing a ride to its ends.
     */
    val orderedStopIds: List<String> = emptyList(),
)
