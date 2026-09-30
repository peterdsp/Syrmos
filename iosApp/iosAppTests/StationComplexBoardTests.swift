import XCTest
@testable import Syrmos

/// Drives the SAME golden fixture the web `station-board.test.js` and the Kotlin
/// `StationComplexBoardFixtureTest` drive, so the three clients cannot drift
/// apart on grouping, ordering, deduplication or coverage.
///
/// The fixture is read from the repository rather than mirrored by hand: a case
/// added on one platform then fails here until the Swift port matches it.
final class StationComplexBoardFixtureTests: XCTestCase {

    private struct Fixture: Decodable {
        let name: String
        let complex: ComplexJSON
        let cases: [Case]

        struct ComplexJSON: Decodable {
            let id: String
            let name: String
            let nameEl: String
            let nameSq: String
            let nameIt: String
            let areas: [AreaJSON]
        }
        struct AreaJSON: Decodable {
            let id: String
            let name: String
            let nameEl: String
            let nameSq: String
            let nameIt: String
            let stopIds: [String]
        }
        struct Case: Decodable {
            let id: String
            let note: String
            let input: Input
            let expect: Expect
        }
        struct Input: Decodable {
            let windowMinutes: Int
            let maxTimesPerGroup: Int
            let departures: [DepartureJSON]
            let coverage: [CoverageJSON]
        }
        struct DepartureJSON: Decodable {
            let stopId: String
            let areaId: String
            let lineId: String
            let destination: String
            let patternKey: String?
            let tripId: String?
            let serviceDate: String?
            let time: String?
            let absoluteMinutes: Int
            let minutesAway: Int?
            let scheduledMinutes: Int?
            let source: String?
            let sourceLabel: String?
            let cancelled: Bool?
            let status: String?
            let observedAt: Double?
            let trainNo: String?
            let boardsHere: Bool?
        }
        struct CoverageJSON: Decodable {
            let areaId: String
            let stopId: String
            let lineId: String
            let destination: String?
            let state: String
            let reason: String?
        }
        struct Expect: Decodable {
            let partial: Bool
            let timedGroupCount: Int
            let groups: [GroupJSON]
        }
        struct GroupJSON: Decodable {
            let id: String
            let destination: String
            let lineId: String?
            let stopId: String?
            let nextMinutes: Int?
            let times: [Int]
            let total: Int?
            let moreCount: Int?
            let coverage: String?
            let source: String?
            let cancelledTimes: [Bool]?
            let timeSources: [String]?
            let beyondWindow: [Bool]?
        }
    }

    private static func repoRoot(_ file: StaticString = #filePath) -> URL {
        URL(fileURLWithPath: "\(file)")
            .deletingLastPathComponent()   // iosAppTests
            .deletingLastPathComponent()   // iosApp
            .deletingLastPathComponent()   // repo root
    }

    private func loadFixture() throws -> Fixture {
        let url = Self.repoRoot()
            .appendingPathComponent("fixtures/station-board/athens-all-directions.json")
        let data = try Data(contentsOf: url)
        return try JSONDecoder().decode(Fixture.self, from: data)
    }

    private func source(_ raw: String?) -> SourceConfidence {
        switch (raw ?? "scheduled").lowercased() {
        case "live": return .live
        case "estimated": return .estimated
        case "offline": return .offline
        case "operator": return .operatorLink
        case "unknown": return .unknown
        default: return .scheduled
        }
    }

    private func coverage(_ raw: String) -> StationComplexBoard.Coverage {
        StationComplexBoard.Coverage(rawValue: raw) ?? .loaded
    }

