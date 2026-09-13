package com.syrmos.core.domain.journey

import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.Leg
import com.syrmos.core.model.journey.LegKind
import kotlinx.datetime.Instant

/**
 * Selected-journey detail timeline (S05), the Kotlin peer of web
 * `SyrmosJourneyDetail.timeline` and iOS `JourneyDetail.timeline`. Pure transform:
 * a [JourneyOption]'s legs become an ordered list of [TimelineRow]s the S05 view
 * renders. Language-neutral (ids/instants/counts only; the view localizes) and
 * honest about time (a null clock stays null, never a fabricated 0). Matches the
 * shared golden fixture fixtures/journeys/detail.json.
 */
object JourneyDetail {
    /** A node's role on the rail, driving its size/outline in the view. */
    enum class Node { ORIGIN, DESTINATION, INTERCHANGE }

    /** One rendered timeline row. Fields are populated per [kind]. */
    data class TimelineRow(
        val kind: String, // board | stops | alight | transfer | walk
        val legId: String,
        val stationId: String? = null,
        val lineId: String? = null,
        val towardsId: String? = null,
        val clock: Instant? = null,
        val timingKind: String? = null,
        val node: Node? = null,
        val count: Int? = null,
        val fromId: String? = null,
        val toId: String? = null,
        val seconds: Int? = null,
    )

    fun timeline(option: JourneyOption): List<TimelineRow> {
        val legs = option.legs
        val lastIndex = legs.size - 1
        val rows = mutableListOf<TimelineRow>()

        legs.forEachIndexed { i, leg ->
            if (leg.kind == LegKind.RIDE) {
                val stops = leg.orderedStopIds
                val towardsId = stops.lastOrNull() ?: leg.toId
                rows += TimelineRow(
                    kind = "board", legId = leg.id, stationId = leg.fromId, lineId = leg.lineId,
                    towardsId = towardsId, clock = leg.departureInstant,
                    timingKind = leg.timingKind.wire(),
                    node = if (i == 0) Node.ORIGIN else Node.INTERCHANGE,
                )
                val count = maxOf(0, stops.size - 2)
                if (count > 0) rows += TimelineRow(kind = "stops", legId = leg.id, lineId = leg.lineId, count = count)
                rows += TimelineRow(
                    kind = "alight", legId = leg.id, stationId = leg.toId,
                    clock = leg.arrivalInstant, timingKind = leg.timingKind.wire(),
                    node = if (i == lastIndex) Node.DESTINATION else Node.INTERCHANGE,
                )
            } else {
                val prev = legs.getOrNull(i - 1)
                val next = legs.getOrNull(i + 1)
                val seconds = leg.transferMinimumSeconds
                    ?: gapSeconds(prev?.arrivalInstant, next?.departureInstant)
                rows += TimelineRow(
                    kind = if (leg.kind == LegKind.WALK) "walk" else "transfer", legId = leg.id,
                    fromId = leg.fromId, toId = leg.toId, seconds = seconds, node = Node.INTERCHANGE,
                )
            }
        }
        return rows
    }

    private fun gapSeconds(from: Instant?, to: Instant?): Int? {
        if (from == null || to == null) return null
        return (to - from).inWholeSeconds.toInt()
    }

    // TimingKind wire name (lowercase, matches @SerialName and the fixture).
    private fun com.syrmos.core.model.journey.TimingKind.wire(): String = name.lowercase()
}
