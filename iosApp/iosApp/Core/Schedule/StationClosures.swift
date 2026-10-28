import Foundation

// Syrmos 3.0.2 station closures (iOS mirror).
//
// Mirrors the server projector (`syrmos_admin/closures.py`), Kotlin
// `StationClosures` and web exactly. One pure rule decides, for a (station, line)
// at an epoch second, whether a closure is active, what a rider can still do
// there (board / alight), and which closure is the one to show. Clients evaluate
// the window themselves, so an 08:00 closure flips at 08:00 even offline.
//
// Covered case-for-case by fixtures/closures/effect.json (StationClosuresTests).

/// `{"en": ..., "el": ..., "sq": ..., "it": ...}`; any key may be missing.
struct LocalizedText: Codable, Equatable, Hashable {
    var en: String?
    var el: String?
    var sq: String?
    var it: String?

    /// The app language, then English, then Greek. Nil when every value is blank.
    func pick(_ language: AppLanguage) -> String? {
        let own: String?
        switch language {
        case .english: own = en
        case .greek: own = el
        case .albanian: own = sq
        case .italian: own = it
        }
        for candidate in [own, en, el] {
            if let text = candidate?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty {
                return text
            }
        }
        return nil
    }
}

struct ReplacementBusStop: Codable, Equatable, Hashable {
    var name: LocalizedText?
    var lat: Double?
    var lon: Double?
}

struct ReplacementBus: Codable, Equatable, Hashable {
    var label: LocalizedText?
    var fromStop: LocalizedText?
    var toStop: LocalizedText?
    var headwayMinutes: Int?
    var stops: [ReplacementBusStop]?
}

/// One closure as served by `GET /api/closures` and the bundled `closures.json`.
struct StationClosure: Codable, Equatable, Hashable, Identifiable {
    var id: String
    var stationIds: [String]
    var lineIds: [String]?
    /// `closed` | `pass_through` | `exit_only` | `entry_only`.
    var mode: String
    var startsAt: String?
    var startsAtEpochSeconds: Int64
    var endsAt: String?
    var endsAtEpochSeconds: Int64?
    /// Informational only; the window is evaluated locally.
    var status: String?
    var reason: LocalizedText?
    var replacementBus: ReplacementBus?
    var source: String?
}

/// The `/api/closures` envelope (and the seed file's shape).
struct StationClosuresPayload: Codable {
    var schemaVersion: Int?
    var generatedAt: String?
    var closures: [StationClosure]
}

/// The effect of every relevant closure on one (station, line) at one instant.
struct ClosureEffect: Equatable {
    let active: Bool
    let mode: String?
    let canBoard: Bool
    let canAlight: Bool
    let closureId: String?
    let endsAtEpochSeconds: Int64?
    let upcomingClosureId: String?
    let upcomingStartsAtEpochSeconds: Int64?

    static let open = ClosureEffect(
        active: false, mode: nil, canBoard: true, canAlight: true, closureId: nil,
        endsAtEpochSeconds: nil, upcomingClosureId: nil, upcomingStartsAtEpochSeconds: nil
    )

    /// Trains do not stop for boarding here (closed or pass-through).
    var blocksBoarding: Bool { !canBoard }
}

enum StationClosures {
    static let modeOrder = ["closed", "pass_through", "exit_only", "entry_only"]
    static let upcomingWindowSeconds: Int64 = 24 * 3600

    static func isActive(_ c: StationClosure, now: Int64) -> Bool {
        if c.status == "draft" || c.status == "cancelled" { return false }
        if now < c.startsAtEpochSeconds { return false }
        guard let ends = c.endsAtEpochSeconds else { return true }
        return now < ends
    }

    static func applies(_ c: StationClosure, stationId: String, lineId: String?) -> Bool {
        guard c.stationIds.contains(stationId) else { return false }
        let lines = c.lineIds ?? []
        guard let lineId else { return true }
        return lines.isEmpty || lines.contains(lineId)
    }

