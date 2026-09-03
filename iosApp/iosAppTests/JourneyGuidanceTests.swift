import XCTest
@testable import Syrmos

/// Validates the iOS GO engine (`JourneyGuidance`) against the cross-client golden
/// contract in `fixtures/go-guidance/cases.json` -- the same fixtures the web
/// (`web-go.js`) and server (`go_guidance.py`) engines use -- plus the walk-to-
/// arrived property. Keeping all engines on one fixture set prevents GO guidance
/// from drifting between platforms.
final class JourneyGuidanceTests: XCTestCase {

    // MARK: Fixture loading

    private struct Fixtures {
        let journeys: [String: GuidanceJourney]
        let cases: [[String: Any]]
    }

    private func loadFixtures() throws -> Fixtures {
        // fixtures/go-guidance/cases.json lives at the repo root; this test file is
        // at iosApp/iosAppTests/, so climb two directories from its folder.
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("fixtures/go-guidance/cases.json")
        let data = try Data(contentsOf: url)
        let root = try XCTUnwrap(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        let journeysRaw = try XCTUnwrap(root["journeys"] as? [String: Any])
        var journeys: [String: GuidanceJourney] = [:]
        for (name, value) in journeysRaw {
            let legsRaw = try XCTUnwrap((value as? [String: Any])?["legs"] as? [[String: Any]])
            let legs: [GuidanceLeg] = try legsRaw.map { legDict in
                let stopsRaw = try XCTUnwrap(legDict["stops"] as? [[String: Any]])
                let stops = try stopsRaw.map { GuidanceStop(id: try XCTUnwrap($0["id"] as? String),
                                                            name: try XCTUnwrap($0["name"] as? String)) }
                return GuidanceLeg(lineId: try XCTUnwrap(legDict["lineId"] as? String),
                                   towards: try XCTUnwrap(legDict["towards"] as? String),
                                   stops: stops)
            }
            journeys[name] = GuidanceJourney(legs: legs)
        }
        let cases = try XCTUnwrap(root["cases"] as? [[String: Any]])
        return Fixtures(journeys: journeys, cases: cases)
    }

    /// Flatten a guidance value into the same field shape the fixtures assert on.
    private func fields(_ g: JourneyGuidance) -> [String: Any?] {
        switch g {
        case let .board(lineId, towards, stopsRemaining, nextStation):
            return ["kind": "board", "lineId": lineId, "towards": towards,
                    "stopsRemaining": stopsRemaining, "nextStation": nextStation]
        case let .ride(lineId, towards, stopsRemaining, nextStation):
            return ["kind": "ride", "lineId": lineId, "towards": towards,
                    "stopsRemaining": stopsRemaining, "nextStation": nextStation]
        case let .getOffNext(nextStation, isDestination, transferTo):
            return ["kind": "getOffNext", "nextStation": nextStation,
                    "isDestination": isDestination, "transferTo": transferTo]
        case let .transfer(atStation, toLineId, towards):
            return ["kind": "transfer", "atStation": atStation, "toLineId": toLineId, "towards": towards]
        case let .arrived(station):
            return ["kind": "arrived", "station": station]
        }
    }

    private func assertField(_ got: Any?, _ want: Any, _ label: String) {
        if want is NSNull { XCTAssertNil(got ?? nil, label); return }
        if let w = want as? String { XCTAssertEqual(got as? String, w, label) }
        else if let w = want as? Bool, got is Bool { XCTAssertEqual(got as? Bool, w, label) }
        else if let w = want as? Int { XCTAssertEqual(got as? Int, w, label) }
        else { XCTFail("\(label): unhandled expected type \(type(of: want))") }
    }

    // MARK: Tests

    func test_matchesEveryGoldenFixtureCase() throws {
        let fx = try loadFixtures()
        for c in fx.cases {
            let name = c["name"] as? String ?? "?"
            let journey = try XCTUnwrap(fx.journeys[try XCTUnwrap(c["journey"] as? String)])
            let posDict = try XCTUnwrap(c["position"] as? [String: Any])
            let pos = GuidancePosition(legIndex: try XCTUnwrap(posDict["legIndex"] as? Int),
                                       stopIndex: try XCTUnwrap(posDict["stopIndex"] as? Int))
            let got = fields(try JourneyGuidance.at(journey, pos))
            let expect = try XCTUnwrap(c["expect"] as? [String: Any])
            for (key, want) in expect {
                // Bool vs Int disambiguation: JSONSerialization yields NSNumber; a
                // JSON bool is __NSCFBoolean. Our fields() returns native Bool/Int,
                // so compare through assertField which branches on the expected type.
                assertField(got[key] ?? nil, want, "[\(name)] \(key)")
            }
            let alert = try XCTUnwrap(c["alert"] as? Bool)
            XCTAssertEqual(JourneyGuidance.shouldAlertGetOff(journey, pos), alert, "[\(name)] alert")
        }
    }

    func test_advanceWalksToArrivedAlertingOncePerLeg() throws {
        let fx = try loadFixtures()
        for (name, journey) in fx.journeys {
            var pos = GuidancePosition(legIndex: 0, stopIndex: 0)
            var alertsPerLeg: [Int: Int] = [:]
            let totalStops = journey.legs.reduce(0) { $0 + $1.stops.count }
            var steps = 0
            while !JourneyGuidance.isArrived(journey, pos) {
                if JourneyGuidance.shouldAlertGetOff(journey, pos) {
                    alertsPerLeg[pos.legIndex, default: 0] += 1
                }
                pos = JourneyGuidance.advance(journey, pos)
                steps += 1
                XCTAssertLessThanOrEqual(steps, totalStops + 5, "[\(name)] did not converge")
            }
            for i in journey.legs.indices {
                XCTAssertEqual(alertsPerLeg[i] ?? 0, 1, "[\(name)] leg \(i) should alert once")
            }
        }
    }

    func test_rejectsOutOfRangePositions() throws {
        let fx = try loadFixtures()
        let j = try XCTUnwrap(fx.journeys["m2_direct_3"])
        XCTAssertThrowsError(try JourneyGuidance.at(j, GuidancePosition(legIndex: 9, stopIndex: 0)))
        XCTAssertThrowsError(try JourneyGuidance.at(j, GuidancePosition(legIndex: 0, stopIndex: 9)))
    }
}
