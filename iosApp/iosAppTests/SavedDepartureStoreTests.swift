import XCTest
@testable import Syrmos

/// Mirrors fixtures/reminders/saved.json, so the iOS saved-departure store, the
/// Kotlin SavedDepartureStore/ReminderContract and web web-reminders.js agree on
/// the persisted wire form and list ops (Phase N J09).
final class SavedDepartureStoreTests: XCTestCase {

    private func dep(
        line: String = "M1", station: String = "M1_VIC", name: String = "Victoria",
        dest: String = "Kifisia", time: String = "08:00", depSec: Int64, lead: Int64 = 900,
        created: String = "2026-01-01T08:00:00Z"
    ) -> SavedDeparture {
        SavedDeparture(lineId: line, stationId: station, stationName: name, destination: dest,
                       scheduledTime: time, departureEpochSeconds: depSec, leadSeconds: lead,
                       createdAt: created)
    }

    // The exact wire form Kotlin ReminderContract and web-reminders.js also produce.
    private let encoded =
        "{\"schemaVersion\":1,\"savedDepartures\":[{" +
        "\"lineId\":\"M1\",\"stationId\":\"M1_VIC\",\"stationName\":\"Victoria\"," +
        "\"destination\":\"Kifisia\",\"scheduledTime\":\"08:00\"," +
        "\"departureEpochSeconds\":1000000,\"leadSeconds\":900," +
        "\"createdAt\":\"2026-01-01T08:00:00Z\",\"schemaVersion\":1}]}"

    func testEncodeIsByteParity() {
        XCTAssertEqual(ReminderContract.encodeRoot([dep(depSec: 1_000_000)]), encoded)
    }

    func testDecodeRoundTrips() {
        guard case let .ok(list) = ReminderContract.decodeRoot(encoded) else {
            return XCTFail("expected ok")
        }
        XCTAssertEqual(list, [dep(depSec: 1_000_000)])
    }

    func testDecodeBlankUnsupportedCorrupt() {
        guard case .ok(let empty) = ReminderContract.decodeRoot("   ") else { return XCTFail() }
        XCTAssertEqual(empty, [])
        guard case .unsupported(let v) = ReminderContract.decodeRoot("{\"schemaVersion\":999,\"savedDepartures\":[]}") else {
            return XCTFail("expected unsupported")
        }
        XCTAssertEqual(v, 999)
        guard case .corrupt = ReminderContract.decodeRoot("{not json") else { return XCTFail("expected corrupt") }
    }

    func testStoreOps() {
        let a = dep(depSec: 1_000_000)
        let b = dep(line: "M3", station: "M3_SYN", name: "Syntagma", dest: "Airport",
                    time: "08:10", depSec: 1_000_600, lead: 600, created: "2026-01-01T08:01:00Z")
        var list = SavedDepartureStore.save([], a)
        list = SavedDepartureStore.save(list, b)
        XCTAssertEqual(list.map { $0.id }, ["M3|M3_SYN|1000600", "M1|M1_VIC|1000000"])

        let aUpdated = dep(depSec: 1_000_000, lead: 1200)
        list = SavedDepartureStore.save(list, aUpdated)
        XCTAssertEqual(list.map { $0.id }, ["M1|M1_VIC|1000000", "M3|M3_SYN|1000600"])
        XCTAssertEqual(list.first { $0.id == "M1|M1_VIC|1000000" }?.leadSeconds, 1200)
        XCTAssertEqual(list.count, 2)

        XCTAssertTrue(SavedDepartureStore.contains(list, id: "M1|M1_VIC|1000000"))
        list = SavedDepartureStore.remove(list, id: "M1|M1_VIC|1000000")
        XCTAssertFalse(SavedDepartureStore.contains(list, id: "M1|M1_VIC|1000000"))

        let pruned = SavedDepartureStore.pruneDeparted([a, b], now: 1_000_300)
        XCTAssertEqual(pruned.map { $0.id }, ["M3|M3_SYN|1000600"])

        XCTAssertEqual(
            SavedDepartureStore.reorder([a, b], order: ["M3|M3_SYN|1000600", "M1|M1_VIC|1000000", "x"]).map { $0.id },
            ["M3|M3_SYN|1000600", "M1|M1_VIC|1000000"]
        )

        XCTAssertEqual(SavedDepartureStore.toReminders([a]).first?.leaveByEpochSeconds, 999_100)
    }
}
