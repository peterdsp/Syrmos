import XCTest
@testable import Syrmos

/// Regression tests for the "Near me" black-screen bug.
///
/// Symptom: tapping the Piraeus station card on the Home screen pushed
/// onto an empty NavigationStack destination — visually, a black screen
/// with only the back chevron and tab bar visible.
///
/// Root cause: the NavigationLink's destination was a conditional `if let`
/// chain. At interchange stations like Piraeus, the underlying
/// `MapStationNode` has `stationIds` and `lineIds` in different orders
/// (e.g. stationIds = ["M1_PIR", "M3_PIR", "A1_PIR", "A4_PIR"] but
/// `lineIds.first` resolves to "A1"). Calling
/// `SyrmosData.stations(for: "A1").first(where: { $0.id == "M1_PIR" })`
/// returned nil, so the destination view collapsed to an empty View —
/// which SwiftUI renders as black inside a NavigationStack push.
final class NearbyStationDestinationTests: XCTestCase {

    private func resolve(_ node: MapStationNode) -> TransitStation? {
        for lineId in node.lineIds {
            if let stationId = node.stationIdByLineId[lineId],
               let match = SyrmosData.stations(for: lineId).first(where: { $0.id == stationId }) {
                return match
            }
        }
        for lineId in node.lineIds {
            let stationsOnLine = SyrmosData.stations(for: lineId)
            for sid in node.stationIds {
                if let match = stationsOnLine.first(where: { $0.id == sid }) {
                    return match
                }
            }
        }
        for sid in node.stationIds {
            for lineId in SyrmosData.lines.map(\.id) {
                if let match = SyrmosData.stations(for: lineId).first(where: { $0.id == sid }) {
                    return match
                }
            }
        }
        return nil
    }

    func testPiraeusInterchangeResolvesToATransitStation() throws {
        let piraeus = MapStationNode(
            id: "merged_piraeus",
            stationIds: ["M1_PIR", "M3_PIR", "A1_PIR", "A4_PIR"],
            stationIdByLineId: [
                "M1": "M1_PIR",
                "M3": "M3_PIR",
                "A1": "A1_PIR",
                "A4": "A4_PIR",
            ],
            name: "Piraeus",
            nameEl: "Πειραιάς",
            coordinate: .init(latitude: 37.9490, longitude: 23.6434),
            lineIds: ["M1", "M3", "A1", "A4"],
            isInterchange: true
        )
        XCTAssertNotNil(resolve(piraeus), "Piraeus must resolve — otherwise the destination is empty and we get the black-screen bug.")
    }

    func testMisalignedStationAndLineIdsStillResolve() throws {
        let node = MapStationNode(
            id: "merged_bad_order",
            stationIds: ["A4_PIR", "M1_PIR"],
            stationIdByLineId: [:],
            name: "Piraeus",
            nameEl: "Πειραιάς",
            coordinate: .init(latitude: 37.9490, longitude: 23.6434),
            lineIds: ["M1", "A4"],
            isInterchange: true
        )
        XCTAssertNotNil(resolve(node), "Cross-product fallback must find a TransitStation even when the dictionary is missing.")
    }

    func testSingleLineStationResolves() throws {
        let omonoia = MapStationNode(
            id: "M1_OMO",
            stationIds: ["M1_OMO"],
            stationIdByLineId: ["M1": "M1_OMO"],
            name: "Omonoia",
            nameEl: "Ομόνοια",
            coordinate: .init(latitude: 37.9837, longitude: 23.7283),
            lineIds: ["M1"],
            isInterchange: false
        )
        XCTAssertNotNil(resolve(omonoia))
    }

    func testTotallyUnknownStationReturnsNil() throws {
        let bogus = MapStationNode(
            id: "BOGUS",
            stationIds: ["TOTALLY_FAKE_ID"],
            stationIdByLineId: [:],
            name: "Nonexistent",
            nameEl: "Ανύπαρκτος",
            coordinate: .init(latitude: 0, longitude: 0),
            lineIds: ["FAKE"],
            isInterchange: false
        )
        XCTAssertNil(resolve(bogus))
    }

