import XCTest
@testable import Syrmos

final class GoLocationAdvancerTests: XCTestCase {

    // A straight west->east line of 4 stops ~1km apart at lat 38.0.
    // 0.009 deg lon ~ 790m at this latitude; use 0.012 for ~1km spacing.
    private let coords: [String: GoLocationAdvancer.Coord] = [
        "S0": .init(lat: 38.0, lon: 23.700),
        "S1": .init(lat: 38.0, lon: 23.712),
        "S2": .init(lat: 38.0, lon: 23.724),
        "S3": .init(lat: 38.0, lon: 23.736),
    ]

    private func line4() -> GuidanceJourney {
        GuidanceJourney(legs: [
            GuidanceLeg(lineId: "L", towards: "S3", stops: [
                GuidanceStop(id: "S0", name: "S0"),
                GuidanceStop(id: "S1", name: "S1"),
                GuidanceStop(id: "S2", name: "S2"),
                GuidanceStop(id: "S3", name: "S3"),
            ]),
        ])
    }

    private func pos(_ l: Int, _ s: Int) -> GuidancePosition { GuidancePosition(legIndex: l, stopIndex: s) }

    func test_atStopCoordinate_placesRiderThere() {
        let j = line4()
        let p = GoLocationAdvancer.advancedPosition(journey: j, current: pos(0, 0), coords: coords, lat: 38.0, lon: 23.724)
        XCTAssertEqual(p, pos(0, 2)) // near S2
    }

    func test_neverMovesBackward() {
        let j = line4()
        // Rider is currently at S2 but a jittery fix lands near S0.
        let p = GoLocationAdvancer.advancedPosition(journey: j, current: pos(0, 2), coords: coords, lat: 38.0, lon: 23.700)
        XCTAssertEqual(p, pos(0, 2), "must not rewind to an earlier stop on a bad fix")
    }

    func test_betweenStops_holdsPosition() {
        let j = line4()
        // Midway between S1 and S2 (~lon 23.718), ~400m from each -> beyond 350m
        // threshold, so hold at current.
        let p = GoLocationAdvancer.advancedPosition(journey: j, current: pos(0, 1), coords: coords, lat: 38.0, lon: 23.718)
        XCTAssertEqual(p, pos(0, 1))
    }

    func test_advancesForwardToNearestStopWithinThreshold() {
        let j = line4()
        let p = GoLocationAdvancer.advancedPosition(journey: j, current: pos(0, 0), coords: coords, lat: 38.0, lon: 23.735)
        XCTAssertEqual(p, pos(0, 3)) // arrived at S3
    }

    func test_transfer_advancesOntoNextLeg() {
        // Two legs sharing an interchange (X) with slightly offset platform coords.
        let j = GuidanceJourney(legs: [
            GuidanceLeg(lineId: "A", towards: "XA", stops: [
                GuidanceStop(id: "A0", name: "A0"),
                GuidanceStop(id: "XA", name: "X"),
            ]),
            GuidanceLeg(lineId: "B", towards: "B1", stops: [
                GuidanceStop(id: "XB", name: "X"),
                GuidanceStop(id: "B1", name: "B1"),
            ]),
        ])
        let c: [String: GoLocationAdvancer.Coord] = [
            "A0": .init(lat: 38.0, lon: 23.700),
            "XA": .init(lat: 38.0, lon: 23.750),
            "XB": .init(lat: 38.0005, lon: 23.7502),
            "B1": .init(lat: 38.010, lon: 23.760),
        ]
        // Rider standing at the interchange, closest to leg B's board platform.
        let p = GoLocationAdvancer.advancedPosition(journey: j, current: pos(0, 0), coords: c, lat: 38.0005, lon: 23.7502)
        XCTAssertEqual(p, pos(1, 0), "at the interchange, advance onto the next leg's board stop")
    }

    func test_haversine_knownDistance() {
        // ~1.1 km per 0.01 deg latitude.
        let d = GoLocationAdvancer.haversine(38.0, 23.7, 38.01, 23.7)
        XCTAssertEqual(d, 1113, accuracy: 30)
    }
}
