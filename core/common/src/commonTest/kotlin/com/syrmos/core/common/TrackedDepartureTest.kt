package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pins the track-this-departure countdown arithmetic. */
class TrackedDepartureTest {

    private fun tracked(targetEpoch: Long) = TrackedDeparture(
        lineId = "M2",
        stationId = "M2_SYN",
        stationName = "Syntagma",
        destination = "Elliniko",
        scheduledTime = "18:40",
        targetEpochSeconds = targetEpoch,
    )

    @Test
    fun minutes_remaining_rounds_up() {
        val now = 1_000_000L
        // 3 min 30 s away rounds up to 4.
        assertEquals(4, tracked(now + 210).minutesRemaining(now))
    }

    @Test
    fun exact_minute_boundary() {
        val now = 1_000_000L
        assertEquals(3, tracked(now + 180).minutesRemaining(now))
    }

    @Test
    fun never_negative() {
        val now = 1_000_000L
        assertEquals(0, tracked(now - 120).minutesRemaining(now))
    }

    @Test
    fun is_due_flips_at_target() {
        val now = 1_000_000L
        assertFalse(tracked(now + 1).isDue(now))
        assertTrue(tracked(now).isDue(now))
        assertTrue(tracked(now - 1).isDue(now))
    }

    @Test
    fun track_and_stop_update_active() {
        DepartureTracking.stop()
        assertEquals(null, DepartureTracking.active.value)
        DepartureTracking.track(tracked(2_000_000L))
        assertEquals("M2", DepartureTracking.active.value?.lineId)
        DepartureTracking.stop()
        assertEquals(null, DepartureTracking.active.value)
    }
}
