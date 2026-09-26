import XCTest
import SwiftUI
@testable import Syrmos

/// Swift twin of the Kotlin `DuoPostureFixturesTest` (plus the general cases of
/// `AdaptiveWorkspaceTest`) so both platforms resolve the six iPhone Duo postures
/// and six transitions from `docs/plans/IPHONE-DUO-SIX-POSTURES-AWARD-DESIGN-PROMPT.md`
/// to the same arrangement, the same pane rectangles and the same collapse
/// decisions. Same names, same inputs, same numbers: change both together.
///
/// Geometry (points, measured on the booted Duo simulator):
///   cover (folded)      466 x 678
///   inner, portrait     669 x 951
///   inner, landscape    951 x 669
final class DuoPostureFixturesTests: XCTestCase {

    private let coverW = 466, coverH = 678
    private let innerW = 669, innerH = 951

    private typealias Policy = SyrmosAdaptiveWorkspacePolicy
    private typealias Rect = SyrmosWorkspaceRect

    private func resolve(
        _ width: Int, _ height: Int, _ task: SyrmosWorkspaceTask,
        regions: [SyrmosReservedRegion] = [], fontScale: Float = 1,
        forceSingleColumn: Bool = false
    ) -> SyrmosAdaptiveWorkspace {
        Policy.resolve(width: width, height: height, task: task, regions: regions,
                       fontScale: fontScale, forceSingleColumn: forceSingleColumn)
    }

    private func division(_ o: SyrmosFoldOrientation, _ start: Int, active: Bool = true) -> SyrmosReservedRegion {
        SyrmosReservedRegion(kind: .division, orientation: o, start: start, size: 0, active: active)
    }

    private func occlusion(_ o: SyrmosFoldOrientation, _ start: Int, _ size: Int, active: Bool = true) -> SyrmosReservedRegion {
        SyrmosReservedRegion(kind: .occlusion, orientation: o, start: start, size: size, active: active)
    }

    // Book: upright window, vertical division at the midpoint.
    private var bookDivision: SyrmosReservedRegion { division(.vertical, innerW / 2) }
    // Laptop and tent: wide window, horizontal division at the midpoint.
    private var laptopDivision: SyrmosReservedRegion { division(.horizontal, innerW / 2) }

    // MARK: P1 Pocket

    func test_p1_pocket_coverIsASingleColumn() {
        let ws = resolve(coverW, coverH, .plan)
        XCTAssertEqual(ws.arrangement, .single)
        XCTAssertNil(ws.pane(.companion))
        XCTAssertFalse(ws.regionDriven)
        XCTAssertEqual(ws.pane(.task)?.rect, Rect(left: 16, top: 0, width: coverW - 32, height: coverH))
    }

    // MARK: P2 Platform glance (tent)

    func test_p2_tent_stacksOnTheHorizontalDivision() {
        let ws = resolve(innerH, innerW, .home, regions: [laptopDivision])
        XCTAssertEqual(ws.arrangement, .stacked)
        XCTAssertTrue(ws.regionDriven)
        XCTAssertNil(ws.hingeGap, "a division does not blank pixels")
        XCTAssertEqual(ws.pane(.companion)?.rect, Rect(left: 0, top: 0, width: innerH, height: 334))
        XCTAssertEqual(ws.pane(.task)?.rect, Rect(left: 0, top: 334, width: innerH, height: 335))
    }

    // MARK: P3 Atlas

    func test_p3_atlas_pairsTaskBesideAWideMap() {
        let ws = resolve(innerH, innerW, .plan)
        XCTAssertEqual(ws.arrangement, .sideBySide)
        XCTAssertFalse(ws.regionDriven)
        XCTAssertEqual(ws.pane(.task)?.rect, Rect(left: 24, top: 0, width: 360, height: innerW))
        XCTAssertEqual(ws.pane(.companion)?.rect, Rect(left: 408, top: 0, width: 519, height: innerW))
        XCTAssertGreaterThanOrEqual(ws.pane(.companion)!.rect.width, 480, "the Atlas map never falls below 480")
        XCTAssertNil(ws.pane(.inspector), "no inspector on the Duo")
        XCTAssertNotNil(ws.divider, "an unobstructed wide layout may adjust its split")
    }

