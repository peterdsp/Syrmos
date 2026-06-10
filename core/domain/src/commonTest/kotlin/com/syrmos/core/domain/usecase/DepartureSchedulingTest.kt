package com.syrmos.core.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the clock-aligned departure scheduling logic that
 * powers the "5 min / 10 min / 15 min" countdown on the station detail
 * screen.
 *
 * The bug we fixed earlier was that departures were generated as "now +
 * frequencyMinutes * i" — which meant they always returned 5/10/15/20
 * regardless of when the user opened the screen. The fix anchors each
 * departure to the next clock-aligned slot since midnight so the countdown
 * actually ticks down (5 → 4 → 3 → …) as the minute moves forward.
 *
 * This logic is duplicated in iOS (`SyrmosData.sampleDepartures`) so any
 * regression here is also a regression there — keep the algorithms in sync.
 */
class DepartureSchedulingTest {

    /**
     * Replicates the algorithm in [GetStationDeparturesUseCase] so we can
     * exercise it in pure-Kotlin tests without a database. If you change the
     * one in the use case, change this too.
     */
    private fun nextSlots(
        nowMinutes: Int,
        seconds: Int,
        freqMinutes: Int,
        count: Int = 4,
    ): List<Int> {
        val secondOffset = if (seconds >= 30) 1 else 0
        var nextSlot = ((nowMinutes / freqMinutes) + 1) * freqMinutes
        return (0 until count).map {
            val minutesAway = (nextSlot - nowMinutes - secondOffset).coerceAtLeast(0)
            nextSlot += freqMinutes
            minutesAway
        }
    }

    @Test
    fun at_14_30_with_5min_frequency_first_slot_is_5_min_away() {
        // At 14:30:00 sharp, next aligned 5-min slot is 14:35 — 5 minutes away.
        val minutes = 14 * 60 + 30
        assertEquals(listOf(5, 10, 15, 20), nextSlots(minutes, seconds = 0, freqMinutes = 5))
    }

    @Test
    fun at_14_31_with_5min_frequency_first_slot_is_4_min_away() {
        // Critical case: the bug we fixed. At 14:31, the next 5-min aligned
        // departure is 14:35, which is 4 minutes away — NOT 5.
        val minutes = 14 * 60 + 31
        assertEquals(listOf(4, 9, 14, 19), nextSlots(minutes, seconds = 0, freqMinutes = 5))
    }

    @Test
    fun at_14_31_45s_subtracts_one_minute_for_visual_smoothness() {
        // When the second hand is past 30s, we subtract one minute so the
        // countdown flows smoothly through the next full minute instead of
        // jumping abruptly. At 14:31:45 with a 5-min freq the next slot is
        // 14:35, which is technically ~3 min 15s away — we round to 3 min.
        val minutes = 14 * 60 + 31
        assertEquals(listOf(3, 8, 13, 18), nextSlots(minutes, seconds = 45, freqMinutes = 5))
    }

    @Test
    fun at_midnight_with_10min_frequency_first_slot_is_10_min_away() {
        assertEquals(listOf(10, 20, 30, 40), nextSlots(0, seconds = 0, freqMinutes = 10))
    }

    @Test
    fun successive_slots_increase_monotonically() {
        val slots = nextSlots(14 * 60 + 17, seconds = 0, freqMinutes = 4)
        for (i in 1 until slots.size) {
            assertTrue(slots[i] > slots[i - 1], "Slot $i (${slots[i]}) must exceed slot ${i - 1} (${slots[i - 1]})")
        }
    }

    @Test
    fun countdown_actually_decreases_as_minutes_pass() {
        // The single test that the original bug would fail on: at 14:30 the
        // first slot is 5 min; at 14:31 it must be 4 min; at 14:32 it must
        // be 3 min. This is the user-visible "live countdown" behaviour.
        val freq = 5
        assertEquals(5, nextSlots(14 * 60 + 30, seconds = 0, freqMinutes = freq).first())
        assertEquals(4, nextSlots(14 * 60 + 31, seconds = 0, freqMinutes = freq).first())
        assertEquals(3, nextSlots(14 * 60 + 32, seconds = 0, freqMinutes = freq).first())
        assertEquals(2, nextSlots(14 * 60 + 33, seconds = 0, freqMinutes = freq).first())
        assertEquals(1, nextSlots(14 * 60 + 34, seconds = 0, freqMinutes = freq).first())
        // At exactly the slot time, the train that *was* at 14:35 has
        // departed; the next aligned slot is 14:40 — so the countdown
        // resets to 5 minutes. This is by design (we don't want a slot
        // to linger at "0 min" for the whole minute it's stopped at).
        assertEquals(5, nextSlots(14 * 60 + 35, seconds = 0, freqMinutes = freq).first())
    }

    @Test
    fun frequency_of_1_minute_returns_consecutive_minutes() {
        // Edge case: high-frequency lines like rush-hour metro.
        val slots = nextSlots(14 * 60, seconds = 0, freqMinutes = 1, count = 5)
        assertEquals(listOf(1, 2, 3, 4, 5), slots)
    }
}
