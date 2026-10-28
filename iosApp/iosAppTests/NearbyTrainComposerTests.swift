import XCTest
@testable import Syrmos

/// 3.0.2 nearby next-train notification text and calendar destination resolving.
final class NearbyTrainComposerTests: XCTestCase {

    func testOwnerExampleGreek() throws {
        let out = try XCTUnwrap(NearbyTrainComposer.compose(
            stationName: "Νίκαια", stationKey: "M3_NIK", walkMinutes: 5,
            directions: [
                NearbyDirection(lineId: "M3", destination: "Αεροδρόμιο",
                                departures: [CoachDeparture(minutes: 8, isLast: false), CoachDeparture(minutes: 18, isLast: false)]),
                NearbyDirection(lineId: "M3", destination: "Δημοτικό Θέατρο",
                                departures: [CoachDeparture(minutes: 2, isLast: false), CoachDeparture(minutes: 10, isLast: false)]),
            ],
            stationClosed: false, athensDate: "2026-10-27", language: .greek))
        XCTAssertEqual(out.title, "Νίκαια · 5' με τα πόδια")
        XCTAssertEqual(out.headlineCase, .relaxNextOneIsClose)
        let lines = out.body.components(separatedBy: "\n")
        XCTAssertEqual(lines[0], "Αεροδρόμιο: 8', 18'")
        XCTAssertEqual(lines[1], "Δημοτικό Θέατρο: 2', 10' (κατά πρόγραμμα)")
        let variant = NearbyDepartureCoach.variantIndex(stationId: "M3_NIK", athensDate: "2026-10-27", variantCount: 4)
        let expected = NearbyCopy.coachLine(.relaxNextOneIsClose, variant: variant, language: .greek,
                                            params: .init(next: 2, following: 10, margin: nil, wait: 5, line: "M3", destination: "Δημοτικό Θέατρο"))
        XCTAssertEqual(lines[2], expected)
        XCTAssertTrue(lines[2].contains("Δημοτικό Θέατρο"))
    }

    func testClosedStationUsesClosedCopy() throws {
        let out = try XCTUnwrap(NearbyTrainComposer.compose(
            stationName: "Syntagma", stationKey: "M2_SYN", walkMinutes: 3, directions: [],
            stationClosed: true, athensDate: "2026-10-28", language: .english))
        XCTAssertEqual(out.headlineCase, .stationClosed)
        XCTAssertTrue(out.body.lowercased().contains("closed"))
    }

    func testNoDeparturesComposesNothing() {
        XCTAssertNil(NearbyTrainComposer.compose(stationName: "X", stationKey: "X", walkMinutes: 3, directions: [],
                                                 stationClosed: false, athensDate: "2026-10-28", language: .english))
    }

    func testDirectionsGroupAndFlagLastTrain() {
        let deps = [
            Departure(time: "23:50", lineId: "M3", direction: "Airport", minutesAway: 20, serviceType: "metro", trainNo: nil),
            Departure(time: "23:35", lineId: "M3", direction: "Airport", minutesAway: 5, serviceType: "metro", trainNo: nil),
            Departure(time: "23:40", lineId: "M3", direction: "Piraeus", minutesAway: 10, serviceType: "metro", trainNo: nil),
        ]
        let dirs = NearbyTrainComposer.directions(from: deps, overnight: false) { $0.direction }
        XCTAssertEqual(dirs.map(\.destination), ["Airport", "Piraeus"])
        XCTAssertEqual(dirs[0].departures, [CoachDeparture(minutes: 5, isLast: false), CoachDeparture(minutes: 20, isLast: true)])
        XCTAssertEqual(dirs[1].departures, [CoachDeparture(minutes: 10, isLast: true)])
        let overnight = NearbyTrainComposer.directions(from: deps, overnight: true) { $0.direction }
        XCTAssertFalse(overnight.flatMap(\.departures).contains { $0.isLast })
    }

    func testLedgerCooldownAndDailyCount() {
        let ids = ["TEST_\(UUID().uuidString)"]
        let now = Date(timeIntervalSince1970: 1_793_167_200)
        XCTAssertFalse(NearbyNotificationLedger.inCooldown(stationIds: ids, now: now))
        NearbyNotificationLedger.record(stationIds: ids, now: now, countsTowardDailyCap: false)
        XCTAssertTrue(NearbyNotificationLedger.inCooldown(stationIds: ids, now: now.addingTimeInterval(44 * 60)))
        XCTAssertFalse(NearbyNotificationLedger.inCooldown(stationIds: ids, now: now.addingTimeInterval(45 * 60)))
    }