    func test_p3_atlas_goPairsInstructionBesideTheMap() {
        let ws = resolve(innerH, innerW, .go)
        XCTAssertEqual(ws.arrangement, .sideBySide)
        XCTAssertNotNil(ws.pane(.companion))
    }

    // MARK: P4 Timetable book

    func test_p4_book_alignsPanesToTheDivision() {
        let ws = resolve(innerW, innerH, .plan, regions: [bookDivision])
        XCTAssertEqual(ws.arrangement, .sideBySide)
        XCTAssertTrue(ws.regionDriven)
        XCTAssertNil(ws.hingeGap)
        XCTAssertNil(ws.divider, "no dragging across a real division")
        XCTAssertEqual(ws.pane(.task)?.rect, Rect(left: 0, top: 0, width: 334, height: innerH))
        XCTAssertEqual(ws.pane(.companion)?.rect, Rect(left: 334, top: 0, width: 335, height: innerH))
    }

    func test_p4_book_goPairsOnTheDivisionToo() {
        let ws = resolve(innerW, innerH, .go, regions: [bookDivision])
        XCTAssertEqual(ws.arrangement, .sideBySide)
    }

    // MARK: P5 Tall canvas

    func test_p5_tallCanvas_planPairsSideBySideAtHalfWidth() {
        let ws = resolve(innerW, innerH, .plan)
        XCTAssertEqual(ws.arrangement, .sideBySide)
        XCTAssertFalse(ws.regionDriven)
        XCTAssertNil(ws.divider)
        XCTAssertEqual(ws.pane(.task)?.rect, Rect(left: 0, top: 0, width: 334, height: innerH))
        XCTAssertEqual(ws.pane(.companion)?.rect, Rect(left: 334, top: 0, width: 335, height: innerH))
    }

    func test_p5_tallCanvas_goStacksMapAboveTimeline() {
        let ws = resolve(innerW, innerH, .go)
        XCTAssertEqual(ws.arrangement, .stacked)
        XCTAssertFalse(ws.regionDriven)
        XCTAssertEqual(ws.pane(.companion)?.rect, Rect(left: 0, top: 0, width: innerW, height: 427))
        XCTAssertEqual(ws.pane(.task)?.rect, Rect(left: 0, top: 427, width: innerW, height: 524))
        XCTAssertGreaterThanOrEqual(ws.pane(.companion)!.rect.height, Policy.tallMinCompanion)
    }

    func test_p5_tallCanvas_exploreStacksMapAboveList() {
        let ws = resolve(innerW, innerH, .explore)
        XCTAssertEqual(ws.arrangement, .stacked)
        XCTAssertEqual(ws.pane(.companion)?.rect.top, 0)
    }

    func test_p5_tallCanvas_largerTextTurnsPlanFromSideBySideToStacked() {
        let ws = resolve(innerW, innerH, .plan, fontScale: 1.2)
        XCTAssertEqual(ws.arrangement, .stacked)
        XCTAssertEqual(ws.pane(.companion)?.rect.height, 432)
        XCTAssertEqual(ws.pane(.task)?.rect.height, 519)
    }

    func test_p5_tallCanvas_accessibilityTextCollapsesToOneColumn() {
        let ws = resolve(innerW, innerH, .plan, fontScale: 1.6)
        XCTAssertEqual(ws.arrangement, .single)
        XCTAssertTrue(ws.singleColumnFallback)
    }

    func test_p5_tallCanvas_formKeepsAReadableColumn() {
        let ws = resolve(innerW, innerH, .form)
        XCTAssertEqual(ws.arrangement, .single)
        XCTAssertEqual(ws.pane(.task)?.rect.left, 24)
        XCTAssertEqual(ws.pane(.task)?.rect.width, 621)
    }

    // MARK: P6 Tray table (laptop)

    func test_p6_laptop_goStacksMapAboveControls() {
        let ws = resolve(innerH, innerW, .go, regions: [laptopDivision])
        XCTAssertEqual(ws.arrangement, .stacked)
        XCTAssertTrue(ws.regionDriven)
        XCTAssertEqual(ws.pane(.companion)?.rect, Rect(left: 0, top: 0, width: innerH, height: 334))
        XCTAssertEqual(ws.pane(.task)?.rect, Rect(left: 0, top: 334, width: innerH, height: 335))
    }

