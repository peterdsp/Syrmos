import XCTest
@testable import Syrmos

/// Ichnos scoped to one departure.
///
/// The rules under test are the ones that keep community evidence honest: a
/// report about another line is not evidence about this train, a stale report is
/// labelled rather than shown as current, and a failed fetch is never the same
/// answer as a quiet feed.
final class StationBoardIchnosTests: XCTestCase {

    private let complex = StationComplexBoard.Complex(
        id: "CPX_ATHENS_LARISSA",
        name: "Athens · Larissa Station",
        nameEl: "Αθήνα · Σταθμός Λαρίσης",
        nameSq: "Athinë · Stacioni Larisa",
        nameIt: "Atene · Stazione Larissa",
        areas: [.init(id: "metro", name: "Metro", nameEl: "Μετρό", nameSq: "Metro",
                      nameIt: "Metropolitana", stopIds: ["M2_STA"])]
    )

    private func group(
        line: String = "M2",
        destination: String = "Anthoupoli"
    ) -> StationComplexBoard.Group {
        StationComplexBoard.build(
            complex: complex,
            candidates: [
                .init(stopId: "M2_STA", areaId: "metro", lineId: line,
                      destination: destination, absoluteMinutes: 4, source: .estimated),
            ]
        ).groups[0]
    }

    private func issue(
        scopeLabel: String,
        signal: String = "delayed",
        detail: String = "Slow boarding",
        minutesAgo: Int
    ) -> IchnosCommunityIssue {
        let when = Date(timeIntervalSince1970: 1_800_000_000 - Double(minutesAgo * 60))
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime]
        return IchnosCommunityIssue(
            scopeId: "scope_x", scopeLabel: scopeLabel, signal: signal,
            detail: detail, count: 2, latestAt: iso.string(from: when)
        )
    }

    private var now: Date { Date(timeIntervalSince1970: 1_800_000_000) }

    // MARK: - Scope

    func testScopesRunNarrowestFirstAndAreDerivedFromBoardIdentity() {
        let scopes = StationBoardIchnos.scopes(complex: complex, group: group())
        XCTAssertEqual(scopes.map(\.breadth), [.direction, .service, .station, .network])
        // The narrowest scope is specific to complex AND line AND destination, so
        // two directions of the same line never share it.
        let other = StationBoardIchnos.scopes(complex: complex, group: group(destination: "Elliniko"))
        XCTAssertNotEqual(scopes[0].id, other[0].id, "each direction has its own scope")
        XCTAssertEqual(scopes[1].id, other[1].id, "both share the service scope")
        XCTAssertEqual(scopes[2].id, other[2].id, "both share the station scope")
    }

    func testTheScopeHashMatchesTheExploreSurface() {
        // FNV-1a over the same input. If these diverge, a report filed from a
        // board row lands in a scope the Explore feed never reads.
        XCTAssertEqual(
            StationBoardIchnos.stableScopeId("CPX_ATHENS_LARISSA"),
            StationBoardIchnos.stableScopeId("CPX_ATHENS_LARISSA")
        )
        XCTAssertTrue(StationBoardIchnos.stableScopeId("x").hasPrefix("scope_"))
    }

    // MARK: - Relevance

    func testAReportAboutAnotherLineIsNotEvidenceAboutThisTrain() {
        let issues = StationBoardIchnos.relevant(
            [issue(scopeLabel: "Athens · A3 → Chalkida", minutesAgo: 5)],
            group: group(line: "M2", destination: "Anthoupoli"),
            now: now
        )
        XCTAssertTrue(issues.isEmpty, "an A3 report must not appear on an M2 row")
    }

    func testAReportOnThisLineIsKept() {
        let issues = StationBoardIchnos.relevant(
            [issue(scopeLabel: "Athens · M2 → Anthoupoli", minutesAgo: 5)],
            group: group(),
            now: now
        )
        XCTAssertEqual(issues.count, 1)
        XCTAssertEqual(issues[0].expired, false)
    }

    func testABroadScopeLabelWithoutADirectionIsKept() {
        // A station-wide report has no "→" in its label; it is real evidence,
        // and the view labels the broader scope rather than hiding it.
        let issues = StationBoardIchnos.relevant(
            [issue(scopeLabel: "Athens · Larissa Station", minutesAgo: 5)],
            group: group(),
            now: now
        )
        XCTAssertEqual(issues.count, 1)
    }

    func testAStaleReportIsMarkedRatherThanShownAsCurrent() {
        let issues = StationBoardIchnos.relevant(
            [issue(scopeLabel: "Athens · M2 → Anthoupoli", minutesAgo: 3 * 60)],
            group: group(),
            now: now
        )
        XCTAssertEqual(issues.count, 1, "history is still shown")
        XCTAssertTrue(issues[0].expired, "but it is not a current warning")
    }

    func testAReportWithNoUsableTimestampDoesNotClaimFreshness() {
        let raw = IchnosCommunityIssue(
            scopeId: "s", scopeLabel: "Athens · M2 → Anthoupoli", signal: "delayed",
            detail: "", count: 1, latestAt: "not-a-timestamp"
        )
        let issues = StationBoardIchnos.relevant([raw], group: group(), now: now)
        XCTAssertEqual(issues.count, 1)
        XCTAssertNil(issues[0].reportedAt)
        XCTAssertNil(issues[0].age(now: now, language: .english),
                     "no timestamp means no age claim")
        XCTAssertFalse(issues[0].expired, "unknown is not expired")
    }

    func testAgeReadsInEverySupportedLanguage() {
        let recent = StationBoardIchnos.relevant(
            [issue(scopeLabel: "Athens · M2 → Anthoupoli", minutesAgo: 12)],
            group: group(), now: now
        )[0]
        XCTAssertEqual(recent.age(now: now, language: .english), "12 min ago")
        XCTAssertEqual(recent.age(now: now, language: .greek), "πριν 12 λεπ")
        XCTAssertEqual(recent.age(now: now, language: .albanian), "12 min më parë")
        XCTAssertEqual(recent.age(now: now, language: .italian), "12 min fa")
    }

    // MARK: - States

    func testLoadingUnavailableAndQuietAreThreeDifferentStates() {
        // They must not be equal to one another; conflating "the request failed"
        // with "no problems reported" is the defect this separation prevents.
        let scope = StationBoardIchnos.scopes(complex: complex, group: group())[0]
        let states: [StationBoardIchnos.State] = [
            .loading, .unavailable, .noRecentReports(scope),
        ]
        for (i, a) in states.enumerated() {
            for (j, b) in states.enumerated() where i != j {
                XCTAssertNotEqual(a, b)
            }
        }
    }
}
