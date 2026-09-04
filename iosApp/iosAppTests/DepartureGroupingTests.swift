import XCTest
@testable import Syrmos

/// Pins the departure-board grouping transform: consecutive departures sharing a
/// (line, destination) collapse into one group carrying the next few times, so
/// the station board stops repeating "Line 3 · towards X · Scheduled" on every
/// row. Mirrors the web `web-tests/departures-grouping.test.js` cases so iOS and
/// web group identically.
final class DepartureGroupingTests: XCTestCase {

    private func dep(
        _ line: String,
        min: Int,
        time: String = "",
        dir: String = "Doukissis Plakentias",
        service: String = "",
        source: SourceConfidence = .scheduled
    ) -> Departure {
        var d = Departure(time: time, lineId: line, direction: dir, minutesAway: min, serviceType: service, trainNo: nil)
        d.sourceConfidence = source
        return d
    }

    func testCollapsesSameLineAndDestinationWithOrderedTimes() {
        let g = DepartureGrouping.group([
            dep("M3", min: 4, time: "12:31"),
            dep("M3", min: 12, time: "12:39"),
            dep("M3", min: 22, time: "12:49"),
        ])
        XCTAssertEqual(g.count, 1, "three same-destination rows become one group")
        XCTAssertEqual(g[0].destination, "Doukissis Plakentias")
        XCTAssertEqual(g[0].lineId, "M3")
        XCTAssertEqual(g[0].times.map(\.minutesAway), [4, 12, 22])
        XCTAssertEqual(g[0].times.map(\.time), ["12:31", "12:39", "12:49"])
        XCTAssertEqual(g[0].total, 3)
        XCTAssertEqual(g[0].moreCount, 0)
    }

    func testDistinctDestinationsStaySeparateSoonestFirst() {
        let g = DepartureGrouping.group([
            dep("M3", min: 4, dir: "Airport"),
            dep("M3", min: 6, dir: "Dimotiko Theatro"),
            dep("M3", min: 22, dir: "Airport"),
        ])
        XCTAssertEqual(g.count, 2, "two destinations => two groups")
        XCTAssertEqual(g[0].destination, "Airport", "group ordered by its soonest member")
        XCTAssertEqual(g[0].times.map(\.minutesAway), [4, 22], "non-adjacent members still merge")
        XCTAssertEqual(g[1].destination, "Dimotiko Theatro")
    }

    func testDestinationKeyFoldsCaseAndWhitespace() {
        let g = DepartureGrouping.group([
            dep("M3", min: 4, dir: "Airport"),
            dep("M3", min: 9, dir: "airport "),
        ])
        XCTAssertEqual(g.count, 1)
        XCTAssertEqual(g[0].destination, "Airport", "keeps the first original spelling for display")
        XCTAssertEqual(g[0].times.map(\.minutesAway), [4, 9])
    }

    func testMaxTimesCapsAndReportsRemainder() {
        let g = DepartureGrouping.group([
            dep("M3", min: 2, dir: "Kifissia"),
            dep("M3", min: 9, dir: "Kifissia"),
            dep("M3", min: 16, dir: "Kifissia"),
            dep("M3", min: 24, dir: "Kifissia"),
        ], maxTimes: 3)
        XCTAssertEqual(g[0].times.map(\.minutesAway), [2, 9, 16])
        XCTAssertEqual(g[0].moreCount, 1)
        XCTAssertEqual(g[0].total, 4)
    }

    func testMaxTimesZeroKeepsEveryTime() {
        let g = DepartureGrouping.group([
            dep("M3", min: 2, dir: "Kifissia"),
            dep("M3", min: 9, dir: "Kifissia"),
        ], maxTimes: 0)
        XCTAssertEqual(g[0].times.count, 2)
        XCTAssertEqual(g[0].moreCount, 0)
    }

    func testConfidenceReflectsSoonestNotStrongest() {
        // Soonest is live -> live chip (times still come out ascending even
        // though the live one was passed second).
        let live = DepartureGrouping.group([
            dep("M3", min: 8, dir: "Airport", source: .scheduled),
            dep("M3", min: 3, dir: "Airport", source: .live),
        ])
        XCTAssertEqual(live[0].times.first?.minutesAway, 3, "times sorted ascending")
        XCTAssertEqual(live[0].sourceConfidence, .live)

        // Soonest is scheduled and a LATER time is live -> chip stays scheduled,
        // so a tracked vehicle is never advertised over a scheduled lead time.
        let sched = DepartureGrouping.group([
            dep("M3", min: 3, dir: "Airport", source: .scheduled),
            dep("M3", min: 20, dir: "Airport", source: .live),
        ])
        XCTAssertEqual(sched[0].sourceConfidence, .scheduled)
    }

    func testDifferentLinesNeverMergeEvenWithSameDestination() {
        let g = DepartureGrouping.group([
            dep("M3", min: 5, dir: "Piraeus"),
            dep("A2", min: 6, dir: "Piraeus"),
        ])
        XCTAssertEqual(g.count, 2)
        XCTAssertEqual(g.map(\.lineId), ["M3", "A2"])
    }

    func testCarriesServiceTypeForAirportPill() {
        let g = DepartureGrouping.group([
            dep("M3", min: 4, dir: "Airport", service: "airport"),
            dep("M3", min: 14, dir: "Airport", service: "airport"),
        ])
        XCTAssertEqual(g[0].serviceType, "airport")
    }

    func testEmptyInputYieldsNoGroups() {
        XCTAssertTrue(DepartureGrouping.group([]).isEmpty)
    }
}
