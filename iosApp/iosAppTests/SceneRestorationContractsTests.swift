import XCTest
@testable import Syrmos

// Phase A of iOS scene state restoration: the pure contracts.
// See docs/design/IOS-SCENE-STATE-RESTORATION-SCOPE.md.

final class SceneRestorationContractsTests: XCTestCase {

    // MARK: Codec round-trip + versioning

    func testHomeRoundTripKeepsSelectionAndPush() {
        let state = HomeRestorationState(
            selectedNearbyId: "M1_KAL",
            push: RestorablePush(kind: RestorablePushKind.station, id: "A1_AIR")
        )
        let blob = HomeRestorationContract.encode(state)
        XCTAssertEqual(HomeRestorationContract.decode(blob), .ok(state))
    }

    func testExploreRoundTrip() {
        let state = ExploreRestorationState(
            selectedRegion: "athens", selectedType: "metro",
            selectedLineId: "M3", push: RestorablePush(kind: RestorablePushKind.line, id: "M3")
        )
        XCTAssertEqual(ExploreRestorationContract.decode(ExploreRestorationContract.encode(state)), .ok(state))
    }

    func testDeparturesRoundTrip() {
        let state = DeparturesRestorationState(selectedCity: "athens", selectedRoute: "M3", dayOffset: 2)
        XCTAssertEqual(DeparturesRestorationContract.decode(DeparturesRestorationContract.encode(state)), .ok(state))
    }

    func testEmptyBlobIsEmptyNotCorrupt() {
        XCTAssertEqual(HomeRestorationContract.decode(nil), .empty)
        XCTAssertEqual(HomeRestorationContract.decode(""), .empty)
        XCTAssertEqual(HomeRestorationContract.decode("   "), .empty)
    }

    func testCorruptBlobIsReported() {
        if case .corrupt = HomeRestorationContract.decode("{not json") {} else {
            XCTFail("expected corrupt")
        }
    }

    func testNewerSchemaIsUnsupportedAndPreserved() {
        let future = "{\"schemaVersion\":99,\"state\":{\"selectedNearbyId\":\"X\"}}"
        XCTAssertEqual(HomeRestorationContract.decode(future), .unsupported(foundVersion: 99))
    }

    func testEncodedBlobDoesNotEscapeSlashesAndCarriesVersion() {
        let blob = DeparturesRestorationContract.encode(
            DeparturesRestorationState(selectedCity: "athens", selectedRoute: "A/B", dayOffset: 0)
        )
        XCTAssertTrue(blob.contains("\"schemaVersion\":1"))
        XCTAssertTrue(blob.contains("A/B"), "slashes should not be unicode-escaped")
    }

    // MARK: Freshness gate

    func testDeepPushRestoresOnlyWithinTheWindow() {
        let now = Date(timeIntervalSince1970: 10_000)
        // Just backgrounded: restore.
        XCTAssertTrue(SceneRestorationFreshness.shouldRestoreDeepPush(
            backgroundedAt: now.addingTimeInterval(-60), now: now))
        // 29 minutes ago: restore.
        XCTAssertTrue(SceneRestorationFreshness.shouldRestoreDeepPush(
            backgroundedAt: now.addingTimeInterval(-29 * 60), now: now))
        // 31 minutes ago: too old.
        XCTAssertFalse(SceneRestorationFreshness.shouldRestoreDeepPush(
            backgroundedAt: now.addingTimeInterval(-31 * 60), now: now))
        // Never backgrounded this run (cold launch, no record): no deep restore.
        XCTAssertFalse(SceneRestorationFreshness.shouldRestoreDeepPush(backgroundedAt: nil, now: now))
        // A clock that went backwards is not treated as fresh.
        XCTAssertFalse(SceneRestorationFreshness.shouldRestoreDeepPush(
            backgroundedAt: now.addingTimeInterval(120), now: now))
    }

    // MARK: Validation

    func testHomeValidationDropsUnknownPushButKeepsSelection() {
        let state = HomeRestorationState(
            selectedNearbyId: "M1_KAL",
            push: RestorablePush(kind: RestorablePushKind.station, id: "GONE")
        )
        let validated = state.validated(
            allowDeepPush: true,
            stationExists: { $0 == "M1_KAL" },   // "GONE" does not exist
            lineExists: { _ in false }
        )
        XCTAssertNil(validated.push, "unknown push id should be dropped")
        XCTAssertEqual(validated.selectedNearbyId, "M1_KAL", "selection is always kept")
    }

    func testHomeValidationDropsPushWhenFreshnessGateClosed() {
        let state = HomeRestorationState(
            selectedNearbyId: nil,
            push: RestorablePush(kind: RestorablePushKind.station, id: "A1_AIR")
        )
        let validated = state.validated(
            allowDeepPush: false,               // gate closed
            stationExists: { _ in true },       // even though it exists
            lineExists: { _ in true }
        )
        XCTAssertNil(validated.push)
    }

    func testExploreValidationDropsUnknownLineAndPush() {
        let state = ExploreRestorationState(
            selectedRegion: "athens", selectedType: "metro",
            selectedLineId: "GONE", push: RestorablePush(kind: RestorablePushKind.line, id: "ALSO_GONE")
        )
        let validated = state.validated(
            allowDeepPush: true,
            stationExists: { _ in false },
            lineExists: { $0 == "M3" }
        )
        XCTAssertNil(validated.selectedLineId, "an unknown selected line is dropped")
        XCTAssertNil(validated.push, "an unknown push is dropped")
        XCTAssertEqual(validated.selectedRegion, "athens", "region/type have no seed dependency")
        XCTAssertEqual(validated.selectedType, "metro")
    }

    // MARK: Store adapter (Phase B)

    func testStoreRoundTripThroughUserDefaults() {
        let suite = "test.scene.restoration.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let key = DeparturesRestorationContract.storageKey

        XCTAssertNil(SceneRestorationStore.load(key, defaults: defaults), "nothing persisted yet")
        XCTAssertEqual(DeparturesRestorationContract.decode(
            SceneRestorationStore.load(key, defaults: defaults)), .empty)

        let state = DeparturesRestorationState(selectedCity: "thessaloniki", selectedRoute: "X1", dayOffset: 3)
        SceneRestorationStore.save(key, DeparturesRestorationContract.encode(state), defaults: defaults)

        XCTAssertEqual(
            DeparturesRestorationContract.decode(SceneRestorationStore.load(key, defaults: defaults)),
            .ok(state)
        )
    }

    func testPushExistsMatchesKind() {
        let station = RestorablePush(kind: RestorablePushKind.station, id: "A1_AIR")
        XCTAssertTrue(station.exists(stationExists: { $0 == "A1_AIR" }, lineExists: { _ in false }))
        XCTAssertFalse(station.exists(stationExists: { _ in false }, lineExists: { _ in true }))
        let unknownKind = RestorablePush(kind: "bogus", id: "A1_AIR")
        XCTAssertFalse(unknownKind.exists(stationExists: { _ in true }, lineExists: { _ in true }))
    }
}
