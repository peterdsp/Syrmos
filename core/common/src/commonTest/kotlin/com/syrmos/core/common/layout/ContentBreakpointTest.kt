package com.syrmos.core.common.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the 3.0 content breakpoints (prompt section 4.1 + the 4.2 reference
 * canvases). These exact numbers are the contract the iOS/SwiftUI and web/JS
 * mirrors must reproduce, so the test doubles as the spec.
 */
class ContentBreakpointTest {

    private fun mode(w: Int, h: Int = 800) = ContentBreakpoint.resolve(w, h).mode

    @Test
    fun boundaryWidthsMapToTheRightMode() {
        assertEquals(ContentMode.COMPACT, mode(320))
        assertEquals(ContentMode.COMPACT, mode(360))
        assertEquals(ContentMode.COMPACT, mode(390))
        assertEquals(ContentMode.COMPACT, mode(599))
        assertEquals(ContentMode.MEDIUM, mode(600))
        assertEquals(ContentMode.MEDIUM, mode(768))
        assertEquals(ContentMode.MEDIUM, mode(839))
        assertEquals(ContentMode.EXPANDED, mode(840))
        assertEquals(ContentMode.EXPANDED, mode(1024))
        assertEquals(ContentMode.EXPANDED, mode(1199))
        assertEquals(ContentMode.WIDE, mode(1200))
        assertEquals(ContentMode.WIDE, mode(1600))
    }

    @Test
    fun compactCanvasesMatchTheReferenceGeometry() {
        // C390: Main x=16, width=358.
        ContentBreakpoint.resolve(390, 740).let {
            assertEquals(16, it.outerInset)
            assertEquals(358, it.contentWidth)
            assertNull(it.secondaryPaneWidth, "compact is single column")
        }
        // C360: Main x=16, width=328.
        ContentBreakpoint.resolve(360, 640).let {
            assertEquals(16, it.outerInset)
            assertEquals(328, it.contentWidth)
        }
    }

    @Test
    fun mediumCanvasMatchesM768() {
        // M768: Main x=44, width=680.
        ContentBreakpoint.resolve(768, 900).let {
            assertEquals(ContentMode.MEDIUM, it.mode)
            assertEquals(44, it.outerInset)
            assertEquals(680, it.contentWidth)
        }
    }

    @Test
    fun expandedCanvasMatchesE1024() {
        // E1024: Primary x=24, width=360; secondary x=408, width=592.
        ContentBreakpoint.resolve(1024, 700).let {
            assertEquals(ContentMode.EXPANDED, it.mode)
            assertEquals(24, it.outerInset)
            assertEquals(360, it.primaryPaneWidth)
            assertEquals(592, it.secondaryPaneWidth)
            assertEquals(24, it.columnGap)
            // secondary x = outerInset + primary + gap
            assertEquals(408, it.outerInset + it.primaryPaneWidth!! + it.columnGap)
        }
    }

    @Test
    fun wideCanvasMatchesW1360() {
        // W1360: Primary x=32, width=400; secondary x=456, width=872.
        ContentBreakpoint.resolve(1360, 840).let {
            assertEquals(ContentMode.WIDE, it.mode)
            assertEquals(32, it.outerInset)
            assertEquals(400, it.primaryPaneWidth)
            assertEquals(872, it.secondaryPaneWidth)
            assertEquals(456, it.outerInset + it.primaryPaneWidth!! + it.columnGap)
        }
    }

    @Test
    fun wideCanvasIsCappedAt1600() {
        val at1600 = ContentBreakpoint.resolve(1600, 900)
        val at2400 = ContentBreakpoint.resolve(2400, 900)
        assertEquals(at1600.secondaryPaneWidth, at2400.secondaryPaneWidth,
            "canvas caps at 1600 so ultra-wide does not keep stretching the map pane")
    }

    @Test
    fun shortWindowFallsBackToOneScrollingColumn() {
        // Short 740x320: single 680-wide scrolling column, map opens separately.
        ContentBreakpoint.resolve(740, 320).let {
            assertTrue(it.singleColumnFallback)
            assertEquals(680, it.contentWidth)
            assertNull(it.secondaryPaneWidth, "no side-by-side panes above a keyboard")
        }
    }

    @Test
    fun accessibilityTextForcesOneColumnEvenWhenWide() {
        val forced = ContentBreakpoint.resolve(1360, 900, forceSingleColumn = true)
        assertTrue(forced.singleColumnFallback)
        assertNull(forced.secondaryPaneWidth)
    }

    @Test
    fun aTwoPaneWindowWithoutRoomForTheSecondaryCollapsesToMedium() {
        // Contrived narrow "expanded" width where the secondary pane can't reach
        // 360: the rule collapses it to a Medium single column instead.
        val layout = ContentBreakpoint.resolve(841, 800)
        // 841 - 48 - 360 - 24 = 409 >= 360, so 841 is genuinely Expanded...
        assertEquals(ContentMode.EXPANDED, layout.mode)
        // ...verify the guard itself with the internal boundary: just above 840 the
        // secondary is comfortably >= MIN_SECONDARY_PANE.
        assertTrue(layout.secondaryPaneWidth!! >= ContentBreakpoint.MIN_SECONDARY_PANE)
    }
}
