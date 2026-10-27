import Foundation
import UIKit
import UserNotifications
import Combine

// Syrmos 3.0.2 announcements from Syrmos ("broadcasts") and APNs registration.
//
// Delivery is push first (APNs straight from the Syrmos server, no third-party
// SDK) with a polling fallback: on app open and background refresh the client
// fetches `GET /api/broadcasts?since=` and posts one local notification for each
// relevant, unexpired broadcast that push has not already delivered. Dedupe is
// by broadcast id. Nothing personal is sent: the registration carries only the
// APNs token, language, topics and app version (see docs/3.0.2-CONTRACTS.md).

struct Broadcast: Codable, Equatable, Identifiable {
    var id: String
    var title: LocalizedText?
    var body: LocalizedText?
    var audience: String?
    var sentAt: String?
    var sentAtEpochSeconds: Int64
    var expiresAt: String?
    var expiresAtEpochSeconds: Int64?
    var deepLink: String?
    var closureId: String?
}

struct BroadcastsPayload: Codable {
    var schemaVersion: Int?
    var broadcasts: [Broadcast]
}

/// The pure rules behind broadcasts: audience matching, expiry, which polled
/// broadcasts deserve a local notification, push topics, and deep links.
enum BroadcastRules {
    static func isExpired(_ b: Broadcast, now: Int64) -> Bool {
        guard let expires = b.expiresAtEpochSeconds else { return false }
        return now >= expires
    }

    /// `all`, a favourite line/station, or the selected region.
    static func isRelevant(audience: String?, favouriteStationIds: Set<String>, favouriteLineIds: Set<String>, region: String) -> Bool {
        let a = (audience ?? "all").trimmingCharacters(in: .whitespaces)
        if a.isEmpty || a == "all" { return true }
        if a.hasPrefix("line:") { return favouriteLineIds.contains(String(a.dropFirst(5))) }
        if a.hasPrefix("station:") { return favouriteStationIds.contains(String(a.dropFirst(8))) }
        if a.hasPrefix("region:") { return String(a.dropFirst(7)) == region }
        return false
    }

    /// Polled broadcasts that should raise a local notification now: relevant,
    /// not expired, not delivered by push, not already notified. Newest first,
    /// capped so a long offline gap cannot flood the lock screen.
    static func toNotify(
        _ fetched: [Broadcast], delivered: Set<String>, notified: Set<String>, now: Int64,
        isRelevant: (Broadcast) -> Bool, cap: Int = 3
    ) -> [Broadcast] {
        var seen = Set<String>()
        let fresh = fetched
            .sorted { ($0.sentAtEpochSeconds, $0.id) > ($1.sentAtEpochSeconds, $1.id) }
            .filter { b in
                guard seen.insert(b.id).inserted else { return false }
                return !delivered.contains(b.id) && !notified.contains(b.id)
                    && !isExpired(b, now: now) && isRelevant(b)
            }
        return Array(fresh.prefix(cap))
    }

    /// The next `since` cursor: the newest sentAt seen so far.
    static func nextSince(current: Int64, fetched: [Broadcast]) -> Int64 {
        max(current, fetched.map(\.sentAtEpochSeconds).max() ?? current)
    }

    /// Push topics: "all", the region, then favourite lines and stations. Sorted
    /// within each group so the registration signature is stable.
    static func topics(favouriteStationIds: Set<String>, favouriteLineIds: Set<String>, region: String) -> [String] {
        var out = ["all"]
        if region == "athens" || region == "thessaloniki" { out.append("region.\(region)") }
        out += favouriteLineIds.sorted().map { "line.\($0)" }
        out += favouriteStationIds.sorted().map { "station.\($0)" }
        return out
    }

    static func hexToken(_ data: Data) -> String {
        data.map { String(format: "%02x", $0) }.joined()
    }

    /// `syrmos://station/M2_SYN` and `syrmos://line/M3`.
    static func destination(fromDeepLink link: String?) -> DeepLinkRouter.Destination? {
        guard let link, let url = URL(string: link), url.scheme == "syrmos", let host = url.host else { return nil }
        let id = url.pathComponents.filter { $0 != "/" }.first ?? ""
        guard !id.isEmpty else { return nil }
        switch host {
        case "station": return .station(id: id)
        case "line": return .line(id: id)
        default: return nil
        }
    }

    /// Broadcast ids carried by notifications (delivered or pending), so a push
    /// that arrived while the app was in the background counts as delivered.
    static func broadcastIds(fromUserInfos infos: [[AnyHashable: Any]]) -> Set<String> {
        Set(infos.compactMap { ($0["broadcastId"] as? String).flatMap { $0.isEmpty ? nil : $0 } })
    }

