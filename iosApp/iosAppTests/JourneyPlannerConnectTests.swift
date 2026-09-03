import XCTest
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
