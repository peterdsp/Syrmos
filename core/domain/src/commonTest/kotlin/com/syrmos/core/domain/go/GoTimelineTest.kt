package com.syrmos.core.domain.go

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the timeline projection the GO companion draws from. The iOS twin
 * (`GoTimelineProjection` tests in JourneyGuidanceTests.swift) uses the same
 * two-leg journey and expects the same roles, states and counts.
 */
class GoTimelineTest {

    private val journey = GuidanceJourney(
        legs = listOf(
            GuidanceLeg("M1", "Monastiraki", listOf(
                GuidanceStop("PIR", "Piraeus"), GuidanceStop("FAL", "Faliro"),
                GuidanceStop("MOS", "Moschato"), GuidanceStop("MON", "Monastiraki"),
            )),
            GuidanceLeg("M3", "Syntagma", listOf(
                GuidanceStop("MON", "Monastiraki"), GuidanceStop("SYN", "Syntagma"),
            )),
        ),
    )

    @Test
    fun rolesFollowTheLegShape() {
        val rows = GoTimeline.rows(journey, GuidancePosition(0, 0))
        assertEquals(listOf(
            GoTimelineRole.ORIGIN, GoTimelineRole.INTERMEDIATE, GoTimelineRole.INTERMEDIATE, GoTimelineRole.ALIGHT,
            GoTimelineRole.ORIGIN, GoTimelineRole.ALIGHT,
        ), rows.map { it.role })
    }

    @Test
    fun onlyTheLastLegsAlightIsTheDestination() {
        val rows = GoTimeline.rows(journey, GuidancePosition(0, 0))
        assertEquals(listOf(false, false, false, false, false, true), rows.map { it.isDestination })
    }

    @Test
    fun statesAtTheStartOfTheJourney() {
        val rows = GoTimeline.rows(journey, GuidancePosition(0, 0))
        assertEquals(listOf(
            GoTimelineState.CURRENT, GoTimelineState.NEXT, GoTimelineState.FUTURE, GoTimelineState.FUTURE,
            GoTimelineState.FUTURE, GoTimelineState.FUTURE,
        ), rows.map { it.state })
    }

    @Test
    fun statesMidLegDimWhatIsBehindTheRider() {
        val rows = GoTimeline.rows(journey, GuidancePosition(0, 2))
        assertEquals(listOf(
            GoTimelineState.PAST, GoTimelineState.PAST, GoTimelineState.CURRENT, GoTimelineState.NEXT,
            GoTimelineState.FUTURE, GoTimelineState.FUTURE,
        ), rows.map { it.state })
    }

    @Test
    fun nextNeverCrossesIntoTheFollowingLeg() {
        // At the alight of leg 0 the next leg's first stop is the same station,
        // reached by changing, not by riding: it stays FUTURE.
        val rows = GoTimeline.rows(journey, GuidancePosition(0, 3))
        assertEquals(GoTimelineState.CURRENT, rows[3].state)
        assertEquals(GoTimelineState.FUTURE, rows[4].state)
    }

    @Test
    fun statesOnTheSecondLegMarkTheWholeFirstLegPast() {
        val rows = GoTimeline.rows(journey, GuidancePosition(1, 0))
        assertEquals(listOf(
            GoTimelineState.PAST, GoTimelineState.PAST, GoTimelineState.PAST, GoTimelineState.PAST,
            GoTimelineState.CURRENT, GoTimelineState.NEXT,
        ), rows.map { it.state })
    }

    @Test
    fun stopCountDoesNotDoubleCountTheInterchange() {
        assertEquals(4, GoTimeline.stopCount(journey))
        assertEquals(0, GoTimeline.stopCount(GuidanceJourney(emptyList())))
    }
}
