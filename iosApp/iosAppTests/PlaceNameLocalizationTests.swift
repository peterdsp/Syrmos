import XCTest
@testable import Syrmos

/// Finding 10: place names resolve to the rider's language through the existing
/// localized data, never a string swap or an invented translation.
final class PlaceNameLocalizationTests: XCTestCase {

    func testGreekHomeDirectionUsesLocalizedStationName() {
        // "προς Ανθούπολη", not "προς Anthoupoli".
        XCTAssertEqual(DirectionL10n.localized(lineId: "M2", direction: "Anthoupoli", language: .greek), "Ανθούπολη")
    }

    func testEnglishDirectionUnchanged() {
        XCTAssertEqual(DirectionL10n.localized(lineId: "M2", direction: "Anthoupoli", language: .english), "Anthoupoli")
    }

    func testGreekInputStillResolves() {
        XCTAssertEqual(DirectionL10n.localized(lineId: "M2", direction: "Ανθούπολη", language: .greek), "Ανθούπολη")
    }

    func testUnknownDirectionFallsBackHonestly() {
        // No matching stop: keep the source text rather than fabricate a name.
        XCTAssertEqual(DirectionL10n.localized(lineId: "M2", direction: "Nowhere", language: .greek), "Nowhere")
    }

    func testAthensAirportHeroLocalizedName() {
        XCTAssertEqual(AirportHub.athens.displayName.text(.greek), "Ελευθέριος Βενιζέλος")
        XCTAssertEqual(AirportHub.athens.displayName.text(.english), "Eleftherios Venizelos")
    }

    func testAirportCodeStaysUnchanged() {
        // Codes are identity, never translated.
        XCTAssertEqual(AirportHub.athens.code, "ATH")
    }
}
