package com.syrmos.core.model.transit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The four honest line-status states and what each means for whether a line
 * carries service, draws greyed, or is merely seasonal.
 *
 * These distinctions drive downstream truth on every platform: a suspended
 * 130-year-old railway (the Diakopto-Kalavryta rack line, halted after the March
 * 2026 rockfalls) must not be called "under construction", and a seasonal tourist
 * line (the Pelion railway) must still list and draw in colour while never getting
 * a confident live countdown out of season.
 */
class LineStatusTest {

    private fun line(status: LineStatus) = Line(
        id = "X",
        name = "X",
        nameEl = "X",
        type = LineType.METRO,
        color = LineColor.BLUE,
        terminalA = "A",
        terminalB = "B",
        stationCount = 3,
        status = status,
    )

    @Test
    fun fromRawMapsEveryKnownState() {
        assertEquals(LineStatus.OPERATIONAL, LineStatus.fromRaw("operational"))
        assertEquals(LineStatus.UNDER_CONSTRUCTION, LineStatus.fromRaw("under_construction"))
        assertEquals(LineStatus.SUSPENDED, LineStatus.fromRaw("suspended"))
        assertEquals(LineStatus.SEASONAL, LineStatus.fromRaw("seasonal"))
    }

    @Test
    fun fromRawIsCaseInsensitiveAndFallsBackToOperational() {
        assertEquals(LineStatus.SUSPENDED, LineStatus.fromRaw("SUSPENDED"))
        assertEquals(LineStatus.SEASONAL, LineStatus.fromRaw("Seasonal"))
        // Unknown / null must never make a live line vanish on a stale payload.
        assertEquals(LineStatus.OPERATIONAL, LineStatus.fromRaw("garbage"))
        assertEquals(LineStatus.OPERATIONAL, LineStatus.fromRaw(null))
    }

    @Test
    fun operationalAndSeasonalCarryService() {
        assertTrue(line(LineStatus.OPERATIONAL).isOperational)
        // Seasonal is a real boardable line: it must list, draw in colour, and
        // count as service (its own dated trips gate the days it actually runs).
        assertTrue(line(LineStatus.SEASONAL).isOperational)
    }

    @Test
    fun suspendedAndUnderConstructionAreBuiltButClosed() {
        val suspended = line(LineStatus.SUSPENDED)
        assertFalse(suspended.isOperational)
        assertTrue(suspended.isBuiltButClosed)
        assertTrue(suspended.isSuspended)

        val construction = line(LineStatus.UNDER_CONSTRUCTION)
        assertFalse(construction.isOperational)
        assertTrue(construction.isBuiltButClosed)
        assertFalse(construction.isSuspended)
    }

    @Test
    fun seasonalIsNotBuiltButClosed() {
        val seasonal = line(LineStatus.SEASONAL)
        assertTrue(seasonal.isSeasonal)
        assertFalse(seasonal.isBuiltButClosed)
        assertFalse(seasonal.isSuspended)
    }
}
