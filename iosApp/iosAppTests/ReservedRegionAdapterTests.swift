import XCTest
import SwiftUI
@testable import Syrmos

/// Pins the reserved-region adapter (six-posture prompt, section 9, item 2) and
/// the map padding rule (parent prompt 9.4): regions are translated into the
/// content box once, a fold beside the box never splits it, a camera cutout is
/// a cutout and not a division, and the map gives up the smaller side of an
/// occluding hinge instead of resetting its camera.
final class ReservedRegionAdapterTests: XCTestCase {

    private typealias Adapter = SyrmosReservedRegionAdapter
    private typealias Raw = SyrmosRawReservedRegion

    // Duo inner display, both orientations (points).
    private let portrait = CGRect(x: 0, y: 0, width: 669, height: 951)
    private let landscape = CGRect(x: 0, y: 0, width: 951, height: 669)

    // MARK: Normalisation

    func test_verticalDivisionLine_becomesAVerticalRegion() {
        let raw = [Raw(kind: .division, frame: CGRect(x: 334, y: 0, width: 0, height: 951))]
        let g = Adapter.normalize(raw, in: portrait)
        XCTAssertEqual(g.regions, [SyrmosReservedRegion(kind: .division, orientation: .vertical, start: 334, size: 0, active: true)])
        XCTAssertTrue(g.cutouts.isEmpty)
    }

    func test_horizontalOcclusionBar_becomesAHorizontalRegion() {
        let raw = [Raw(kind: .occlusion, frame: CGRect(x: 0, y: 314, width: 951, height: 40))]
        let g = Adapter.normalize(raw, in: landscape)
        XCTAssertEqual(g.regions, [SyrmosReservedRegion(kind: .occlusion, orientation: .horizontal, start: 314, size: 40, active: true)])
    }

    func test_frameIsTranslatedIntoTheBoxOnce() {
        // The box sits 80 pt to the right of the window (a nav rail); the fold
        // reported at window x 414 is at box x 334.
        let box = CGRect(x: 80, y: 0, width: 669, height: 951)
        let raw = [Raw(kind: .division, frame: CGRect(x: 414, y: 0, width: 0, height: 951))]
        XCTAssertEqual(Adapter.normalize(raw, in: box).regions.first?.start, 334)
    }

    func test_aFoldBesideTheBoxDoesNotSplitIt() {
        // A vertical fold at window x 40 lies inside the rail, left of the box.
        let box = CGRect(x: 80, y: 0, width: 669, height: 951)
        let raw = [Raw(kind: .occlusion, frame: CGRect(x: 30, y: 0, width: 20, height: 951))]
        XCTAssertTrue(Adapter.normalize(raw, in: box).isEmpty)
    }

    func test_aBarOnTheBoxEdgeReservesNothingInside() {
        let raw = [Raw(kind: .division, frame: CGRect(x: 669, y: 0, width: 0, height: 951))]
        XCTAssertTrue(Adapter.normalize(raw, in: portrait).isEmpty)
    }

    func test_inactiveDivisionIsKeptInactive() {
        let raw = [Raw(kind: .division, frame: CGRect(x: 334, y: 0, width: 0, height: 951), isActive: false)]
        let g = Adapter.normalize(raw, in: portrait)
        XCTAssertEqual(g.regions.first?.active, false)
        // And the policy then treats it as no region at all (T3 flat).
        let ws = SyrmosAdaptiveWorkspacePolicy.resolve(width: 669, height: 951, task: .plan, regions: g.regions)
        XCTAssertFalse(ws.regionDriven)
    }

    func test_cameraCutoutIsACutoutNotADivision() {
        let raw = [Raw(kind: .occlusion, frame: CGRect(x: 620, y: 12, width: 28, height: 28))]
        let g = Adapter.normalize(raw, in: portrait)
        XCTAssertTrue(g.regions.isEmpty, "a cutout must never split the layout")
        XCTAssertEqual(g.cutouts, [CGRect(x: 620, y: 12, width: 28, height: 28)])
    }

    func test_inactiveCutoutIsIgnored() {
        let raw = [Raw(kind: .occlusion, frame: CGRect(x: 620, y: 12, width: 28, height: 28), isActive: false)]
        XCTAssertTrue(Adapter.normalize(raw, in: portrait).isEmpty)
    }

