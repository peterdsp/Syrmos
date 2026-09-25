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
object GoTimeline {
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