    func test_p6_laptop_keyboardCollapsesPlanToTheTaskAboveIt() {
        let ws = resolve(innerH, 369, .plan, regions: [laptopDivision])
        XCTAssertEqual(ws.arrangement, .single)
        XCTAssertTrue(ws.regionDriven)
        XCTAssertEqual(ws.pane(.task)?.rect, Rect(left: 0, top: 0, width: innerH, height: 334))
        XCTAssertNil(ws.pane(.companion))
    }

    // MARK: Transitions

    func test_t1_unfold_planGainsACompanionWithoutLosingTheTask() {
        let before = resolve(coverW, coverH, .plan)
        let after = resolve(innerW, innerH, .plan)
        XCTAssertEqual(before.arrangement, .single)
        XCTAssertEqual(after.arrangement, .sideBySide)
        XCTAssertNotNil(before.pane(.task))
        XCTAssertNotNil(after.pane(.task))
        XCTAssertNotNil(after.pane(.companion))
    }

    func test_t2_close_goReturnsToOneColumn() {
        let open = resolve(innerW, innerH, .go)
        let closed = resolve(coverW, coverH, .go)
        XCTAssertEqual(open.arrangement, .stacked)
        XCTAssertEqual(closed.arrangement, .single)
        XCTAssertNotNil(closed.pane(.task))
    }

    func test_t3_flatToBook_leavesNoGutterAndMovesNoPane() {
        let flat = resolve(innerW, innerH, .plan, regions: [division(.vertical, innerW / 2, active: false)])
        let book = resolve(innerW, innerH, .plan, regions: [bookDivision])
        XCTAssertFalse(flat.regionDriven, "an inactive division must not drive the split")
        XCTAssertTrue(book.regionDriven)
        XCTAssertNil(flat.hingeGap)
        XCTAssertNil(book.hingeGap)
        XCTAssertEqual(flat.pane(.task)?.rect, book.pane(.task)?.rect)
        XCTAssertEqual(flat.pane(.companion)?.rect, book.pane(.companion)?.rect)
    }

    func test_t4_portraitToLaptop_keepsTheMapOnTop() {
        let portrait = resolve(innerW, innerH, .go)
        let laptop = resolve(innerH, innerW, .go, regions: [laptopDivision])
        XCTAssertEqual(portrait.arrangement, .stacked)
        XCTAssertEqual(laptop.arrangement, .stacked)
        XCTAssertEqual(portrait.pane(.companion)?.rect.top, 0)
        XCTAssertEqual(laptop.pane(.companion)?.rect.top, 0)
        XCTAssertGreaterThan(portrait.pane(.task)!.rect.top, 0)
        XCTAssertGreaterThan(laptop.pane(.task)!.rect.top, 0)
    }

    func test_t5_laptopToTent_isTheSamePolicyPath() {
        let laptop = resolve(innerH, innerW, .go, regions: [laptopDivision])
        let tent = resolve(innerH, innerW, .home, regions: [laptopDivision])
        XCTAssertEqual(laptop.arrangement, tent.arrangement)
        XCTAssertEqual(laptop.pane(.companion)?.rect, tent.pane(.companion)?.rect)
    }

    func test_t6_pinnedVideo_shortWindowIsNotTabletop() {
        let ws = resolve(innerH, 400, .go)
        XCTAssertEqual(ws.arrangement, .single)
        XCTAssertTrue(ws.singleColumnFallback)
        XCTAssertFalse(ws.regionDriven)
    }

    // MARK: General parity with AdaptiveWorkspaceTest.kt

    func test_compactWindowIsASingleTaskPane() {
        let ws = resolve(390, 740, .plan)
        XCTAssertEqual(ws.arrangement, .single)
        XCTAssertEqual(ws.pane(.task)?.rect, Rect(left: 16, top: 0, width: 358, height: 740))
    }

