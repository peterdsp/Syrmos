import XCTest
import SwiftUI
@testable import Syrmos

/// Renders the adaptive two-pane at the real iPhone Duo display geometry so we can
/// see how the foldable / Duo layout actually looks, and guards that the unfolded
/// inner display pairs into two panes while the folded cover display stays a single
/// scrolling column. When this runs on the iOS 27.1 runtime the native
/// `ArrangementView` split path executes; older runtimes take the `HStack`
/// fallback, and the structural assertions hold for both.
///
/// Geometry matters, and the first cut got it wrong: it rendered at 466 x 678 pt,
/// which is the Duo's COVER (folded) display, not the unfolded inner screen, so the
/// "Duo" images were a cramped phone column rather than the two-pane. The real
/// displays, measured on the booted Duo sim (pixels / scale 3):
///
///   inner (unfolded): 2007 x 2853 px -> 669 x 951 pt   the two-pane surface
///   cover (folded)  : 1398 x 2034 px -> 466 x 678 pt   a single phone column
///
/// The inner display clears the policy's 640 pt pairing floor in both
/// orientations, so the unfolded Duo always pairs (side by side, or stacked for a
/// task that wants the map above); the cover display is below it, so the folded
/// phone stays single column.
///
/// The rendered PNGs are written under `iosAppTests/__DuoSnapshots__/` for visual
/// inspection. The assertions are structural, not pixel-exact, so they survive
/// cosmetic changes but fail if the arrangement regresses (an unfolded canvas that
/// stops pairing, or a folded one that wrongly splits).
final class DuoSnapshotTests: XCTestCase {

    // Unfolded inner display (the two-pane surface), both orientations.
    private let duoInnerPortrait = CGSize(width: 669, height: 951)
    private let duoInnerLandscape = CGSize(width: 951, height: 669)
    // Folded cover display (a single phone column).
    private let duoCover = CGSize(width: 466, height: 678)

