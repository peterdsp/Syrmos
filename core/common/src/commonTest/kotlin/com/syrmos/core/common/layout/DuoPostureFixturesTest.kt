package com.syrmos.core.common.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The six iPhone Duo postures and six transitions from
 * `docs/plans/IPHONE-DUO-SIX-POSTURES-AWARD-DESIGN-PROMPT.md` (sections 3 and 11),
 * pinned on the shared policy. Every case here has a Swift twin with the same
 * name, the same inputs and the same expected numbers in
 * `iosApp/iosAppTests/DuoPostureFixturesTests.swift`; change both together.
 *
 * Geometry (points, measured on the booted Duo simulator):
 *   cover (folded)      466 x 678
 *   inner, portrait     669 x 951
 *   inner, landscape    951 x 669
 *
 * The policy never sees a posture name. A posture is a window size plus the
 * regions the system reports. Which orientation carries the physical hinge is a
 * runtime fact to observe on the Duo; the fixtures therefore pin a vertical
 * division in the upright window (book) and a horizontal division in the wide
 * window (laptop, tent) and treat the other pairing as the same policy path.
 */
class DuoPostureFixturesTest {

    private companion object {
        const val COVER_W = 466
        const val COVER_H = 678
        const val INNER_W = 669
        const val INNER_H = 951
    }

    private fun resolve(
        width: Int,
        height: Int,
        task: WorkspaceTask,
        regions: List<ReservedRegion> = emptyList(),
        fontScale: Float = 1f,
    ) = AdaptiveWorkspacePolicy.resolve(width, height, task, regions, fontScale)

    private fun division(orientation: FoldOrientation, start: Int, active: Boolean = true) =
        ReservedRegion(RegionKind.DIVISION, orientation, start, 0, active)

    // Book: upright window, vertical division at the midpoint.
    private val bookDivision = division(FoldOrientation.VERTICAL, INNER_W / 2)

    // Laptop and tent: wide window, horizontal division at the midpoint.
    private val laptopDivision = division(FoldOrientation.HORIZONTAL, INNER_W / 2)

    // --- P1 Pocket: the folded cover is one phone column. ---

    @Test
    fun p1_pocket_coverIsASingleColumn() {
        val ws = resolve(COVER_W, COVER_H, WorkspaceTask.PLAN)
        assertEquals(WorkspaceArrangement.SINGLE, ws.arrangement)
        assertNull(ws.pane(PaneRole.COMPANION))
        assertFalse(ws.regionDriven)
        val task = ws.pane(PaneRole.TASK)!!
        assertEquals(16, task.rect.left)
        assertEquals(COVER_W - 32, task.rect.width)
    }

    // --- P2 Platform glance: tent stacks on the horizontal division. ---

    @Test
    fun p2_tent_stacksOnTheHorizontalDivision() {
        val ws = resolve(INNER_H, INNER_W, WorkspaceTask.HOME, listOf(laptopDivision))
        assertEquals(WorkspaceArrangement.STACKED, ws.arrangement)
        assertTrue(ws.regionDriven)
        assertNull(ws.hingeGap, "a division does not blank pixels")
        val companion = ws.pane(PaneRole.COMPANION)!!
        val task = ws.pane(PaneRole.TASK)!!
        assertEquals(WorkspaceRect(0, 0, INNER_H, 334), companion.rect)
        assertEquals(WorkspaceRect(0, 334, INNER_H, 335), task.rect)
    }

    // --- P3 Atlas: flat landscape pairs beside a wide map. ---

    @Test
    fun p3_atlas_pairsTaskBesideAWideMap() {
        val ws = resolve(INNER_H, INNER_W, WorkspaceTask.PLAN)
        assertEquals(WorkspaceArrangement.SIDE_BY_SIDE, ws.arrangement)
        assertFalse(ws.regionDriven)
        val task = ws.pane(PaneRole.TASK)!!
        val companion = ws.pane(PaneRole.COMPANION)!!
        assertEquals(WorkspaceRect(24, 0, 360, INNER_W), task.rect)
        assertEquals(WorkspaceRect(408, 0, 519, INNER_W), companion.rect)
        assertTrue(companion.rect.width >= 480, "the Atlas map never falls below 480")
        assertNull(ws.pane(PaneRole.INSPECTOR), "no inspector on the Duo")
        assertNotNull(ws.divider, "an unobstructed wide layout may adjust its split")
    }