    func test_mediumWindowPairsAPlannerSideBySide() {
        let ws = resolve(768, 900, .plan)
        XCTAssertEqual(ws.arrangement, .sideBySide)
        XCTAssertEqual(ws.pane(.task)?.rect, Rect(left: 0, top: 0, width: 384, height: 900))
        XCTAssertEqual(ws.pane(.companion)?.rect, Rect(left: 384, top: 0, width: 384, height: 900))
        XCTAssertNil(ws.divider)
    }

    func test_aFormOnAMediumWindowStaysSingleReadableColumn() {
        let ws = resolve(768, 900, .form)
        XCTAssertEqual(ws.arrangement, .single)
        XCTAssertEqual(ws.pane(.task)?.rect.width, 680)
    }

    func test_aMediumWindowTooNarrowForTheMapFloorStaysSingle() {
        let ws = resolve(600, 500, .plan)
        XCTAssertEqual(ws.arrangement, .single)
        XCTAssertNil(ws.pane(.companion))
    }

    func test_expandedWindowSplitsTaskAndCompanion() {
        let ws = resolve(1024, 700, .plan)
        XCTAssertEqual(ws.arrangement, .sideBySide)
        XCTAssertEqual(ws.pane(.task)?.rect, Rect(left: 24, top: 0, width: 360, height: 700))
        XCTAssertEqual(ws.pane(.companion)?.rect, Rect(left: 408, top: 0, width: 592, height: 700))
        XCTAssertNil(ws.pane(.inspector))
    }

    func test_wideWindowOffersAThirdInspector() {
        let ws = resolve(1440, 900, .plan)
        XCTAssertEqual(ws.arrangement, .sideBySide)
        let inspector = try! XCTUnwrap(ws.pane(.inspector))
        XCTAssertGreaterThanOrEqual(inspector.rect.width, Policy.minInspector)
        XCTAssertLessThanOrEqual(ws.pane(.task)!.rect.right, ws.pane(.companion)!.rect.left)
        XCTAssertLessThanOrEqual(ws.pane(.companion)!.rect.right, inspector.rect.left)
    }

    func test_inspectorIsHiddenFirstJustUnderTheWideCanvasFloor() {
        let ws = resolve(Policy.inspectorMinCanvas - 1, 900, .plan)
        XCTAssertNil(ws.pane(.inspector))
        XCTAssertNotNil(ws.pane(.companion))
    }

    func test_aFormNeverPairsEvenOnAWideWindow() {
        XCTAssertEqual(resolve(1440, 900, .form).arrangement, .single)
    }

    func test_largeTextForcesOneColumnEvenOnAWideWindow() {
        let ws = resolve(1360, 900, .plan, fontScale: 1.6)
        XCTAssertEqual(ws.arrangement, .single)
        XCTAssertTrue(ws.singleColumnFallback)
    }

    func test_shortWindowFallsBackToOneColumn() {
        let ws = resolve(1024, 320, .plan)
        XCTAssertEqual(ws.arrangement, .single)
        XCTAssertTrue(ws.singleColumnFallback)
    }

    func test_aVerticalOcclusionSplitsBothRegionsAndKeepsTheHingeClear() {
        let ws = resolve(1600, 900, .plan, regions: [occlusion(.vertical, 780, 40)])
        XCTAssertEqual(ws.arrangement, .sideBySide)
        XCTAssertTrue(ws.regionDriven)
        XCTAssertEqual(ws.pane(.task)?.rect, Rect(left: 0, top: 0, width: 780, height: 900))
        XCTAssertEqual(ws.pane(.companion)?.rect, Rect(left: 820, top: 0, width: 780, height: 900))
        XCTAssertEqual(ws.hingeGap, Rect(left: 780, top: 0, width: 40, height: 900))
        XCTAssertNil(ws.divider)
    }

    func test_aVerticalDivisionSplitsWithoutReservingAGap() {
        let ws = resolve(1400, 900, .explore, regions: [SyrmosReservedRegion(kind: .division, orientation: .vertical, start: 700, size: 20)])
        XCTAssertEqual(ws.arrangement, .sideBySide)
        XCTAssertNil(ws.hingeGap)
        XCTAssertEqual(ws.pane(.task)?.rect.width, 700)
        XCTAssertEqual(ws.pane(.companion)?.rect, Rect(left: 700, top: 0, width: 700, height: 900))
    }

