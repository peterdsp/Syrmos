import XCTest
import CoreLocation
@testable import Syrmos

/// Mirrors the shared RouteGeometryTest (core/common) so the iOS loop-closing
/// port stays in lockstep with Android/web. A circular bus route (both
/// terminals identical, e.g. PU1 Kastelokampos) must have its return leg drawn.
final class MapRouteGeometryTests: XCTestCase {

    private func c(_ lat: Double, _ lon: Double) -> CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: lat, longitude: lon)
    }

    // MARK: isLoopTerminals

    func testLoopWhenTerminalsMatch() {
        XCTAssertTrue(MapRouteGeometry.isLoopTerminals("Kastelokampos", "Kastelokampos"))
    }

    func testLoopIsCaseInsensitiveAndTrimmed() {
        XCTAssertTrue(MapRouteGeometry.isLoopTerminals(" Kastelokampos ", "kastelokampos"))
    }

    func testNotLoopWhenTerminalsDiffer() {
        XCTAssertFalse(MapRouteGeometry.isLoopTerminals("Piraeus", "Kifissia"))
    }

    func testBlankTerminalsAreNeverLoop() {
        XCTAssertFalse(MapRouteGeometry.isLoopTerminals("", ""))
        XCTAssertFalse(MapRouteGeometry.isLoopTerminals("   ", "   "))
    }

    // MARK: closeLoop

    func testClosesLoopByAppendingFirstPoint() {
        let pts = [c(38.28, 21.78), c(38.29, 21.79), c(38.30, 21.80)]
        let closed = MapRouteGeometry.closeLoop(pts, isLoop: true)
        XCTAssertEqual(closed.count, 4)
        XCTAssertEqual(closed.last?.latitude, pts.first?.latitude)
        XCTAssertEqual(closed.last?.longitude, pts.first?.longitude)
    }

    func testNonLoopIsUnchanged() {
        let pts = [c(38.28, 21.78), c(38.29, 21.79)]
        XCTAssertEqual(MapRouteGeometry.closeLoop(pts, isLoop: false).count, 2)
    }

    func testAlreadyClosedLoopIsUnchanged() {
        let pts = [c(38.28, 21.78), c(38.29, 21.79), c(38.28, 21.78)]
        XCTAssertEqual(MapRouteGeometry.closeLoop(pts, isLoop: true).count, 3)
    }

    func testFewerThanTwoPointsIsUnchanged() {
        let pts = [c(38.28, 21.78)]
        XCTAssertEqual(MapRouteGeometry.closeLoop(pts, isLoop: true).count, 1)
    }
}

// MARK: - Service area for the automatic recentre

final class SyrmosServiceAreaTests: XCTestCase {
    func testGreekCitiesAreInside() {
        XCTAssertTrue(SyrmosServiceArea.contains(CLLocationCoordinate2D(latitude: 37.9838, longitude: 23.7275)))  // Athens
        XCTAssertTrue(SyrmosServiceArea.contains(CLLocationCoordinate2D(latitude: 40.6401, longitude: 22.9444)))  // Thessaloniki
        XCTAssertTrue(SyrmosServiceArea.contains(CLLocationCoordinate2D(latitude: 35.3387, longitude: 25.1442)))  // Heraklion
    }

    func testFarAwayFixesAreOutside() {
        XCTAssertFalse(SyrmosServiceArea.contains(CLLocationCoordinate2D(latitude: 37.3349, longitude: -122.0090)))  // Cupertino
        XCTAssertFalse(SyrmosServiceArea.contains(CLLocationCoordinate2D(latitude: 51.5072, longitude: -0.1276)))    // London
        XCTAssertFalse(SyrmosServiceArea.contains(CLLocationCoordinate2D(latitude: 0, longitude: 0)))
    }
}