    func testCalendarResolverAirportAndStations() {
        XCTAssertEqual(CalendarDestinationResolver.airportStationId(title: "Flight to Paris", location: "ATH"), "M3_AER")
        XCTAssertEqual(CalendarDestinationResolver.airportStationId(title: "Πτήση", location: "Αεροδρόμιο Μακεδονία"), "THS_AIR")
        XCTAssertEqual(CalendarDestinationResolver.airportStationId(title: "A3 123", location: "SKG"), "THS_AIR")
        let stations: [(id: String, names: [String])] = [
            (id: "M3_SYN", names: ["Syntagma", "Σύνταγμα"]),
            (id: "M3_MEG", names: ["Megaro Mousikis", "Μέγαρο Μουσικής"]),
            (id: "M1_ANO", names: ["Ano", "Άνω"]),
        ]
        XCTAssertEqual(CalendarDestinationResolver.stationMatch(location: "Πλατεία Συντάγματος, Αθήνα", stations: stations), nil)
        XCTAssertEqual(CalendarDestinationResolver.stationMatch(location: "Σύνταγμα, Αθήνα", stations: stations), "M3_SYN")
        XCTAssertEqual(CalendarDestinationResolver.stationMatch(location: "Megaro Mousikis, Vas. Sofias", stations: stations), "M3_MEG")
        XCTAssertNil(CalendarDestinationResolver.stationMatch(location: "Ano Liosia 5", stations: stations))
        XCTAssertTrue(CalendarDestinationResolver.isServed(.init(latitude: 37.98, longitude: 23.73)))
        XCTAssertTrue(CalendarDestinationResolver.isServed(.init(latitude: 40.63, longitude: 22.95)))
        XCTAssertFalse(CalendarDestinationResolver.isServed(.init(latitude: 39.36, longitude: 22.94)))
    }
}

/// Nearby notification names go through the app's real localized-name data
/// (station-complex registry, then the coordinate table), not raw strings.
@MainActor
final class NearbyPlaceNamesTests: XCTestCase {
    private let expected: [AppLanguage: (station: String, airport: String, doukissis: String, dimotiko: String)] = [
        .greek: ("Νίκαια", "Αεροδρόμιο", "Δουκίσσης Πλακεντίας", "Δημοτικό Θέατρο"),
        .english: ("Nikaia", "Airport", "Douk. Plakentias", "Dimotiko Theatro"),
        .albanian: ("Nikaia", "Aeroporti", "Dukisës Plakendias", "Dimotiko Theatro"),
        .italian: ("Nikaia", "Aeroporto", "Douk. Plakentias", "Dimotiko Theatro"),
    ]

    private var nikaia: MapStationNode {
        get throws { try XCTUnwrap(PreloadedData.stations.first { $0.stationIds.contains("M3_NIK") }) }
    }

    func testNikaiaNamesInAllLanguages() throws {
        let node = try nikaia
        for (lang, e) in expected {
            XCTAssertEqual(NearbyPlaceNames.stationName(node, lang), e.station, "\(lang)")
            XCTAssertEqual(NearbyPlaceNames.destination(lineId: "M3", direction: "Airport", lang), e.airport, "\(lang)")
            XCTAssertEqual(NearbyPlaceNames.destination(lineId: "M3", direction: "Doukissis Plakentias", lang), e.doukissis, "\(lang)")
            XCTAssertEqual(NearbyPlaceNames.destination(lineId: "M3", direction: "Dimotiko Theatro", lang), e.dimotiko, "\(lang)")
            XCTAssertEqual(NearbyPlaceNames.destination(lineId: "M3", direction: "Δημοτικό Θέατρο", lang), e.dimotiko, "\(lang)")
        }
        XCTAssertEqual(NearbyPlaceNames.destination(lineId: "M3", direction: "Nowhere Street", .greek), "Nowhere Street")
    }