    func test_partialOcclusionThatDoesNotSpanIsACutout() {
        // Covers half the height only: not a hinge bar.
        let raw = [Raw(kind: .occlusion, frame: CGRect(x: 320, y: 0, width: 30, height: 475))]
        let g = Adapter.normalize(raw, in: portrait)
        XCTAssertTrue(g.regions.isEmpty)
        XCTAssertEqual(g.cutouts.count, 1)
    }

    func test_cutoutIsClippedToTheBox() {
        let raw = [Raw(kind: .occlusion, frame: CGRect(x: 650, y: -10, width: 40, height: 30))]
        let g = Adapter.normalize(raw, in: portrait)
        XCTAssertEqual(g.cutouts, [CGRect(x: 650, y: 0, width: 19, height: 20)])
    }

    func test_barOverhangingTheBoxIsClippedAndSized() {
        // Hinge reported 20 pt wider than the box on each side.
        let raw = [Raw(kind: .occlusion, frame: CGRect(x: -20, y: 314, width: 991, height: 40))]
        let g = Adapter.normalize(raw, in: landscape)
        XCTAssertEqual(g.regions.first, SyrmosReservedRegion(kind: .occlusion, orientation: .horizontal, start: 314, size: 40, active: true))
    }

    func test_fractionalFramesRoundToWholePoints() {
        let raw = [Raw(kind: .occlusion, frame: CGRect(x: 0, y: 313.6, width: 951, height: 39.2))]
        let r = Adapter.normalize(raw, in: landscape).regions.first
        XCTAssertEqual(r?.start, 314)
        XCTAssertEqual(r?.size, 40)
    }

    func test_emptyBoxYieldsNothing() {
        let raw = [Raw(kind: .division, frame: CGRect(x: 10, y: 0, width: 0, height: 100))]
        XCTAssertTrue(Adapter.normalize(raw, in: .zero).isEmpty)
    }

    func test_bothKindsFlowIntoThePolicyAsTheBookFixture() {
        // P4 Timetable book through the adapter: same outcome as the fixture.
        let raw = [Raw(kind: .division, frame: CGRect(x: 334, y: 0, width: 0, height: 951))]
        let g = Adapter.normalize(raw, in: portrait)
        let ws = SyrmosAdaptiveWorkspacePolicy.resolve(width: 669, height: 951, task: .plan, regions: g.regions)
        XCTAssertEqual(ws.arrangement, .sideBySide)
        XCTAssertTrue(ws.regionDriven)
        XCTAssertEqual(ws.pane(.task)?.rect.width, 334)
    }

    func test_activeOcclusionsExcludeDivisionsAndInactiveBars() {
        let g = SyrmosReservedGeometry(regions: [
            SyrmosReservedRegion(kind: .division, orientation: .vertical, start: 334, size: 0),
            SyrmosReservedRegion(kind: .occlusion, orientation: .vertical, start: 334, size: 20, active: false),
            SyrmosReservedRegion(kind: .occlusion, orientation: .horizontal, start: 300, size: 20),
        ])
        XCTAssertEqual(g.activeOcclusions.count, 1)
        XCTAssertEqual(g.activeOcclusions.first?.orientation, .horizontal)
    }

    // MARK: Map padding

    private typealias Padding = SyrmosMapPadding

    func test_noRegions_keepsTheBasePadding() {
        let ins = Padding.insets(mapRect: CGRect(x: 0, y: 0, width: 951, height: 280), geometry: .none)
        XCTAssertEqual(ins, .all(44))
    }

    func test_divisionsNeverPad() {
        let g = SyrmosReservedGeometry(regions: [
            SyrmosReservedRegion(kind: .division, orientation: .horizontal, start: 150, size: 0),
        ])
        let ins = Padding.insets(mapRect: CGRect(x: 0, y: 0, width: 951, height: 280), geometry: g)
        XCTAssertEqual(ins, .all(44))
    }

    func test_hingeAcrossTheLowerPartOfTheMap_padsTheBottom() {
        // Map 0..400 tall, hinge at 300..340: keep the larger upper side.
        let g = SyrmosReservedGeometry(regions: [
            SyrmosReservedRegion(kind: .occlusion, orientation: .horizontal, start: 300, size: 40),
        ])
        let ins = Padding.insets(mapRect: CGRect(x: 0, y: 0, width: 951, height: 400), geometry: g)
        XCTAssertEqual(ins.bottom, 400 - 300 + 44)
        XCTAssertEqual(ins.top, 44)
    }

