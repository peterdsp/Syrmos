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

/// Pins the iOS live-session lifecycle (GoActiveJourneyContract) against the same
/// cases as the shared Kotlin ActiveJourneyStore and the web SyrmosActiveJourney:
/// start/advance/back/end, phase derivation, and id-anchored resume that survives a
/// name change (language switch). Persistence is checked through an isolated store.
@MainActor
final class GoActiveJourneyStoreTests: XCTestCase {

    // Two ride legs with a transfer semantics: leg-0 (4 stops) so an interior stop is
    // a real RIDING state; leg-1 (2 stops). `suffix` varies names but never ids.
    private func journey(_ suffix: String = "") -> GuidanceJourney {
        GuidanceJourney(legs: [
            GuidanceLeg(lineId: "line-A", towards: "S4" + suffix, stops: [
                GuidanceStop(id: "s1", name: "S1" + suffix),
                GuidanceStop(id: "s2", name: "S2" + suffix),
                GuidanceStop(id: "s3", name: "S3" + suffix),
                GuidanceStop(id: "s4", name: "S4" + suffix),
            ]),
            GuidanceLeg(lineId: "line-B", towards: "S5" + suffix, stops: [
                GuidanceStop(id: "s4", name: "S4" + suffix),
                GuidanceStop(id: "s5", name: "S5" + suffix),
            ]),
        ])
    }

    func test_start_beginsAtOriginReadyToBoard() {
        let g = journey()
        let a = GoActiveJourneyContract.start(journey: g, language: .english)
        XCTAssertEqual(a.phase, GoActiveJourneyContract.phaseReadyToBoard)
        XCTAssertEqual(a.legId, "leg-0")
        XCTAssertEqual(a.confirmedStopId, "s1")
        XCTAssertEqual(GoActiveJourneyContract.positionOf(a, g), GuidancePosition(legIndex: 0, stopIndex: 0))
    }

    func test_advance_walksEveryPhaseThenArrives() {
        let g = journey()
        var a = GoActiveJourneyContract.start(journey: g, language: .english)
        a = GoActiveJourneyContract.advance(a, g); XCTAssertEqual(a.phase, GoActiveJourneyContract.phaseRiding); XCTAssertEqual(a.confirmedStopId, "s2")
        a = GoActiveJourneyContract.advance(a, g); XCTAssertEqual(a.phase, GoActiveJourneyContract.phaseAlightSoon)
        a = GoActiveJourneyContract.advance(a, g); XCTAssertEqual(a.phase, GoActiveJourneyContract.phaseTransfer); XCTAssertEqual(a.legId, "leg-0"); XCTAssertEqual(a.confirmedStopId, "s4")
        a = GoActiveJourneyContract.advance(a, g); XCTAssertEqual(a.phase, GoActiveJourneyContract.phaseReadyToBoard); XCTAssertEqual(a.legId, "leg-1"); XCTAssertEqual(a.confirmedStopId, "s4")
        a = GoActiveJourneyContract.advance(a, g); XCTAssertEqual(a.phase, GoActiveJourneyContract.phaseArrived); XCTAssertEqual(a.confirmedStopId, "s5")
        let end = GoActiveJourneyContract.advance(a, g) // past destination is a no-op
        XCTAssertEqual(GoActiveJourneyContract.positionOf(end, g), GoActiveJourneyContract.positionOf(a, g))
    }

    func test_back_stepsAcrossLegBoundary() {
        let g = journey()
        var a = GoActiveJourneyContract.start(journey: g, language: .english)
        for _ in 0..<4 { a = GoActiveJourneyContract.advance(a, g) } // (1,0)
        XCTAssertEqual(GoActiveJourneyContract.positionOf(a, g), GuidancePosition(legIndex: 1, stopIndex: 0))
        a = GoActiveJourneyContract.back(a, g)
        XCTAssertEqual(GoActiveJourneyContract.positionOf(a, g), GuidancePosition(legIndex: 0, stopIndex: 3))
        XCTAssertEqual(a.confirmedStopId, "s4")
    }

    func test_resume_landsOnSameStopWhenNamesChange() {
        let g = journey("")
        var a = GoActiveJourneyContract.start(journey: g, language: .english)
        a = GoActiveJourneyContract.advance(a, g); a = GoActiveJourneyContract.advance(a, g) // (0,2)
        guard case .ok(let decodedOpt) = GoActiveJourneyContract.decode(GoActiveJourneyContract.encode(a)),
              let decoded = decodedOpt else { return XCTFail("decode failed") }
        let relabelled = journey(" (EL)")
        XCTAssertEqual(GoActiveJourneyContract.positionOf(decoded, relabelled), GuidancePosition(legIndex: 0, stopIndex: 2))
    }

    func test_unknownStop_fallsBackToLegOrigin() {
        let g = journey()
        var a = GoActiveJourneyContract.start(journey: g, language: .english)
        a.legId = "leg-1"; a.confirmedStopId = "ghost"
        XCTAssertEqual(GoActiveJourneyContract.positionOf(a, g), GuidancePosition(legIndex: 1, stopIndex: 0))
    }

    func test_end_marksEndedAndNotResumable() {
        let g = journey()
        let a = GoActiveJourneyContract.start(journey: g, language: .english)
        XCTAssertTrue(GoActiveJourneyContract.isResumable(a))
        let ended = GoActiveJourneyContract.end(a)
        XCTAssertEqual(ended.phase, GoActiveJourneyContract.phaseEnded)
        XCTAssertTrue(GoActiveJourneyContract.isEnded(ended))
        XCTAssertFalse(GoActiveJourneyContract.isResumable(ended))
    }

    func test_store_roundTripsLiveSessionAndDropsEnded() {
        let defaults = UserDefaults(suiteName: "test.activejourney.\(UUID().uuidString)")!
        let store = GoActiveJourneyStore(defaults: defaults)
        XCTAssertNil(store.active)
        let g = journey()
        let a = GoActiveJourneyContract.advance(GoActiveJourneyContract.start(journey: g, language: .english), g)
        store.set(a)
        XCTAssertEqual(store.active?.confirmedStopId, "s2")
        store.set(GoActiveJourneyContract.end(a))
        XCTAssertNil(store.active, "an ended session is not offered for resume")
        // A fresh store reading the same defaults sees no resumable session either.
        XCTAssertNil(GoActiveJourneyStore(defaults: defaults).active)
        store.clear()
    }
}
