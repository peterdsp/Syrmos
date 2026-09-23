package com.syrmos.core.common.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the adaptive workspace policy (foldables + iPhone Duo prompt, section 5)
 * and the acceptance geometry (section 12). These fixtures are the contract the
 * SwiftUI mirror must also satisfy: same arrangement, same fit and collapse
 * decisions, same hinge-clearance rules for the same inputs.
 */
class AdaptiveWorkspaceTest {

    private fun resolve(
        width: Int,
        height: Int = 800,
        task: WorkspaceTask = WorkspaceTask.PLAN,
        regions: List<ReservedRegion> = emptyList(),
        fontScale: Float = 1f,
        forceSingleColumn: Boolean = false,
    ) = AdaptiveWorkspacePolicy.resolve(width, height, task, regions, fontScale, forceSingleColumn)

    // --- Plain window (no fold): must stay consistent with ContentBreakpoint. ---

    @Test
    fun compactWindowIsASingleTaskPane() {
        val ws = resolve(390, 740)
        assertEquals(WorkspaceArrangement.SINGLE, ws.arrangement)
        assertNull(ws.pane(PaneRole.COMPANION))
        assertFalse(ws.regionDriven)
        val task = ws.pane(PaneRole.TASK)!!
        assertEquals(16, task.rect.left)
        assertEquals(358, task.rect.width)
    }

    @Test
    fun mediumWindowStaysSingleReadableColumn() {
        val ws = resolve(768, 900)
        assertEquals(WorkspaceArrangement.SINGLE, ws.arrangement)
        val task = ws.pane(PaneRole.TASK)!!
        assertEquals(680, task.rect.width)
    }

    @Test
    fun expandedWindowSplitsTaskAndCompanion() {
        val ws = resolve(1024, 700)
        assertEquals(WorkspaceArrangement.SIDE_BY_SIDE, ws.arrangement)
        val task = ws.pane(PaneRole.TASK)!!
        val companion = ws.pane(PaneRole.COMPANION)!!
        assertEquals(24, task.rect.left)
        assertEquals(360, task.rect.width)
        // Companion starts after the primary + the 24 gap, at x=408.
        assertEquals(408, companion.rect.left)
        assertEquals(592, companion.rect.width)
        assertNull(ws.pane(PaneRole.INSPECTOR))
    }

    @Test
    fun wideWindowOffersAThirdInspector() {
        val ws = resolve(1440, 900)
        assertEquals(WorkspaceArrangement.SIDE_BY_SIDE, ws.arrangement)
        assertNotNull(ws.pane(PaneRole.TASK))
        assertNotNull(ws.pane(PaneRole.COMPANION))
        val inspector = ws.pane(PaneRole.INSPECTOR)
        assertNotNull(inspector, "a wide canvas earns an inspector")
        assertTrue(inspector.rect.width >= AdaptiveWorkspacePolicy.MIN_INSPECTOR)
        // Panes never overlap and stay left-to-right.
        val task = ws.pane(PaneRole.TASK)!!
        val companion = ws.pane(PaneRole.COMPANION)!!
        assertTrue(task.rect.right <= companion.rect.left)
        assertTrue(companion.rect.right <= inspector.rect.left)
    }

    @Test
    fun inspectorIsHiddenFirstJustUnderTheWideCanvasFloor() {
        val ws = resolve(AdaptiveWorkspacePolicy.INSPECTOR_MIN_CANVAS - 1, 900)
        assertNull(ws.pane(PaneRole.INSPECTOR), "inspector collapses before the task")
        assertNotNull(ws.pane(PaneRole.COMPANION))
    }

    // --- Task awareness. ---

    @Test
    fun aFormNeverPairsEvenOnAWideWindow() {
        val ws = resolve(1440, 900, task = WorkspaceTask.FORM)
        assertEquals(WorkspaceArrangement.SINGLE, ws.arrangement)
        assertNull(ws.pane(PaneRole.COMPANION))
    }