    private var outputDir: URL {
        URL(fileURLWithPath: #filePath).deletingLastPathComponent()
            .appendingPathComponent("__DuoSnapshots__")
    }

    override func setUpWithError() throws {
        try FileManager.default.createDirectory(at: outputDir, withIntermediateDirectories: true)
    }

    // MARK: Structural: the unfolded inner display pairs, the folded cover collapses

    /// A probe arrangement with a solid-red task pane and a solid-blue companion, so
    /// the split is unambiguous to sample regardless of real screen content.
    private var probeArrangement: some View {
        SyrmosArrangement(
            primary: { Color.red },
            companion: { Color.blue },
            combined: { Color.green }
        )
    }

    @MainActor
    func test_duoInner_pairsTwoPanes() throws {
        let image = render(probeArrangement, size: duoInnerLandscape)
        try save(image, "arrangement-duo-inner.png")
        let grid = sampleGrid(image)
        // The unfolded inner display pairs, so BOTH the red task pane and the blue
        // companion are present (position-agnostic: the OS may split side-by-side
        // or, on the native Duo ArrangementView, stack the two halves).
        XCTAssertTrue(grid.contains(where: isRed), "unfolded inner display should show the red task pane")
        XCTAssertTrue(grid.contains(where: isBlue), "unfolded inner display should show the blue companion")
    }

    /// A GO probe on the tall inner display stacks: the blue companion (map)
    /// fills the upper band and the red task pane the lower part, so the hands
    /// are on the controls (P5 Tall canvas, six-posture prompt section 5).
    @MainActor
    func test_duoInnerPortrait_goProbeStacksCompanionAbove() throws {
        let probe = SyrmosArrangement(
            task: .go,
            primary: { Color.red },
            companion: { Color.blue },
            combined: { Color.green }
        )
        let image = render(probe, size: duoInnerPortrait)
        try save(image, "arrangement-duo-inner-portrait-go.png")
        #if SYRMOS_DUO_SDK
        // The native split owns the axis on the Duo runtime: assert presence only.
        let grid = sampleGrid(image)
        XCTAssertTrue(grid.contains(where: isBlue), "companion present")
        XCTAssertTrue(grid.contains(where: isRed), "task pane present")
        #else
        // 45 percent of 951 is 427: sample well inside each band.
        XCTAssertTrue(patchColor(image, fx: 0.5, fy: 0.2).map(isBlue) ?? false, "map band should be on top")
        XCTAssertTrue(patchColor(image, fx: 0.5, fy: 0.8).map(isRed) ?? false, "task pane should be below")
        XCTAssertTrue(patchColor(image, fx: 0.15, fy: 0.8).map(isRed) ?? false, "task pane spans the full width")
        #endif
    }

    /// A Plan probe on the same tall inner display pairs side by side at half
    /// the width (334 | 335): the planner reads as two columns.
    @MainActor
    func test_duoInnerPortrait_planProbePairsSideBySide() throws {
        let probe = SyrmosArrangement(
            task: .plan,
            primary: { Color.red },
            companion: { Color.blue },
            combined: { Color.green }
        )
        let image = render(probe, size: duoInnerPortrait)
        try save(image, "arrangement-duo-inner-portrait-plan.png")
        #if SYRMOS_DUO_SDK
        let grid = sampleGrid(image)
        XCTAssertTrue(grid.contains(where: isRed), "task pane present")
        XCTAssertTrue(grid.contains(where: isBlue), "companion present")
        #else
        XCTAssertTrue(patchColor(image, fx: 0.2, fy: 0.5).map(isRed) ?? false, "task pane on the left")
        XCTAssertTrue(patchColor(image, fx: 0.8, fy: 0.5).map(isBlue) ?? false, "companion on the right")
        #endif
    }

    /// An injected occluding hinge across the wide inner display (laptop or
    /// tent) stacks any task on the fold and leaves the hinge band empty.
    @MainActor
    func test_duoInnerLandscape_hingeStacksAndKeepsTheBandClear() throws {
        let hinge = SyrmosReservedGeometry(regions: [
            SyrmosReservedRegion(kind: .occlusion, orientation: .horizontal, start: 314, size: 40),
        ])
        let probe = SyrmosArrangement(
            task: .plan,
            primary: { Color.red },
            companion: { Color.blue },
            combined: { Color.green }
        )
        .environment(\.syrmosReservedGeometryOverride, hinge)
        let image = render(probe, size: duoInnerLandscape)
        try save(image, "arrangement-duo-inner-landscape-hinge.png")
        #if SYRMOS_DUO_SDK
        // The native split reserves the SYSTEM's regions, not an injected one, so
        // only the pane order is asserted on the Duo runtime.
        let grid = sampleGrid(image)
        XCTAssertTrue(grid.contains(where: isBlue), "companion present")
        XCTAssertTrue(grid.contains(where: isRed), "task pane present")
        #else
        XCTAssertTrue(patchColor(image, fx: 0.5, fy: 0.25).map(isBlue) ?? false, "overview above the hinge")
        XCTAssertTrue(patchColor(image, fx: 0.5, fy: 0.75).map(isRed) ?? false, "task below the hinge")
        // The hinge band itself (314..354 of 669) carries no pane colour.
        let band = patchColor(image, fx: 0.5, fy: 334.0 / 669.0)
        XCTAssertFalse(band.map(isRed) ?? true, "nothing red bridges the hinge")
        XCTAssertFalse(band.map(isBlue) ?? true, "nothing blue bridges the hinge")
        #endif
    }

    @MainActor
    func test_duoCover_singleColumn() throws {
        let image = render(probeArrangement, size: duoCover)
        try save(image, "arrangement-duo-cover.png")
        let grid = sampleGrid(image)
        // The folded cover display shows only the green single column: no companion.
        XCTAssertTrue(grid.contains(where: isGreen), "folded cover display should show the green single column")
        XCTAssertFalse(grid.contains(where: isBlue), "folded cover display must not show a companion")
    }

    // MARK: Visual: the real GO screen, unfolded (two-pane) and folded (single column)

    @MainActor
    func test_goScreen_duoInnerLandscape_render() throws {
        let journey = try XCTUnwrap(demoJourney(), "bundled data should yield a demo journey")
        let view = GoJourneyView(journey: journey, language: .english, coords: demoCoords(journey))
        let image = render(view, size: duoInnerLandscape)
        try save(image, "go-duo-inner-landscape.png")
        // A real two-pane screen is far from uniform: assert visible variance so a
        // blank / crashed render fails rather than silently passing.
        XCTAssertTrue(hasVisibleVariance(image), "GO unfolded landscape render should not be blank")
    }

    @MainActor
    func test_goScreen_duoInnerPortrait_render() throws {
        let journey = try XCTUnwrap(demoJourney(), "bundled data should yield a demo journey")
        let view = GoJourneyView(journey: journey, language: .english, coords: demoCoords(journey))
        let image = render(view, size: duoInnerPortrait)
        try save(image, "go-duo-inner-portrait.png")
        XCTAssertTrue(hasVisibleVariance(image), "GO unfolded portrait render should not be blank")
    }

    /// The GO companion with an injected occluding hinge across the lower part of
    /// its map (a laptop or tent posture where the fold crosses the companion):
    /// the map pads its bottom so the route fit and the current stop stay above
    /// the hinge. Rendered through the test seam because a 27.0 simulator
    /// reports no regions; the image documents the padded fit.
    @MainActor
    func test_goScreen_duoInnerLandscape_hingeAcrossCompanion_render() throws {
        let journey = try XCTUnwrap(demoJourney(), "bundled data should yield a demo journey")
        let hinge = SyrmosReservedGeometry(regions: [
            SyrmosReservedRegion(kind: .occlusion, orientation: .horizontal, start: 220, size: 40),
        ])
        let view = GoJourneyView(journey: journey, language: .english, coords: demoCoords(journey))
            .environment(\.syrmosReservedGeometryOverride, hinge)
        let image = render(view, size: duoInnerLandscape)
        try save(image, "go-duo-inner-landscape-hinge.png")
        XCTAssertTrue(hasVisibleVariance(image), "GO hinge-padded render should not be blank")
    }

    @MainActor
    func test_goScreen_duoCover_render() throws {
        let journey = try XCTUnwrap(demoJourney(), "bundled data should yield a demo journey")
        let view = GoJourneyView(journey: journey, language: .english, coords: demoCoords(journey))
        let image = render(view, size: duoCover)
        try save(image, "go-duo-cover.png")
        XCTAssertTrue(hasVisibleVariance(image), "GO folded cover render should not be blank")
    }

    // MARK: Visual: the real Plan screen on the Duo (paired) and the cover (single)

    /// Plan on the unfolded display pairs the query with the Routes pane, which
    /// shows its calm empty state before the first search instead of a blank half.
    @MainActor
    func test_planScreen_duoInnerLandscape_render() throws {
        let image = render(PlanView(language: .english), size: duoInnerLandscape)
        try save(image, "plan-duo-inner-landscape.png")
        XCTAssertTrue(hasVisibleVariance(image), "Plan unfolded landscape render should not be blank")
    }

    @MainActor
    func test_planScreen_duoInnerPortrait_render() throws {
        let image = render(PlanView(language: .english), size: duoInnerPortrait)
        try save(image, "plan-duo-inner-portrait.png")
        XCTAssertTrue(hasVisibleVariance(image), "Plan unfolded portrait render should not be blank")
    }

    @MainActor
    func test_planScreen_duoCover_render() throws {
        let image = render(PlanView(language: .english), size: duoCover)
        try save(image, "plan-duo-cover.png")
        XCTAssertTrue(hasVisibleVariance(image), "Plan folded cover render should not be blank")
    }

    // MARK: Journey fixture (mirrors GoDemoEntryView so the snapshot is a real route)

    private func demoJourney(_ language: AppLanguage = .english) -> GuidanceJourney? {
        let ops = SyrmosData.operationalLines
        if let m1 = ops.first(where: { $0.id == "M1" }),
           let m2 = ops.first(where: { $0.id == "M2" }),
           let a = SyrmosData.stations(for: m1.id).first?.id,
           let b = SyrmosData.stations(for: m2.id).last?.id,
           let detailed = JourneyPlanner.planDetailed(from: a, to: b, language: language) {
            return GuidanceJourney.from(detailed, language: language)
        }
        for line in ops {
            let stops = SyrmosData.stations(for: line.id)
            if stops.count >= 4,
               let detailed = JourneyPlanner.planDetailed(from: stops[0].id, to: stops[stops.count - 1].id, language: language) {
                return GuidanceJourney.from(detailed, language: language)
            }
        }
        return nil
    }

    private func demoCoords(_ journey: GuidanceJourney) -> [String: GoLocationAdvancer.Coord] {
        var out: [String: GoLocationAdvancer.Coord] = [:]
        for leg in journey.legs {
            for stop in leg.stops where out[stop.id] == nil {
                if let c = StationCoordinateLookup.shared.coordinate(for: stop.id) {
                    out[stop.id] = GoLocationAdvancer.Coord(lat: c.lat, lon: c.lon)
                }
            }
        }
        return out
    }

    // MARK: Rendering + sampling

    @MainActor
    private func render(_ view: some View, size: CGSize) -> UIImage {
        // Let the SwiftUI root fill the host view (no explicit .frame, which would
        // center and clip content-bearing views) and ignore the safe area so the
        // content reaches the top edge instead of leaving a status-bar strip; host
        // the controller in a live window and capture the host view, which renders
        // SwiftUI reliably offscreen (capturing the window itself yields a blank
        // frame). Pin light appearance and a system background so the branded UI
        // renders as shipped rather than on a black default canvas.
        let host = UIHostingController(rootView: view.ignoresSafeArea())
        host.view.frame = CGRect(origin: .zero, size: size)
        host.view.backgroundColor = UIColor.systemBackground
        host.overrideUserInterfaceStyle = .light
        let window = UIWindow(frame: CGRect(origin: .zero, size: size))
        window.overrideUserInterfaceStyle = .light
        window.rootViewController = host
        window.isHidden = false
        window.makeKeyAndVisible()
        host.view.setNeedsLayout()
        host.view.layoutIfNeeded()
        // Let SwiftUI and MapKit finish their asynchronous passes before capture.
        RunLoop.current.run(until: Date().addingTimeInterval(0.6))
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 2
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            host.view.drawHierarchy(in: CGRect(origin: .zero, size: size), afterScreenUpdates: true)
        }
    }

