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

            let progress = try XCTUnwrap((c["progress"] as? NSNumber)?.doubleValue)
            XCTAssertEqual(JourneyGuidance.progress(journey, pos), progress, accuracy: 1e-9, "[\(name)] progress")
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

    // MARK: GO companion-map route projection

    /// A two-leg journey with one interchange, for the projection tests.
    private func projectionJourney() -> GuidanceJourney {
        GuidanceJourney(legs: [
            GuidanceLeg(lineId: "M1", towards: "Kifisia", stops: [
                GuidanceStop(id: "a", name: "A"),
                GuidanceStop(id: "b", name: "B"),
                GuidanceStop(id: "x", name: "X"),  // interchange
            ]),
            GuidanceLeg(lineId: "M3", towards: "Airport", stops: [
                GuidanceStop(id: "x", name: "X"),  // interchange (repeated)
                GuidanceStop(id: "c", name: "C"),
            ]),
        ])
    }

    private let projectionCoords: [String: (lat: Double, lon: Double)] = [
        "a": (37.90, 23.70), "b": (37.95, 23.72),
        "x": (37.98, 23.73), "c": (37.99, 23.74),
    ]

    func test_routeCoordinates_ordersEveryPlacedStop() {
        let journey = projectionJourney()
        let coords = GoRouteProjection.routeCoordinates(journey: journey) { projectionCoords[$0] }
        // Every stop across both legs is placed, in ride order, including the
        // repeated interchange stop X (once per leg).
        XCTAssertEqual(coords.count, 5)
        XCTAssertEqual(coords.map { $0.latitude }, [37.90, 37.95, 37.98, 37.98, 37.99])
    }

    func test_routeCoordinates_skipsUnplaceableStops() {
        let journey = projectionJourney()
        // Drop B from the lookup: the line spans only the stops we can place.
        let partial = projectionCoords.filter { $0.key != "b" }
        let coords = GoRouteProjection.routeCoordinates(journey: journey) { partial[$0] }
        XCTAssertEqual(coords.count, 4)
        XCTAssertFalse(coords.contains { $0.latitude == 37.95 })
    }

    func test_currentCoordinate_followsPosition() {
        let journey = projectionJourney()
        let first = GoRouteProjection.currentCoordinate(
            journey: journey, position: GuidancePosition(legIndex: 0, stopIndex: 1)) { projectionCoords[$0] }
        XCTAssertEqual(first?.latitude, 37.95)  // stop B
        let secondLeg = GoRouteProjection.currentCoordinate(
            journey: journey, position: GuidancePosition(legIndex: 1, stopIndex: 1)) { projectionCoords[$0] }
        XCTAssertEqual(secondLeg?.latitude, 37.99)  // stop C
    }

    func test_currentCoordinate_nilWhenOutOfRangeOrUnplaceable() {
        let journey = projectionJourney()
        // Out of range.
        XCTAssertNil(GoRouteProjection.currentCoordinate(
            journey: journey, position: GuidancePosition(legIndex: 9, stopIndex: 0)) { projectionCoords[$0] })
        // In range but the stop has no coordinate.
        XCTAssertNil(GoRouteProjection.currentCoordinate(
            journey: journey, position: GuidancePosition(legIndex: 0, stopIndex: 0)) { _ in nil })
    }

    // MARK: Timeline projection (twin of core/domain GoTimelineTest.kt)

    private var timelineJourney: GuidanceJourney {
        GuidanceJourney(legs: [
            GuidanceLeg(lineId: "M1", towards: "Monastiraki", stops: [
                GuidanceStop(id: "PIR", name: "Piraeus"), GuidanceStop(id: "FAL", name: "Faliro"),
                GuidanceStop(id: "MOS", name: "Moschato"), GuidanceStop(id: "MON", name: "Monastiraki"),
            ]),
            GuidanceLeg(lineId: "M3", towards: "Syntagma", stops: [
                GuidanceStop(id: "MON", name: "Monastiraki"), GuidanceStop(id: "SYN", name: "Syntagma"),
            ]),
        ])
    }

    func test_timeline_rolesFollowTheLegShape() {
        let rows = GoTimelineProjection.rows(journey: timelineJourney, position: GuidancePosition(legIndex: 0, stopIndex: 0))
        XCTAssertEqual(rows.map(\.role), [.origin, .intermediate, .intermediate, .alight, .origin, .alight])
        XCTAssertEqual(rows.map(\.isDestination), [false, false, false, false, false, true])
    }

    func test_timeline_statesAtTheStartOfTheJourney() {
        let rows = GoTimelineProjection.rows(journey: timelineJourney, position: GuidancePosition(legIndex: 0, stopIndex: 0))
        XCTAssertEqual(rows.map(\.state), [.current, .next, .future, .future, .future, .future])
    }

    func test_timeline_statesMidLegDimWhatIsBehindTheRider() {
        let rows = GoTimelineProjection.rows(journey: timelineJourney, position: GuidancePosition(legIndex: 0, stopIndex: 2))
        XCTAssertEqual(rows.map(\.state), [.past, .past, .current, .next, .future, .future])
    }

    func test_timeline_nextNeverCrossesIntoTheFollowingLeg() {
        let rows = GoTimelineProjection.rows(journey: timelineJourney, position: GuidancePosition(legIndex: 0, stopIndex: 3))
        XCTAssertEqual(rows[3].state, .current)
        XCTAssertEqual(rows[4].state, .future)
    }

    func test_timeline_secondLegMarksTheWholeFirstLegPast() {
        let rows = GoTimelineProjection.rows(journey: timelineJourney, position: GuidancePosition(legIndex: 1, stopIndex: 0))
        XCTAssertEqual(rows.map(\.state), [.past, .past, .past, .past, .current, .next])
    }

    func test_timeline_stopCountDoesNotDoubleCountTheInterchange() {
        XCTAssertEqual(GoTimelineProjection.stopCount(journey: timelineJourney), 4)
        XCTAssertEqual(GoTimelineProjection.stopCount(journey: GuidanceJourney(legs: [])), 0)
    }

    func test_legProgress_fillsLegsBehindTheRiderAndPartOfTheCurrentOne() {
        let segs = GoLegProgress.segments(journey: timelineJourney, position: GuidancePosition(legIndex: 0, stopIndex: 2))
        XCTAssertEqual(segs.map(\.lineId), ["M1", "M3"])
        XCTAssertEqual(segs[0].fraction, 2.0 / 3.0, accuracy: 1e-9)
        XCTAssertEqual(segs[1].fraction, 0, accuracy: 1e-9)
        let later = GoLegProgress.segments(journey: timelineJourney, position: GuidancePosition(legIndex: 1, stopIndex: 0))
        XCTAssertEqual(later[0].fraction, 1, accuracy: 1e-9)
        XCTAssertEqual(later[1].fraction, 0, accuracy: 1e-9)
    }

    func test_legProgress_stopsRiddenCountsHopsAcrossLegs() {
        XCTAssertEqual(GoLegProgress.stopsRidden(journey: timelineJourney, position: GuidancePosition(legIndex: 0, stopIndex: 0)), 0)
        XCTAssertEqual(GoLegProgress.stopsRidden(journey: timelineJourney, position: GuidancePosition(legIndex: 0, stopIndex: 2)), 2)
        XCTAssertEqual(GoLegProgress.stopsRidden(journey: timelineJourney, position: GuidancePosition(legIndex: 1, stopIndex: 0)), 3)
        XCTAssertEqual(GoLegProgress.stopsRidden(journey: timelineJourney, position: GuidancePosition(legIndex: 1, stopIndex: 1)), 4)
    }

    // MARK: Per-leg map runs

    func test_legRuns_oneRunPerLegInRideOrder_skippingUnplaceableLegs() {
        let coords: [String: (lat: Double, lon: Double)] = [
            "PIR": (37.948, 23.643), "FAL": (37.945, 23.665), "MOS": (37.955, 23.680), "MON": (37.976, 23.726),
            "SYN": (37.975, 23.735),
        ]
        let runs = GoRouteProjection.legRuns(journey: timelineJourney) { coords[$0] }
        XCTAssertEqual(runs.map(\.lineId), ["M1", "M3"])
        XCTAssertEqual(runs[0].coordinates.count, 4)
        XCTAssertEqual(runs[1].coordinates.count, 2)
        // A leg whose stops cannot be placed draws nothing rather than a stray point.
        let partial = GoRouteProjection.legRuns(journey: timelineJourney) { $0 == "SYN" ? nil : coords[$0] }
        XCTAssertEqual(partial.map(\.lineId), ["M1"])
    }

    // MARK: GO camera intent (twin of Kotlin GoCameraTest)

    func test_camera_followCentersOnStopChangeAndManualPanHoldsTheView() {
        XCTAssertEqual(GoCamera.reduce(.follow, .currentStopChanged).action, .centerCurrent)
        let panned = GoCamera.reduce(.follow, .userPanned)
        XCTAssertEqual(panned.intent, .manual)
        XCTAssertEqual(panned.action, .none)
        XCTAssertEqual(GoCamera.reduce(.manual, .currentStopChanged).action, .none)
    }

    func test_camera_fitKeepsTheWholeRouteUntilFollowIsTapped() {
        let fit = GoCamera.reduce(.manual, .fitTapped)
        XCTAssertEqual(fit.intent, .fit)
        XCTAssertEqual(fit.action, .fitRoute)
        XCTAssertEqual(GoCamera.reduce(.fit, .currentStopChanged).action, .none)
        let follow = GoCamera.reduce(.fit, .followTapped)
        XCTAssertEqual(follow.intent, .follow)
        XCTAssertEqual(follow.action, .centerCurrent)
    }

    func test_camera_geometryChangeReframesOnlyWhenTheIntentAsksForIt() {
        XCTAssertEqual(GoCamera.reduce(.follow, .geometryChanged).action, .centerCurrent)
        XCTAssertEqual(GoCamera.reduce(.fit, .geometryChanged).action, .fitRoute)
        XCTAssertEqual(GoCamera.reduce(.manual, .geometryChanged).action, .none)
    }

    // MARK: GO timeline focus (twin of Kotlin GoTimelineFocusTest)

    func test_timelineFocus_visibleOnlyWhenTheWholeRowIsInsideTheViewport() {
        XCTAssertTrue(GoTimelineFocus.isVisible(rowTop: 100, rowBottom: 144, viewportTop: 0, viewportBottom: 600))
        XCTAssertFalse(GoTimelineFocus.isVisible(rowTop: -10, rowBottom: 34, viewportTop: 0, viewportBottom: 600))
        XCTAssertFalse(GoTimelineFocus.isVisible(rowTop: 580, rowBottom: 624, viewportTop: 0, viewportBottom: 600))
        XCTAssertTrue(GoTimelineFocus.isVisible(rowTop: 0, rowBottom: 44, viewportTop: 0, viewportBottom: 600))
    }

    func test_timelineFocus_targetPlacesTheRowAThirdDown() {
        XCTAssertEqual(GoTimelineFocus.targetOffset(rowTopInContent: 900, viewportHeight: 600, maxOffset: 2000), 700)
    }

    func test_timelineFocus_targetIsClampedToTheScrollableRange() {
        XCTAssertEqual(GoTimelineFocus.targetOffset(rowTopInContent: 100, viewportHeight: 600, maxOffset: 2000), 0)
        XCTAssertEqual(GoTimelineFocus.targetOffset(rowTopInContent: 2500, viewportHeight: 600, maxOffset: 2000), 2000)
        XCTAssertEqual(GoTimelineFocus.targetOffset(rowTopInContent: 300, viewportHeight: 600, maxOffset: -5), 0)
    }
}