    /// Whether to re-register: token/lang/topics changed, or a day has passed.
    static func needsRegistration(previousSignature: String?, signature: String, lastRegistered: Int64?, now: Int64) -> Bool {
        if previousSignature != signature { return true }
        guard let lastRegistered else { return true }
        return now - lastRegistered >= 24 * 3600
    }
}

/// Favourite stations (the shared UserDefaults set) and the lines they serve.
enum FavouriteStations {
    static let key = "syrmos.favorite.stations"
    static var stationIds: Set<String> { Set(UserDefaults.standard.stringArray(forKey: key) ?? []) }
    @MainActor
    static var lineIds: Set<String> {
        let ids = stationIds
        guard !ids.isEmpty else { return [] }
        var out = Set<String>()
        for line in SyrmosData.lines where SyrmosData.stations(for: line.id).contains(where: { ids.contains($0.id) }) {
            out.insert(line.id)
        }
        return out
    }
    static var region: String { UserDefaults.standard.string(forKey: "syrmos.map.defaultRegion") ?? "athens" }
}

@MainActor
final class BroadcastStore: ObservableObject {
    static let shared = BroadcastStore()

    /// Unexpired, relevant broadcasts for the Home banner, newest first.
    @Published private(set) var active: [Broadcast] = []

    private let apiBase = "https://api-syrmos.peterdsp.dev"
    private let cacheKey = "syrmos.broadcasts.cache.v1"
    private let sinceKey = "syrmos.broadcasts.since"
    private let deliveredKey = "syrmos.broadcasts.delivered"
    private let notifiedKey = "syrmos.broadcasts.notified"
    private let dismissedKey = "syrmos.broadcasts.dismissed"

    private init() {
        if let data = UserDefaults.standard.data(forKey: cacheKey),
           let list = try? JSONDecoder().decode([Broadcast].self, from: data) {
            active = filterActive(list)
        }
    }

    private var now: Int64 { Int64(SyrmosClock.now.timeIntervalSince1970) }

    private func ids(_ key: String) -> Set<String> { Set(UserDefaults.standard.stringArray(forKey: key) ?? []) }
    private func save(_ set: Set<String>, _ key: String) {
        // Keep the newest few hundred ids; broadcasts are rare and short-lived.
        UserDefaults.standard.set(Array(set.suffix(300)), forKey: key)
    }

    func recordDelivered(_ id: String) {
        var d = ids(deliveredKey); d.insert(id); save(d, deliveredKey)
    }

    /// Record every broadcast already in Notification Center (delivered or
    /// pending) as delivered. A push received in the background never passes
    /// through willPresent or a tap, so without this the polling fallback would
    /// post it a second time. Runs on launch and before every poll.
    func syncDeliveredFromNotificationCenter() async {
        let center = UNUserNotificationCenter.current()
        let delivered = await center.deliveredNotifications().map { $0.request.content.userInfo }
        let pending = await center.pendingNotificationRequests().map { $0.content.userInfo }
        let ids = BroadcastRules.broadcastIds(fromUserInfos: delivered + pending)
        guard !ids.isEmpty else { return }
        var d = self.ids(deliveredKey)
        let before = d.count
        d.formUnion(ids)
        if d.count != before { save(d, deliveredKey) }
    }

    func dismiss(_ id: String) {
        var d = ids(dismissedKey); d.insert(id); save(d, dismissedKey)
        active.removeAll { $0.id == id }
    }

    private func relevant(_ b: Broadcast) -> Bool {
        BroadcastRules.isRelevant(audience: b.audience, favouriteStationIds: FavouriteStations.stationIds,
                                  favouriteLineIds: FavouriteStations.lineIds, region: FavouriteStations.region)
    }

    private func filterActive(_ list: [Broadcast]) -> [Broadcast] {
        let dismissed = ids(dismissedKey)
        return list
            .filter { !BroadcastRules.isExpired($0, now: now) && !dismissed.contains($0.id) && relevant($0) }
            .sorted { $0.sentAtEpochSeconds > $1.sentAtEpochSeconds }
    }