    // Colour predicates for the probe panes (system red / blue / green as rendered).
    private func isRed(_ c: (r: Int, g: Int, b: Int)) -> Bool { c.r > 150 && c.g < 120 && c.b < 120 }
    private func isBlue(_ c: (r: Int, g: Int, b: Int)) -> Bool { c.b > 150 && c.r < 100 && c.g < 180 }
    private func isGreen(_ c: (r: Int, g: Int, b: Int)) -> Bool { c.g > 120 && c.r < 120 && c.b < 140 }

    /// A grid of sampled colours across the image, for presence checks.
    private func sampleGrid(_ image: UIImage, cols: Int = 12, rows: Int = 12) -> [(r: Int, g: Int, b: Int)] {
        var out: [(r: Int, g: Int, b: Int)] = []
        for i in 0..<cols {
            for j in 0..<rows {
                let fx = (CGFloat(i) + 0.5) / CGFloat(cols)
                let fy = (CGFloat(j) + 0.5) / CGFloat(rows)
                if let c = patchColor(image, fx: fx, fy: fy) { out.append(c) }
            }
        }
        return out
    }

    private func save(_ image: UIImage, _ name: String) throws {
        let data = try XCTUnwrap(image.pngData(), "png encode")
        try data.write(to: outputDir.appendingPathComponent(name))
    }

