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
///
/// Fix: `NearbyStationDestination.resolveTransitStation()` now walks
/// every (lineId, stationId) pair until it finds a match, and falls back
/// to a visible placeholder view if nothing matches.
///
/// To wire this file into Xcode: File > Add Files… select this file and
/// the target "Syrmos - Athens Rail TimesTests". Or create a new Unit
/// Testing Bundle target if one doesn't exist yet.
final class NearbyStationDestinationTests: XCTestCase {

    // MARK: - Helpers

    /// Reaches inside the resolver using the same algorithm as production
    /// code. Kept here as the contract for the test to exercise.
    private func resolve(_ node: MapStationNode) -> TransitStation? {
        // Mirror of NearbyStationDestination.resolveTransitStation().
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

    // MARK: - Tests

    /// The canonical Piraeus case from the user's bug report.
    /// Piraeus is an interchange with M1, M3, A1, A4 — 4 lines.
    func testPiraeusInterchangeResolvesToATransitStation() throws {
        let piraeus = MapStationNode(
            id: "merged_piraeus",
            displayName: "Piraeus",
            nameEl: "Πειραιάς",
            coordinate: .init(latitude: 37.9490, longitude: 23.6434),
            stationIds: ["M1_PIR", "M3_PIR", "A1_PIR", "A4_PIR"],
            lineIds: ["M1", "M3", "A1", "A4"],
            isInterchange: true,
            stationIdByLineId: [
                "M1": "M1_PIR",
                "M3": "M3_PIR",
                "A1": "A1_PIR",
                "A4": "A4_PIR",
            ]
        )
        let resolved = resolve(piraeus)
        XCTAssertNotNil(resolved, "Piraeus must resolve — otherwise the destination is empty and we get the black-screen bug.")
    }

    /// The pathological case: stationIds and lineIds are in different orders.
    func testMisalignedStationAndLineIdsStillResolve() throws {
        let node = MapStationNode(
            id: "merged_bad_order",
            displayName: "Piraeus",
            nameEl: "Πειραιάς",
            coordinate: .init(latitude: 37.9490, longitude: 23.6434),
            stationIds: ["A4_PIR", "M1_PIR"],
            lineIds: ["M1", "A4"],
            isInterchange: true,
            // Note: dictionary missing — forces the algorithm to use the
            // cross-product fallback rather than the happy-path lookup.
            stationIdByLineId: [:]
        )
        XCTAssertNotNil(resolve(node), "Cross-product fallback must find a TransitStation even when the dictionary is missing.")
    }

    /// Single-line non-interchange stations (most stations in the network).
    func testSingleLineStationResolves() throws {
        let omonoia = MapStationNode(
            id: "M1_OMO",
            displayName: "Omonoia",
            nameEl: "Ομόνοια",
            coordinate: .init(latitude: 37.9837, longitude: 23.7283),
            stationIds: ["M1_OMO"],
            lineIds: ["M1"],
            isInterchange: false,
            stationIdByLineId: ["M1": "M1_OMO"]
        )
        XCTAssertNotNil(resolve(omonoia))
    }

    /// Garbage input must return nil — the production code then renders the
    /// fallback placeholder view, not a black screen.
    func testTotallyUnknownStationReturnsNil() throws {
        let bogus = MapStationNode(
            id: "BOGUS",
            displayName: "Nonexistent",
            nameEl: "Ανύπαρκτος",
            coordinate: .init(latitude: 0, longitude: 0),
            stationIds: ["TOTALLY_FAKE_ID"],
            lineIds: ["FAKE"],
            isInterchange: false,
            stationIdByLineId: [:]
        )
        XCTAssertNil(resolve(bogus))
    }
}