    static func effect(_ closures: [StationClosure], stationId: String, lineId: String?, now: Int64) -> ClosureEffect {
        var active = closures.filter { applies($0, stationId: stationId, lineId: lineId) && isActive($0, now: now) }
        if active.isEmpty {
            let upcoming = closures
                .filter {
                    applies($0, stationId: stationId, lineId: lineId)
                        && $0.status != "draft" && $0.status != "cancelled"
                        && now < $0.startsAtEpochSeconds
                        && $0.startsAtEpochSeconds <= now + upcomingWindowSeconds
                }
                .sorted { ($0.startsAtEpochSeconds, $0.id) < ($1.startsAtEpochSeconds, $1.id) }
            return ClosureEffect(
                active: false, mode: nil, canBoard: true, canAlight: true, closureId: nil,
                endsAtEpochSeconds: nil,
                upcomingClosureId: upcoming.first?.id,
                upcomingStartsAtEpochSeconds: upcoming.first?.startsAtEpochSeconds
            )
        }
        let canBoard = active.allSatisfy { $0.mode == "entry_only" }
        let canAlight = active.allSatisfy { $0.mode == "exit_only" }
        var mode: String
        if !canBoard && !canAlight {
            let modes = Set(active.map(\.mode))
            mode = modeOrder.first { modes.contains($0) } ?? "closed"
            // exit_only + entry_only together leave nothing open.
            if mode == "exit_only" || mode == "entry_only" { mode = "closed" }
        } else {
            mode = active[0].mode
        }
        active.sort { (rank($0.mode), $0.id) < (rank($1.mode), $1.id) }
        let primary = active[0]
        // Latest end wins; any open-ended closure means "until further notice".
        let ends = active.map(\.endsAtEpochSeconds)
        let endsAt: Int64? = ends.contains { $0 == nil } ? nil : ends.compactMap { $0 }.max()
        return ClosureEffect(
            active: true, mode: mode, canBoard: canBoard, canAlight: canAlight,
            closureId: primary.id, endsAtEpochSeconds: endsAt,
            upcomingClosureId: nil, upcomingStartsAtEpochSeconds: nil
        )
    }

    /// The effect for a station that may carry several per-line ids (an
    /// interchange node): the most restrictive active effect across the ids, so a
    /// merged map node or a Home row greys when any of its platforms is closed.
    static func effect(_ closures: [StationClosure], stationIds: [String], lineId: String?, now: Int64) -> ClosureEffect {
        var best: ClosureEffect = .open
        var upcoming: ClosureEffect?
        for sid in stationIds {
            let e = effect(closures, stationId: sid, lineId: lineId, now: now)
            if e.active {
                if !best.active || rank(e.mode ?? "") < rank(best.mode ?? "") { best = e }
            } else if e.upcomingClosureId != nil {
                if upcoming == nil || (e.upcomingStartsAtEpochSeconds ?? .max) < (upcoming?.upcomingStartsAtEpochSeconds ?? .max) {
                    upcoming = e
                }
            }
        }
        return best.active ? best : (upcoming ?? .open)
    }

    private static func rank(_ mode: String) -> Int { modeOrder.firstIndex(of: mode) ?? modeOrder.count }

    static func closure(id: String?, in closures: [StationClosure]) -> StationClosure? {
        guard let id else { return nil }
        return closures.first { $0.id == id }
    }
}

/// The last known closure list, shared by the app, the widget extension and any
/// background thread (the planner and projector are not main-actor bound).
///
/// Source order: the App Group cache written by `ClosuresStore` after a
/// successful `/api/closures` fetch, then the bundled seed `closures.json`. The
/// App Group is used (not standard defaults) so the widgets, which run in their
/// own process, see the same closures and render a closed station as closed.
enum ClosureSnapshot {
    static let suite = "group.com.syrmosApp.ios"
    static let payloadKey = "closures.payload.v1"

    private static let lock = NSLock()
    // Guarded by `lock`.
    nonisolated(unsafe) private static var cached: [StationClosure]?

    static func current() -> [StationClosure] {
        lock.lock(); defer { lock.unlock() }
        if let cached { return cached }
        let loaded = loadPersisted() ?? loadBundled() ?? []
        cached = loaded
        return loaded
    }

    /// Decode and persist a payload. Returns the decoded closures, or nil when
    /// the bytes are not a valid payload (the previous snapshot is kept).
    @discardableResult
    static func store(payloadData data: Data) -> [StationClosure]? {
        guard let payload = try? JSONDecoder().decode(StationClosuresPayload.self, from: data) else { return nil }
        UserDefaults(suiteName: suite)?.set(data, forKey: payloadKey)
        replace(payload.closures)
        return payload.closures
    }

    /// Swap the in-memory snapshot without touching storage (debug fixtures, tests).
    static func replace(_ closures: [StationClosure]) {
        lock.lock(); cached = closures; lock.unlock()
    }

    private static func loadPersisted() -> [StationClosure]? {
        guard let data = UserDefaults(suiteName: suite)?.data(forKey: payloadKey) else { return nil }
        return (try? JSONDecoder().decode(StationClosuresPayload.self, from: data))?.closures
    }

    private static func loadBundled() -> [StationClosure]? {
        guard let url = Bundle.main.url(forResource: "closures", withExtension: "json", subdirectory: "seed-schedules-v2")
                ?? Bundle.main.url(forResource: "closures", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return nil }
        return (try? JSONDecoder().decode(StationClosuresPayload.self, from: data))?.closures
    }
}