    func testFixtureCases() throws {
        let fixture = try loadFixture()
        let complex = StationComplexBoard.Complex(
            id: fixture.complex.id,
            name: fixture.complex.name,
            nameEl: fixture.complex.nameEl,
            nameSq: fixture.complex.nameSq,
            nameIt: fixture.complex.nameIt,
            areas: fixture.complex.areas.map {
                .init(id: $0.id, name: $0.name, nameEl: $0.nameEl, nameSq: $0.nameSq,
                      nameIt: $0.nameIt, stopIds: $0.stopIds)
            }
        )
        XCTAssertFalse(fixture.cases.isEmpty, "the fixture must carry cases")

        for testCase in fixture.cases {
            let candidates = testCase.input.departures.map { d in
                StationComplexBoard.Candidate(
                    stopId: d.stopId,
                    areaId: d.areaId,
                    lineId: d.lineId,
                    destination: d.destination,
                    patternKey: d.patternKey ?? "",
                    tripId: d.tripId,
                    serviceDate: d.serviceDate,
                    time: d.time ?? "",
                    absoluteMinutes: d.absoluteMinutes,
                    scheduledMinutes: d.scheduledMinutes,
                    source: source(d.source),
                    cancelled: d.cancelled ?? false,
                    status: d.status,
                    observedAt: d.observedAt.map { Date(timeIntervalSince1970: $0) },
                    trainNo: d.trainNo,
                    boardsHere: d.boardsHere ?? true
                )
            }
            let coverageEntries = testCase.input.coverage.map { c in
                StationComplexBoard.CoverageEntry(
                    areaId: c.areaId, stopId: c.stopId, operatorId: "", lineId: c.lineId,
                    patternKey: "", destination: c.destination ?? "",
                    state: coverage(c.state), reason: c.reason
                )
            }
            let board = StationComplexBoard.build(
                complex: complex,
                candidates: candidates,
                coverage: coverageEntries,
                windowMinutes: testCase.input.windowMinutes,
                maxTimesPerGroup: testCase.input.maxTimesPerGroup
            )

            XCTAssertEqual(board.partial, testCase.expect.partial, "\(testCase.id): partial")
            XCTAssertEqual(board.timedGroupCount, testCase.expect.timedGroupCount,
                           "\(testCase.id): timed group count")
            XCTAssertEqual(board.groups.count, testCase.expect.groups.count,
                           "\(testCase.id): group count")
            guard board.groups.count == testCase.expect.groups.count else { continue }

            for (index, want) in testCase.expect.groups.enumerated() {
                let got = board.groups[index]
                let where_ = "\(testCase.id) group \(index) (\(want.destination))"
                XCTAssertEqual(got.id, want.id, "\(where_): stable group id")
                XCTAssertEqual(got.destination, want.destination, "\(where_): destination")
                if let lineId = want.lineId { XCTAssertEqual(got.lineId, lineId, "\(where_): lineId") }
                if let stopId = want.stopId { XCTAssertEqual(got.stopId, stopId, "\(where_): stop") }
                XCTAssertEqual(got.times.map(\.absoluteMinutes), want.times, "\(where_): times")
                if let next = want.nextMinutes {
                    XCTAssertEqual(got.next?.absoluteMinutes, next, "\(where_): next")
                } else {
                    XCTAssertNil(got.next, "\(where_): expected no next departure")
                }
                if let total = want.total { XCTAssertEqual(got.total, total, "\(where_): total") }
                if let more = want.moreCount { XCTAssertEqual(got.moreCount, more, "\(where_): more") }
                if let cov = want.coverage {
                    XCTAssertEqual(got.coverage.rawValue, cov, "\(where_): coverage")
                }
                if let src = want.source {
                    XCTAssertEqual(got.source, source(src), "\(where_): source")
                }
                if let cancelled = want.cancelledTimes {
                    XCTAssertEqual(got.times.map(\.cancelled), cancelled, "\(where_): cancellations")
                }
                if let sources = want.timeSources {
                    XCTAssertEqual(got.times.map(\.source), sources.map(source), "\(where_): per-time source")
                }
                if let beyond = want.beyondWindow {
                    XCTAssertEqual(got.times.map(\.beyondWindow), beyond, "\(where_): beyond window")
                }
            }
        }
    }

