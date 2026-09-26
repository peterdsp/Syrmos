package com.syrmos.core.domain.go

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Twin of the focus cases in iOS `JourneyGuidanceTests`. */
class GoTimelineFocusTest {
    @Test
    fun visibleOnlyWhenTheWholeRowIsInsideTheViewport() {
        assertTrue(GoTimelineFocus.isVisible(100f, 144f, 0f, 600f))
        assertFalse(GoTimelineFocus.isVisible(-10f, 34f, 0f, 600f))    // scrolled above
        assertFalse(GoTimelineFocus.isVisible(580f, 624f, 0f, 600f))   // cut off below
        assertTrue(GoTimelineFocus.isVisible(0f, 44f, 0f, 600f))       // touching the edge counts
    }

    @Test
    fun targetPlacesTheRowAThirdDown() {
        assertEquals(700f, GoTimelineFocus.targetOffset(900f, 600f, 2000f))
    }

    @Test
    fun targetIsClampedToTheScrollableRange() {
        assertEquals(0f, GoTimelineFocus.targetOffset(100f, 600f, 2000f))
        assertEquals(2000f, GoTimelineFocus.targetOffset(2500f, 600f, 2000f))
        assertEquals(0f, GoTimelineFocus.targetOffset(300f, 600f, -5f))
    }
}