    @Test
    fun p3_atlas_goPairsInstructionBesideTheMap() {
        val ws = resolve(INNER_H, INNER_W, WorkspaceTask.GO)
        assertEquals(WorkspaceArrangement.SIDE_BY_SIDE, ws.arrangement)
        assertNotNull(ws.pane(PaneRole.COMPANION))
    }

    // --- P4 Timetable book: panes align to the vertical division. ---

    @Test
    fun p4_book_alignsPanesToTheDivision() {
        val ws = resolve(INNER_W, INNER_H, WorkspaceTask.PLAN, listOf(bookDivision))
        assertEquals(WorkspaceArrangement.SIDE_BY_SIDE, ws.arrangement)
        assertTrue(ws.regionDriven)
        assertNull(ws.hingeGap)
        assertNull(ws.divider, "no dragging across a real division")
        val task = ws.pane(PaneRole.TASK)!!
        val companion = ws.pane(PaneRole.COMPANION)!!
        assertEquals(WorkspaceRect(0, 0, 334, INNER_H), task.rect)
        assertEquals(WorkspaceRect(334, 0, 335, INNER_H), companion.rect)
    }

    @Test
    fun p4_book_goPairsOnTheDivisionToo() {
        // The region overrides the task's tall-canvas preference for stacking.
        val ws = resolve(INNER_W, INNER_H, WorkspaceTask.GO, listOf(bookDivision))
        assertEquals(WorkspaceArrangement.SIDE_BY_SIDE, ws.arrangement)
    }

    // --- P5 Tall canvas: flat portrait, axis by task. ---

    @Test
    fun p5_tallCanvas_planPairsSideBySideAtHalfWidth() {
        val ws = resolve(INNER_W, INNER_H, WorkspaceTask.PLAN)
        assertEquals(WorkspaceArrangement.SIDE_BY_SIDE, ws.arrangement)
        assertFalse(ws.regionDriven)
        assertNull(ws.divider)
        val task = ws.pane(PaneRole.TASK)!!
        val companion = ws.pane(PaneRole.COMPANION)!!
        assertEquals(WorkspaceRect(0, 0, 334, INNER_H), task.rect)
        assertEquals(WorkspaceRect(334, 0, 335, INNER_H), companion.rect)
    }

    @Test
    fun p5_tallCanvas_goStacksMapAboveTimeline() {
        val ws = resolve(INNER_W, INNER_H, WorkspaceTask.GO)
        assertEquals(WorkspaceArrangement.STACKED, ws.arrangement)
        assertFalse(ws.regionDriven)
        val companion = ws.pane(PaneRole.COMPANION)!!
        val task = ws.pane(PaneRole.TASK)!!
        // 45 percent of 951 is 427, above the 360 map floor.
        assertEquals(WorkspaceRect(0, 0, INNER_W, 427), companion.rect)
        assertEquals(WorkspaceRect(0, 427, INNER_W, 524), task.rect)
        assertTrue(companion.rect.height >= AdaptiveWorkspacePolicy.TALL_MIN_COMPANION)
    }

    @Test
    fun p5_tallCanvas_exploreStacksMapAboveList() {
        val ws = resolve(INNER_W, INNER_H, WorkspaceTask.EXPLORE)
        assertEquals(WorkspaceArrangement.STACKED, ws.arrangement)
        assertEquals(0, ws.pane(PaneRole.COMPANION)!!.rect.top)
    }

