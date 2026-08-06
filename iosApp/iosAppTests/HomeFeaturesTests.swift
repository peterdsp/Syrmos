import XCTest
@testable import Syrmos

/// Tests for the three answer-first home features. Mirrors the Kotlin
/// `FreshnessEvaluatorTest` and `GetLastTrainSelectionTest` so the iOS and KMP
/// implementations drift together or not at all.
///
/// Two rules are pinned:
///
///  1. The offline-alive pill reads LIVE only when a fetch landed inside the
///     freshness window; never-fetched and aged-past-window both read
///     PREDICTED, the honest default for the offline-first model.
///
///  2. The last-train teaser surfaces the single latest slot still running
///     tonight and ignores any look-ahead row beyond the horizon (tomorrow's
///     first airport train must not masquerade as tonight's last train).
final class HomeFeaturesTests: XCTestCase {

    // MARK: - Offline-alive freshness rule

    private let now = Date(timeIntervalSince1970: 1_700_000_000)

    func test_nilLastUpdate_isPredicted() {
        XCTAssertEqual(
            DataFreshness.evaluate(lastLiveUpdate: nil, now: now, windowSeconds: 90),
            .predicted
        )
    }

    func test_freshFetchWithinWindow_isLive() {
        let last = now.addingTimeInterval(-30)
        XCTAssertEqual(
            DataFreshness.evaluate(lastLiveUpdate: last, now: now, windowSeconds: 90),
            .live
        )
    }

    func test_onWindowBoundary_isLive() {
        let last = now.addingTimeInterval(-90)
        XCTAssertEqual(
            DataFreshness.evaluate(lastLiveUpdate: last, now: now, windowSeconds: 90),
            .live
        )
    }

    func test_staleFetchPastWindow_isPredicted() {
        let last = now.addingTimeInterval(-91)
        XCTAssertEqual(
            DataFreshness.evaluate(lastLiveUpdate: last, now: now, windowSeconds: 90),
            .predicted
        )
    }

    func test_futureTimestampClockSkew_isPredicted() {
        let last = now.addingTimeInterval(10)
        XCTAssertEqual(
            DataFreshness.evaluate(lastLiveUpdate: last, now: now, windowSeconds: 90),
            .predicted
        )
    }

    // MARK: - Last-train selection rule
    //
    // Replica of ScheduleProjector.lastTrainTonight's selection step (filter to
    // the tonight window, take the latest), so the rule is pinned without
    // needing the schedule bundles loaded in a unit test.

    private func selectLastTrain(_ deps: [Departure], maxLookaheadMinutes: Int) -> Departure? {
        deps
            .filter { $0.minutesAway >= 0 && $0.minutesAway <= maxLookaheadMinutes }
            .max { $0.minutesAway < $1.minutesAway }
    }

    private func dep(_ minutesAway: Int, _ time: String, line: String = "M2") -> Departure {
        Departure(time: time, lineId: line, direction: "Elliniko", minutesAway: minutesAway, serviceType: "regular", trainNo: nil)
    }

    func test_picksLatestSlotWithinWindow() {
        let deps = [dep(4, "23:30"), dep(24, "23:50"), dep(44, "00:10")]
        let last = selectLastTrain(deps, maxLookaheadMinutes: 12 * 60)
        XCTAssertEqual(last?.time, "00:10")
        XCTAssertEqual(last?.minutesAway, 44)
    }

    func test_ignoresLookaheadBeyondHorizon() {
        let deps = [
            dep(4, "23:30"),
            dep(44, "00:10"),
            dep(7 * 60 + 20, "05:30", line: "M3"),
        ]
        let last = selectLastTrain(deps, maxLookaheadMinutes: 6 * 60)
        XCTAssertEqual(last?.time, "00:10")
        XCTAssertEqual(last?.minutesAway, 44)
    }

    func test_returnsNilWhenServiceOver() {
        XCTAssertNil(selectLastTrain([], maxLookaheadMinutes: 12 * 60))
    }

    func test_excludesNegativeMinutesAway() {
        let deps = [dep(-3, "23:00"), dep(12, "23:15")]
        XCTAssertEqual(selectLastTrain(deps, maxLookaheadMinutes: 12 * 60)?.time, "23:15")
    }

    // MARK: - Italian station names

    func test_italianLiveTrainRouteUsesItalianExonyms() {
        XCTAssertEqual(
            SyrmosData.resolveStation("Αθήνα", en: "Athens", language: .italian),
            "Atene"
        )
        XCTAssertEqual(
            SyrmosData.resolveStation("Θεσσαλονίκη", en: "Thessaloniki", language: .italian),
            "Salonicco"
        )
        XCTAssertEqual(
            SyrmosData.resolveStation("Πειραιάς", en: "Piraeus", language: .italian),
            "Pireo"
        )
    }

    func test_italianStationNameKeepsNamesWithoutAnItalianExonym() {
        XCTAssertEqual(
            SyrmosData.resolveStation("Σύνταγμα", en: "Syntagma", language: .italian),
            "Syntagma"
        )
    }

    // MARK: - Permanent anonymous Ichnos history contract

    func test_ichnosHistoryDecodesGoodAndIssueCounts() throws {
        let payload = """
        {
          "granularity":"day",
          "scopeId":null,
          "buckets":[{
            "period":"2026-08-06",
            "totalReports":3,
            "positiveReports":2,
            "issueReports":1,
            "counts":{"normal":1,"clean":1,"delayed":1}
          }],
          "updatedAt":"2026-08-06T03:00:00Z",
          "privacy":"Permanent anonymous aggregates only."
        }
        """

        let history = try JSONDecoder().decode(IchnosCommunityHistory.self, from: Data(payload.utf8))

        XCTAssertEqual(history.granularity, "day")
        XCTAssertEqual(history.buckets.first?.totalReports, 3)
        XCTAssertEqual(history.buckets.first?.positiveReports, 2)
        XCTAssertEqual(history.buckets.first?.counts["delayed"], 1)
    }
}
