import XCTest
import CoreLocation
@testable import Syrmos

/// Finding 2: the station selector must show one row per real physical station.
/// Co-located same-name stops collapse (the tram `A1_KIF`/`A2_KIF`, both
/// "Kifisias" at one point), keeping every member line, while genuinely distinct
/// stations ("Kifissia" vs "Kifisias") stay separate. Identity is name AND place,
/// never one alone.
final class StationGroupingTests: XCTestCase {
    private func station(_ id: String, _ name: String, _ nameEl: String,
                         _ lat: Double, _ lon: Double, _ lines: [String]) -> TransitStation {
        TransitStation(id: id, name: name, nameEl: nameEl,
                       coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon),
                       lineIds: lines, isInterchange: false)
    }

    private func kifisData() -> [TransitStation] {
        [
            station("M1_KIF", "Kifissia", "Κηφισιά", 38.0733951, 23.8082198, ["M1"]),
            station("A1_KIF", "Kifisias", "Κηφισίας", 38.0419210, 23.8040729, ["A1"]),
            station("A2_KIF", "Kifisias", "Κηφισίας", 38.0419210, 23.8040729, ["A2"]),
        ]
    }

    func testColocatedDuplicatesCollapseKeepingEveryLine() {
        let groups = StationGrouping.groups(from: kifisData())
        XCTAssertEqual(groups.count, 2, "three ids, two physical stations")
        let kifisias = groups.first { $0.name == "Kifisias" }!
        XCTAssertEqual(kifisias.memberIds, ["A1_KIF", "A2_KIF"], "every routable stop retained")
        XCTAssertEqual(kifisias.representativeId, "A1_KIF", "stable representative = smallest id")
        XCTAssertTrue(kifisias.lineIds.contains("A1") && kifisias.lineIds.contains("A2"),
                      "both served lines remain represented for routing")
    }

    func testKifissiaAndKifisiasStayDistinct() {
        let groups = StationGrouping.groups(from: kifisData())
        let names = Set(groups.map { $0.name })
        XCTAssertTrue(names.contains("Kifissia") && names.contains("Kifisias"),
                      "different spelling and place must not merge")
        let kifissia = groups.first { $0.name == "Kifissia" }!
        XCTAssertEqual(kifissia.memberIds, ["M1_KIF"])
    }

    func testAccentedAndUnaccentedSearchBothHit() {
        let groups = StationGrouping.groups(from: kifisData())
        let kifisias = groups.first { $0.name == "Kifisias" }!
        for q in ["Kifis", "kifis", "Κηφισ", "Κηφισίας", "κηφισιας"] {
            XCTAssertTrue(StationGrouping.matches(kifisias, query: StationGrouping.fold(q)),
                          "query \(q) should match the Kifisias group")
        }
    }

    func testSameNameFarApartDoesNotMerge() {
        // Proximity is required in addition to a name match: identical names at
        // different places stay two rows.
        let groups = StationGrouping.groups(from: [
            station("X_DIM", "Dimarcheio", "Δημαρχείο", 38.0000, 23.7000, ["X"]),
            station("Y_DIM", "Dimarcheio", "Δημαρχείο", 40.6000, 22.9000, ["Y"]),
        ])
        XCTAssertEqual(groups.count, 2, "same name, different place = distinct stations")
    }

    func testReorderedInputYieldsSameGroups() {
        let a = StationGrouping.groups(from: kifisData())
        let b = StationGrouping.groups(from: kifisData().reversed())
        XCTAssertEqual(Set(a.map { $0.id }), Set(b.map { $0.id }))
        let ka = a.first { $0.name == "Kifisias" }!
        let kb = b.first { $0.name == "Kifisias" }!
        XCTAssertEqual(ka.memberIds, kb.memberIds, "member set is order-independent")
        XCTAssertEqual(ka.representativeId, kb.representativeId, "representative is stable")
    }
}
