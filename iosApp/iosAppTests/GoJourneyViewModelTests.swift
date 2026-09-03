import XCTest
@testable import Syrmos

@MainActor
final class GoJourneyViewModelTests: XCTestCase {

    // Two-leg journey: M2 (3 stops) -> transfer -> M3 (2 stops).
    private func journey() -> GuidanceJourney {
        GuidanceJourney(legs: [
            GuidanceLeg(lineId: "M2", towards: "Syntagma", stops: [
                GuidanceStop(id: "M2_A", name: "A"),
                GuidanceStop(id: "M2_B", name: "B"),
                GuidanceStop(id: "M2_SYN", name: "Syntagma"),
            ]),
            GuidanceLeg(lineId: "M3", towards: "Airport", stops: [
                GuidanceStop(id: "M3_SYN", name: "Syntagma"),
                GuidanceStop(id: "M3_AER", name: "Airport"),
            ]),
        ])
    }

    func test_walkThrough_currentStepsAndAlerts() {
        let vm = GoJourneyViewModel(journey: journey())

        // Start: board M2, not yet alerting, can advance, cannot go back.
        guard case .board(let line, _, _, _) = vm.current else { return XCTFail("expected board") }
        XCTAssertEqual(line, "M2")
        XCTAssertFalse(vm.shouldAlert)
        XCTAssertTrue(vm.canAdvance)
        XCTAssertFalse(vm.canGoBack)
        XCTAssertEqual(vm.currentLineId, "M2")

        vm.advance() // at B: ride, one stop before Syntagma -> alert
        guard case .getOffNext(let s, let dest, let xfer) = vm.current else { return XCTFail("expected getOffNext") }
        XCTAssertEqual(s, "Syntagma"); XCTAssertFalse(dest); XCTAssertEqual(xfer, "M3")
        XCTAssertTrue(vm.shouldAlert)

        vm.advance() // at Syntagma (M2 alight): transfer to M3
        guard case .transfer(_, let to, _) = vm.current else { return XCTFail("expected transfer") }
        XCTAssertEqual(to, "M3")
        XCTAssertFalse(vm.shouldAlert)

        vm.advance() // board M3 (2-stop leg -> board coincides with alert)
        guard case .board(let l2, _, _, _) = vm.current else { return XCTFail("expected board M3") }
        XCTAssertEqual(l2, "M3")
        XCTAssertTrue(vm.shouldAlert, "2-stop leg: get-off cue coincides with board")

        vm.advance() // arrived at Airport
        XCTAssertTrue(vm.isArrived)
        guard case .arrived(let station) = vm.current else { return XCTFail("expected arrived") }
        XCTAssertEqual(station, "Airport")
        XCTAssertFalse(vm.canAdvance)
    }

    func test_back_reversesPositionAcrossLegs() {
        let vm = GoJourneyViewModel(journey: journey())
        vm.advance(); vm.advance(); vm.advance() // now on M3 leg, stop 0
        XCTAssertEqual(vm.currentLineId, "M3")
        vm.back() // back to M2 alight (Syntagma)
        XCTAssertEqual(vm.currentLineId, "M2")
        if case .transfer = vm.current {} else { XCTFail("expected transfer after back") }
    }

    func test_progress_isMonotonicToOne() {
        let vm = GoJourneyViewModel(journey: journey())
        var last = -1.0
        while vm.canAdvance {
            XCTAssertGreaterThanOrEqual(vm.progress, last)
            last = vm.progress
            vm.advance()
        }
        XCTAssertEqual(vm.progress, 1.0, accuracy: 0.0001)
    }

    func test_reset_returnsToOrigin() {
        let vm = GoJourneyViewModel(journey: journey())
        vm.advance(); vm.advance()
        vm.reset()
        XCTAssertEqual(vm.position.legIndex, 0)
        XCTAssertEqual(vm.position.stopIndex, 0)
    }

    // MARK: Live GO

    // Coords for the two-leg journey: M2 (A,B,Syntagma) west->east, then M3
    // (Syntagma, Airport). 0.012 deg lon ~ 1km at this latitude.
    private func liveCoords() -> [String: GoLocationAdvancer.Coord] {
        [
            "M2_A": .init(lat: 38.0, lon: 23.700),
            "M2_B": .init(lat: 38.0, lon: 23.712),
            "M2_SYN": .init(lat: 38.0, lon: 23.724),
            "M3_SYN": .init(lat: 38.0005, lon: 23.7242),
            "M3_AER": .init(lat: 38.0, lon: 23.736),
        ]
    }

    func test_applyLocation_noOpWhenNotLive() {
        let vm = GoJourneyViewModel(journey: journey(), coords: liveCoords())
        XCTAssertTrue(vm.canGoLive)
        vm.applyLocation(lat: 38.0, lon: 23.712) // near B, but not live
        XCTAssertEqual(vm.position, GuidancePosition(legIndex: 0, stopIndex: 0))
    }

    func test_applyLocation_advancesAndAlertsOncePerLeg() {
        let vm = GoJourneyViewModel(journey: journey(), coords: liveCoords())
        var alerts: [String] = []
        vm.onGetOffAlert = { g in
            if case let .getOffNext(next, _, _) = g { alerts.append(next) }
            if case let .board(_, _, _, next) = g { alerts.append("board:\(next)") }
        }
        vm.startLive()

        vm.applyLocation(lat: 38.0, lon: 23.712)   // near B -> get off next (Syntagma), alert
        XCTAssertEqual(vm.position, GuidancePosition(legIndex: 0, stopIndex: 1))
        XCTAssertEqual(alerts, ["Syntagma"])

        vm.applyLocation(lat: 38.0005, lon: 23.7242) // at interchange -> board M3 (2-stop leg alert)
        XCTAssertEqual(vm.position, GuidancePosition(legIndex: 1, stopIndex: 0))
        XCTAssertEqual(alerts, ["Syntagma", "board:Airport"])

        // A repeat fix on the same leg must not re-alert.
        vm.applyLocation(lat: 38.0005, lon: 23.7242)
        XCTAssertEqual(alerts, ["Syntagma", "board:Airport"])
    }
}