    // MARK: - Deduplication

    func testOneTripCollapsesAndTheStrongerSourceWins() {
        let out = StationComplexBoard.dedupe([
            .init(stopId: "A1_ATH", areaId: "rail", lineId: "A1", destination: "Airport",
                  tripId: "T1", serviceDate: "D", absoluteMinutes: 5, source: .scheduled),
            .init(stopId: "A1_ATH", areaId: "rail", lineId: "A1", destination: "Airport",
                  tripId: "T1", serviceDate: "D", absoluteMinutes: 7, source: .live),
            .init(stopId: "A1_ATH", areaId: "rail", lineId: "A1", destination: "Airport",
                  tripId: "T2", serviceDate: "D", absoluteMinutes: 7, source: .scheduled),
        ])
        XCTAssertEqual(out.count, 2)
        XCTAssertEqual(out[0].tripId, "T1")
        XCTAssertEqual(out[0].source, .live)
        XCTAssertEqual(out[0].absoluteMinutes, 7)
        XCTAssertEqual(out[1].tripId, "T2")
    }

    func testWithoutTripIdsTheExactMinuteKeepsTwoTrainsApart() {
        let out = StationComplexBoard.dedupe([
            .init(stopId: "M2_STA", areaId: "metro", lineId: "M2", destination: "Elliniko",
                  absoluteMinutes: 4, source: .estimated),
            .init(stopId: "M2_STA", areaId: "metro", lineId: "M2", destination: "Elliniko",
                  absoluteMinutes: 5, source: .estimated),
            .init(stopId: "M2_STA", areaId: "metro", lineId: "M2", destination: "Elliniko",
                  absoluteMinutes: 5, source: .offline),
        ])
        XCTAssertEqual(out.map(\.absoluteMinutes), [4, 5])
        XCTAssertEqual(out[1].source, .estimated)
    }

    func testDestinationAloneNeverMergesTwoDepartures() {
        let out = StationComplexBoard.dedupe([
            .init(stopId: "A1_ATH", areaId: "rail", lineId: "A1", destination: "Airport",
                  absoluteMinutes: 5, source: .scheduled),
            .init(stopId: "A2_ATH", areaId: "rail", lineId: "A2", destination: "Airport",
                  absoluteMinutes: 5, source: .scheduled),
        ])
        XCTAssertEqual(out.count, 2)
    }

    // MARK: - Identity

    func testGroupIdsSurviveAReorder() {
        let complex = StationComplexBoard.Complex(
            id: "C", name: "C", nameEl: "C", nameSq: "C", nameIt: "C",
            areas: [.init(id: "metro", name: "Metro", nameEl: "Μετρό", nameSq: "Metro",
                          nameIt: "Metropolitana", stopIds: ["M2_STA"])])
        func board(_ elliniko: Int, _ anthoupoli: Int) -> StationComplexBoard.Board {
            StationComplexBoard.build(complex: complex, candidates: [
                .init(stopId: "M2_STA", areaId: "metro", lineId: "M2", destination: "Elliniko",
                      absoluteMinutes: elliniko, source: .estimated),
                .init(stopId: "M2_STA", areaId: "metro", lineId: "M2", destination: "Anthoupoli",
                      absoluteMinutes: anthoupoli, source: .estimated),
            ])
        }
        let before = board(1, 4)
        let after = board(9, 2)
        XCTAssertEqual(before.groups.map(\.destination), ["Elliniko", "Anthoupoli"])
        XCTAssertEqual(after.groups.map(\.destination), ["Anthoupoli", "Elliniko"])
        func id(_ b: StationComplexBoard.Board, _ dest: String) -> String {
            b.groups.first { $0.destination == dest }?.id ?? ""
        }
        XCTAssertEqual(id(before, "Elliniko"), id(after, "Elliniko"))
        XCTAssertEqual(id(before, "Anthoupoli"), id(after, "Anthoupoli"))
    }

