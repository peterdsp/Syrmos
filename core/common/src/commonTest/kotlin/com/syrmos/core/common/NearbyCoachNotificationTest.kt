package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NearbyCoachNotificationTest {
    @Test
    fun ownerExample_headlineFromRelaxDirection() {
        val c = NearbyCoachNotification.build(
            stationId = "M3_NIK",
            stationName = "Νίκαια",
            walkMinutes = 6,
            directions = listOf(
                NearbyDirection("M3", "Αεροδρόμιο", listOf(CoachDeparture(10), CoachDeparture(20))),
                NearbyDirection("M3", "Δουκίσσης Πλακεντίας", listOf(CoachDeparture(2), CoachDeparture(10))),
            ),
            stationClosed = false,
            athensDate = "2026-10-27",
            lang = AppLanguage.GREEK,
        )!!
        assertEquals("Νίκαια · 6' με τα πόδια", c.title)
        assertEquals(listOf("M3 Αεροδρόμιο: 10', 20' (κατά πρόγραμμα)", "M3 Δουκίσσης Πλακεντίας: 2', 10' (κατά πρόγραμμα)"), c.lines)
        assertEquals(CoachCase.RELAX_NEXT_ONE_IS_CLOSE, c.headlineCase)
        // fnv1a32("M3_NIK|2026-10-27") % 4 picks the variant deterministically.
        val v = NearbyDepartureCoach.variantIndex("M3_NIK", "2026-10-27", 4)
        assertEquals(NearbyCoachCopy.render(CoachCase.RELAX_NEXT_ONE_IS_CLOSE, v, AppLanguage.GREEK,
            mapOf("destination" to "Δουκίσσης Πλακεντίας", "next" to "2", "following" to "10", "wait" to "4")), c.headline)
    }

    @Test
    fun closedStation_usesStationClosedCopy() {
        val c = NearbyCoachNotification.build("M2_SYN", "Syntagma", 3, emptyList(), true, "2026-10-28", AppLanguage.ENGLISH)!!
        assertEquals(CoachCase.STATION_CLOSED, c.headlineCase)
        assertEquals(emptyList(), c.lines)
    }

    @Test
    fun noDirections_isNull() {
        assertNull(NearbyCoachNotification.build("X", "X", 3, emptyList(), false, "2026-10-28", AppLanguage.ENGLISH))
    }
}
