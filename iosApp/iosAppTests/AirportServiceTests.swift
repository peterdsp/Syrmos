import XCTest
@testable import Syrmos

/// Covers the airport-services redesign: live express-bus ETA reduction and the
/// de-duplicated, correctly-labeled airport departures list. Pins the three bugs
/// that shipped in the old inline `rows`:
///   1. duplicate "M3 Syntagma HH:MM" rows (M3 + M3_AIR project one train),
///   2. non-metro slots relabeled as "M3 / Syntagma / metro departure",
///   3. buses stuck on a passive "24/7 - check OASA" despite a live ETA feed.
@MainActor
final class AirportServiceTests: XCTestCase {

    private func dep(_ line: String, _ time: String, dir: String = "Syntagma") -> Departure {
        Departure(time: time, lineId: line, direction: dir, minutesAway: 0, serviceType: "", trainNo: nil)
    }

    // MARK: - AirportBusService.reduce

    func testReduceGroupsSortsAndClampsEtas() {
        let payload = AirportBusService.Payload(updatedAt: "2026-09-03T21:42:32.703144+00:00", airportArrivals: [
            .init(lineId: "X95", minutesAway: 19),
            .init(lineId: "X95", minutesAway: 5),
            .init(lineId: "X93", minutesAway: -3), // clamps to 0
            .init(lineId: "", minutesAway: 4),     // dropped: no line
        ])
        let live = AirportBusService.reduce(payload)
        XCTAssertEqual(live.soonest("X95"), 5, "soonest X95 ETA wins")
        XCTAssertEqual(live.etasByLine["X95"], [5, 19], "ETAs sorted ascending")
        XCTAssertEqual(live.soonest("X93"), 0, "negative ETA clamps to 0")
        XCTAssertNil(live.soonest("X97"), "untracked line absent")
        XCTAssertNotNil(live.updatedAt, "fractional-second ISO timestamp parses")
    }

    func testReduceEmptyFeed() {
        let live = AirportBusService.reduce(.init(updatedAt: "", airportArrivals: []))
        XCTAssertTrue(live.isEmpty)
        XCTAssertNil(live.updatedAt)
    }

    // MARK: - De-duplication + labeling

    func testMetroDuplicatesCollapseByTime() {
        // M3 and M3_AIR both project the 06:39 airport train.
        let deps = [dep("M3", "06:39"), dep("M3_AIR", "06:39"), dep("M3", "06:49")]
        let rows = AirportServiceRows.build(metroDepartures: deps, buses: nil, language: .english)
        let metro = rows.filter { $0.route == "M3" }
        XCTAssertEqual(metro.map { $0.time }, ["06:39", "06:49"], "one row per distinct time")
    }

    func testSuburbanKeepsRealLineAndIsNotRelabeledMetro() {
        // The old prefix(3) turned an A1 slot into "M3 / Syntagma / metro departure".
        let deps = [dep("M3", "06:39"), dep("A1", "04:00", dir: "Piraeus"), dep("A2", "04:10", dir: "Piraeus")]
        let rows = AirportServiceRows.build(metroDepartures: deps, buses: nil, language: .english)
        XCTAssertFalse(rows.contains { $0.route == "A1" && $0.destination == "Syntagma" },
                       "A1 must never be relabeled a metro to Syntagma")
        XCTAssertTrue(rows.contains { $0.route == "A1" && $0.destination == "Piraeus" })
        XCTAssertTrue(rows.contains { $0.route == "A2" && $0.destination == "Piraeus" })
    }

    func testSuburbanDedupAndCap() {
        let deps = [dep("A1", "04:00", dir: "Piraeus"), dep("A1", "04:00", dir: "Piraeus"),
                    dep("A1", "04:30", dir: "Piraeus"), dep("A1", "05:00", dir: "Piraeus")]
        let rows = AirportServiceRows.build(metroDepartures: deps, buses: nil, language: .english)
        let sub = rows.filter { $0.route == "A1" }
        XCTAssertEqual(sub.map { $0.time }, ["04:00", "04:30"], "duplicates dropped, capped at 2")
    }

