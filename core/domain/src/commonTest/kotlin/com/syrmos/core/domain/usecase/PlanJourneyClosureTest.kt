package com.syrmos.core.domain.usecase

import com.syrmos.core.common.ClosureMode
import com.syrmos.core.common.StationClosure
import com.syrmos.core.common.StationClosures
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The 3.0.2 planner rule from fixtures/closures/effect.json: never board where
 * canBoard is false, never alight where canAlight is false, and pass_through keeps
 * the line continuous.
 */
class PlanJourneyClosureTest {
    private val now = 1_793_167_200L

    private fun ride(to: String, line: String) = Edge(to, line, line, 2, isTransfer = false)
    private fun walk(to: String) = Edge(to, PlanJourneyUseCase.TRANSFER_LINE_ID, "", 3, isTransfer = true)

    // M2: M2_A - M2_SYN - M2_B ; M3: M3_X - M3_SYN - M3_Y ; transfer M2_SYN <-> M3_SYN
    private fun graph(): MutableMap<String, MutableList<Edge>> = mutableMapOf(
        "M2_A" to mutableListOf(ride("M2_SYN", "M2")),
        "M2_SYN" to mutableListOf(ride("M2_A", "M2"), ride("M2_B", "M2"), walk("M3_SYN")),
        "M2_B" to mutableListOf(ride("M2_SYN", "M2")),
        "M3_X" to mutableListOf(ride("M3_SYN", "M3")),
        "M3_SYN" to mutableListOf(ride("M3_X", "M3"), ride("M3_Y", "M3"), walk("M2_SYN")),
        "M3_Y" to mutableListOf(ride("M3_SYN", "M3")),
    )

    private fun reachable(closures: List<StationClosure>, from: String, to: String): Boolean {
        val g = graph()
        applyClosureRestrictions(g, from, to) { id -> StationClosures.effect(closures, id, StationClosures.lineIdOfStation(id), now) }
        val seen = mutableSetOf(from)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n == to) return true
            g[n].orEmpty().forEach { if (seen.add(it.toStationId)) queue.add(it.toStationId) }
        }
        return false
    }

    private fun closure(mode: ClosureMode) = listOf(
        StationClosure("c", listOf("M2_SYN", "M3_SYN"), emptyList(), mode, now - 60, null),
    )

    @Test
    fun noClosure_everythingReachable() {
        assertTrue(reachable(emptyList(), "M2_A", "M3_Y"))
    }

    @Test
    fun passThrough_keepsLineContinuous_butNoBoardAlightOrTransfer() {
        val c = closure(ClosureMode.PASS_THROUGH)
        assertTrue(reachable(c, "M2_A", "M2_B"))
        assertFalse(reachable(c, "M2_A", "M3_Y"))
        assertFalse(reachable(c, "M2_A", "M2_SYN"))
        assertFalse(reachable(c, "M2_SYN", "M2_B"))
    }

    @Test
    fun exitOnly_allowsAlightingButNotBoarding() {
        val c = closure(ClosureMode.EXIT_ONLY)
        assertTrue(reachable(c, "M2_A", "M2_SYN"))
        assertFalse(reachable(c, "M2_SYN", "M2_B"))
        assertFalse(reachable(c, "M2_A", "M3_Y"))
    }

    @Test
    fun entryOnly_allowsBoardingButNotAlighting() {
        val c = closure(ClosureMode.ENTRY_ONLY)
        assertTrue(reachable(c, "M2_SYN", "M2_B"))
        assertFalse(reachable(c, "M2_A", "M2_SYN"))
    }

    @Test
    fun expiredClosure_isIgnored() {
        val c = listOf(StationClosure("c", listOf("M2_SYN", "M3_SYN"), emptyList(), ClosureMode.CLOSED, now - 7200, now - 3600))
        assertTrue(reachable(c, "M2_A", "M3_Y"))
    }
}