    /// End to end at Nikaia: real offline projection, real name lookup, composer.
    func testComposedNotificationIsLocalized() throws {
        let node = try nikaia
        var departures: [Departure] = []
        for lineId in node.lineIds {
            departures += ScheduleProjector.nextDepartures(for: node.stationIdByLineId[lineId] ?? "M3_NIK",
                                                           lineIds: [lineId], limit: 400)
        }
        try XCTSkipIf(departures.isEmpty, "no service at this hour in the offline schedule")
        for (lang, e) in expected {
            let dirs = NearbyTrainComposer.directions(from: departures, overnight: false) {
                NearbyPlaceNames.destination(lineId: $0.lineId, direction: $0.direction, lang)
            }
            let out = try XCTUnwrap(NearbyTrainComposer.compose(
                stationName: NearbyPlaceNames.stationName(node, lang), stationKey: "M3_NIK", walkMinutes: 3,
                directions: dirs, stationClosed: false, athensDate: "2026-10-27", language: lang))
            XCTAssertTrue(out.title.hasPrefix(e.station + " · "), "\(lang): \(out.title)")
            for d in dirs {
                XCTAssertTrue([e.airport, e.doukissis, e.dimotiko].contains(d.destination), "\(lang): \(d.destination)")
            }
            if lang != .greek {
                XCTAssertNil(out.body.range(of: "\\p{Greek}", options: .regularExpression), "\(lang): \(out.body)")
            }
        }
    }
}

/// Calendar re-plan cleanup: only real calendar changes cancel alarms.
final class CalendarPlanRetentionTests: XCTestCase {
    func testUnresolvedEventKeepsItsAlarms() {
        let planned: Set<String> = ["flight@1", "dinner@2", "gone@3", "declined@4"]
        // flight replanned; dinner still in the calendar but the geocoder was
        // offline; gone was deleted; declined is no longer eligible.
        let cancel = CalendarPlanRetention.keysToCancel(planned: planned, replanned: ["flight@1"], retained: ["dinner@2"])
        XCTAssertEqual(cancel, ["gone@3", "declined@4"])
    }

    func testNothingPlannedNothingCancelled() {
        XCTAssertEqual(CalendarPlanRetention.keysToCancel(planned: [], replanned: ["a"], retained: ["b"]), [])
    }
}

/// Station board destinations at Syntagma (M2 and M3) in all four languages,
/// through the same lookup the board rows use.
@MainActor
final class StationBoardDestinationNamesTests: XCTestCase {
    private let cases: [(line: String, direction: String, names: [AppLanguage: String])] = [
        ("M3", "Airport", [.greek: "Αεροδρόμιο", .english: "Airport", .albanian: "Aeroporti", .italian: "Aeroporto"]),
        ("M3", "Doukissis Plakentias", [.greek: "Δουκίσσης Πλακεντίας", .english: "Douk. Plakentias",
                                        .albanian: "Dukisës Plakendias", .italian: "Douk. Plakentias"]),
        ("M3", "Dimotiko Theatro", [.greek: "Δημοτικό Θέατρο", .english: "Dimotiko Theatro",
                                    .albanian: "Dimotiko Theatro", .italian: "Dimotiko Theatro"]),
        ("M2", "Anthoupoli", [.greek: "Ανθούπολη", .english: "Anthoupoli", .albanian: "Anthoupoli", .italian: "Anthoupoli"]),
        ("M2", "Elliniko", [.greek: "Ελληνικό", .english: "Elliniko", .albanian: "Elliniko", .italian: "Elliniko"]),
    ]

    func testSyntagmaDestinationsInAllLanguages() {
        for c in cases {
            for (lang, want) in c.names {
                XCTAssertEqual(NearbyPlaceNames.destination(lineId: c.line, direction: c.direction, lang), want,
                               "\(c.line) \(c.direction) \(lang)")
            }
        }
    }

    /// Whatever direction strings the offline schedule projects at Syntagma
    /// today resolve to a localized name: Greek output has no Latin letters.
    func testProjectedSyntagmaDirectionsResolveInGreek() throws {
        var deps: [Departure] = []
        for (sid, line) in [("M2_SYN", "M2"), ("M3_SYN", "M3")] {
            deps += ScheduleProjector.nextDepartures(for: sid, lineIds: [line], limit: 400,
                                                     timeHorizonMinutes: 12 * 60)
        }
        try XCTSkipIf(deps.isEmpty, "offline schedule has no departures")
        for d in Set(deps.map { "\($0.lineId)|\($0.direction)" }) {
            let parts = d.split(separator: "|", maxSplits: 1).map(String.init)
            let el = NearbyPlaceNames.destination(lineId: parts[0], direction: parts[1], .greek)
            XCTAssertNil(el.range(of: "[A-Za-z]", options: .regularExpression), "\(d) -> \(el)")
        }
    }
}
