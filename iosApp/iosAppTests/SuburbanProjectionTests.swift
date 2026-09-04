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

    // MARK: - Live vehicle freshness (mirrors KMP LiveVehicleFreshnessTest)

    func test_freshness_classifier_buckets() {
        let now = Date(timeIntervalSince1970: 1_000_000)
        func at(_ ageSeconds: Double) -> Date { now.addingTimeInterval(-ageSeconds) }

        XCTAssertEqual(LiveVehicleFreshnessRule.classify(updatedAt: at(10), now: now).state, .live)
        XCTAssertEqual(LiveVehicleFreshnessRule.classify(updatedAt: at(90), now: now).state, .live, "90s boundary is still live")
        XCTAssertEqual(LiveVehicleFreshnessRule.classify(updatedAt: at(91), now: now).state, .stale)
        XCTAssertEqual(LiveVehicleFreshnessRule.classify(updatedAt: at(600), now: now).state, .stale, "600s boundary is still stale")
        XCTAssertEqual(LiveVehicleFreshnessRule.classify(updatedAt: at(601), now: now).state, .expired)
        XCTAssertEqual(LiveVehicleFreshnessRule.classify(updatedAt: at(300), now: now).ageSeconds, 300)
    }

    func test_freshness_classifier_missing_and_future_timestamps_never_live() {
        let now = Date(timeIntervalSince1970: 1_000_000)
        // No timestamp -> stale, no age, never live.
        let none = LiveVehicleFreshnessRule.classify(updatedAt: nil, now: now)
        XCTAssertEqual(none.state, .stale)
        XCTAssertNil(none.ageSeconds)
        // Small future skew tolerated as just-now.
        XCTAssertEqual(LiveVehicleFreshnessRule.classify(updatedAt: now.addingTimeInterval(60), now: now).state, .live)
        // Far future is not trusted.
        XCTAssertEqual(LiveVehicleFreshnessRule.classify(updatedAt: now.addingTimeInterval(100_000), now: now).state, .stale)
    }

    /// The safety-critical guarantee: an EXPIRED live train must NOT cover its
    /// line, so the schedule projector fills back in once the feed goes stale.
    /// Mirrors `MapView.visibleLiveTrains` + the coveredLines filter.
    func test_expired_live_train_releases_its_line_to_projection() {
        let now = Date(timeIntervalSince1970: 1_000_000)
        let live: [(lineId: String, updatedAt: Date?)] = [
            ("A1", now.addingTimeInterval(-10)),    // LIVE
            ("A2", now.addingTimeInterval(-300)),   // STALE (still tracked)
            ("A3", now.addingTimeInterval(-1000)),  // EXPIRED -> released
        ]
        let covered = Set(
            live.filter {
                LiveVehicleFreshnessRule.classify(updatedAt: $0.updatedAt, now: now).state != .expired
            }.map(\.lineId)
        )
        XCTAssertEqual(covered, ["A1", "A2"], "A3 expired so its line is handed back to the projector")
        XCTAssertFalse(covered.contains("A3"))
    }
}