    func test_hingeAcrossTheUpperPartOfTheMap_padsTheTop() {
        // Map occupies 200..600 of the box, hinge at 314..354: upper side is 114,
        // lower side is 246, so the map gives up the upper side.
        let g = SyrmosReservedGeometry(regions: [
            SyrmosReservedRegion(kind: .occlusion, orientation: .horizontal, start: 314, size: 40),
        ])
        let ins = Padding.insets(mapRect: CGRect(x: 0, y: 200, width: 951, height: 400), geometry: g)
        XCTAssertEqual(ins.top, 354 - 200 + 44)
        XCTAssertEqual(ins.bottom, 44)
    }

    func test_hingeOutsideTheMapRect_doesNotPad() {
        let g = SyrmosReservedGeometry(regions: [
            SyrmosReservedRegion(kind: .occlusion, orientation: .horizontal, start: 500, size: 40),
        ])
        let ins = Padding.insets(mapRect: CGRect(x: 0, y: 0, width: 951, height: 400), geometry: g)
        XCTAssertEqual(ins, .all(44))
    }

    func test_verticalHingeOnTheRightOfTheMap_padsTheRight() {
        let g = SyrmosReservedGeometry(regions: [
            SyrmosReservedRegion(kind: .occlusion, orientation: .vertical, start: 600, size: 40),
        ])
        let ins = Padding.insets(mapRect: CGRect(x: 0, y: 0, width: 669, height: 400), geometry: g)
        XCTAssertEqual(ins.right, 669 - 600 + 44)
        XCTAssertEqual(ins.left, 44)
    }

    func test_cameraCutoutInTheTopCorner_padsTheTop() {
        let g = SyrmosReservedGeometry(cutouts: [CGRect(x: 620, y: 12, width: 28, height: 28)])
        let ins = Padding.insets(mapRect: CGRect(x: 0, y: 0, width: 669, height: 400), geometry: g)
        XCTAssertEqual(ins.top, 40 + 44)
        XCTAssertEqual(ins.right, 44)
    }

    func test_cutoutOutsideTheMap_doesNotPad() {
        let g = SyrmosReservedGeometry(cutouts: [CGRect(x: 620, y: 500, width: 28, height: 28)])
        let ins = Padding.insets(mapRect: CGRect(x: 0, y: 0, width: 669, height: 400), geometry: g)
        XCTAssertEqual(ins, .all(44))
    }

    func test_paddingNeverInvertsTheVisibleArea() {
        // A hinge right through the middle of a short map: both sides are small.
        let g = SyrmosReservedGeometry(regions: [
            SyrmosReservedRegion(kind: .occlusion, orientation: .horizontal, start: 100, size: 40),
        ])
        let ins = Padding.insets(mapRect: CGRect(x: 0, y: 0, width: 951, height: 200), geometry: g)
        XCTAssertLessThanOrEqual(ins.top + ins.bottom, 150)
    }

    func test_visibleCenterAndCompensatingPoint() {
        let size = CGSize(width: 400, height: 300)
        let ins = SyrmosEdgeInsets(top: 44, left: 44, bottom: 144, right: 44)
        XCTAssertEqual(Padding.visibleCenter(size: size, insets: ins), CGPoint(x: 200, y: 100))
        // The view centre is (200,150); mirroring it through (200,100) gives (200,200).
        XCTAssertEqual(Padding.compensatingPoint(size: size, insets: ins), CGPoint(x: 200, y: 200))
        XCTAssertEqual(Padding.compensatingPoint(size: size, insets: .all(44)), CGPoint(x: 200, y: 150))
    }

    // MARK: Reading the system

    /// On a toolchain or runtime without the Duo API the reader returns nothing,
    /// so every caller falls through to the plain-window path.
    @MainActor
    func test_readerIsEmptyWithoutTheDuoApiOrRegions() {
        var read: SyrmosReservedGeometry?
        let probe = GeometryReader { geo -> Color in
            read = geo.syrmosReservedGeometry()
            return Color.clear
        }
        let host = UIHostingController(rootView: probe)
        host.view.frame = CGRect(x: 0, y: 0, width: 669, height: 951)
        let window = UIWindow(frame: host.view.frame)
        window.rootViewController = host
        window.makeKeyAndVisible()
        host.view.layoutIfNeeded()
        RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        let geometry = read ?? .none
        #if SYRMOS_DUO_SDK
        // Under the 27.1 SDK on a Duo runtime this reports the real regions.
        // Record them so the readiness record can quote what the system said.
        print("SYRMOS_DUO_SDK reserved geometry: \(geometry)")
        #else
        XCTAssertTrue(geometry.isEmpty)
        #endif
    }
}
