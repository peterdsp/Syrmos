import XCTest
@testable import Syrmos

/// iOS peer of web `web-tests/journey-detail.test.js` and Kotlin
/// `JourneyDetailTest`, mirroring the golden cases in fixtures/journeys/detail.json.
/// Proves the S05 timeline transform emits the same ordered rows on iOS as on
/// web/Kotlin (node roles, intermediate-stop counts, transfer-gap fallback,
/// null-clock honesty).
final class JourneyDetailTests: XCTestCase {
    private func iso(_ s: String) -> Date { ISO8601DateFormatter().date(from: s)! }

    private func ride(_ id: String, _ line: String, _ from: String, _ to: String, _ stops: [String],
                      _ dep: String?, _ arr: String?, _ timing: String = "scheduled") -> JourneyDetail.DetailLeg {
        JourneyDetail.DetailLeg(id: id, kind: "ride", lineId: line, fromId: from, toId: to,
                                orderedStopIds: stops, departure: dep.map(iso), arrival: arr.map(iso), timingKind: timing)
    }
    private func transfer(_ id: String, _ from: String, _ to: String, min: Int? = nil, walk: Bool = false) -> JourneyDetail.DetailLeg {
        JourneyDetail.DetailLeg(id: id, kind: walk ? "walk" : "transfer", fromId: from, toId: to,
                                orderedStopIds: [from, to], transferMinimumSeconds: min, timingKind: "estimated")
    }

    func testTwoRidesOneTransfer() {
        let rows = JourneyDetail.timeline([
            ride("ride-0", "M1", "M1_PIR", "M1_MON", ["M1_PIR", "M1_FAL", "M1_TAV", "M1_MON"],
                 "2026-01-15T08:00:00+02:00", "2026-01-15T08:15:00+02:00"),
            transfer("transfer-1", "M1_MON", "M3_MON", min: 180),
            ride("ride-2", "M3", "M3_MON", "M3_SYN", ["M3_MON", "M3_SYN"],
                 "2026-01-15T08:20:00+02:00", "2026-01-15T08:25:00+02:00"),
        ])
        XCTAssertEqual(rows.map { $0.kind }, ["board", "stops", "alight", "transfer", "board", "alight"])
        XCTAssertEqual(rows.compactMap { $0.node }, [.origin, .interchange, .interchange, .interchange, .destination])
        XCTAssertEqual(rows[0].lineId, "M1")
        XCTAssertEqual(rows[0].towardsId, "M1_MON")
        XCTAssertEqual(rows[0].clock, iso("2026-01-15T08:00:00+02:00"))
        XCTAssertEqual(rows[0].timingKind, "scheduled")
        XCTAssertEqual(rows[1].count, 2)
        XCTAssertEqual(rows[3].seconds, 180)
    }

    func testDirectRideKeepsNilClocks() {
        let rows = JourneyDetail.timeline([
            ride("ride-0", "M2", "M2_SYN", "M2_ELL", ["M2_SYN", "M2_SYG", "M2_ELL"], nil, nil, "estimated"),
        ])
        XCTAssertEqual(rows.map { $0.kind }, ["board", "stops", "alight"])
        XCTAssertNil(rows.first?.clock)
        XCTAssertNil(rows.last?.clock)
        XCTAssertEqual(rows.first?.node, .origin)
        XCTAssertEqual(rows.last?.node, .destination)
        XCTAssertEqual(rows[1].count, 1)
    }

    func testWalkTransferFallsBackToGap() {
        let rows = JourneyDetail.timeline([
            ride("ride-0", "M1", "M1_PIR", "M1_MON", ["M1_PIR", "M1_MON"],
                 "2026-01-15T08:05:00+02:00", "2026-01-15T08:15:00+02:00"),
            transfer("walk-1", "M1_MON", "M3_MON", walk: true),
            ride("ride-2", "M3", "M3_MON", "M3_SYN", ["M3_MON", "M3_SYN"],
                 "2026-01-15T08:20:00+02:00", "2026-01-15T08:24:00+02:00"),
        ])
        XCTAssertFalse(rows.contains { $0.kind == "stops" })
        XCTAssertEqual(rows.first { $0.kind == "walk" }?.seconds, 300)
    }
}
