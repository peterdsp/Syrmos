import XCTest
@testable import Syrmos

/// Mirrors fixtures/freshness/presentation.json case-for-case, so the iOS
/// FreshnessPresentation, the Kotlin FreshnessPresentation and web web-freshness.js
/// agree for the same golden inputs (Phase N J07).
final class FreshnessPresentationTests: XCTestCase {

    func testOnlineLive() {
        let s = FreshnessPresentation.evaluate(isNetworkAvailable: true, isLive: true)
        XCTAssertEqual(s, .live)
        XCTAssertFalse(FreshnessPresentation.showsBanner(s))
    }

    func testOnlinePredicted() {
        let s = FreshnessPresentation.evaluate(isNetworkAvailable: true, isLive: false)
        XCTAssertEqual(s, .predicted)
        XCTAssertTrue(FreshnessPresentation.showsBanner(s))
    }

    func testOfflineWinsOverLive() {
        let s = FreshnessPresentation.evaluate(isNetworkAvailable: false, isLive: true)
        XCTAssertEqual(s, .offline)
        XCTAssertTrue(FreshnessPresentation.showsBanner(s))
    }

    func testOfflinePredicted() {
        let s = FreshnessPresentation.evaluate(isNetworkAvailable: false, isLive: false)
        XCTAssertEqual(s, .offline)
        XCTAssertTrue(FreshnessPresentation.showsBanner(s))
    }
}
