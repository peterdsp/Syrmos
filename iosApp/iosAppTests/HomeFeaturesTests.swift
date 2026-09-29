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

    // MARK: Insight dedupe (twin of Kotlin InsightDedupeTest)

    func test_insightDedupe_dropsARepeatedNoticeAndKeepsTheFirst() {
        let items = [("a", "Line 3 works 27/09"), ("b", "Line 3 works 27/09"), ("c", "Line 1 closure")]
        XCTAssertEqual(InsightDedupe.distinctByText(items) { $0.1 }.map { $0.0 }, ["a", "c"])
    }

    func test_insightDedupe_normalisationIgnoresCaseWhitespaceAndTrailingPunctuation() {
        let items = [("a", "  Line 3   works. "), ("b", "line 3 works"), ("c", "Line 3 works!")]
        XCTAssertEqual(InsightDedupe.distinctByText(items) { $0.1 }.map { $0.0 }, ["a"])
    }

    func test_insightDedupe_emptyTextsAreNeverDuplicatesOfEachOther() {
        let items = [("a", ""), ("b", "  "), ("c", "Real notice")]
        XCTAssertEqual(InsightDedupe.distinctByText(items) { $0.1 }.map { $0.0 }, ["a", "b", "c"])
    }

    // MARK: Athens clock label (twin of Kotlin AthensClockLabelTest)

    func test_athensClockLabel_isoInstantBecomesAnAthensClock() {
        XCTAssertEqual(AthensClockLabel.label("2026-09-26T10:14:00.000Z"), "13:14")
        XCTAssertEqual(AthensClockLabel.label("2026-09-26T13:14:00+03:00"), "13:14")
        XCTAssertEqual(AthensClockLabel.label("2026-01-15T10:14:00Z"), "12:14")
    }

    func test_athensClockLabel_bareClocksAreNormalisedAndOtherTextPassesThrough() {
        XCTAssertEqual(AthensClockLabel.label("9:05"), "09:05")
        XCTAssertEqual(AthensClockLabel.label("09:05:30"), "09:05")
        XCTAssertEqual(AthensClockLabel.label(" n/a "), "n/a")
    }

    func test_athensClockLabel_blankIsNil() {
        XCTAssertNil(AthensClockLabel.label(nil))
        XCTAssertNil(AthensClockLabel.label("  "))
    }
}

// MARK: - Count labels (twin of CountLabelTest in core/common)

final class CountLabelTests: XCTestCase {
    private func reports(_ count: Int, _ language: AppLanguage) -> String {
        countLabel(count, language,
                   en: ("report", "reports"),
                   el: ("αναφορά", "αναφορές"),
                   sq: ("raport", "raporte"),
                   it: ("segnalazione", "segnalazioni"))
    }

    func testOneTakesTheSingularInEveryLanguage() {
        XCTAssertEqual(reports(1, .english), "1 report")
        XCTAssertEqual(reports(1, .greek), "1 αναφορά")
        XCTAssertEqual(reports(1, .albanian), "1 raport")
        XCTAssertEqual(reports(1, .italian), "1 segnalazione")
    }

    func testManyAndZeroTakeThePlural() {
        XCTAssertEqual(reports(3, .english), "3 reports")
        XCTAssertEqual(reports(0, .greek), "0 αναφορές")
        XCTAssertEqual(reports(12, .albanian), "12 raporte")
        XCTAssertEqual(reports(2, .italian), "2 segnalazioni")
    }
}

// MARK: - Ichnos scope labels (twin of IchnosScopeLabelTest in core/common)

final class IchnosScopeLabelTests: XCTestCase {
    func testAStationLabelFromAnotherLanguageIsReadInTheReadersLanguage() {
        XCTAssertEqual(localizedScopeLabel("Ichnos at Florina", .albanian), "Ichnos në Florina")
        XCTAssertEqual(localizedScopeLabel("Ichnos në Florina", .greek), "Ichnos στο Florina")
        XCTAssertEqual(localizedScopeLabel("Ichnos στο Florina", .italian), "Ichnos a Florina")
        XCTAssertEqual(localizedScopeLabel("Ichnos a Florina", .english), "Ichnos at Florina")
    }