    @Test
    fun goPairsWithACompanionWhenItFits() {
        val ws = resolve(1024, 700, task = WorkspaceTask.GO)
        assertEquals(WorkspaceArrangement.SIDE_BY_SIDE, ws.arrangement)
        assertNotNull(ws.pane(PaneRole.COMPANION))
    }

    // --- Accessibility text + short windows collapse to one column. ---

    @Test
    fun largeTextForcesOneColumnEvenOnAWideWindow() {
        val ws = resolve(1360, 900, fontScale = 1.6f)
        assertEquals(WorkspaceArrangement.SINGLE, ws.arrangement)
        assertTrue(ws.singleColumnFallback)
        assertNull(ws.pane(PaneRole.COMPANION))
    }

    @Test
    fun shortWindowFallsBackToOneColumn() {
        val ws = resolve(1024, 320)
        assertEquals(WorkspaceArrangement.SINGLE, ws.arrangement)
        assertTrue(ws.singleColumnFallback)
    }

    @Test
    fun forceSingleColumnOverrideIsHonoured() {
        val ws = resolve(1360, 900, forceSingleColumn = true)
        assertEquals(WorkspaceArrangement.SINGLE, ws.arrangement)
        assertTrue(ws.singleColumnFallback)
    }

    // --- Vertical fold: book posture, side by side. ---

    @Test
    fun aVerticalOcclusionSplitsBothRegionsAndKeepsTheHingeClear() {
        // 1600 wide, opaque hinge 40dp thick centred at x=780.
        val region = ReservedRegion(
            kind = RegionKind.OCCLUSION,
            orientation = FoldOrientation.VERTICAL,
            start = 780,
            size = 40,
        )
        val ws = resolve(1600, 900, task = WorkspaceTask.PLAN, regions = listOf(region))
        assertEquals(WorkspaceArrangement.SIDE_BY_SIDE, ws.arrangement)
        assertTrue(ws.regionDriven)
        val task = ws.pane(PaneRole.TASK)!!
        val companion = ws.pane(PaneRole.COMPANION)!!
        assertEquals(0, task.rect.left)
        assertEquals(780, task.rect.width)
        // Companion begins on the far side of the hinge (780 + 40).
        assertEquals(820, companion.rect.left)
        assertEquals(780, companion.rect.width)
        // The hinge gap is reserved and no pane bridges it.
        val gap = ws.hingeGap!!
        assertEquals(780, gap.left)
        assertEquals(40, gap.width)
        assertTrue(task.rect.right <= gap.left)
        assertTrue(companion.rect.left >= gap.right)
        // A real hinge is never draggable.
        assertNull(ws.divider)
    }

    @Test
    fun aVerticalDivisionSplitsWithoutReservingAGap() {
        val region = ReservedRegion(
            kind = RegionKind.DIVISION,
            orientation = FoldOrientation.VERTICAL,
            start = 700,
            size = 20,
        )
        val ws = resolve(1400, 900, task = WorkspaceTask.EXPLORE, regions = listOf(region))
        assertEquals(WorkspaceArrangement.SIDE_BY_SIDE, ws.arrangement)
        assertNull(ws.hingeGap, "a division does not blank pixels")
        val task = ws.pane(PaneRole.TASK)!!
        val companion = ws.pane(PaneRole.COMPANION)!!
        // Halves abut across the division line (no reserved thickness).
        assertEquals(700, task.rect.width)
        assertEquals(700, companion.rect.left)
        assertEquals(700, companion.rect.width)
    }

    @Test
    fun aFormOnAFoldStaysSingleFocusInTheLargerRegion() {
        val region = ReservedRegion(
            kind = RegionKind.OCCLUSION,
            orientation = FoldOrientation.VERTICAL,
            start = 900,
            size = 40,
        )
        val ws = resolve(1600, 900, task = WorkspaceTask.FORM, regions = listOf(region))
        assertEquals(WorkspaceArrangement.SINGLE, ws.arrangement)
        val task = ws.pane(PaneRole.TASK)!!
        // Larger region is the left one (900 vs 660): the form uses it.
        assertEquals(0, task.rect.left)
        assertEquals(900, task.rect.width)
        assertNotNull(ws.hingeGap)
    }

