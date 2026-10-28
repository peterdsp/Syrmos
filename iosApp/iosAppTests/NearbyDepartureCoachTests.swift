import XCTest
@testable import Syrmos

/// Mirrors fixtures/nearby/coach.json case-for-case, so the iOS
/// NearbyDepartureCoach and Kotlin NearbyDepartureCoach agree (3.0.2).
final class NearbyDepartureCoachTests: XCTestCase {

    private struct WalkCase: Decodable { let distanceMeters: Double; let expected: Int }
    private struct Dep: Decodable { let minutes: Int; let isLast: Bool }
    private struct CoachExpected: Decodable {
        let `case`: String
        let nextMinutes: Int?
        let followingMinutes: Int?
        let waitMinutes: Int?
        let marginMinutes: Int?
        let tight: Bool?
    }
    private struct CoachCaseRow: Decodable {
        let id: String
        let walkMinutes: Int
        let departures: [Dep]
        let stationClosed: Bool
        let expected: CoachExpected
    }
    private struct HeadlineCase: Decodable { let cases: [String]; let expected: Int? }
    private struct VariantCase: Decodable {
        let stationId: String
        let athensDate: String
        let variantCount: Int
        let expectedHash: UInt32
        let expected: Int
    }
    private struct GuardIn: Decodable {
        let enabled: Bool
        let goJourneyActive: Bool
        let athensHour: Int
        let speedKmh: Double?
        let lastNotifiedForStationEpochSeconds: Int64?
        let notificationsToday: Int
        let now: Int64
    }
    private struct GuardCase: Decodable { let id: String; let input: GuardIn; let expected: String }
    private struct File: Decodable {
        let walkCases: [WalkCase]
        let coachCases: [CoachCaseRow]
        let headlineCases: [HeadlineCase]
        let variantCases: [VariantCase]
        let guardCases: [GuardCase]
    }

    private func load() throws -> File { try Fixture302.decode(File.self, "fixtures/nearby/coach.json") }

    func testWalkMinutes() throws {
        let f = try load()
        XCTAssertFalse(f.walkCases.isEmpty)
        for c in f.walkCases {
            XCTAssertEqual(NearbyDepartureCoach.walkMinutes(distanceMeters: c.distanceMeters), c.expected, "\(c.distanceMeters) m")
        }
    }

    func testCoachCases() throws {
        let f = try load()
        XCTAssertEqual(f.coachCases.count, 14)
        for c in f.coachCases {
            let got = NearbyDepartureCoach.coachCase(
                walk: c.walkMinutes,
                departures: c.departures.map { CoachDeparture(minutes: $0.minutes, isLast: $0.isLast) },
                stationClosed: c.stationClosed
            )
            let e = c.expected
            let want = CoachResult(
                coachCase: try XCTUnwrap(CoachCase(rawValue: e.case), c.id),
                nextMinutes: e.nextMinutes, followingMinutes: e.followingMinutes,
                waitMinutes: e.waitMinutes, marginMinutes: e.marginMinutes, tight: e.tight
            )
            XCTAssertEqual(got, want, c.id)
        }
    }

    func testHeadline() throws {
        for c in try load().headlineCases {
            let cases = try c.cases.map { try XCTUnwrap(CoachCase(rawValue: $0)) }
            XCTAssertEqual(NearbyDepartureCoach.headlineIndex(cases), c.expected, "\(c.cases)")
        }
    }

    func testVariant() throws {
        for c in try load().variantCases {
            XCTAssertEqual(NearbyDepartureCoach.fnv1a32("\(c.stationId)|\(c.athensDate)"), c.expectedHash, c.stationId)
            XCTAssertEqual(
                NearbyDepartureCoach.variantIndex(stationId: c.stationId, athensDate: c.athensDate, variantCount: c.variantCount),
                c.expected, "\(c.stationId) \(c.athensDate)"
            )
        }
    }

    func testGuards() throws {
        let f = try load()
        XCTAssertEqual(f.guardCases.count, 12)
        for c in f.guardCases {
            let g = c.input
            let input = CoachGuardInput(
                enabled: g.enabled, goJourneyActive: g.goJourneyActive, athensHour: g.athensHour,
                speedKmh: g.speedKmh, lastNotifiedForStationEpochSeconds: g.lastNotifiedForStationEpochSeconds,
                notificationsToday: g.notificationsToday, now: g.now
            )
            XCTAssertEqual(NearbyDepartureCoach.guardReason(input), c.expected, c.id)
        }
    }

    /// Copy guard: no coach line, in any language or variant, tells the rider to run.
    func testCoachCopyNeverSaysRun() {
        let banned = ["run", "τρέξ", "τρεξ", "vrapo", "vrapo", "corri", "corre"]
        for lang in AppLanguage.allCases {
            for c in CoachCase.allCases {
                for v in 0..<NearbyCopy.variantCount(c) {
                    let text = NearbyCopy.coachLine(c, variant: v, language: lang,
                                                    params: .init(next: 2, following: 10, margin: 3, wait: 5, line: "M3", destination: "X"))
                        .lowercased()
                    XCTAssertFalse(text.isEmpty, "\(c) \(v) \(lang)")
                    XCTAssertFalse(text.contains("{"), "unfilled placeholder: \(text)")
                    for word in banned {
                        XCTAssertFalse(text.range(of: "\\b\(word)", options: .regularExpression) != nil, "\(lang) \(c) v\(v): \(text)")
                    }
                }
            }
        }
    }

    func testVariantCounts() {
        XCTAssertEqual(NearbyCopy.variantCount(.relaxNextOneIsClose), 4)
        for c in CoachCase.allCases where c != .relaxNextOneIsClose {
            XCTAssertEqual(NearbyCopy.variantCount(c), 3, "\(c)")
        }
    }
}
