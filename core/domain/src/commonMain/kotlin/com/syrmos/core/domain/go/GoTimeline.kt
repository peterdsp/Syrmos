package com.syrmos.core.domain.go

/** The role a stop plays on its leg. */
enum class GoTimelineRole { ORIGIN, INTERMEDIATE, ALIGHT }

/** The rider's relation to a stop. */
enum class GoTimelineState { PAST, CURRENT, NEXT, FUTURE }

/**
 * One timeline row, pure data so the GO companion and its tests share the rule.
 * Mirrors the iOS `GoTimelineRow` in GoJourneyView.swift.
 */
data class GoTimelineRow(
    val legIndex: Int,
    val stopIndex: Int,
    val name: String,
    val role: GoTimelineRole,
    val state: GoTimelineState,
    /** The journey's final stop (the alight of the last leg). */
    val isDestination: Boolean,
)

/**
 * Pure projection of a guidance journey and position into timeline rows: which
 * stop is the origin or alight of its leg, which is behind the rider, which is
 * current, which is next. Same numbers on iOS (`GoTimelineProjection`).
 */
/**
 * One leg of the segmented progress bar: the line and how much of the leg the
 * rider has covered (0 before it, 1 after it, hops ridden over hops while on it).
 */
data class GoLegSegment(val lineId: String, val fraction: Double)

object GoTimeline {
    fun legProgress(journey: GuidanceJourney, position: GuidancePosition): List<GoLegSegment> =
        journey.legs.mapIndexed { idx, leg ->
            val hops = maxOf(1, leg.stops.size - 1)
            val fraction = when {
                idx < position.legIndex -> 1.0
                idx > position.legIndex -> 0.0
                else -> (position.stopIndex.toDouble() / hops).coerceIn(0.0, 1.0)
            }
            GoLegSegment(leg.lineId, fraction)
        }

    /** Hops ridden so far across the journey (the "X" in "stop X of Y"). */
    fun stopsRidden(journey: GuidanceJourney, position: GuidancePosition): Int {
        var done = 0
        journey.legs.forEachIndexed { idx, leg ->
            val hops = maxOf(0, leg.stops.size - 1)
            if (idx < position.legIndex) done += hops
            else if (idx == position.legIndex) done += minOf(hops, position.stopIndex)
        }
        return done
    }

    fun rows(journey: GuidanceJourney, position: GuidancePosition): List<GoTimelineRow> {
        val out = ArrayList<GoTimelineRow>()
        journey.legs.forEachIndexed { legIdx, leg ->
            leg.stops.forEachIndexed { stopIdx, stop ->
                val role = when (stopIdx) {
                    0 -> GoTimelineRole.ORIGIN
                    leg.stops.lastIndex -> GoTimelineRole.ALIGHT
                    else -> GoTimelineRole.INTERMEDIATE
                }
                val state = when {
                    legIdx < position.legIndex ||
                        (legIdx == position.legIndex && stopIdx < position.stopIndex) -> GoTimelineState.PAST
                    legIdx == position.legIndex && stopIdx == position.stopIndex -> GoTimelineState.CURRENT
                    legIdx == position.legIndex && stopIdx == position.stopIndex + 1 -> GoTimelineState.NEXT
                    else -> GoTimelineState.FUTURE
                }
                out += GoTimelineRow(
                    legIndex = legIdx, stopIndex = stopIdx, name = stop.name, role = role, state = state,
                    isDestination = role == GoTimelineRole.ALIGHT && legIdx == journey.legs.lastIndex,
                )
            }
        }
        return out
    }

    /**
     * Stops ridden across the journey: each leg's stops minus its boarding stop, so
     * an interchange counted at the end of one leg is not counted again at the
     * start of the next.
     */
    fun stopCount(journey: GuidanceJourney): Int =
        journey.legs.sumOf { maxOf(0, it.stops.size - 1) }
}
