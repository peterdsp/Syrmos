import XCTest
@testable import Syrmos

/// 3.0.2 closures wired into the planner and the departure board.
@MainActor
final class ClosureIntegrationTests: XCTestCase {
    private let t0: Int64 = 1_793_167_200

    private func closure(_ id: String, _ stations: [String], mode: String, lines: [String] = [],
                         start: Int64? = nil, end: Int64? = nil) -> StationClosure {
        StationClosure(id: id, stationIds: stations, lineIds: lines, mode: mode,
                       startsAtEpochSeconds: start ?? t0, endsAtEpochSeconds: end, status: "active")
    }

    private func gate(_ closures: [StationClosure]) -> JourneyPlanner.ClosureGate {
        JourneyPlanner.ClosureGate(closures: closures, now: t0 + 60)
    }

    func testPassThroughKeepsTheLineContinuous() throws {
        let g = gate([closure("syn", ["M2_SYN", "M3_SYN"], mode: "pass_through")])
        let plan = try XCTUnwrap(JourneyPlanner.planDetailed(from: "M3_MON", to: "M3_EVA", language: .english, gate: g))
        XCTAssertEqual(plan.legs.count, 1)
        XCTAssertEqual(plan.legs.first?.lineId, "M3")
        XCTAssertTrue(plan.legs.first?.stationIds.contains("M3_SYN") ?? false, "rides through Syntagma")
    }

    func testCannotBoardOrAlightAtClosedStation() {
        let g = gate([closure("syn", ["M2_SYN", "M3_SYN"], mode: "closed")])
        XCTAssertNil(JourneyPlanner.planDetailed(from: "M3_SYN", to: "M3_EVA", language: .english, gate: g))
        XCTAssertNil(JourneyPlanner.planDetailed(from: "M3_MON", to: "M3_SYN", language: .english, gate: g))
        XCTAssertNil(JourneyPlanner.plan(from: "M3_MON", to: "M2_SYN", language: .english, gate: g))
    }

    func testNoLineChangeAtClosedInterchange() throws {
        let open = try XCTUnwrap(JourneyPlanner.planDetailed(from: "M2_AKR", to: "M3_EVA", language: .english,
                                                             gate: gate([])))
        XCTAssertEqual(open.legs.first?.alightId, "M2_SYN", "baseline changes at Syntagma")

        let g = gate([closure("syn", ["M2_SYN", "M3_SYN"], mode: "pass_through")])
        let rerouted = try XCTUnwrap(JourneyPlanner.planDetailed(from: "M2_AKR", to: "M3_EVA", language: .english, gate: g))
        for (i, leg) in rerouted.legs.enumerated() {
            if i > 0 { XCTAssertNotEqual(leg.boardId, "M3_SYN") }
            if i < rerouted.legs.count - 1 { XCTAssertNotEqual(leg.alightId, "M2_SYN") }
        }
    }

    func testExitOnlyAllowsAlightingButNotBoarding() {
        let g = gate([closure("nik", ["M3_NIK"], mode: "exit_only")])
        XCTAssertNotNil(JourneyPlanner.planDetailed(from: "M3_KOR", to: "M3_NIK", language: .english, gate: g))
        XCTAssertNil(JourneyPlanner.planDetailed(from: "M3_NIK", to: "M3_KOR", language: .english, gate: g))
    }

    func testBoardDropsDeparturesInsideTheWindowOnly() {
        let now = Date(timeIntervalSince1970: TimeInterval(t0 - 10 * 60))
        let deps = [0, 5, 15, 20].map {
            Departure(time: "00:00", lineId: "M3", direction: "Airport", minutesAway: $0, serviceType: "metro", trainNo: nil)
        }
        // Closed from t0 (10 minutes from now) for 8 minutes: 15' is inside, 20' is after.
        let c = closure("eva", ["M3_EVA"], mode: "closed", end: t0 + 8 * 60)
        let kept = ScheduleProjector.removingClosedBoardings(deps, stationId: "M3_EVA", closures: [c], now: now)
        XCTAssertEqual(kept.map(\.minutesAway), [0, 5, 20])
        let otherLine = closure("eva1", ["M3_EVA"], mode: "closed", lines: ["M1"])
        XCTAssertEqual(ScheduleProjector.removingClosedBoardings(deps, stationId: "M3_EVA", closures: [otherLine], now: now).count, 4)
    }

    func testClosureCopyWindow() {
        let lang = AppLanguage.english
        XCTAssertEqual(ClosureCopy.window(startsAt: t0, endsAt: t0 + 8 * 3600, now: t0, lang), "08:00 to 16:00")
        XCTAssertEqual(ClosureCopy.window(startsAt: t0, endsAt: nil, now: t0 + 60, lang), "Until further notice")
        XCTAssertEqual(ClosureCopy.window(startsAt: t0, endsAt: nil, now: t0 + 60, .greek), "Έως νεωτέρας")
        XCTAssertEqual(ClosureCopy.window(startsAt: t0, endsAt: t0 + 8 * 3600, now: t0, .greek), "08:00 έως 16:00")
        let bus = ReplacementBus(label: LocalizedText(en: "Replacement bus", el: "Έκτακτο λεωφορείο"),
                                 fromStop: LocalizedText(en: "Syntagma (Amalias)", el: "Σύνταγμα (Αμαλίας)"),
                                 toStop: LocalizedText(en: "Evangelismos", el: "Ευαγγελισμός"), headwayMinutes: 10, stops: [])
        XCTAssertEqual(ClosureCopy.replacementBus(bus, .greek), "Έκτακτο λεωφορείο κάθε 10', από Σύνταγμα (Αμαλίας) προς Ευαγγελισμός")
        XCTAssertEqual(ClosureCopy.replacementBus(bus, .italian), "Replacement bus ogni 10', da Syntagma (Amalias) a Evangelismos")
    }
}

/// The station screen's row name follows the reader's language (it read the
/// Latin name for Greek readers before 3.0.2).
@MainActor
final class StationRowNameTests: XCTestCase {
    func testRowNameIsLocalized() throws {
        let syn = try XCTUnwrap(SyrmosData.stations(for: "M3").first { $0.id == "M3_SYN" })
        XCTAssertEqual(StationDetailView.stationRowName(syn, .greek), "Σύνταγμα")
        XCTAssertEqual(StationDetailView.stationRowName(syn, .english), "Syntagma")
        let pir = try XCTUnwrap(SyrmosData.stations(for: "M1").first { $0.id == "M1_PIR" })
        XCTAssertEqual(StationDetailView.stationRowName(pir, .italian), "Pireo")
    }
}
