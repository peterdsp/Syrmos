import XCTest
@testable import Syrmos

/// iOS peer of web `web-tests/saved-journeys.test.js` and Kotlin
/// `SavedJourneyStoreTest`, mirroring the golden cases in
/// fixtures/journeys/saved.json. Proves the pure ops return the same ordered ids
/// and honour the HARD de-dup invariant (one row per (fromId,toId)), plus the
/// atomic/versioned decode outcomes.
final class SavedJourneyStoreTests: XCTestCase {

    private func sj(_ id: String, _ from: String, _ to: String, _ created: String,
                    label: String? = nil, ranking: String = "fastest") -> SavedJourney {
        SavedJourney(id: id, fromId: from, toId: to, createdAt: created, label: label,
                     preferences: SavedJourneyPreferences(ranking: ranking, accessibilityPreference: "none"))
    }
    private func ids(_ list: [SavedJourney]) -> [String] { list.map { $0.id } }

    func testSavePrependsNewestFirst() {
        let before = [sj("a", "M1_PIR", "M1_OMO", "2026-01-15T08:00:00+02:00")]
        let result = SavedJourneyContract.save(before, sj("b", "M2_SYN", "M2_ELL", "2026-01-15T09:00:00+02:00", label: "Home"))
        XCTAssertEqual(ids(result), ["b", "a"])
    }

    func testSaveDedupsSamePairKeepsIdMovesToTop() {
        let before = [
            sj("a", "M1_PIR", "M1_OMO", "2026-01-15T08:00:00+02:00"),
            sj("b", "M2_SYN", "M2_ELL", "2026-01-15T08:30:00+02:00", label: "Home"),
        ]
        let result = SavedJourneyContract.save(before, sj("c", "M1_PIR", "M1_OMO", "2026-01-15T09:00:00+02:00", label: "Office", ranking: "fewestChanges"))
        XCTAssertEqual(ids(result), ["a", "b"])
        XCTAssertEqual(result.first?.id, "a")
        XCTAssertEqual(result.first?.label, "Office")
        XCTAssertEqual(result.first?.preferences.ranking, "fewestChanges")
        XCTAssertEqual(result.first?.createdAt, "2026-01-15T09:00:00+02:00")
    }

    func testRenameSetsLabelAndBlankClearsToNil() {
        let before = [sj("a", "M1_PIR", "M1_OMO", "2026-01-15T08:00:00+02:00", label: "Work")]
        XCTAssertEqual(SavedJourneyContract.rename(before, id: "a", label: "Gym").first?.label, "Gym")
        XCTAssertNil(SavedJourneyContract.rename(before, id: "a", label: "   ").first?.label)
    }

    func testRemoveDeletesById() {
        let before = [
            sj("a", "M1_PIR", "M1_OMO", "2026-01-15T08:00:00+02:00"),
            sj("b", "M2_SYN", "M2_ELL", "2026-01-15T08:30:00+02:00", label: "Home"),
        ]
        XCTAssertEqual(ids(SavedJourneyContract.remove(before, id: "a")), ["b"])
    }

    func testReorderAppliesIdSequenceDroppingUnknown() {
        let before = [
            sj("a", "M1_PIR", "M1_OMO", "2026-01-15T08:00:00+02:00"),
            sj("b", "M2_SYN", "M2_ELL", "2026-01-15T08:30:00+02:00"),
            sj("c", "M3_AIR", "M3_SYN", "2026-01-15T09:00:00+02:00"),
        ]
        XCTAssertEqual(ids(SavedJourneyContract.reorder(before, order: ["c", "a", "b", "zzz"])), ["c", "a", "b"])
    }

    func testDeDupIsAHardInvariant() {
        var list: [SavedJourney] = []
        list = SavedJourneyContract.save(list, sj("x1", "A", "B", "2026-01-15T08:00:00+02:00", label: "one"))
        list = SavedJourneyContract.save(list, sj("x2", "A", "B", "2026-01-15T08:01:00+02:00", label: "two"))
        list = SavedJourneyContract.save(list, sj("x3", "A", "B", "2026-01-15T08:02:00+02:00", label: "three"))
        XCTAssertEqual(list.count, 1)
        XCTAssertEqual(list.first?.id, "x1")
        XCTAssertEqual(list.first?.label, "three")
    }

    func testEncodeMatchesFixtureEmptyRootShapeAndRoundTrips() {
        XCTAssertEqual(SavedJourneyContract.encode([]), "{\"schemaVersion\":1,\"savedJourneys\":[]}")
        let list = SavedJourneyContract.save([], sj("a", "M1_PIR", "M1_OMO", "2026-01-15T08:00:00+02:00"))
        if case .ok(let round) = SavedJourneyContract.decode(SavedJourneyContract.encode(list)) {
            XCTAssertEqual(ids(round), ["a"])
        } else {
            XCTFail("round-trip should decode ok")
        }
    }

    func testDecodeIsAtomicAndVersioned() {
        if case .ok = SavedJourneyContract.decode("") {} else { XCTFail("empty is ok") }
        if case .unsupported(let v) = SavedJourneyContract.decode("{\"schemaVersion\":2,\"savedJourneys\":[]}") {
            XCTAssertEqual(v, 2)
        } else { XCTFail("newer schema is unsupported") }
        if case .corrupt = SavedJourneyContract.decode("{not json") {} else { XCTFail("garbage is corrupt") }
    }
}