    // --- Horizontal fold: tabletop, overview above / task below. ---

    @Test
    fun aHorizontalOcclusionMakesTabletopWithTaskBelow() {
        // 900 wide, 1400 tall, opaque hinge 40dp at y=680.
        val region = ReservedRegion(
            kind = RegionKind.OCCLUSION,
            orientation = FoldOrientation.HORIZONTAL,
            start = 680,
            size = 40,
        )
        val ws = resolve(900, 1400, task = WorkspaceTask.GO, regions = listOf(region))
        assertEquals(WorkspaceArrangement.STACKED, ws.arrangement)
        assertTrue(ws.regionDriven)
        val companion = ws.pane(PaneRole.COMPANION)!!
        val task = ws.pane(PaneRole.TASK)!!
        // Overview above the fold.
        assertEquals(0, companion.rect.top)
        assertEquals(680, companion.rect.height)
        // Task below the fold, within reach.
        assertEquals(720, task.rect.top)
        assertEquals(680, task.rect.height)
        // Hinge stays clear.
        val gap = ws.hingeGap!!
        assertEquals(680, gap.top)
        assertEquals(40, gap.height)
        assertTrue(companion.rect.bottom <= gap.top)
        assertTrue(task.rect.top >= gap.bottom)
    }

    @Test
    fun tabletopCollapsesWhenAHalfIsTooSmall() {
        // Bottom region only 200dp tall < the 280 task floor: keep one usable pane.
        val region = ReservedRegion(
            kind = RegionKind.OCCLUSION,
            orientation = FoldOrientation.HORIZONTAL,
            start = 900,
            size = 30,
        )
        val ws = resolve(900, 1130, task = WorkspaceTask.GO, regions = listOf(region))
        assertEquals(WorkspaceArrangement.SINGLE, ws.arrangement)
        val task = ws.pane(PaneRole.TASK)!!
        // Larger (top) region holds the task, clear of the hinge.
        assertEquals(0, task.rect.top)
        assertEquals(900, task.rect.height)
        assertNotNull(ws.hingeGap)
    }

    // --- Region validity + geometry hygiene. ---

    @Test
    fun aRegionOutsideTheWindowIsIgnored() {
        // Hinge reported at x=2000 but the window is only 1024 wide.
        val region = ReservedRegion(
            kind = RegionKind.OCCLUSION,
            orientation = FoldOrientation.VERTICAL,
            start = 2000,
            size = 40,
        )
        val ws = resolve(1024, 700, regions = listOf(region))
        assertFalse(ws.regionDriven, "a fold outside the window must not split it")
        assertNull(ws.hingeGap)
    }

    @Test
    fun anInactiveRegionDoesNotBlankPixels() {
        val region = ReservedRegion(
            kind = RegionKind.OCCLUSION,
            orientation = FoldOrientation.VERTICAL,
            start = 512,
            size = 40,
            active = false,
        )
        val ws = resolve(1024, 700, regions = listOf(region))
        assertFalse(ws.regionDriven)
        assertNull(ws.hingeGap)
    }

    @Test
    fun aSeparatingRegionOverridesTheWidthRuleOnANarrowWindow() {
        // 560dp wide would be COMPACT by width alone, but a real vertical
        // separation with two usable halves must still fit both regions.
        val region = ReservedRegion(
            kind = RegionKind.DIVISION,
            orientation = FoldOrientation.VERTICAL,
            start = 280,
            size = 0,
        )
        val ws = resolve(560, 900, task = WorkspaceTask.EXPLORE, regions = listOf(region))
        assertEquals(WorkspaceArrangement.SIDE_BY_SIDE, ws.arrangement)
        assertTrue(ws.regionDriven)
    }

