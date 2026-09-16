import XCTest
@testable import Syrmos

/// iOS peer of the comfortable-first cases in fixtures/journeys/ranking.json and
/// Kotlin `JourneyRankerTest` / web `journey-ranking.test.js` (finding 7, product
/// decision option 2). Proves `JourneyPlanAdapter`'s default ordering key ranks a
/// known-comfortable journey ahead of a genuinely faster tight/unknown one, sorts
/// a missed connection last so it can never lead, and stays a deterministic total
/// ordering (feasibility class, then duration, arrival, changes, stable id).
final class JourneyRankingOrderTests: XCTestCase {
    private typealias PJ = JourneyPlanAdapter.PlannedJourney

    private func journey(_ chain: [String], _ duration: Int,
                         _ feasibility: JourneyPlanAdapter.Feasibility,
                         leaveBy: Date? = nil) -> PJ {
        PJ(lineChain: chain, transferCount: max(0, chain.count - 1),
           durationSeconds: duration, feasibility: feasibility, leaveBy: leaveBy)
    }

    private func order(_ options: [PJ]) -> [String] {
        options.sorted { JourneyPlanAdapter.recommendedSortKey($0) < JourneyPlanAdapter.recommendedSortKey($1) }
            .map { $0.lineChain.joined(separator: "-") }
    }

    /// S04: a comfortable direct and a comfortable slower option both outrank a
    /// genuinely faster tight one; the tight route is not the recommendation.
    func testComfortableOutranksFasterTight() {
        let out = order([
            journey(["M1", "A1"], 2880, .tight),        // faster but tight
            journey(["A1"], 2880, .comfortable),        // comfortable direct
            journey(["M1", "A2"], 3360, .comfortable),  // comfortable, slower
        ])
        XCTAssertEqual(out, ["A1", "M1-A2", "M1-A1"])
    }

    /// A faster but missed connection never leads; the comfortable option wins.
    func testMissedNeverLeads() {
        let out = order([
            journey(["M1", "A1"], 1800, .missed),
            journey(["A1"], 2400, .comfortable),
        ])
        XCTAssertEqual(out.first, "A1")
        XCTAssertEqual(out.last, "M1-A1")
    }

    /// A known-tight-but-makeable connection outranks a faster unknown one: a
    /// direct ride with unknown boarding access is not evidence of comfort.
    func testTightOutranksUnknown() {
        let out = order([
            journey(["X1"], 1500, .unknown),
            journey(["Y1", "Y2"], 2000, .tight),
        ])
        XCTAssertEqual(out, ["Y1-Y2", "X1"])
    }

    /// Equal feasibility and duration fall back to arrival, then the stable id, so
    /// shuffled input yields the same deterministic order.
    func testDeterministicOnTies() {
        let base = Date(timeIntervalSince1970: 1_760_000_000)
        let early = journey(["E1"], 1800, .comfortable, leaveBy: base)                 // arrives base+1800
        let late = journey(["L1"], 1800, .comfortable, leaveBy: base.addingTimeInterval(60)) // later
        XCTAssertEqual(order([late, early]), ["E1", "L1"])
        XCTAssertEqual(order([early, late]), ["E1", "L1"])
    }
}
