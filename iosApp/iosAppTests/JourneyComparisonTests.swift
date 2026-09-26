import XCTest
@testable import Syrmos

/// iOS twin of Kotlin `JourneyComparisonTest`: the same fixtures must agree.
final class JourneyComparisonTests: XCTestCase {
    func test_loneAlternativeIsNeutral() {
        let f = JourneyComparison.facts(durations: [1500], changes: [1])
        XCTAssertEqual(f, [JourneyComparison.Facts()])
    }

    func test_fastestFlagsTheShortestWhenAnotherIsSlower() {
        let f = JourneyComparison.facts(durations: [1500, 1800, 2400], changes: [1, 1, 2])
        XCTAssertTrue(f[0].fastest)
        XCTAssertFalse(f[1].fastest)
        XCTAssertFalse(f[2].fastest)
    }

    func test_tiedFastestFlagsBothWhenAThirdIsSlower() {
        let f = JourneyComparison.facts(durations: [1500, 1500, 2100], changes: [1, 1, 1])
        XCTAssertTrue(f[0].fastest)
        XCTAssertTrue(f[1].fastest)
        XCTAssertFalse(f[2].fastest)
    }

    func test_equalDurationsFlagNobodyFastest() {
        let f = JourneyComparison.facts(durations: [1500, 1500], changes: [0, 1])
        XCTAssertFalse(f[0].fastest)
        XCTAssertFalse(f[1].fastest)
        XCTAssertNil(f[0].minutesSlowerThanFastest)
    }

    func test_unknownDurationIsNeverFastestAndHasNoDelta() {
        let f = JourneyComparison.facts(durations: [nil, 1500, 1800], changes: [1, 1, 1])
        XCTAssertFalse(f[0].fastest)
        XCTAssertNil(f[0].minutesSlowerThanFastest)
        XCTAssertTrue(f[1].fastest)
        XCTAssertEqual(f[2].minutesSlowerThanFastest, 5)
    }

    func test_slowerMinutesRoundToTheNearestMinuteAndDropZero() {
        let f = JourneyComparison.facts(durations: [1500, 1589, 1650, 1510], changes: [1, 1, 1, 1])
        XCTAssertNil(f[0].minutesSlowerThanFastest)
        XCTAssertEqual(f[1].minutesSlowerThanFastest, 1)
        XCTAssertEqual(f[2].minutesSlowerThanFastest, 3)
        XCTAssertNil(f[3].minutesSlowerThanFastest)
    }

    func test_fewestChangesAndExtraChangesAgainstTheMinimum() {
        let f = JourneyComparison.facts(durations: [2400, 1500, 1800], changes: [0, 2, 1])
        XCTAssertTrue(f[0].fewestChanges)
        XCTAssertFalse(f[1].fewestChanges)
        XCTAssertEqual(f[0].extraChanges, 0)
        XCTAssertEqual(f[1].extraChanges, 2)
        XCTAssertEqual(f[2].extraChanges, 1)
    }

    func test_equalChangesFlagNobodyFewest() {
        let f = JourneyComparison.facts(durations: [1500, 1800], changes: [1, 1])
        XCTAssertFalse(f[0].fewestChanges)
        XCTAssertFalse(f[1].fewestChanges)
    }

    func test_selectionRetainsAnOfferedIdAndFallsBackToTheFirst() {
        let ids = ["m1", "m1-m3", "m2"]
        XCTAssertEqual(JourneySelection.retain(previous: "m1-m3", ids: ids), "m1-m3")
        XCTAssertEqual(JourneySelection.retain(previous: "gone", ids: ids), "m1")
        XCTAssertEqual(JourneySelection.retain(previous: nil, ids: ids), "m1")
        XCTAssertNil(JourneySelection.retain(previous: "m1", ids: []))
    }

    func test_selectionIndexIsNilWhenNotOffered() {
        let ids = ["a", "b"]
        XCTAssertEqual(JourneySelection.index(of: "b", in: ids), 1)
        XCTAssertNil(JourneySelection.index(of: "z", in: ids))
        XCTAssertNil(JourneySelection.index(of: nil, in: ids))
    }
}