    @Test
    fun p5_tallCanvas_largerTextTurnsPlanFromSideBySideToStacked() {
        // At 1.2x the side-by-side floors (360 task, 384 map) no longer fit in
        // 334, but stacking (432 map + 336 task) still fits in 951.
        val ws = resolve(INNER_W, INNER_H, WorkspaceTask.PLAN, fontScale = 1.2f)
        assertEquals(WorkspaceArrangement.STACKED, ws.arrangement)
        val companion = ws.pane(PaneRole.COMPANION)!!
        assertEquals(432, companion.rect.height)
        assertEquals(519, ws.pane(PaneRole.TASK)!!.rect.height)
    }

    @Test
    fun p5_tallCanvas_accessibilityTextCollapsesToOneColumn() {
        val ws = resolve(INNER_W, INNER_H, WorkspaceTask.PLAN, fontScale = 1.6f)
        assertEquals(WorkspaceArrangement.SINGLE, ws.arrangement)
        assertTrue(ws.singleColumnFallback)
    }

    @Test
    fun p5_tallCanvas_formKeepsAReadableColumn() {
        val ws = resolve(INNER_W, INNER_H, WorkspaceTask.FORM)
        assertEquals(WorkspaceArrangement.SINGLE, ws.arrangement)
        val task = ws.pane(PaneRole.TASK)!!
        assertEquals(24, task.rect.left)
        assertEquals(621, task.rect.width)
    }

    // --- P6 Tray table: laptop, look above, touch below. ---

    @Test
    fun p6_laptop_goStacksMapAboveControls() {
        val ws = resolve(INNER_H, INNER_W, WorkspaceTask.GO, listOf(laptopDivision))
        assertEquals(WorkspaceArrangement.STACKED, ws.arrangement)
        assertTrue(ws.regionDriven)
        assertEquals(WorkspaceRect(0, 0, INNER_H, 334), ws.pane(PaneRole.COMPANION)!!.rect)
        assertEquals(WorkspaceRect(0, 334, INNER_H, 335), ws.pane(PaneRole.TASK)!!.rect)
    }

    @Test
    fun p6_laptop_keyboardCollapsesPlanToTheTaskAboveIt() {
        // The keyboard takes the lower half: the caller reports 369 usable
        // height, the division stays at 334, so the lower region (35) cannot
        // hold the task and the query keeps the upper region, clear of the fold.
        val ws = resolve(INNER_H, 369, WorkspaceTask.PLAN, listOf(laptopDivision))
        assertEquals(WorkspaceArrangement.SINGLE, ws.arrangement)
        assertTrue(ws.regionDriven)
        assertEquals(WorkspaceRect(0, 0, INNER_H, 334), ws.pane(PaneRole.TASK)!!.rect)
        assertNull(ws.pane(PaneRole.COMPANION))
    }

    // --- Transitions: the same task through consecutive geometries. ---

    @Test
    fun t1_unfold_planGainsACompanionWithoutLosingTheTask() {
        val before = resolve(COVER_W, COVER_H, WorkspaceTask.PLAN)
        val after = resolve(INNER_W, INNER_H, WorkspaceTask.PLAN)
        assertEquals(WorkspaceArrangement.SINGLE, before.arrangement)
        assertEquals(WorkspaceArrangement.SIDE_BY_SIDE, after.arrangement)
        assertNotNull(before.pane(PaneRole.TASK))
        assertNotNull(after.pane(PaneRole.TASK))
        assertNotNull(after.pane(PaneRole.COMPANION))
    }

    @Test
    fun t2_close_goReturnsToOneColumn() {
        val open = resolve(INNER_W, INNER_H, WorkspaceTask.GO)
        val closed = resolve(COVER_W, COVER_H, WorkspaceTask.GO)
        assertEquals(WorkspaceArrangement.STACKED, open.arrangement)
        assertEquals(WorkspaceArrangement.SINGLE, closed.arrangement)
        assertNotNull(closed.pane(PaneRole.TASK))
    }

