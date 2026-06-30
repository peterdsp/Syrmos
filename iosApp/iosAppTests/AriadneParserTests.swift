import XCTest
@testable import Syrmos

/// Mirrors the KMP `AthensTransitParserTest` so Ariadne behaves identically on
/// iOS and Android/Web across all three supported languages.
final class AriadneParserTests: XCTestCase {

    private let vocab = AssistantVocabulary(
        stations: [
            StationVocab(id: "M2_SYN", names: ["Syntagma", "Σύνταγμα", "Sintagma"], lineIds: ["M2", "M3"]),
            StationVocab(id: "M1_PIR", names: ["Piraeus", "Πειραιάς", "Pireas"], lineIds: ["M1", "A1"]),
            StationVocab(id: "M3_AER", names: ["Airport", "Αεροδρόμιο", "Aeroporti"], lineIds: ["M3", "A1"]),
            StationVocab(id: "M1_MON", names: ["Monastiraki", "Μοναστηράκι"], lineIds: ["M1", "M3"]),
        ],
        lines: [
            LineVocab(id: "M1", aliases: ["M1", "line 1", "γραμμή 1"]),
            LineVocab(id: "M2", aliases: ["M2", "line 2", "γραμμή 2", "metro 2"]),
            LineVocab(id: "M3", aliases: ["M3", "line 3"]),
            LineVocab(id: "A1", aliases: ["A1", "airport line"]),
        ]
    )
    private lazy var parser = AthensTransitParser(vocabulary: vocab)

    func test_departuresFromStation() {
        guard case let .showDepartures(stationId, _, _) = parser.parse("next trains from Syntagma") else {
            return XCTFail("expected departures")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    func test_bareLineDeparturesNoClarification() {
        guard case let .showDepartures(_, lineId, _) = parser.parse("next M2 train") else {
            return XCTFail("expected departures")
        }
        XCTAssertEqual(lineId, "M2")
    }

    func test_departuresWithNeitherAsksStation() {
        guard case let .needsClarification(_, missing) = parser.parse("when is the next train") else {
            return XCTFail("expected clarification")
        }
        XCTAssertEqual(missing, .station)
    }

    func test_lastTrainWithStation() {
        guard case let .lastTrain(stationId, _) = parser.parse("when is the last train from Piraeus") else {
            return XCTFail("expected last train")
        }
        XCTAssertEqual(stationId, "M1_PIR")
    }

    func test_planTripOrdersEndpoints() {
        guard case let .planTrip(from, to, _) = parser.parse("how do I get from Piraeus to Syntagma") else {
            return XCTFail("expected plan trip")
        }
        XCTAssertEqual(from, "M1_PIR")
        XCTAssertEqual(to, "M2_SYN")
    }

    func test_rainRoutesLowExposureAsksOrigin() {
        guard case let .needsClarification(base, missing) = parser.parse("it's raining, get me to the Airport") else {
            return XCTFail("expected clarification")
        }
        XCTAssertEqual(missing, .originStation)
        guard case let .planTrip(_, to, lowExposure) = base else { return XCTFail("expected plan trip base") }
        XCTAssertEqual(to, "M3_AER")
        XCTAssertTrue(lowExposure)
    }

    func test_alerts() {
        guard case .showAlerts = parser.parse("any service alerts today?") else {
            return XCTFail("expected alerts")
        }
    }

    func test_help() {
        guard case .help = parser.parse("what can you do?") else { return XCTFail("expected help") }
    }

    func test_outOfScopeWeatherElsewhere() {
        guard case .outOfScope = parser.parse("what's the weather in London") else {
            return XCTFail("expected out of scope")
        }
    }

    func test_outOfScopeGeneral() {
        guard case .outOfScope = parser.parse("who won the election") else {
            return XCTFail("expected out of scope")
        }
    }

    func test_greekDepartures() {
        guard case let .showDepartures(stationId, _, _) = parser.parse("επόμενα δρομολόγια από Σύνταγμα") else {
            return XCTFail("expected departures")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    func test_albanianPlanTrip() {
        guard case let .planTrip(from, to, _) = parser.parse("si shkoj nga Pireas te Sintagma") else {
            return XCTFail("expected plan trip")
        }
        XCTAssertEqual(from, "M1_PIR")
        XCTAssertEqual(to, "M2_SYN")
    }
}
