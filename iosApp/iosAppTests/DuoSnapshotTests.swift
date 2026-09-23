import XCTest
import SwiftUI
@testable import Syrmos

/// Renders the adaptive two-pane at the iPhone Duo inner-display geometry so we can
/// see how the foldable / Duo layout actually looks, and guards that a regular
/// width pairs into two panes while a compact width stays a single scrolling
/// column. When this runs on the iOS 27.1 runtime the native `ArrangementView`
/// split path executes (the Duo simulator's own runtime); older runtimes take the
/// `HStack` fallback, and the structural assertions hold for both.
///
/// The rendered PNGs are written under `iosAppTests/__DuoSnapshots__/` for visual
/// inspection. The assertions are structural, not pixel-exact, so they survive
/// cosmetic changes but fail if the arrangement regresses (a wide Duo canvas that
/// stops pairing, or a compact one that wrongly splits).
final class DuoSnapshotTests: XCTestCase {

    // iPhone Duo inner display, in points (measured on the booted Duo sim): 466 x 678.
    // Portrait width (466) is below SyrmosArrangement's 640pt pair threshold, so it
    // is single column; landscape width (678) is above it, so it pairs.
    private let duoPortrait = CGSize(width: 466, height: 678)
    private let duoLandscape = CGSize(width: 678, height: 466)

    private var outputDir: URL {
        URL(fileURLWithPath: #filePath).deletingLastPathComponent()
            .appendingPathComponent("__DuoSnapshots__")
    }

    override func setUpWithError() throws {
        try FileManager.default.createDirectory(at: outputDir, withIntermediateDirectories: true)
    }

    // MARK: Structural: the arrangement splits at Duo landscape, collapses at portrait

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
    func test_duoLandscape_pairsTwoPanes() throws {
        let image = render(probeArrangement, size: duoLandscape)
        try save(image, "arrangement-duo-landscape.png")
        let grid = sampleGrid(image)
        // A wide Duo canvas pairs, so BOTH the red task pane and the blue companion
        // are present (position-agnostic: the OS may split side-by-side or, on the
        // native Duo ArrangementView, stack the two halves).
        XCTAssertTrue(grid.contains(where: isRed), "paired canvas should show the red task pane")
        XCTAssertTrue(grid.contains(where: isBlue), "paired canvas should show the blue companion")
    }

    @MainActor
    func test_duoPortrait_singleColumn() throws {
        let image = render(probeArrangement, size: duoPortrait)
        try save(image, "arrangement-duo-portrait.png")
        let grid = sampleGrid(image)
        // A compact width shows only the green single column: no blue companion.
        XCTAssertTrue(grid.contains(where: isGreen), "compact width should show the green single column")
        XCTAssertFalse(grid.contains(where: isBlue), "compact width must not show a companion")
    }

    // MARK: Visual: the real GO screen at both Duo postures

    @MainActor
    func test_goScreen_duoLandscape_render() throws {
        let journey = try XCTUnwrap(demoJourney(), "bundled data should yield a demo journey")
        let view = GoJourneyView(journey: journey, language: .english, coords: demoCoords(journey))
        let image = render(view, size: duoLandscape)
        try save(image, "go-duo-landscape.png")
        // A real two-pane screen is far from uniform: assert visible variance so a
        // blank / crashed render fails rather than silently passing.
        XCTAssertTrue(hasVisibleVariance(image), "GO landscape render should not be blank")
    }

    @MainActor
    func test_goScreen_duoPortrait_render() throws {
        let journey = try XCTUnwrap(demoJourney(), "bundled data should yield a demo journey")
        let view = GoJourneyView(journey: journey, language: .english, coords: demoCoords(journey))
        let image = render(view, size: duoPortrait)
        try save(image, "go-duo-portrait.png")
        XCTAssertTrue(hasVisibleVariance(image), "GO portrait render should not be blank")
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
        // frame).
        let host = UIHostingController(rootView: view.ignoresSafeArea())
        host.view.frame = CGRect(origin: .zero, size: size)
        host.view.backgroundColor = UIColor.systemBackground
        let window = UIWindow(frame: CGRect(origin: .zero, size: size))
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