    func test_aFormOnAFoldStaysSingleFocusInTheLargerRegion() {
        let ws = resolve(1600, 900, .form, regions: [occlusion(.vertical, 900, 40)])
        XCTAssertEqual(ws.arrangement, .single)
        XCTAssertEqual(ws.pane(.task)?.rect, Rect(left: 0, top: 0, width: 900, height: 900))
        XCTAssertNotNil(ws.hingeGap)
    }

    func test_aHorizontalOcclusionMakesTabletopWithTaskBelow() {
        let ws = resolve(900, 1400, .go, regions: [occlusion(.horizontal, 680, 40)])
        XCTAssertEqual(ws.arrangement, .stacked)
        XCTAssertEqual(ws.pane(.companion)?.rect, Rect(left: 0, top: 0, width: 900, height: 680))
        XCTAssertEqual(ws.pane(.task)?.rect, Rect(left: 0, top: 720, width: 900, height: 680))
        XCTAssertEqual(ws.hingeGap, Rect(left: 0, top: 680, width: 900, height: 40))
    }

    func test_tabletopCollapsesWhenAHalfIsTooSmall() {
        let ws = resolve(900, 1130, .go, regions: [occlusion(.horizontal, 900, 30)])
        XCTAssertEqual(ws.arrangement, .single)
        XCTAssertEqual(ws.pane(.task)?.rect, Rect(left: 0, top: 0, width: 900, height: 900))
        XCTAssertNotNil(ws.hingeGap)
    }

    func test_aRegionOutsideTheWindowIsIgnored() {
        let ws = resolve(1024, 700, .plan, regions: [occlusion(.vertical, 2000, 40)])
        XCTAssertFalse(ws.regionDriven)
        XCTAssertNil(ws.hingeGap)
    }

    func test_anInactiveRegionDoesNotBlankPixels() {
        let ws = resolve(1024, 700, .plan, regions: [occlusion(.vertical, 512, 40, active: false)])
        XCTAssertFalse(ws.regionDriven)
        XCTAssertNil(ws.hingeGap)
    }

    func test_aSeparatingRegionOverridesTheWidthRuleOnANarrowWindow() {
        let ws = resolve(560, 900, .explore, regions: [division(.vertical, 280)])
        XCTAssertEqual(ws.arrangement, .sideBySide)
        XCTAssertTrue(ws.regionDriven)
    }

    func test_occlusionWinsWhenBothADivisionAndAnOcclusionAreReported() {
        let ws = resolve(1600, 900, .plan, regions: [division(.vertical, 400), occlusion(.vertical, 800, 40)])
        XCTAssertEqual(ws.hingeGap?.left, 800)
    }

    func test_theDividerIsClampedToKeepBothPanesUsable() {
        let ws = resolve(1440, 900, .plan)
        let divider = try! XCTUnwrap(ws.divider)
        XCTAssertEqual(divider.orientation, .vertical)
        XCTAssertGreaterThanOrEqual(divider.min, 320)
        XCTAssertGreaterThan(divider.max, divider.min)
        let available = 1440 - 32 * 2 - 24
        XCTAssertGreaterThanOrEqual(available - divider.max, Policy.minCompanion)
    }

    func test_cgSizeEntryFloorsToWholePoints() {
        let ws = Policy.resolve(size: CGSize(width: 669.6, height: 951.3), task: .plan)
        XCTAssertEqual(ws.pane(.task)?.rect.width, 334)
    }

    // MARK: Parity with the shipped SyrmosArrangement threshold

    /// `SyrmosArrangement` is driven by the policy; its documented pairing floor
    /// (640 pt, the threshold the width-only container used to hard-code) must
    /// equal the narrowest width at which the policy pairs a planner side by
    /// side, so nothing that relied on 640 moved.
    func test_shippedArrangementThresholdMatchesThePolicyFloor() {
        XCTAssertEqual(Int(SyrmosArrangementRule.pairFloor), Policy.minMapPane * 2)
        XCTAssertEqual(resolve(Policy.minMapPane * 2, innerH, .plan).arrangement, .sideBySide)
        XCTAssertEqual(resolve(Policy.minMapPane * 2 - 1, innerH, .plan).arrangement, .stacked)
    }

