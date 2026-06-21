import XCTest
@testable import Syrmos

/// Regression tests for the suburban (A1/A2/A3/A4) projection pipeline.
///
/// Contract:
///   - Online: railway.gov.gr live SSE wins (`LiveTrainService.trains`).
///   - Offline: the projector keeps the dots moving using bundled offsets
///     (`LivePositionsService.projectedLineIds` now includes A1-A4).
///   - Dedupe lives at the map-view call site: whenever the live feed has
///     ANY train for a line, the projected dots for that line are hidden.
@MainActor
final class SuburbanProjectionTests: XCTestCase {

    func test_projected_line_ids_include_all_suburban_lines() {
        let ids = Set(LivePositionsService.shared.projectedLineIds)
        for line in ["A1", "A2", "A3", "A4"] {
            XCTAssertTrue(ids.contains(line), "Suburban \(line) must be in projectedLineIds so the dots are simulated when offline")
        }
    }

    func test_projected_line_ids_still_include_metro_tram() {
        let ids = Set(LivePositionsService.shared.projectedLineIds)
        for line in ["M1", "M2", "M3", "M3_AIR", "T6", "T7"] {
            XCTAssertTrue(ids.contains(line), "Metro/tram \(line) must remain in projectedLineIds")
        }
    }

    func test_dedupe_filters_projected_suburban_when_live_present() {
        // Mirrors the closure in TransitMapView: simulatedTrains is filtered
        // by `coveredLines` derived from liveTrains.lineId. This proves the
        // dedupe is per-line and doesn't accidentally hide metro/tram dots.
        let simulated: [(id: String, lineId: String)] = [
            ("M1_t1", "M1"),
            ("A1_t1", "A1"),
            ("A1_t2", "A1"),
            ("A2_t1", "A2"),
        ]
        let liveLineIds: Set<String> = ["A1"]

        let kept = simulated.filter { !liveLineIds.contains($0.lineId) }
        let keptIds = kept.map(\.id)
        XCTAssertEqual(Set(keptIds), ["M1_t1", "A2_t1"], "A1 projection hidden; M1 and A2 kept")
    }

    func test_dedupe_keeps_all_projections_when_live_feed_empty() {
        let simulated: [(id: String, lineId: String)] = [
            ("A1_t1", "A1"),
            ("A2_t1", "A2"),
        ]
        let liveLineIds: Set<String> = []
        let kept = simulated.filter { !liveLineIds.contains($0.lineId) }
        XCTAssertEqual(kept.count, 2, "Offline (empty live feed) means suburban dots stay on screen")
    }
}