    func testFoldingMatchesAccentsButNotDifferentSpellings() {
        XCTAssertEqual(StationComplexBoard.fold("Ελληνικό"), StationComplexBoard.fold("Ελληνικο"))
        XCTAssertEqual(StationComplexBoard.fold("  Airport "), "airport")
        XCTAssertNotEqual(StationComplexBoard.fold("Kifissia"), StationComplexBoard.fold("Kifisias"))
    }
}

/// The reviewed registry and the trip reader, against the bundled seed.
final class StationComplexRegistryTests: XCTestCase {

    func testAthensJoinsTheMetroStopAndEveryRailwayPlatform() throws {
        let athens = try XCTUnwrap(StationComplexRegistry.complex(forStopId: "M2_STA"),
                                   "the registry must be bundled with the app")
        XCTAssertEqual(athens.id, "CPX_ATHENS_LARISSA")
        XCTAssertEqual(athens.memberStopIds, ["M2_STA", "A1_ATH", "A3_ATH", "A4_ATH", "GR_ATH"])
        for id in ["A1_ATH", "A3_ATH", "A4_ATH", "GR_ATH"] {
            XCTAssertEqual(StationComplexRegistry.complex(forStopId: id)?.id, "CPX_ATHENS_LARISSA", id)
        }
    }

    func testBoardingMembershipIsRouteMembershipNotAnInterchangeUnion() {
        // The metro stop never carries a railway service: the seed's
        // `stations.json:line_ids` union used to route A1 lookups to M2_STA.
        XCTAssertEqual(SyrmosLineStops.boardingLines["M2_STA"], ["M2"])
        XCTAssertEqual(SyrmosLineStops.boardingLines["A1_ATH"], ["A1"])
        XCTAssertEqual(SyrmosLineStops.boardingLines["GR_ATH"], ["IC1", "RG1"])
    }

    func testReviewedSplitsKeepDistinctNeighboursApart() {
        // Faliro, SEF and Gipedo Karaiskaki sit within 180 m but are three
        // stations; SKA Acharnon stays separate from Kato Acharnai.
        for id in ["M1_FAL", "T7_PEA", "T7_GIP", "GR_SKA"] {
            XCTAssertNil(StationComplexRegistry.complex(forStopId: id), id)
        }
        XCTAssertEqual(StationComplexRegistry.complex(forStopId: "A1_KAT")?.memberStopIds,
                       ["A1_KAT", "A4_KAT"])
    }

    func testATerminalOffersOneDirectionAnIntermediateStopTwo() {
        XCTAssertEqual(
            SyrmosLineStops.directions(lineId: "M2", stopId: "M2_STA").map(\.destination).sorted(),
            ["Anthoupoli", "Elliniko"])
        XCTAssertEqual(
            SyrmosLineStops.directions(lineId: "M2", stopId: "M2_ANT").map(\.destination),
            ["Elliniko"])
        XCTAssertTrue(SyrmosLineStops.directions(lineId: "M2", stopId: "M1_PIR").isEmpty)
    }

    func testLocalizedComplexNamesCoverTheFourSupportedLanguages() throws {
        let athens = try XCTUnwrap(StationComplexRegistry.complex(forStopId: "M2_STA"))
        XCTAssertEqual(athens.localizedName(.english), "Athens · Larissa Station")
        XCTAssertEqual(athens.localizedName(.greek), "Αθήνα · Σταθμός Λαρίσης")
        XCTAssertEqual(athens.localizedName(.albanian), "Athinë · Stacioni Larisa")
        XCTAssertEqual(athens.localizedName(.italian), "Atene · Stazione Larissa")
        XCTAssertEqual(athens.localizedAreaName("rail", .greek), "Σιδηροδρομικός σταθμός")
        XCTAssertEqual(athens.localizedAreaName("metro", .italian), "Metropolitana")
    }
}
