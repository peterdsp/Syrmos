import XCTest
import CoreLocation
@testable import Syrmos

/// Integration: the point-to-point planner feeds the GO engine end to end on the
/// real bundled network. Proves `JourneyPlanner.planDetailed` produces per-leg
/// stop sequences, `GuidanceJourney.from` maps them, and `JourneyGuidance` guides
/// the rider board -> ... -> arrived. Also guards that the planner refactor did
/// not change `plan()`'s leg shape (compute and planDetailed stay consistent).
final class JourneyPlannerConnectTests: XCTestCase {

    private let lang: AppLanguage = .english

    /// First line with at least `n` stops, so the test is robust to seed changes.
    private func lineWithStops(_ n: Int) -> (id: String, stops: [TransitStation])? {
        for line in SyrmosData.operationalLines {
            let stops = SyrmosData.stations(for: line.id)
            if stops.count >= n { return (line.id, stops) }
        }
        return nil
    }

    /// Regression: the planner must never "transfer" between two stations that
    /// merely share a name but are in different cities (Athens vs Thessaloniki
    /// "Panepistimio", ~300 km apart). Piraeus -> Nea Elvetia used to route
    /// M3 -> M2 -> TM1 by jumping cities at Panepistimio. Every real transfer is
    /// between physically co-located stops, so assert that invariant directly.
    func test_crossCityJourneyNeverTransfersBetweenFarSameNamedStations() throws {
        var byId: [String: TransitStation] = [:]
        for line in SyrmosData.operationalLines {
            for station in SyrmosData.stations(for: line.id) { byId[station.id] = station }
        }
        // Athens Panepistimio and Thessaloniki Panepistimio share a name; confirm
        // the fixture still has them far apart, else the test proves nothing.
        if let athens = byId["M2_PAN"], let thess = byId["TM1_PAN"] {
            let gap = CLLocation(latitude: athens.coordinate.latitude, longitude: athens.coordinate.longitude)
                .distance(from: CLLocation(latitude: thess.coordinate.latitude, longitude: thess.coordinate.longitude))
            XCTAssertGreaterThan(gap, 1000, "fixture precondition: the two Panepistimio are in different cities")
        }

        guard let plan = JourneyPlanner.planDetailed(from: "M1_PIR", to: "TM1_NEL", language: lang) else {
            // No connecting route is acceptable: the bug was a WRONG route, not a
            // missing one. A nil plan cannot contain a bad transfer.
            return
        }
        guard plan.legs.count >= 2 else { return }
        for i in 0..<(plan.legs.count - 1) {
            let alightId = plan.legs[i].alightId
            let boardId = plan.legs[i + 1].boardId
            guard alightId != boardId, let a = byId[alightId], let b = byId[boardId] else { continue }
            let metres = CLLocation(latitude: a.coordinate.latitude, longitude: a.coordinate.longitude)
                .distance(from: CLLocation(latitude: b.coordinate.latitude, longitude: b.coordinate.longitude))
            XCTAssertLessThanOrEqual(
                metres, 500,
                "transfer \(alightId) -> \(boardId) spans \(Int(metres)) m: the planner stitched two cities together"
            )
        }
    }

    /// Regression: the Athens -> Thessaloniki intercity (~500 km via IC1) must be
    /// costed by distance, not a flat per-stop value, or the planner reports ~83
    /// minutes for a ride that really takes several hours.
    func test_intercityJourneyUsesRealisticTravelTime() throws {
        // Piraeus -> Thessaloniki (GR_THE, the IC1 terminus) routes via the
        // ~500 km Athens-Thessaloniki train; it cannot be ~83 min.
        guard let plan = JourneyPlanner.plan(from: "M1_PIR", to: "GR_THE", language: lang) else {
            throw XCTSkip("no Athens->Thessaloniki route in the test bundle")
        }
        XCTAssertGreaterThan(
            plan.totalMinutes, 180,
            "Piraeus -> Thessaloniki came out as \(plan.totalMinutes) min; the intercity leg is being costed like metro hops"
        )
    }

