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
}
