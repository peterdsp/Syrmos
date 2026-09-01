package com.syrmos.core.data.seed

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the overlay-only membership rule. The adversarial review of the
 * overlay-only change (parity #16) caught a regression: nesting the membership
 * insert under the station-novelty check alone meant a genuinely-new line whose
 * stops include already-seeded interchange stations got an incomplete (or empty)
 * membership and drew nothing. The rule must attach a membership when the LINE
 * is new OR the STATION is new, and skip only when both are already known (that
 * is the seeded-membership reorder overlay-only must avoid).
 */
class LinesRefresherTest {

    @Test
    fun newLineWithAlreadySeededStationStillGetsMembership() {
        // The regression case: novel line, station already seeded (e.g. a new M4
        // whose stops include Syntagma). Must still attach so the line draws.
        assertTrue(LinesRefresher.shouldAttachMembership(lineIsNovel = true, stationIsKnown = true))
    }

    @Test
    fun newLineWithNewStationGetsMembership() {
        assertTrue(LinesRefresher.shouldAttachMembership(lineIsNovel = true, stationIsKnown = false))
    }

    @Test
    fun seededLineGainingANewStationAttachesIt() {
        assertTrue(LinesRefresher.shouldAttachMembership(lineIsNovel = false, stationIsKnown = false))
    }

    @Test
    fun seededLineWithSeededStationIsLeftAlone() {
        // Both known: never rewrite the seeded membership (would reorder stops).
        assertFalse(LinesRefresher.shouldAttachMembership(lineIsNovel = false, stationIsKnown = true))
    }
}