    @Test
    fun t3_flatToBook_leavesNoGutterAndMovesNoPane() {
        val inactive = division(FoldOrientation.VERTICAL, INNER_W / 2, active = false)
        val flat = resolve(INNER_W, INNER_H, WorkspaceTask.PLAN, listOf(inactive))
        val book = resolve(INNER_W, INNER_H, WorkspaceTask.PLAN, listOf(bookDivision))
        assertFalse(flat.regionDriven, "an inactive division must not drive the split")
        assertTrue(book.regionDriven)
        assertNull(flat.hingeGap)
        assertNull(book.hingeGap)
        assertEquals(flat.pane(PaneRole.TASK)!!.rect, book.pane(PaneRole.TASK)!!.rect)
        assertEquals(flat.pane(PaneRole.COMPANION)!!.rect, book.pane(PaneRole.COMPANION)!!.rect)
    }

    @Test
    fun t4_portraitToLaptop_keepsTheMapOnTop() {
        val portrait = resolve(INNER_W, INNER_H, WorkspaceTask.GO)
        val laptop = resolve(INNER_H, INNER_W, WorkspaceTask.GO, listOf(laptopDivision))
        assertEquals(WorkspaceArrangement.STACKED, portrait.arrangement)
        assertEquals(WorkspaceArrangement.STACKED, laptop.arrangement)
        assertEquals(0, portrait.pane(PaneRole.COMPANION)!!.rect.top)
        assertEquals(0, laptop.pane(PaneRole.COMPANION)!!.rect.top)
        assertTrue(portrait.pane(PaneRole.TASK)!!.rect.top > 0)
        assertTrue(laptop.pane(PaneRole.TASK)!!.rect.top > 0)
    }

    @Test
    fun t5_laptopToTent_isTheSamePolicyPath() {
        val laptop = resolve(INNER_H, INNER_W, WorkspaceTask.GO, listOf(laptopDivision))
        val tent = resolve(INNER_H, INNER_W, WorkspaceTask.HOME, listOf(laptopDivision))
        assertEquals(laptop.arrangement, tent.arrangement)
        assertEquals(laptop.pane(PaneRole.COMPANION)!!.rect, tent.pane(PaneRole.COMPANION)!!.rect)
    }

    @Test
    fun t6_pinnedVideo_shortWindowIsNotTabletop() {
        val ws = resolve(INNER_H, 400, WorkspaceTask.GO)
        assertEquals(WorkspaceArrangement.SINGLE, ws.arrangement)
        assertTrue(ws.singleColumnFallback)
        assertFalse(ws.regionDriven)
    }

    // --- Departures task: planning cards beside the live board. ---

    @Test
    fun departures_pairsSideBySideOnBothInnerOrientations() {
        assertEquals(WorkspaceArrangement.SIDE_BY_SIDE, resolve(INNER_H, INNER_W, WorkspaceTask.DEPARTURES).arrangement)
        assertEquals(WorkspaceArrangement.SIDE_BY_SIDE, resolve(INNER_W, INNER_H, WorkspaceTask.DEPARTURES).arrangement)
        assertEquals(WorkspaceArrangement.SINGLE, resolve(COVER_W, COVER_H, WorkspaceTask.DEPARTURES).arrangement)
    }

    // --- Wide medium window: a fold's inner display in landscape beside a rail. ---

    @Test
    fun wideMediumWindow_pairsSideBySideEvenForStackingTasks() {
        // 761 x 649 (841 x 673 minus an 80 dp rail and the status bar): wider than
        // tall, so Explore and GO pair as two columns, not a band over a list.
        for (task in listOf(WorkspaceTask.EXPLORE, WorkspaceTask.GO, WorkspaceTask.PLAN)) {
            val ws = resolve(761, 649, task)
            assertEquals(WorkspaceArrangement.SIDE_BY_SIDE, ws.arrangement, task.name)
            assertEquals(380, ws.pane(PaneRole.TASK)!!.rect.width, task.name)
        }
        // The tall canvas keeps the task's preference.
        assertEquals(WorkspaceArrangement.STACKED, resolve(INNER_W, INNER_H, WorkspaceTask.EXPLORE).arrangement)
    }

    private fun assertFalse(value: Boolean, message: String? = null) =
        assertTrue(!value, message ?: "expected false")
}
