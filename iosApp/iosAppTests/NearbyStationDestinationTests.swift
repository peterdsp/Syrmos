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
}

/// Interchange completeness + actionable-interchange resolution.
/// A hub must expose every line that serves it (symmetric associations), and
/// the interchange must resolve to a real, co-located stop on each other line
/// so DestinationDetailView can offer a tap-through to its timetable.
final class InterchangeAssociationTests: XCTestCase {

    private func meters(_ aLat: Double, _ aLon: Double, _ bLat: Double, _ bLon: Double) -> Double {
        let R = 6_371_000.0
        let p1 = aLat * .pi / 180, p2 = bLat * .pi / 180
        let dp = (bLat - aLat) * .pi / 180, dl = (bLon - aLon) * .pi / 180
        let x = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * R * asin(min(1, sqrt(x)))
    }

    func testPiraeusOnLine1KnowsMetro3AndSuburban() throws {
        let piraeus = SyrmosData.stations(for: "M1").first { $0.id == "M1_PIR" }
        XCTAssertNotNil(piraeus, "M1_PIR must exist on Line 1")
        let lines = Set(piraeus?.lineIds ?? [])
        XCTAssertTrue(lines.isSuperset(of: ["M1", "M3", "A1", "A4"]),
                      "Piraeus via Line 1 must expose M3 and the suburban lines; got \(lines.sorted())")
        XCTAssertTrue(piraeus?.isInterchange ?? false)
    }

    func testInterchangeTargetsFromLine1PiraeusAreActionable() throws {
        let piraeus = try XCTUnwrap(SyrmosData.stations(for: "M1").first { $0.id == "M1_PIR" })
        let targets = SyrmosData.interchangeTargets(from: piraeus, currentLineId: "M1")
        XCTAssertEqual(Set(targets.map { $0.line.id }), ["M3", "A1", "A4"])
        for t in targets {
            let match = SyrmosData.stations(for: t.line.id).first { $0.id == t.stationId }
            XCTAssertNotNil(match, "interchange target \(t.stationId) must be a real stop on \(t.line.id)")
        }
    }

    func testLineAssociationsAreSymmetricAcrossColocatedHubs() throws {
        let assoc = StationCoords.lineAssociations
        let coord = Dictionary(uniqueKeysWithValues: StationCoords.allStations.map {
            ($0.id, ($0.coordinate.latitude, $0.coordinate.longitude))
        })
        for (id, lines) in assoc where lines.count > 1 {
            guard let a = coord[id] else { continue }
            for (otherId, otherLines) in assoc where otherId != id {
                guard let b = coord[otherId] else { continue }
                // Same physical hub: co-located and sharing at least one line.
                if meters(a.0, a.1, b.0, b.1) <= 150, !Set(lines).isDisjoint(with: otherLines) {
                    XCTAssertEqual(Set(lines), Set(otherLines),
                        "Co-located \(id)=\(lines.sorted()) and \(otherId)=\(otherLines.sorted()) must list the same lines")
                }
            }
        }
    }
}