    /// Average RGB (0-255) of the single pixel at the given fractional position.
    /// Uses CGImage cropping (top-left pixel origin) so the sample is unambiguous.
    private func patchColor(_ image: UIImage, fx: CGFloat, fy: CGFloat) -> (r: Int, g: Int, b: Int)? {
        guard let cg = image.cgImage else { return nil }
        let px = min(max(Int(CGFloat(cg.width) * fx), 0), cg.width - 1)
        let py = min(max(Int(CGFloat(cg.height) * fy), 0), cg.height - 1)
        guard let crop = cg.cropping(to: CGRect(x: px, y: py, width: 1, height: 1)) else { return nil }
        var pixel = [UInt8](repeating: 0, count: 4)
        guard let ctx = CGContext(
            data: &pixel, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        ctx.draw(crop, in: CGRect(x: 0, y: 0, width: 1, height: 1))
        return (Int(pixel[0]), Int(pixel[1]), Int(pixel[2]))
    }

    /// True when the render carries real content: enough sampled patches are not
    /// near-white, so a blank / crashed render fails rather than silently passing.
    private func hasVisibleVariance(_ image: UIImage) -> Bool {
        let grid = sampleGrid(image)
        guard !grid.isEmpty else { return false }
        let nonWhite = grid.filter { !($0.r > 238 && $0.g > 238 && $0.b > 238) }.count
        return nonWhite >= 8
    }
}