    /// Poll for broadcasts since the last cursor; raise local notifications for
    /// those push did not deliver. Safe to call from app open and BG refresh.
    func poll() async {
        let since = UserDefaults.standard.object(forKey: sinceKey) as? Int64 ?? (now - 48 * 3600)
        guard let url = URL(string: "\(apiBase)/api/broadcasts?since=\(since)") else { return }
        var request = URLRequest(url: url)
        request.timeoutInterval = 10
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let payload = try? JSONDecoder().decode(BroadcastsPayload.self, from: data) else { return }
        let fetched = payload.broadcasts

        var cached: [Broadcast] = []
        if let d = UserDefaults.standard.data(forKey: cacheKey), let list = try? JSONDecoder().decode([Broadcast].self, from: d) {
            cached = list
        }
        var byId = Dictionary(cached.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        for b in fetched { byId[b.id] = b }
        let merged = byId.values.filter { !BroadcastRules.isExpired($0, now: now) }
        if let encoded = try? JSONEncoder().encode(Array(merged)) { UserDefaults.standard.set(encoded, forKey: cacheKey) }
        active = filterActive(Array(merged))
        UserDefaults.standard.set(BroadcastRules.nextSince(current: since, fetched: fetched), forKey: sinceKey)

        guard NotificationPreferences.broadcastsEnabled else { return }
        await syncDeliveredFromNotificationCenter()
        let toNotify = BroadcastRules.toNotify(fetched, delivered: ids(deliveredKey), notified: ids(notifiedKey),
                                               now: now, isRelevant: relevant)
        guard !toNotify.isEmpty else { return }
        var notified = ids(notifiedKey)
        let lang = LocalizationManager.shared.language
        for b in toNotify {
            let content = UNMutableNotificationContent()
            content.title = b.title?.pick(lang) ?? BroadcastCopy.inboxTitle(lang)
            content.body = b.body?.pick(lang) ?? ""
            content.sound = .default
            content.threadIdentifier = "syrmos.broadcast"
            content.categoryIdentifier = "SYRMOS_BROADCAST"
            content.userInfo = ["broadcastId": b.id, "deepLink": b.deepLink ?? ""]
            try? await UNUserNotificationCenter.current().add(
                UNNotificationRequest(identifier: "syrmos.broadcast.\(b.id)", content: content, trigger: nil)
            )
            notified.insert(b.id)
        }
        save(notified, notifiedKey)
    }
}

/// APNs device registration with the Syrmos server.
@MainActor
final class PushRegistrar {
    static let shared = PushRegistrar()

    private let tokenKey = "syrmos.push.token"
    private let signatureKey = "syrmos.push.signature"
    private let registeredAtKey = "syrmos.push.registeredAt"
    private let apiBase = "https://api-syrmos.peterdsp.dev"
    private var languageObserver: AnyCancellable?

    static var environment: String {
        #if DEBUG
        return "sandbox"
        #else
        return "production"
        #endif
    }

    private init() {
        languageObserver = LocalizationManager.shared.$language.dropFirst().sink { _ in
            Task { @MainActor in PushRegistrar.shared.syncIfNeeded() }
        }
    }

    /// Ask iOS for an APNs token when announcements are on. The token arrives in
    /// AppDelegate.didRegisterForRemoteNotificationsWithDeviceToken.
    func start() {
        guard NotificationPreferences.broadcastsEnabled, !SyrmosClock.isPinned else { return }
        UIApplication.shared.registerForRemoteNotifications()
        syncIfNeeded()
    }

    func didReceive(token data: Data) {
        UserDefaults.standard.set(BroadcastRules.hexToken(data), forKey: tokenKey)
        syncIfNeeded()
    }

    private var token: String? { UserDefaults.standard.string(forKey: tokenKey) }

    private var topics: [String] {
        BroadcastRules.topics(favouriteStationIds: FavouriteStations.stationIds,
                              favouriteLineIds: FavouriteStations.lineIds, region: FavouriteStations.region)
    }

    /// Re-register on token, language or favourites change, else at most daily.
    func syncIfNeeded() {
        guard NotificationPreferences.broadcastsEnabled, let token else { return }
        let lang = LocalizationManager.shared.language.rawValue
        let topics = topics
        let signature = ([token, lang, Self.environment] + topics).joined(separator: "|")
        let now = Int64(SyrmosClock.now.timeIntervalSince1970)
        let last = UserDefaults.standard.object(forKey: registeredAtKey) as? Int64
        guard BroadcastRules.needsRegistration(previousSignature: UserDefaults.standard.string(forKey: signatureKey),
                                               signature: signature, lastRegistered: last, now: now) else { return }
        let body: [String: Any] = [
            "token": token, "platform": "ios", "environment": Self.environment, "lang": lang,
            "topics": topics,
            "appVersion": Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "",
        ]
        Task {
            if await post("/api/push/register", body) {
                UserDefaults.standard.set(signature, forKey: signatureKey)
                UserDefaults.standard.set(now, forKey: registeredAtKey)
            }
        }
    }

    /// "Announcements from Syrmos" turned off: drop the server registration.
    func unregister() {
        UserDefaults.standard.removeObject(forKey: signatureKey)
        UserDefaults.standard.removeObject(forKey: registeredAtKey)
        guard let token else { return }
        Task { _ = await post("/api/push/unregister", ["token": token]) }
    }

    private func post(_ path: String, _ body: [String: Any]) async -> Bool {
        guard let url = URL(string: apiBase + path),
              let data = try? JSONSerialization.data(withJSONObject: body) else { return false }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = data
        guard let (_, response) = try? await URLSession.shared.data(for: request),
              let http = response as? HTTPURLResponse else { return false }
        return (200..<300).contains(http.statusCode)
    }
}
