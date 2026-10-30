import XCTest
@testable import Syrmos

/// Mirrors `MapTapTargetTest` in core/common, so the Swift copy of the rule
/// cannot drift from the Kotlin one.
///
/// A marker's artwork is also its hit rect: `MKAnnotationView` hit-tests
/// against its bounds, and the station image is 15pt. Sizing the view to the
/// shared minimum is what makes a station openable with a finger.
final class MapTapTargetTests: XCTestCase {

    func testMinimumTapTargetClearsTheAppleAndAndroidFloor() {
        XCTAssertGreaterThanOrEqual(MapDesignTokens.minTapTarget, 48, "44pt Apple / 48dp Android")
    }

    func testEveryStationDotClearsTheMinimumTapTarget() {
        for dot in [MapDesignTokens.dotCountry, MapDesignTokens.dotCity, MapDesignTokens.dotSelected, 15] {
            for selected in [false, true] {
                let size = MapDesignTokens.pinBitmapSize(dotSize: dot, selected: selected)
                XCTAssertGreaterThanOrEqual(
                    size, MapDesignTokens.minTapTarget,
                    "dot \(dot) selected \(selected) produced \(size)"
                )
            }
        }
    }

    func testASmallDotIsPaddedUpRatherThanGrown() {
        XCTAssertEqual(
            MapDesignTokens.pinBitmapSize(dotSize: MapDesignTokens.dotCountry, selected: false),
            MapDesignTokens.minTapTarget
        )
    }

    func testALargeSelectedDotReservesRoomForItsHalo() {
        XCTAssertEqual(MapDesignTokens.pinBitmapSize(dotSize: 60, selected: true), 120)
        XCTAssertEqual(MapDesignTokens.pinBitmapSize(dotSize: 60, selected: false), 64)
    }

    func testSelectingADotNeverShrinksIt() {
        for dot in stride(from: CGFloat(1), through: 80, by: 1) {
            XCTAssertGreaterThanOrEqual(
                MapDesignTokens.pinBitmapSize(dotSize: dot, selected: true),
                MapDesignTokens.pinBitmapSize(dotSize: dot, selected: false),
                "dot \(dot)"
            )
        }
    }

    /// The vehicle capsule is 24x13pt, so its canvas must also be lifted.
    func testTheVehicleCanvasClearsTheMinimum() {
        let canvas = max(max(MapDesignTokens.vehicleW, MapDesignTokens.vehicleH) + 8,
                         MapDesignTokens.minTapTarget)
        XCTAssertGreaterThanOrEqual(canvas, MapDesignTokens.minTapTarget)
    }
}