    // MARK: Arrangement numbers derived from a workspace

    func test_arrangementRule_horizontalFallbackHonoursTaskWidthAndGap() {
        // P3 Atlas: task 24..384, companion from 408: column 384 wide, gap 24.
        let ws = resolve(innerH, innerW, .plan)
        XCTAssertEqual(SyrmosArrangementRule.taskExtent(ws, axis: .horizontal), 384)
        XCTAssertEqual(SyrmosArrangementRule.gap(ws, axis: .horizontal), 24)
        XCTAssertEqual(SyrmosArrangementRule.taskShare(ws, size: CGSize(width: 951, height: 669)), 384.0 / 951.0, accuracy: 0.001)
    }

    func test_arrangementRule_verticalFallbackPutsTheCompanionOnTop() {
        // P5 Tall canvas GO: companion 0..427, task from 427: band 427, no gap.
        let ws = resolve(innerW, innerH, .go)
        XCTAssertEqual(SyrmosArrangementRule.companionExtent(ws, axis: .vertical), 427)
        XCTAssertEqual(SyrmosArrangementRule.gap(ws, axis: .vertical), 0)
        XCTAssertEqual(SyrmosArrangementRule.companionShare(ws, size: CGSize(width: 669, height: 951)), 427.0 / 951.0, accuracy: 0.001)
    }

    func test_arrangementRule_occludingHingeLeavesItsThicknessEmpty() {
        let hinge = SyrmosReservedRegion(kind: .occlusion, orientation: .horizontal, start: 314, size: 40)
        let ws = resolve(innerH, innerW, .go, regions: [hinge])
        XCTAssertEqual(ws.arrangement, .stacked)
        XCTAssertEqual(SyrmosArrangementRule.companionExtent(ws, axis: .vertical), 314)
        XCTAssertEqual(SyrmosArrangementRule.gap(ws, axis: .vertical), 40)
    }

    func test_arrangementRule_bookDivisionHasNoGap() {
        let ws = resolve(innerW, innerH, .plan, regions: [bookDivision])
        XCTAssertEqual(SyrmosArrangementRule.taskExtent(ws, axis: .horizontal), 334)
        XCTAssertEqual(SyrmosArrangementRule.gap(ws, axis: .horizontal), 0)
    }

    func test_arrangementRule_sharesAreClampedForASinglePane() {
        let ws = resolve(coverW, coverH, .plan)
        XCTAssertEqual(SyrmosArrangementRule.taskShare(ws, size: CGSize(width: 466, height: 678)), 0.8, accuracy: 0.001)
        XCTAssertEqual(SyrmosArrangementRule.companionExtent(ws, axis: .vertical), 0)
    }

    // MARK: Dynamic Type as font scale

    func test_dynamicTypeScale_matchesBodySizesOver17() {
        XCTAssertEqual(SyrmosDynamicType.fontScale(.large), 1, accuracy: 0.001)
        XCTAssertEqual(SyrmosDynamicType.fontScale(.xSmall), 14.0 / 17.0, accuracy: 0.001)
        XCTAssertEqual(SyrmosDynamicType.fontScale(.xxxLarge), 23.0 / 17.0, accuracy: 0.001)
        XCTAssertEqual(SyrmosDynamicType.fontScale(.accessibility1), 28.0 / 17.0, accuracy: 0.001)
        XCTAssertEqual(SyrmosDynamicType.fontScale(.accessibility5), 53.0 / 17.0, accuracy: 0.001)
    }

    func test_dynamicTypeScale_accessibilitySizesCollapseTheTallCanvas() {
        // xxxLarge (1.35) is the first size that forces one column on a plain
        // window, exactly the policy's large-text threshold.
        XCTAssertEqual(resolve(innerW, innerH, .plan, fontScale: SyrmosDynamicType.fontScale(.xxLarge)).arrangement, .stacked)
        XCTAssertEqual(resolve(innerW, innerH, .plan, fontScale: SyrmosDynamicType.fontScale(.xxxLarge)).arrangement, .single)
        XCTAssertEqual(resolve(innerW, innerH, .plan, fontScale: SyrmosDynamicType.fontScale(.accessibility1)).arrangement, .single)
    }
}
