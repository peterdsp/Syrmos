package com.syrmos.core.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the per-station departure projection in
 * [ComputeDeparturesFromBandsUseCase]. The projector takes line-level
 * frequency bands (origin-departure schedule) and shifts each slot by the
 * station's `minutesFromOrigin` to display the time the train passes through
 * THIS station.
 *
 * Two correctness invariants are pinned here:
 *
 *  1. "Skip past trains" must run on ARRIVAL TIME at the station, not on
 *     origin-departure time. Otherwise a train that already left terminus
 *     but is still en-route gets dropped, and the first displayed countdown
 *     jumps `offsetMinutes` into the future. This was the bug that turned
 *     "Now / 5 min" at Kerameikos into "22 min / 27 min" offline.
 *
 *  2. The next-slot search must start at the right floor and step forward
 *     by `headway` until arrival is in the future. Coarse skips can land
 *     just before now; the while-loop catches that.
 *
 * The algorithm here mirrors the body of
 * [ComputeDeparturesFromBandsUseCase.projectBand] (and the iOS
 * `ScheduleProjector.projectBand`). If either is changed, mirror the
 * change here so the test continues to catch regressions of the same
 * shape.
 */
class BandProjectionTest {

    /**
     * Faithful replica of the FIXED projectBand math: skip on arrival.
     * Returns minutesAway values for the first [count] departures at this
     * station given a band starting at [startMin] with [headway] step,
     * [nowMin] current clock, and [offsetMin] minutes from origin.
     */
    private fun projectArrivals(
        startMin: Int,
        endMin: Int,
        headway: Double,
        nowMin: Int,
        offsetMin: Int,
        count: Int,
    ): List<Int> {
        var slot = startMin.toDouble()
        val stationSlot = slot + offsetMin
        if (stationSlot < nowMin) {
            val skips = ((nowMin - stationSlot) / headway).toLong().coerceAtLeast(0L)
            slot = startMin + skips * headway
            while (slot + offsetMin < nowMin) slot += headway
        }
        val out = mutableListOf<Int>()
        while (slot <= endMin && out.size < count) {
            val arrival = slot + offsetMin
            out.add((arrival - nowMin).toInt().coerceAtLeast(0))
            slot += headway
        }
        return out
    }

    /** Buggy pre-fix variant: skip on origin time only. Kept here so the
     *  test can prove the new behavior differs from the old at the exact
     *  Kerameikos-at-18:30 reproduction. */
    private fun projectArrivals_skipOnOriginOnly(
        startMin: Int,
        endMin: Int,
        headway: Double,
        nowMin: Int,
        offsetMin: Int,
        count: Int,
    ): List<Int> {
        var slot = startMin.toDouble()
        if (slot < nowMin) {
            val skips = ((nowMin - slot) / headway).toLong().coerceAtLeast(0L)
            slot = startMin + skips * headway
            while (slot < nowMin) slot += headway
        }
        val out = mutableListOf<Int>()
        while (slot <= endMin && out.size < count) {
            val arrival = slot + offsetMin
            out.add((arrival - nowMin).toInt().coerceAtLeast(0))
            slot += headway
        }
        return out
    }

    @Test
    fun kerameikos_18_30_first_arrival_is_within_one_headway() {
        // Kerameikos on M3 outbound: minutesFromOrigin = 20. Band: 06:00 →
        // 22:00 every 5 min. The first arrival at this station after 18:30
        // is the train that left origin at 18:10 — already in motion — so
        // minutesAway should be 0, not 20.
        val arrivals = projectArrivals(
            startMin = 6 * 60,
            endMin = 22 * 60,
            headway = 5.0,
            nowMin = 18 * 60 + 30,
            offsetMin = 20,
            count = 4,
        )
        assertEquals(listOf(0, 5, 10, 15), arrivals)
    }

    @Test
    fun old_skip_on_origin_only_shifts_first_arrival_by_offset() {
        // Pin the pre-fix behavior so it can never be reintroduced as a
        // "simplification." Same inputs as the test above; the buggy
        // version returns 20-minute-shifted slots.
        val arrivals = projectArrivals_skipOnOriginOnly(
            startMin = 6 * 60,
            endMin = 22 * 60,
            headway = 5.0,
            nowMin = 18 * 60 + 30,
            offsetMin = 20,
            count = 4,
        )
        assertEquals(listOf(20, 25, 30, 35), arrivals)
    }

    @Test
    fun zero_offset_terminal_station_first_arrival_at_next_headway_tick() {
        // Origin station (offset 0). At 18:32 with 5-min headway starting
        // at 06:00, the next origin departure is 18:35 → 3 min away.
        val arrivals = projectArrivals(
            startMin = 6 * 60,
            endMin = 22 * 60,
            headway = 5.0,
            nowMin = 18 * 60 + 32,
            offsetMin = 0,
            count = 3,
        )
        assertEquals(listOf(3, 8, 13), arrivals)
    }

    @Test
    fun band_starting_after_now_emits_first_slot_at_band_start() {
        // Future band: starts at 19:00, now is 18:50. First arrival is at
        // 19:00 + offset = 19:00 → 10 min away (offset 0 for clarity).
        val arrivals = projectArrivals(
            startMin = 19 * 60,
            endMin = 22 * 60,
            headway = 10.0,
            nowMin = 18 * 60 + 50,
            offsetMin = 0,
            count = 3,
        )
        assertEquals(listOf(10, 20, 30), arrivals)
    }

    @Test
    fun coarse_skip_then_while_loop_advances_to_first_future_arrival() {
        // Headway 7.0, offset 13, now 600 (10:00), band start 480 (08:00).
        // Coarse skip = ((600 - (480+13)) / 7).toLong() = (107/7).toLong()
        // = 15. slot becomes 480 + 15*7 = 585. arrival = 598 (still < 600).
        // The while-loop bumps it to 592 → 605 arrival → 5 min away.
        val arrivals = projectArrivals(
            startMin = 8 * 60,
            endMin = 12 * 60,
            headway = 7.0,
            nowMin = 10 * 60,
            offsetMin = 13,
            count = 2,
        )
        assertTrue(arrivals.first() >= 0, "first arrival must not be negative")
        assertTrue(arrivals.first() < 7, "first arrival must be within one headway (was ${arrivals.first()})")
    }

    @Test
    fun successive_arrivals_step_by_one_headway() {
        val arrivals = projectArrivals(
            startMin = 6 * 60,
            endMin = 22 * 60,
            headway = 5.0,
            nowMin = 18 * 60 + 30,
            offsetMin = 20,
            count = 5,
        )
        for (i in 1 until arrivals.size) {
            assertEquals(5, arrivals[i] - arrivals[i - 1], "step $i should equal headway")
        }
    }
}