    func testMetroCappedAtThree() {
        let deps = (0..<6).map { dep("M3", String(format: "07:%02d", $0 * 5)) }
        let rows = AirportServiceRows.build(metroDepartures: deps, buses: nil, language: .english)
        XCTAssertEqual(rows.filter { $0.route == "M3" }.count, 3)
    }

    // MARK: - Buses: live vs fallback

    func testBusesShowLiveEtaWhenTracked() {
        let live = AirportBusService.reduce(.init(updatedAt: "", airportArrivals: [
            .init(lineId: "X95", minutesAway: 5),
            .init(lineId: "X93", minutesAway: 9),
        ]))
        let rows = AirportServiceRows.build(metroDepartures: [], buses: live, language: .english)
        let x95 = rows.first { $0.route == "X95" }
        XCTAssertEqual(x95?.time, "5 min")
        XCTAssertEqual(x95?.confidence, .live)
        // X96 is untracked in this feed -> neutral fallback, never a fake time.
        let x96 = rows.first { $0.route == "X96" }
        XCTAssertEqual(x96?.time, "24/7")
        XCTAssertEqual(x96?.confidence, .operatorLink)
    }

    func testBusesFallBackWhenNoLiveData() {
        let rows = AirportServiceRows.build(metroDepartures: [], buses: nil, language: .english)
        for line in ["X95", "X93", "X96", "X97"] {
            let row = rows.first { $0.route == line }
            XCTAssertEqual(row?.time, "24/7")
            XCTAssertEqual(row?.confidence, .operatorLink)
            XCTAssertFalse(row?.detail.contains("OASA") ?? true, "no passive check-OASA copy")
        }
    }

    // MARK: - LiveAirportBusService.parse (map vehicles)

    func testParseKeepsValidVehiclesAndDerivesDirection() {
        let payload = LiveAirportBusService.Payload(
            vehicles: [
                .init(vehicleId: "61233", lat: 37.90, lng: 23.90, routeCode: 2051, lineId: "X95"), // to airport
                .init(vehicleId: "61167", lat: 37.93, lng: 23.94, routeCode: 2052, lineId: "X95"), // from airport
                .init(vehicleId: "0", lat: 0, lng: 0, routeCode: 2051, lineId: "X95"),   // dropped: null island
                .init(vehicleId: "y", lat: 37.9, lng: 23.9, routeCode: 999, lineId: ""), // dropped: no line
            ],
            routes: ["X95": .init(toAirport: [2051], fromAirport: [2052])]
        )
        let vehicles = LiveAirportBusService.parse(payload)
        XCTAssertEqual(vehicles.count, 2)
        XCTAssertEqual(vehicles[0].toAirport, true)
        XCTAssertEqual(vehicles[1].toAirport, false)
        XCTAssertEqual(vehicles[0].id, "61233")
    }

    func testParseUnknownDirectionWhenNoRoutes() {
        let payload = LiveAirportBusService.Payload(
            vehicles: [.init(vehicleId: "1", lat: 37.9, lng: 23.9, routeCode: 5675, lineId: "X93")],
            routes: [:]
        )
        let vehicles = LiveAirportBusService.parse(payload)
        XCTAssertEqual(vehicles.count, 1)
        XCTAssertNil(vehicles[0].toAirport)
    }

    func testEtaLabelFormatting() {
        XCTAssertEqual(AirportServiceRows.etaLabel(minutes: 0, language: .english), "Now")
        XCTAssertEqual(AirportServiceRows.etaLabel(minutes: 1, language: .english), "Now")
        XCTAssertEqual(AirportServiceRows.etaLabel(minutes: 7, language: .english), "7 min")
        XCTAssertEqual(AirportServiceRows.etaLabel(minutes: 65, language: .english), "1h 5 min")
    }
}