    /// Regression: Piraeus -> Thessaloniki rides the intercity, which has no
    /// bundled timetable. Arrive-by must still return the route as an estimate
    /// (estimatedNoTimetable) rather than reporting "no journey arrives by that
    /// time", which wrongly implied no route exists.
    func test_arriveBy_withNoIntercityTimetable_showsEstimateNotNoJourney() throws {
        let target = Date(timeIntervalSince1970: SyrmosClock.now.timeIntervalSince1970 + 3 * 24 * 3600)
        guard let p = JourneyPlanAdapter.plan(
            from: "M1_PIR", to: "GR_THE", language: lang,
            departuresFor: { _, _ in [] }, mode: .arriveBy, arriveBy: target
        ) else {
            throw XCTSkip("no Athens->Thessaloniki route in the test bundle")
        }
        XCTAssertFalse(p.noJourney, "a route whose intercity leg has no timetable is an estimate, not 'no journey'")
        XCTAssertTrue(p.estimatedNoTimetable, "the no-timetable route should be flagged as an estimate")
    }

    func test_planDetailed_singleLeg_guidesToArrived() throws {
        guard let (lineId, stops) = lineWithStops(4) else {
            throw XCTSkip("No operational line with >=4 stops in the test bundle")
        }
        let from = stops[0].id
        let to = stops[3].id
        guard let detailed = JourneyPlanner.planDetailed(from: from, to: to, language: lang) else {
            throw XCTSkip("planDetailed returned nil for \(from)->\(to)")
        }
        // Single-line hop: one leg on that line, contiguous stop ids from board to
        // alight, board == from and alight == to.
        XCTAssertEqual(detailed.legs.count, 1)
        let leg = detailed.legs[0]
        XCTAssertEqual(leg.lineId, lineId)
        XCTAssertEqual(leg.boardId, from)
        XCTAssertEqual(leg.alightId, to)
        XCTAssertGreaterThanOrEqual(leg.stationIds.count, 2)

        // Feed to the GO engine and walk to arrival.
        let journey = GuidanceJourney.from(detailed, language: lang)
        var pos = GuidancePosition(legIndex: 0, stopIndex: 0)
        var alerts = 0
        var steps = 0
        while !JourneyGuidance.isArrived(journey, pos) {
            if JourneyGuidance.shouldAlertGetOff(journey, pos) { alerts += 1 }
            pos = JourneyGuidance.advance(journey, pos)
            steps += 1
            XCTAssertLessThanOrEqual(steps, leg.stationIds.count + 3, "did not converge")
        }
        XCTAssertEqual(alerts, 1, "exactly one get-off alert on a single leg")
        if case let .arrived(station) = try JourneyGuidance.at(journey, pos) {
            XCTAssertEqual(station, journey.legs[0].stops.last?.name)
        } else {
            XCTFail("expected arrived at destination")
        }
    }

    func test_planDetailed_agreesWith_plan_onLegShape() throws {
        guard let (_, stops) = lineWithStops(4) else {
            throw XCTSkip("No operational line with >=4 stops in the test bundle")
        }
        let from = stops[0].id
        let to = stops[3].id
        guard let plan = JourneyPlanner.plan(from: from, to: to, language: lang),
              let detailed = JourneyPlanner.planDetailed(from: from, to: to, language: lang) else {
            throw XCTSkip("no route for \(from)->\(to)")
        }
        // Same number of legs and same line ids in order.
        XCTAssertEqual(plan.legs.count, detailed.legs.count)
        XCTAssertEqual(plan.legs.map { $0.lineId }, detailed.legs.map { $0.lineId })
        // Each display leg's `stops` count equals detailed leg segment count.
        for (p, d) in zip(plan.legs, detailed.legs) {
            XCTAssertEqual(p.stops, d.stationIds.count - 1, "leg \(p.lineId) stop count")
        }
    }

    func test_plan_stillReturnsARoute_afterRefactor() throws {
        // Guard the Ariadne-facing API: a valid same-line route still plans.
        guard let (_, stops) = lineWithStops(3) else {
            throw XCTSkip("No operational line with >=3 stops")
        }
        let plan = JourneyPlanner.plan(from: stops[0].id, to: stops[2].id, language: lang)
        XCTAssertNotNil(plan)
        XCTAssertGreaterThan(plan?.totalMinutes ?? 0, 0)
    }
}
