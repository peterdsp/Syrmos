package com.syrmos.core.domain.journey

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fixtures shared with the iOS twin `JourneyComparisonTests`. Keep both in step.
 */
class JourneyComparisonTest {
    @Test
    fun loneAlternativeIsNeutral() {
        val f = JourneyComparison.facts(listOf(1500), listOf(1)).single()
        assertFalse(f.fastest)
        assertFalse(f.fewestChanges)
        assertNull(f.minutesSlowerThanFastest)
        assertEquals(0, f.extraChanges)
    }

    @Test
    fun fastestFlagsTheShortestWhenAnotherIsSlower() {
        val f = JourneyComparison.facts(listOf(1500, 1800, 2400), listOf(1, 1, 2))
        assertTrue(f[0].fastest)
        assertFalse(f[1].fastest)
        assertFalse(f[2].fastest)
    }

    @Test
    fun tiedFastestFlagsBothWhenAThirdIsSlower() {
        val f = JourneyComparison.facts(listOf(1500, 1500, 2100), listOf(1, 1, 1))
        assertTrue(f[0].fastest)
        assertTrue(f[1].fastest)
        assertFalse(f[2].fastest)
    }

    @Test
    fun equalDurationsFlagNobodyFastest() {
        val f = JourneyComparison.facts(listOf(1500, 1500), listOf(0, 1))
        assertFalse(f[0].fastest)
        assertFalse(f[1].fastest)
        assertNull(f[0].minutesSlowerThanFastest)
    }

    @Test
    fun unknownDurationIsNeverFastestAndHasNoDelta() {
        val f = JourneyComparison.facts(listOf(null, 1500, 1800), listOf(1, 1, 1))
        assertFalse(f[0].fastest)
        assertNull(f[0].minutesSlowerThanFastest)
        assertTrue(f[1].fastest)
        assertEquals(5, f[2].minutesSlowerThanFastest)
    }

    @Test
    fun slowerMinutesRoundToTheNearestMinuteAndDropZero() {
        val f = JourneyComparison.facts(listOf(1500, 1589, 1650, 1510), listOf(1, 1, 1, 1))
        assertNull(f[0].minutesSlowerThanFastest)
        assertEquals(1, f[1].minutesSlowerThanFastest)   // 89 s
        assertEquals(3, f[2].minutesSlowerThanFastest)   // 150 s rounds up
        assertNull(f[3].minutesSlowerThanFastest)        // 10 s rounds to 0, dropped
    }

    @Test
    fun fewestChangesAndExtraChangesAgainstTheMinimum() {
        val f = JourneyComparison.facts(listOf(2400, 1500, 1800), listOf(0, 2, 1))
        assertTrue(f[0].fewestChanges)
        assertFalse(f[1].fewestChanges)
        assertEquals(0, f[0].extraChanges)
        assertEquals(2, f[1].extraChanges)
        assertEquals(1, f[2].extraChanges)
    }

    @Test
    fun equalChangesFlagNobodyFewest() {
        val f = JourneyComparison.facts(listOf(1500, 1800), listOf(1, 1))
        assertFalse(f[0].fewestChanges)
        assertFalse(f[1].fewestChanges)
    }

    @Test
    fun selectionRetainsAnOfferedIdAndFallsBackToTheFirst() {
        val ids = listOf("m1", "m1-m3", "m2")
        assertEquals("m1-m3", JourneySelection.retain("m1-m3", ids))
        assertEquals("m1", JourneySelection.retain("gone", ids))
        assertEquals("m1", JourneySelection.retain(null, ids))
        assertNull(JourneySelection.retain("m1", emptyList()))
    }

    @Test
    fun selectionIndexIsNullWhenNotOffered() {
        val ids = listOf("a", "b")
        assertEquals(1, JourneySelection.index("b", ids))
        assertNull(JourneySelection.index("z", ids))
        assertNull(JourneySelection.index(null, ids))
    }
}
