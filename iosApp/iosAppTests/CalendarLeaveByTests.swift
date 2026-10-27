import XCTest
@testable import Syrmos

/// Mirrors fixtures/calendar/leave-by.json case-for-case, so the iOS
/// CalendarLeaveBy and Kotlin CalendarLeaveBy agree (3.0.2).
final class CalendarLeaveByTests: XCTestCase {

    private struct KeywordSet: Decodable { let substring: [String]; let token: [String] }
    private struct FoldCase: Decodable { let input: String; let expected: String }
    private struct ClassifyCase: Decodable { let title: String; let location: String; let expected: String }
    private struct Event: Decodable {
        let title: String
        let location: String?
        let startEpochSeconds: Int64
        let journeyMinutes: Int
        let transfers: Int
        let firstLegHeadwayMinutes: Int
        let raining: Bool?
        let alertOnRoute: Bool?
        let flightBufferMinutes: Int?
    }
    private struct Fire: Decodable { let kind: String; let atEpochSeconds: Int64 }
    private struct PlanExpected: Decodable {
        let kind: String
        let bufferMinutes: Int
        let marginMinutes: Int
        let arriveByEpochSeconds: Int64
        let leaveAtEpochSeconds: Int64
        let state: String
        let notifications: [Fire]
    }
    private struct PlanCase: Decodable { let id: String; let event: Event; let now: Int64; let expected: PlanExpected }
    private struct EligEvent: Decodable {
        let startEpochSeconds: Int64
        let allDay: Bool?
        let declined: Bool?
        let destinationResolved: Bool?
    }
    private struct EligCase: Decodable { let id: String; let event: EligEvent; let now: Int64; let expected: String }
    private struct ReschedCase: Decodable { let old: Int64; let new: Int64; let expected: Bool }
    private struct File: Decodable {
        let keywords: [String: KeywordSet]
        let foldCases: [FoldCase]
        let classifyCases: [ClassifyCase]
        let planCases: [PlanCase]
        let eligibilityCases: [EligCase]
        let rescheduleCases: [ReschedCase]
    }

    private func load() throws -> File { try Fixture302.decode(File.self, "fixtures/calendar/leave-by.json") }

    func testKeywordListsMatchFixture() throws {
        let f = try load()
        XCTAssertEqual(f.keywords.count, CalendarLeaveBy.classes.count)
        for (kind, subs, toks) in CalendarLeaveBy.classes {
            let set = try XCTUnwrap(f.keywords[kind.rawValue], kind.rawValue)
            XCTAssertEqual(subs, set.substring, kind.rawValue)
            XCTAssertEqual(toks, set.token, kind.rawValue)
        }
    }

    func testFold() throws {
        for c in try load().foldCases {
            XCTAssertEqual(CalendarLeaveBy.fold(c.input), c.expected, c.input)
        }
    }

    func testClassify() throws {
        let f = try load()
        XCTAssertEqual(f.classifyCases.count, 20)
        for c in f.classifyCases {
            XCTAssertEqual(CalendarLeaveBy.classify(title: c.title, location: c.location).rawValue, c.expected,
                           "\(c.title) / \(c.location)")
        }
    }

    func testAthensIsNotTheAirport() {
        XCTAssertEqual(CalendarLeaveBy.classify(title: "Meeting in Athens", location: "Syntagma"), .default)
        XCTAssertEqual(CalendarLeaveBy.classify(title: "Trip", location: "Athens International Airport"), .flight)
    }

    func testPlan() throws {
        let f = try load()
        XCTAssertEqual(f.planCases.count, 9)
        for c in f.planCases {
            let e = c.event
            let input = CalendarTripInput(
                title: e.title, location: e.location ?? "", startEpochSeconds: e.startEpochSeconds,
                journeyMinutes: e.journeyMinutes, transfers: e.transfers,
                firstLegHeadwayMinutes: e.firstLegHeadwayMinutes, raining: e.raining ?? false,
                alertOnRoute: e.alertOnRoute ?? false, flightBufferMinutes: e.flightBufferMinutes
            )
            let got = CalendarLeaveBy.plan(input, now: c.now)
            let x = c.expected
            let want = CalendarTripPlan(
                kind: try XCTUnwrap(CalendarEventKind(rawValue: x.kind)),
                bufferMinutes: x.bufferMinutes, marginMinutes: x.marginMinutes,
                arriveByEpochSeconds: x.arriveByEpochSeconds, leaveAtEpochSeconds: x.leaveAtEpochSeconds,
                state: try XCTUnwrap(CalendarTripState(rawValue: x.state)),
                notifications: try x.notifications.map {
                    CalendarFire(kind: try XCTUnwrap(CalendarNotificationKind(rawValue: $0.kind)), atEpochSeconds: $0.atEpochSeconds)
                }
            )
            XCTAssertEqual(got, want, c.id)
        }
    }

    func testEligibility() throws {
        let f = try load()
        XCTAssertEqual(f.eligibilityCases.count, 7)
        for c in f.eligibilityCases {
            let e = c.event
            let input = CalendarEligibilityInput(
                startEpochSeconds: e.startEpochSeconds, allDay: e.allDay ?? false,
                declined: e.declined ?? false, destinationResolved: e.destinationResolved ?? true
            )
            XCTAssertEqual(CalendarLeaveBy.eligibility(input, now: c.now), c.expected, c.id)
        }
    }

    func testReschedule() throws {
        for c in try load().rescheduleCases {
            XCTAssertEqual(CalendarLeaveBy.needsReschedule(oldLeaveAt: c.old, newLeaveAt: c.new), c.expected, "\(c.old)->\(c.new)")
        }
    }
}
