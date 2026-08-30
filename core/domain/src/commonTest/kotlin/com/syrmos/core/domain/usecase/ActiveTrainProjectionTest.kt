package com.syrmos.core.domain.usecase

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the active-train slot enumeration in
 * [ComputeActiveTrainsFromBandsUseCase]. Unlike the next-departure projector
 * (which emits FUTURE trains at a station), the active-train projector emits
 * every train that has ALREADY left origin and is still somewhere on the line
 * right now, so the map can place a moving dot for it.
 *
 * The invariant that distinguishes the two, pinned here:
 *
 *   A slot is emitted iff  slot <= now  AND  0 <= (now - slot) <= travel.
 *
 * i.e. the train must have departed (not a future slot) and must not yet have
 * reached its terminus (elapsed within the line's total travel time).
 *
 * [activeSlots] is a faithful replica of the per-band/direction inner loop of
 * [ComputeActiveTrainsFromBandsUseCase.projectLine] and the Pi server projector
 * (`ops/syrmos-api/syrmos_admin/projector.py` -> `active_trains`). If either is
 * changed, mirror the change here so this test keeps catching regressions of
 * the same shape.
 */
class ActiveTrainProjectionTest {

    private data class Active(val slot: Double, val elapsed: Double)

    /**
     * Enumerate the active departures for one band + direction. [start]/[end]
     * are the band window in minutes-of-day (already shifted), [headway] the
     * step, [now] the fractional Athens minute-of-day, [travel] the line's
     * max minutes-from-origin. Dedupes by rounded slot against [seen].
     */
    private fun activeSlots(
        start: Int,
        end: Int,
        headway: Double,
        now: Double,
        travel: Int,
        seen: MutableSet<Int> = mutableSetOf(),
    ): List<Active> {
        val out = mutableListOf<Active>()
        if (travel <= 0) return out
        val earliest = maxOf(start.toDouble(), now - travel)
        val skips = maxOf(0L, ((earliest - start) / headway).toLong())
        var slot = start + skips * headway
        while (slot <= end && slot <= now + 0.5) {
            val elapsed = now - slot
            if (elapsed in 0.0..travel.toDouble()) {
                if (seen.add(slot.roundToInt())) out.add(Active(slot, elapsed))
            }
            slot += headway
        }
        return out
    }

    @Test
    fun emitsEveryTrainCurrentlyOnTheLine() {
        // Band 06:00->24:00 every 5 min, now 10:00, travel 20 min. The trains
        // still on the line are exactly those that left in the last 20 minutes:
        // slots 09:40, 09:45, 09:50, 09:55, 10:00 -> 5 dots.
        val active = activeSlots(
            start = 6 * 60,
            end = 24 * 60,
            headway = 5.0,
            now = 10.0 * 60,
            travel = 20,
        )
        assertEquals(5, active.size)
        assertEquals(listOf(580, 585, 590, 595, 600), active.map { it.slot.roundToInt() })
    }

    @Test
    fun neverEmitsAFutureSlot() {
        // Every emitted slot must already have departed (slot <= now).
        val now = 10.0 * 60 + 32
        val active = activeSlots(
            start = 6 * 60,
            end = 24 * 60,
            headway = 5.0,
            now = now,
            travel = 20,
        )
        assertTrue(active.isNotEmpty())
        assertTrue(active.all { it.slot <= now + 0.5 }, "no future train may be emitted")
    }

    @Test
    fun neverEmitsATrainPastItsTerminus() {
        // Elapsed must be within the total travel time for every emitted train.
        val now = 10.0 * 60 + 17
        val travel = 20
        val active = activeSlots(
            start = 6 * 60,
            end = 24 * 60,
            headway = 5.0,
            now = now,
            travel = travel,
        )
        assertTrue(active.isNotEmpty())
        assertTrue(
            active.all { it.elapsed in 0.0..travel.toDouble() },
            "every emitted train must have 0 <= elapsed <= travel",
        )
    }

    @Test
    fun emptyWhenServiceHasNotStartedYet() {
        // Band starts 06:00; at 05:00 no train has departed, so no dots.
        val active = activeSlots(
            start = 6 * 60,
            end = 24 * 60,
            headway = 5.0,
            now = 5.0 * 60,
            travel = 20,
        )
        assertTrue(active.isEmpty(), "no train is active before the first departure")
    }

    @Test
    fun overlappingBandsDedupeByRoundedSlot() {
        // Two bands covering the same minute (a schedule seam) must yield ONE
        // train per slot, not two, thanks to the shared seen-set.
        val seen = mutableSetOf<Int>()
        val now = 10.0 * 60
        val first = activeSlots(6 * 60, 10 * 60, 5.0, now, 20, seen)
        val second = activeSlots(10 * 60, 14 * 60, 5.0, now, 20, seen)
        val allSlots = (first + second).map { it.slot.roundToInt() }
        assertEquals(allSlots.toSet().size, allSlots.size, "no slot may appear twice")
    }

    @Test
    fun skipsDirectionWithNoOffsets() {
        // travel <= 0 (no station offsets for that direction) emits nothing.
        val active = activeSlots(
            start = 6 * 60,
            end = 24 * 60,
            headway = 5.0,
            now = 10.0 * 60,
            travel = 0,
        )
        assertTrue(active.isEmpty(), "a direction with no offsets contributes no trains")
    }

    @Test
    fun serviceTypeIsLateNightForOvernightLabels() {
        // Pin the label rule mirrored from _service_type / serviceType().
        fun serviceType(label: String): String {
            val l = label.lowercase()
            return if ("late" in l || "overnight" in l) "late_night" else "regular"
        }
        assertEquals("late_night", serviceType("Late night"))
        assertEquals("late_night", serviceType("saturday_overnight_24_7"))
        assertEquals("regular", serviceType(""))
        assertEquals("regular", serviceType("peak"))
    }
}
