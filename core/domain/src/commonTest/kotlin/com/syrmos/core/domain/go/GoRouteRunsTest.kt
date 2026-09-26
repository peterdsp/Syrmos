package com.syrmos.core.domain.go

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Fixtures mirror iOS `JourneyGuidanceTests.test_legRuns_oneRunPerLegInRideOrder_skippingUnplaceableLegs`. */
class GoRouteRunsTest {
    private val coords = mapOf(
        "PIR" to GoRoutePoint(37.948, 23.643), "FAL" to GoRoutePoint(37.945, 23.665),
        "MOS" to GoRoutePoint(37.955, 23.680), "MON" to GoRoutePoint(37.976, 23.726),
        "SYN" to GoRoutePoint(37.975, 23.735),
    )
    private val journey = GuidanceJourney(
        legs = listOf(
            GuidanceLeg("M1", "Kifissia", listOf(
                GuidanceStop("PIR", "Piraeus"), GuidanceStop("FAL", "Faliro"),
                GuidanceStop("MOS", "Moschato"), GuidanceStop("MON", "Monastiraki"),
            )),
            GuidanceLeg("M3", "Airport", listOf(GuidanceStop("MON", "Monastiraki"), GuidanceStop("SYN", "Syntagma"))),
        ),
    )

    @Test
    fun oneRunPerLegInRideOrder() {
        val runs = GoRouteRuns.legRuns(journey) { coords[it] }
        assertEquals(listOf("M1", "M3"), runs.map { it.lineId })
        assertEquals(4, runs[0].points.size)
        assertEquals(2, runs[1].points.size)
    }

    @Test
    fun aLegWhoseStopsCannotBePlacedDrawsNothing() {
        val partial = GoRouteRuns.legRuns(journey) { if (it == "SYN") null else coords[it] }
        assertEquals(listOf("M1"), partial.map { it.lineId })
    }

    @Test
    fun currentPointFollowsThePositionAndIsNullWhenUnplaceable() {
        assertEquals(coords["MOS"], GoRouteRuns.currentPoint(journey, GuidancePosition(0, 2)) { coords[it] })
        assertEquals(coords["SYN"], GoRouteRuns.currentPoint(journey, GuidancePosition(1, 1)) { coords[it] })
        assertNull(GoRouteRuns.currentPoint(journey, GuidancePosition(1, 1)) { if (it == "SYN") null else coords[it] })
        assertNull(GoRouteRuns.currentPoint(journey, GuidancePosition(5, 0)) { coords[it] })
    }
}