    func testALabelAlreadyInTheReadersLanguageIsUnchanged() {
        XCTAssertEqual(localizedScopeLabel("Ichnos at 1st Ag. Kosma", .english), "Ichnos at 1st Ag. Kosma")
    }

    func testLineAndTrainContextsAreLeftAlone() {
        XCTAssertEqual(localizedScopeLabel("Kallithea to Monastiraki", .greek), "Kallithea to Monastiraki")
        XCTAssertEqual(localizedScopeLabel("Train 1635", .albanian), "Train 1635")
    }

    func testAResolvableStationIdRebuildsTheWholeLabelInTheReadersLanguage() {
        // A Greek reporter submitted "Ichnos στο Καλλιθέα"; a resolvable id lets an
        // English reader see the English station name, not the Greek one.
        let names: [String: String] = ["M1_KAL": "Kallithea"]
        XCTAssertEqual(
            resolvedScopeLabel(scopeId: "M1_KAL", scopeLabel: "Ichnos στο Καλλιθέα", .english) { names[$0] },
            "Ichnos at Kallithea"
        )
        XCTAssertEqual(
            resolvedScopeLabel(scopeId: "M1_KAL", scopeLabel: "Ichnos at Kallithea", .greek) { _ in "Kallithea" },
            "Ichnos στο Kallithea"
        )
    }

    func testAnUnresolvableIdFallsBackToPrefixRelocalization() {
        XCTAssertEqual(
            resolvedScopeLabel(scopeId: "h_9f2c", scopeLabel: "Ichnos at Florina", .albanian) { _ in nil },
            "Ichnos në Florina"
        )
        XCTAssertEqual(
            resolvedScopeLabel(scopeId: "train_1635", scopeLabel: "Train 1635", .italian) { _ in nil },
            "Train 1635"
        )
    }

    func testRealStationIdResolvesAgainstLocalCoordinateData() {
        // M1_KAL and A1_AIR exist in the bundled coordinate data; a Greek reader
        // reads the Greek name regardless of the reporter's language.
        XCTAssertEqual(
            resolvedScopeLabel(scopeId: "M1_KAL", scopeLabel: "Ichnos at Kallithea", .greek) {
                StationCoords.localizedStationName(for: $0, .greek)
            },
            "Ichnos στο Καλλιθέα"
        )
        XCTAssertEqual(
            resolvedScopeLabel(scopeId: "A1_AIR", scopeLabel: "Ichnos στο Αεροδρόμιο", .english) {
                StationCoords.localizedStationName(for: $0, .english)
            },
            "Ichnos at Airport"
        )
    }
}

// MARK: - Browse-all station count (twin of StationCountLabelTest in core/common)

final class StationCountLabelTests: XCTestCase {
    func testTheRealCountIsWrittenIntoEveryLanguage() {
        XCTAssertEqual(browseAllStationsLabel(count: 394, .english), "Browse all 394 stations")
        XCTAssertEqual(browseAllStationsLabel(count: 394, .greek), "Περιήγηση σε όλους τους 394 σταθμούς")
        XCTAssertEqual(browseAllStationsLabel(count: 394, .albanian), "Shfleto të gjitha 394 stacionet")
        XCTAssertEqual(browseAllStationsLabel(count: 394, .italian), "Sfoglia tutte le 394 stazioni")
    }

    func testBeforeTheStationsLoadTheNumberIsDropped() {
        XCTAssertEqual(browseAllStationsLabel(count: 0, .english), "Browse all stations")
        for language in AppLanguage.allCases {
            XCTAssertFalse(browseAllStationsLabel(count: 0, language).contains("0"), language.rawValue)
            XCTAssertFalse(browseAllStationsLabel(count: 0, language).contains("{n}"), language.rawValue)
        }
    }
}

