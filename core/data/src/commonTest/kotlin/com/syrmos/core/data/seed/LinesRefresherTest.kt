package com.syrmos.core.data.seed

import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun novelLineKeepsPayloadOrderPositions() {
        // A brand-new line owns its whole ordered stop list; positions are the
        // payload indices so the stops draw in order.
        assertEquals(0L, LinesRefresher.membershipPosition(lineIsNovel = true, index = 0))
        assertEquals(3L, LinesRefresher.membershipPosition(lineIsNovel = true, index = 3))
    }

    @Test
    fun novelStopOnSeededLineParksPastEverySeededPosition() {
        // The regression the adversarial review found: a new stop landing at a
        // mid-line payload index (e.g. 2) must NOT be written as position 2,
        // which collides with the seeded stop already at 2 and corrupts the
        // ORDER BY. It parks past every real seeded position instead.
        val pos = LinesRefresher.membershipPosition(lineIsNovel = false, index = 2)
        assertEquals(LinesRefresher.NOVEL_STOP_BASE + 2L, pos)
        assertTrue(pos > LinesRefresher.NOVEL_STOP_BASE)
        // Two novel stops on the same seeded line keep their relative payload
        // order and never collide with each other.
        assertTrue(
            LinesRefresher.membershipPosition(lineIsNovel = false, index = 1) <
                LinesRefresher.membershipPosition(lineIsNovel = false, index = 4),
        )
    }
}
