import XCTest
@testable import Syrmos

/// Mirrors fixtures/closures/effect.json case-for-case, so the iOS
/// StationClosures, the server projector, Kotlin StationClosures and web agree
/// for the same golden inputs (3.0.2).
final class StationClosuresTests: XCTestCase {

    private struct Expected: Decodable {
        let active: Bool
        let mode: String?
        let canBoard: Bool
        let canAlight: Bool
        let closureId: String?
        let endsAtEpochSeconds: Int64?
        let upcomingClosureId: String?
        let upcomingStartsAtEpochSeconds: Int64?
    }

    private struct Case: Decodable {
        let id: String
        let stationId: String
        let lineId: String?
        let now: Int64
        let expected: Expected
    }

    private struct File: Decodable {
        let closures: [StationClosure]
        let cases: [Case]
    }

    func testEveryFixtureCase() throws {
        let file = try Fixture302.decode(File.self, "fixtures/closures/effect.json")
        XCTAssertEqual(file.cases.count, 13)
        for c in file.cases {
            let got = StationClosures.effect(file.closures, stationId: c.stationId, lineId: c.lineId, now: c.now)
            let e = c.expected
            let want = ClosureEffect(
                active: e.active, mode: e.mode, canBoard: e.canBoard, canAlight: e.canAlight,
                closureId: e.closureId, endsAtEpochSeconds: e.endsAtEpochSeconds,
                upcomingClosureId: e.upcomingClosureId, upcomingStartsAtEpochSeconds: e.upcomingStartsAtEpochSeconds
            )
            XCTAssertEqual(got, want, c.id)
        }
    }

    func testPayloadDecodesContractShape() throws {
        let json = """
        {"schemaVersion":1,"generatedAt":"2026-10-27T18:00:00Z","closures":[{"id":"syn-parade-2026",
        "stationIds":["M2_SYN","M3_SYN"],"lineIds":[],"mode":"pass_through","startsAt":"2026-10-28T06:00:00Z",
        "startsAtEpochSeconds":1793167200,"endsAt":null,"endsAtEpochSeconds":null,"status":"scheduled",
        "reason":{"el":"Με εντολή της Αστυνομίας","en":"By police order"},
        "replacementBus":{"label":{"el":"Έκτακτο λεωφορείο","en":"Replacement bus"},
        "fromStop":{"el":"Σύνταγμα (Αμαλίας)","en":"Syntagma (Amalias)"},"toStop":{"el":"Ευαγγελισμός","en":"Evangelismos"},
        "headwayMinutes":10,"stops":[{"name":{"el":"Σύνταγμα"},"lat":37.9755,"lon":23.7356}]},"source":"admin"}]}
        """
        let payload = try JSONDecoder().decode(StationClosuresPayload.self, from: Data(json.utf8))
        let c = try XCTUnwrap(payload.closures.first)
        XCTAssertNil(c.endsAtEpochSeconds)
        XCTAssertEqual(c.replacementBus?.headwayMinutes, 10)
        XCTAssertEqual(c.reason?.pick(.italian), "By police order")
        XCTAssertEqual(c.reason?.pick(.greek), "Με εντολή της Αστυνομίας")
        XCTAssertEqual(c.replacementBus?.stops?.first?.name?.pick(.english), "Σύνταγμα")
    }

    func testMultiIdStationTakesMostRestrictive() throws {
        let file = try Fixture302.decode(File.self, "fixtures/closures/effect.json")
        let t0: Int64 = 1_793_167_200
        let e = StationClosures.effect(file.closures, stationIds: ["M1_KIF", "M3_EVA"], lineId: nil, now: t0 + 60)
        XCTAssertEqual(e.mode, "closed")
        XCTAssertFalse(e.canBoard)
        let up = StationClosures.effect(file.closures, stationIds: ["M1_KIF", "M2_SYN"], lineId: nil, now: t0 - 60)
        XCTAssertEqual(up.upcomingClosureId, "syn-parade")
    }
}

/// Server contract update: headwayMinutes may be null and fromStop/toStop may be
/// empty objects; the bundled seed closures.json must decode.
final class StationClosuresContractTests: XCTestCase {
    func testOptionalReplacementBusFields() throws {
        let json = """
        {"closures":[{"id":"x","stationIds":["M3_EVA"],"lineIds":[],"mode":"closed","startsAtEpochSeconds":1,
        "endsAtEpochSeconds":null,"status":"draft","reason":{},"replacementBus":{"label":{"el":"Λεωφορείο"},
        "fromStop":{},"toStop":{},"headwayMinutes":null,"stops":[]}}]}
        """
        let c = try XCTUnwrap(try JSONDecoder().decode(StationClosuresPayload.self, from: Data(json.utf8)).closures.first)
        let bus = try XCTUnwrap(c.replacementBus)
        XCTAssertNil(bus.headwayMinutes)
        XCTAssertNil(c.reason?.pick(.english))
        XCTAssertEqual(ClosureCopy.replacementBus(bus, .english), "Λεωφορείο")
        XCTAssertEqual(ClosureCopy.replacementBus(ReplacementBus(), .greek), "Έκτακτο λεωφορείο")
    }

    func testBundledSeedDecodes() throws {
        let data = try Fixture302.data("iosApp/iosApp/Resources/seed-schedules-v2/closures.json")
        XCTAssertNoThrow(try JSONDecoder().decode(StationClosuresPayload.self, from: data))
    }
}
