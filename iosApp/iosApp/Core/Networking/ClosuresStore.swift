import Foundation
import Combine
import WidgetKit

/// Station closures for the app UI (3.0.2).
///
/// Fetches `GET /api/closures` (ETag aware) and keeps the last good payload in
/// the shared `ClosureSnapshot`, so the board, the map, the planner and the
/// widgets all agree. Offline the snapshot falls back to the bundled seed
/// `closures.json`. The window itself is always evaluated locally with
/// `StationClosures.effect`, so a closure scheduled for 08:00 flips at 08:00
/// even without a network.
@MainActor
final class ClosuresStore: ObservableObject {
    static let shared = ClosuresStore()

    @Published private(set) var closures: [StationClosure]
    /// Bumped every minute while observed, so views re-evaluate the window.
    @Published private(set) var tick = SyrmosClock.now

    private let apiURL = URL(string: "https://api-syrmos.peterdsp.dev/api/closures")!
    private let etagKey = "syrmos.closures.etag"
    private var lastFetch: Date?
    private var timer: AnyCancellable?

    private init() {
        #if DEBUG
        // Runtime-verification hook: point the app at a local closures file with
        // SIMCTL_CHILD_SYRMOS_CLOSURES_FIXTURE=/path/closures.json.
        if let path = ProcessInfo.processInfo.environment["SYRMOS_CLOSURES_FIXTURE"],
           let data = FileManager.default.contents(atPath: path),
           let payload = try? JSONDecoder().decode(StationClosuresPayload.self, from: data) {
            ClosureSnapshot.replace(payload.closures)
            closures = payload.closures
            fixtureMode = true
            startTicking()
            return
        }
        #endif
        closures = ClosureSnapshot.current()
        startTicking()
    }

    private var fixtureMode = false

    private func startTicking() {
        timer = Timer.publish(every: 60, on: .main, in: .common).autoconnect()
            .sink { [weak self] now in self?.tick = now }
    }

    var nowEpoch: Int64 { Int64(SyrmosClock.now.timeIntervalSince1970) }

    #if DEBUG
    /// Swap in a closure list without the network (render checks and tests).
    func overrideForTesting(_ list: [StationClosure]) {
        ClosureSnapshot.replace(list)
        closures = list
        fixtureMode = true
    }
    #endif

    /// Refresh from the API. Throttled to once a minute (the server caches 60 s).
    func refresh(force: Bool = false) async {
        if fixtureMode { return }
        if !force, let lastFetch, SyrmosClock.now.timeIntervalSince(lastFetch) < 60 { return }
        lastFetch = SyrmosClock.now
        var request = URLRequest(url: apiURL)
        request.timeoutInterval = 10
        request.cachePolicy = .reloadIgnoringLocalCacheData
        if let etag = UserDefaults.standard.string(forKey: etagKey), !closures.isEmpty {
            request.setValue(etag, forHTTPHeaderField: "If-None-Match")
        }
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              let http = response as? HTTPURLResponse else { return }
        if http.statusCode == 304 { return }
        guard http.statusCode == 200, let decoded = ClosureSnapshot.store(payloadData: data) else { return }
        if let etag = http.value(forHTTPHeaderField: "ETag") {
            UserDefaults.standard.set(etag, forKey: etagKey)
        }
        if decoded != closures {
            closures = decoded
            WidgetCenter.shared.reloadAllTimelines()
        }
    }

    func effect(stationId: String, lineId: String? = nil, at epoch: Int64? = nil) -> ClosureEffect {
        StationClosures.effect(closures, stationId: stationId, lineId: lineId, now: epoch ?? nowEpoch)
    }

    func effect(stationIds: [String], lineId: String? = nil, at epoch: Int64? = nil) -> ClosureEffect {
        StationClosures.effect(closures, stationIds: stationIds, lineId: lineId, now: epoch ?? nowEpoch)
    }

    func closure(for effect: ClosureEffect) -> StationClosure? {
        StationClosures.closure(id: effect.closureId ?? effect.upcomingClosureId, in: closures)
    }

    /// Station ids where nobody can board right now (closed or pass-through),
    /// the set the map greys.
    func closedStationIds(at epoch: Int64? = nil) -> Set<String> {
        let now = epoch ?? nowEpoch
        var out = Set<String>()
        for c in closures where StationClosures.isActive(c, now: now) {
            for sid in c.stationIds where !StationClosures.effect(closures, stationId: sid, lineId: nil, now: now).canBoard {
                out.insert(sid)
            }
        }
        return out
    }
}
