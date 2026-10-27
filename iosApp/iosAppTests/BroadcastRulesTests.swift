import XCTest
@testable import Syrmos

/// 3.0.2 announcements from Syrmos: audience, polling dedupe, topics, deep links.
@MainActor
final class BroadcastRulesTests: XCTestCase {
    private func b(_ id: String, sent: Int64, expires: Int64? = nil, audience: String = "all") -> Broadcast {
        Broadcast(id: id, title: LocalizedText(en: id), body: nil, audience: audience, sentAt: nil,
                  sentAtEpochSeconds: sent, expiresAt: nil, expiresAtEpochSeconds: expires, deepLink: nil, closureId: nil)
    }

    func testAudience() {
        let fav: Set<String> = ["M3_NIK"], lines: Set<String> = ["M3"]
        func rel(_ a: String?) -> Bool {
            BroadcastRules.isRelevant(audience: a, favouriteStationIds: fav, favouriteLineIds: lines, region: "athens")
        }
        XCTAssertTrue(rel("all"))
        XCTAssertTrue(rel(nil))
        XCTAssertTrue(rel("line:M3"))
        XCTAssertFalse(rel("line:M1"))
        XCTAssertTrue(rel("station:M3_NIK"))
        XCTAssertFalse(rel("station:M2_SYN"))
        XCTAssertTrue(rel("region:athens"))
        XCTAssertFalse(rel("region:thessaloniki"))
        XCTAssertFalse(rel("weird"))
    }

    func testToNotifyDedupesDeliveredNotifiedExpiredAndCaps() {
        let now: Int64 = 1_000
        let list = [b("a", sent: 900), b("b", sent: 950), b("c", sent: 990, expires: 999), b("d", sent: 980),
                    b("e", sent: 970), b("f", sent: 960), b("a", sent: 900), b("g", sent: 999, audience: "line:M1")]
        let out = BroadcastRules.toNotify(list, delivered: ["b"], notified: ["d"], now: now,
                                          isRelevant: { $0.audience == "all" })
        XCTAssertEqual(out.map(\.id), ["e", "f", "a"])
    }

    func testCursorAndTopics() {
        XCTAssertEqual(BroadcastRules.nextSince(current: 100, fetched: [b("x", sent: 50), b("y", sent: 300)]), 300)
        XCTAssertEqual(BroadcastRules.nextSince(current: 100, fetched: []), 100)
        XCTAssertEqual(BroadcastRules.topics(favouriteStationIds: ["M3_NIK", "M2_SYN"], favouriteLineIds: ["M3", "M2"], region: "athens"),
                       ["all", "region.athens", "line.M2", "line.M3", "station.M2_SYN", "station.M3_NIK"])
        XCTAssertEqual(BroadcastRules.topics(favouriteStationIds: [], favouriteLineIds: [], region: "national"), ["all"])
    }

    func testTokenDeepLinkAndRegistrationCadence() {
        XCTAssertEqual(BroadcastRules.hexToken(Data([0x00, 0xab, 0x10])), "00ab10")
        XCTAssertEqual(BroadcastRules.destination(fromDeepLink: "syrmos://station/M2_SYN"), .station(id: "M2_SYN"))
        XCTAssertEqual(BroadcastRules.destination(fromDeepLink: "syrmos://line/M3"), .line(id: "M3"))
        XCTAssertNil(BroadcastRules.destination(fromDeepLink: "https://example.com/station/M2_SYN"))
        XCTAssertNil(BroadcastRules.destination(fromDeepLink: nil))
        XCTAssertTrue(BroadcastRules.needsRegistration(previousSignature: nil, signature: "s", lastRegistered: nil, now: 0))
        XCTAssertTrue(BroadcastRules.needsRegistration(previousSignature: "old", signature: "s", lastRegistered: 0, now: 10))
        XCTAssertFalse(BroadcastRules.needsRegistration(previousSignature: "s", signature: "s", lastRegistered: 0, now: 3600))
        XCTAssertTrue(BroadcastRules.needsRegistration(previousSignature: "s", signature: "s", lastRegistered: 0, now: 86_400))
    }

    func testBroadcastDecodesContractShape() throws {
        let json = """
        {"schemaVersion":1,"broadcasts":[{"id":"b_01J","title":{"el":"28η Οκτωβρίου","en":"28 October"},
        "body":{"el":"Από τις 08:00","en":"From 08:00"},"audience":"all","sentAt":"2026-10-27T18:00:00Z",
        "sentAtEpochSeconds":1793124000,"expiresAt":"2026-10-28T22:00:00Z","expiresAtEpochSeconds":1793224800,
        "deepLink":"syrmos://station/M2_SYN","closureId":"syn-parade-2026"}]}
        """
        let p = try JSONDecoder().decode(BroadcastsPayload.self, from: Data(json.utf8))
        XCTAssertEqual(p.broadcasts.first?.title?.pick(.greek), "28η Οκτωβρίου")
        XCTAssertEqual(p.broadcasts.first?.title?.pick(.albanian), "28 October")
        XCTAssertEqual(p.broadcasts.first?.expiresAtEpochSeconds, 1_793_224_800)
    }

    /// A push delivered in the background (seen only in Notification Center)
    /// must count as delivered so polling does not repeat it.
    func testBackgroundDeliveredPushSuppressesPolledDuplicate() {
        let infos: [[AnyHashable: Any]] = [["broadcastId": "b1", "deepLink": ""], ["alertId": "x"], ["broadcastId": ""]]
        let delivered = BroadcastRules.broadcastIds(fromUserInfos: infos)
        XCTAssertEqual(delivered, ["b1"])
        let out = BroadcastRules.toNotify([b("b1", sent: 10), b("b2", sent: 9)], delivered: delivered, notified: [],
                                          now: 20, isRelevant: { _ in true })
        XCTAssertEqual(out.map(\.id), ["b2"])
    }
}