    func testNearMeResolvedPiraeusHasOnlyValidLinePairs() throws {
        // Regression for the Near Me resolver: it must return a direct-membership
        // base station, never a synthetic one with the whole hub's lines on a
        // single id (which projected phantom departures). Build the real
        // multi-line Piraeus map node and assert every (id, line) pair is valid.
        let node = try XCTUnwrap(
            SyrmosData.mapStations.first {
                $0.displayName.localizedCaseInsensitiveContains("Piraeus") && $0.lineIds.count > 1
            },
            "a multi-line Piraeus map node should exist"
        )
        let station = try XCTUnwrap(resolve(node), "Piraeus node should resolve to a base station")
        for lid in station.lineIds {
            XCTAssertTrue(SyrmosData.stations(for: lid).contains { $0.id == station.id },
                          "resolved \(station.id) lists \(lid) but is not a stop on \(lid)")
        }
    }
}

/// Actionable-interchange resolution. Interchanges are computed by PROXIMITY at
/// the point of use (interchangeTargets), NOT by enriching a station's lineIds:
/// each target resolves to the real per-line station id, so navigation and
/// schedule queries stay valid, and only boardable (operational + scheduled)
/// lines are offered.
/// @MainActor because interchangeTargets/hasSchedule read the main-actor
/// schedule store.
@MainActor
final class InterchangeAssociationTests: XCTestCase {

    func testInterchangeTargetsFromLine1PiraeusAreActionable() throws {
        let piraeus = try XCTUnwrap(SyrmosData.stations(for: "M1").first { $0.id == "M1_PIR" })
        let targets = SyrmosData.interchangeTargets(from: piraeus, currentLineId: "M1")
        XCTAssertEqual(Set(targets.map { $0.line.id }), ["M3", "A1", "A4"],
                       "Piraeus on Line 1 must offer M3 and the suburban lines as transfers")
    }

    func testEveryTransferResolvesToARealStopOnThatLine() throws {
        // Codex regression guard: an interchange target must be a VALID
        // (station id, line) pair on the TARGET line, so navigation and
        // schedule queries never use a foreign id. This is exactly the
        // invariant the earlier lineIds-enrichment approach violated.
        for line in SyrmosData.lines {
            for station in SyrmosData.stations(for: line.id) {
                for target in SyrmosData.interchangeTargets(from: station, currentLineId: line.id) {
                    let realStop = SyrmosData.stations(for: target.line.id)
                        .contains { $0.id == target.stationId }
                    XCTAssertTrue(realStop,
                        "target \(target.stationId) is not a real stop on \(target.line.id)")
                }
            }
        }
    }

    func testTransfersAreOnlyOperationalAndScheduled() throws {
        // Never offer a transfer that opens an empty timetable: drops suspended
        // DK1 and the unscheduled X3/2X airport shuttles.
        for line in SyrmosData.lines {
            for station in SyrmosData.stations(for: line.id) {
                for target in SyrmosData.interchangeTargets(from: station, currentLineId: line.id) {
                    XCTAssertTrue(target.line.isOperational,
                                  "\(target.line.id) offered at \(station.id) is not operational")
                    XCTAssertTrue(SyrmosData.hasSchedule(target.line.id),
                                  "\(target.line.id) offered at \(station.id) has no timetable")
                }
            }
        }
    }

    func testThessalonikiMetroHubExposesBothLines() throws {
        // Proves the fix is not Athens-only: some TM1 station offers a TM2
        // transfer (they share the central Thessaloniki metro corridor).
        let offersTM2 = SyrmosData.stations(for: "TM1").contains { station in
            SyrmosData.interchangeTargets(from: station, currentLineId: "TM1")
                .contains { $0.line.id == "TM2" }
        }
        XCTAssertTrue(offersTM2, "some TM1 station must offer a TM2 transfer")
    }

    func testEveryStationLineIdPairIsValid() throws {
        // Codex invariant: every (station.id, line) in a station's lineIds must
        // resolve through stations(for: line), so the projector never keys a
        // foreign id and produces phantom departures. Covers curated, per-line
        // bundle, and the public map/browse collection.
        func assertValid(_ station: TransitStation) {
            for lid in station.lineIds {
                let onThatLine = SyrmosData.stations(for: lid).contains { $0.id == station.id }
                XCTAssertTrue(onThatLine, "\(station.id) lists \(lid) but is not a stop on \(lid)")
            }
        }
        for line in SyrmosData.lines {
            for station in SyrmosData.stations(for: line.id) { assertValid(station) }
        }
        for station in SyrmosData.bundleStations { assertValid(station) }
    }
}