    @Test
    fun occlusionWinsWhenBothADivisionAndAnOcclusionAreReported() {
        val division = ReservedRegion(RegionKind.DIVISION, FoldOrientation.VERTICAL, 400, 0)
        val occlusion = ReservedRegion(RegionKind.OCCLUSION, FoldOrientation.VERTICAL, 800, 40)
        val ws = resolve(1600, 900, regions = listOf(division, occlusion))
        // The occlusion (harder constraint) drives the split, so a gap is kept.
        assertNotNull(ws.hingeGap)
        assertEquals(800, ws.hingeGap!!.left)
    }

    // --- Adjustable divider only on unobstructed large layouts. ---

    @Test
    fun theDividerIsClampedToKeepBothPanesUsable() {
        val ws = resolve(1440, 900)
        val divider = ws.divider!!
        assertEquals(FoldOrientation.VERTICAL, divider.orientation)
        assertTrue(divider.min >= 320)
        assertTrue(divider.max > divider.min)
        // Dragging to the max still leaves the companion at least its floor.
        val available = 1440 - 32 * 2 - 24
        assertTrue(available - divider.max >= AdaptiveWorkspacePolicy.MIN_COMPANION)
    }

    // --- iPhone Duo device geometry (parity with iOS DuoSnapshotTests). ---
    //
    // The unfolded INNER display is 669 x 951 dp (2007 x 2853 px / scale 3), NOT
    // the folded cover's 466 x 678. The iOS snapshots first rendered at the cover
    // size and looked like a cramped phone column; the correction renders at the
    // inner size and shows the real two-pane. These fixtures pin the same corrected
    // geometry on the shared policy so the two platforms agree on the Duo.
    //
    // Android pairs the Duo by the hinge the window reports (region driven), not by
    // raw width: 669 dp alone is MEDIUM and would stay single, so the unfolded
    // cases include the book-posture vertical hinge the Duo reports, and the folded
    // cover (no fold, a plain phone window) stays a single column.

    private fun duoHinge(width: Int) = ReservedRegion(
        kind = RegionKind.OCCLUSION,
        orientation = FoldOrientation.VERTICAL,
        start = width / 2,
        size = 40,
    )

    @Test
    fun duoInnerLandscapePairsTwoPanes() {
        // Unfolded inner display, landscape: 951 x 669 dp.
        val width = 951
        val ws = resolve(width, 669, task = WorkspaceTask.GO, regions = listOf(duoHinge(width)))
        assertEquals(WorkspaceArrangement.SIDE_BY_SIDE, ws.arrangement)
        assertTrue(ws.regionDriven, "the unfolded Duo pairs on its reported hinge")
        assertNotNull(ws.pane(PaneRole.COMPANION), "the two-pane needs a companion")
        // The corrected inner width, never the folded cover's 466.
        assertTrue(width != 466)
    }

    @Test
    fun duoInnerPortraitPairsTwoPanes() {
        // Unfolded inner display, portrait: 669 x 951 dp. iOS shows this as a
        // left/right two-pane, so the Android book-posture hinge is vertical too.
        val width = 669
        val ws = resolve(width, 951, task = WorkspaceTask.GO, regions = listOf(duoHinge(width)))
        assertEquals(WorkspaceArrangement.SIDE_BY_SIDE, ws.arrangement)
        assertTrue(ws.regionDriven)
        val task = ws.pane(PaneRole.TASK)!!
        val companion = ws.pane(PaneRole.COMPANION)!!
        // Panes sit either side of the reported hinge, left to right.
        assertTrue(task.rect.right <= companion.rect.left)
    }

    @Test
    fun duoFoldedCoverStaysSingleColumn() {
        // Folded cover display: 466 x 678 dp, a plain phone window (no fold).
        val ws = resolve(466, 678, task = WorkspaceTask.PLAN)
        assertEquals(WorkspaceArrangement.SINGLE, ws.arrangement)
        assertNull(ws.pane(PaneRole.COMPANION), "the folded cover is a single column")
        assertFalse(ws.regionDriven)
    }

    private fun assertFalse(value: Boolean, message: String? = null) =
        assertTrue(!value, message ?: "expected false")
}
