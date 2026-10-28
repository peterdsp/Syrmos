package com.syrmos.core.domain.usecase

import com.syrmos.core.common.ClosureMode
import com.syrmos.core.common.StationClosure
import com.syrmos.core.common.StationClosures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reproduces the cold-start race seen on the emulator: the board's first load ran
 * before the cached closures were hydrated, so a closed Syntagma showed times for
 * one refresh. The gate must hydrate first and judge after, inside the same flow.
 */
class ClosureGateTest {
    private val now = 1_793_174_400L
    private val syntagma = StationClosure("syn", listOf("M2_SYN", "M3_SYN"), emptyList(), ClosureMode.PASS_THROUGH, now - 7200, null)

    @Test
    fun coldStart_hydratesBeforeJudging_soNoDepartureLeaks() = runTest {
        var loaded = emptyList<StationClosure>() // nothing in memory yet: the race
        val departures = flowOf(listOf("10:05", "10:10"))
        val out = departures.gatedByClosure(
            hydrate = { loaded = listOf(syntagma) },
            canBoard = { StationClosures.effect(loaded, "M3_SYN", "M3", now).canBoard },
        ).first()
        assertEquals(emptyList(), out)
    }

    @Test
    fun openStation_keepsDepartures() = runTest {
        val out = flowOf(listOf("10:05")).gatedByClosure(
            hydrate = {},
            canBoard = { StationClosures.effect(listOf(syntagma), "M3_NIK", "M3", now).canBoard },
        ).first()
        assertEquals(listOf("10:05"), out)
    }

    @Test
    fun hydrationFailure_doesNotBreakTheBoard() = runTest {
        val out = flowOf(listOf("10:05")).gatedByClosure(
            hydrate = { error("no bundle") },
            canBoard = { true },
        ).first()
        assertEquals(listOf("10:05"), out)
    }
}
