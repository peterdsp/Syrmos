import Combine
import Foundation

/// Locally-owned saved journeys for iOS (S08 / J05), the Swift peer of web
/// `SyrmosSavedJourneys` and Kotlin `SavedJourneyStore`. No account, no server:
/// the whole ordered list is one versioned root persisted in UserDefaults under
/// `syrmos.saved-journeys.v1`. The pure ops and the container shape mirror the
/// shared golden fixture fixtures/journeys/saved.json exactly, including the HARD
/// de-dup invariant: at most one saved journey per (fromId,toId). Re-saving a pair
/// updates it in place (keeping its id), refreshes label/createdAt/preferences, and
/// moves it to the top, never adding a duplicate row.

struct SavedJourneyPreferences: Codable, Equatable {
    var ranking: String = "fastest"
    var accessibilityPreference: String = "none"
}

struct SavedJourney: Codable, Equatable, Identifiable {
    let id: String
    let fromId: String
    let toId: String
    /// ISO-8601 instant; kept as a string so the persisted shape matches the
    /// cross-client contract (the value itself is never invented).
    var createdAt: String?
    var label: String?
    var preferences: SavedJourneyPreferences
    var schemaVersion: Int

    init(
        id: String, fromId: String, toId: String, createdAt: String? = nil,
        label: String? = nil, preferences: SavedJourneyPreferences = SavedJourneyPreferences(),
        schemaVersion: Int = SavedJourneyContract.schemaVersion
    ) {
        self.id = id
        self.fromId = fromId
        self.toId = toId
        self.createdAt = createdAt
        // A blank label is no label; never store whitespace as a name.
        self.label = SavedJourneyContract.cleanLabel(label)
        self.preferences = preferences
        self.schemaVersion = schemaVersion
    }
}

/// The persisted list root. `schemaVersion` first so the encoded JSON key order
/// matches the web/Kotlin container.
private struct SavedJourneysRoot: Codable {
    var schemaVersion: Int
    var savedJourneys: [SavedJourney]
}

/// Versioning + pure list operations. Kept free of persistence so they are unit
/// testable against the shared fixture.
enum SavedJourneyContract {
    static let schemaVersion = 1
    static let storageKey = "syrmos.saved-journeys.v1"

    enum DecodeResult {
        case ok([SavedJourney])
        case unsupported(foundVersion: Int)
        case corrupt(reason: String)
    }

    static func cleanLabel(_ label: String?) -> String? {
        guard let t = label?.trimmingCharacters(in: .whitespacesAndNewlines), !t.isEmpty else { return nil }
        return t
    }

    /// Prepend a save, de-duplicating by (fromId,toId).
    static func save(_ list: [SavedJourney], _ entry: SavedJourney) -> [SavedJourney] {
        let existing = list.first { $0.fromId == entry.fromId && $0.toId == entry.toId }
        let merged: SavedJourney
        if let existing = existing {
            merged = SavedJourney(
                id: existing.id, fromId: existing.fromId, toId: existing.toId,
                createdAt: entry.createdAt, label: entry.label, preferences: entry.preferences,
            )
        } else {
            merged = entry
        }
        let without = list.filter { !($0.fromId == entry.fromId && $0.toId == entry.toId) }
        return [merged] + without
    }

    static func rename(_ list: [SavedJourney], id: String, label: String?) -> [SavedJourney] {
        let clean = cleanLabel(label)
        return list.map { s in
            guard s.id == id else { return s }
            var copy = s
            copy.label = clean
            return copy
        }
    }

    static func remove(_ list: [SavedJourney], id: String) -> [SavedJourney] {
        list.filter { $0.id != id }
    }

    /// Reorder to an explicit id sequence; ids not present are dropped, unknown ignored.
    static func reorder(_ list: [SavedJourney], order: [String]) -> [SavedJourney] {
        let byId = Dictionary(list.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        return order.compactMap { byId[$0] }
    }

    static func encode(_ list: [SavedJourney]) -> String {
        let root = SavedJourneysRoot(schemaVersion: schemaVersion, savedJourneys: list)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.withoutEscapingSlashes]
        guard let data = try? encoder.encode(root), let s = String(data: data, encoding: .utf8) else {
            return "{\"schemaVersion\":\(schemaVersion),\"savedJourneys\":[]}"
        }
        return s
    }

    /// Atomic, versioned decode. An empty blob is a fresh valid empty list; a newer
    /// schema is `unsupported` (keep the blob); anything unparseable is `corrupt`.
    static func decode(_ raw: String?) -> DecodeResult {
        guard let raw = raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return .ok([])
        }
        guard let data = raw.data(using: .utf8) else { return .corrupt(reason: "not utf8") }
        // Peek the version first so a future schema is reported, not silently coerced.
        if let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let v = obj["schemaVersion"] as? Int, v > schemaVersion {
            return .unsupported(foundVersion: v)
        }
        guard let root = try? JSONDecoder().decode(SavedJourneysRoot.self, from: data) else {
            return .corrupt(reason: "decode failed")
        }
        return .ok(root.savedJourneys)
    }
}

/// UserDefaults-backed, observable store the Plan UI binds to.
@MainActor
final class SavedJourneysStore: ObservableObject {
    static let shared = SavedJourneysStore()

    @Published private(set) var items: [SavedJourney] = []
    /// True when the persisted blob could not be decoded (newer schema / corrupt);
    /// the raw blob is preserved until the next successful save.
    private(set) var lastDecodeFailed = false

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        refresh()
    }

    func refresh() {
        switch SavedJourneyContract.decode(defaults.string(forKey: SavedJourneyContract.storageKey)) {
        case .ok(let list): lastDecodeFailed = false; items = list
        default: lastDecodeFailed = true; items = []
        }
    }

    private func persist(_ list: [SavedJourney]) {
        defaults.set(SavedJourneyContract.encode(list), forKey: SavedJourneyContract.storageKey)
        lastDecodeFailed = false
        items = list
    }

    func save(_ entry: SavedJourney) { persist(SavedJourneyContract.save(items, entry)) }
    func rename(id: String, label: String?) { persist(SavedJourneyContract.rename(items, id: id, label: label)) }
    func remove(id: String) { persist(SavedJourneyContract.remove(items, id: id)) }
    func reorder(_ order: [String]) { persist(SavedJourneyContract.reorder(items, order: order)) }

    func isSaved(fromId: String, toId: String) -> Bool {
        items.contains { $0.fromId == fromId && $0.toId == toId }
    }

    /// Time-based id; de-dup is by (fromId,toId), never by this id.
    func newId() -> String { "sj-" + String(Int(Date().timeIntervalSince1970 * 1000), radix: 36) }
}


/// Persistent live GO session for iOS (S06), the Swift peer of web
/// `SyrmosActiveJourney` and the shared Kotlin `ActiveJourney` + `ActiveJourneyStore`.
/// A single, locally-owned live journey: no account, no server. One versioned blob
/// lives in UserDefaults under `syrmos.active-journey.v1` so a trip in progress
/// survives the app being killed or navigated away, and can be resumed where it left
/// off. The session is anchored by ids (`legId` + `confirmedStopId`), never indices,
/// so a resumed trip lands on the exact same stop even after names were re-resolved
/// in another language. The itinerary snapshot is FROZEN for the life of the session.
///
/// The wire shape mirrors the shared `ActiveJourney` contract (same keys / wire
/// values) so all three clients write the same-shaped blob; the pure lifecycle
/// mirrors `ActiveJourneyStore` and is unit-tested against the same cases.

/// One ride leg of the frozen snapshot (a minimal `Leg`).
struct GoLegSnapshot: Codable, Equatable {
    let id: String
    let kind: String
    let fromId: String
    let toId: String
    let lineId: String?
    let orderedStopIds: [String]
    let serviceDate: String
}

struct GoFeasibility: Codable, Equatable {
    let status: String
    let explanationCode: String
}

/// The frozen itinerary snapshot (a minimal `JourneyOption`).
struct GoOptionSnapshot: Codable, Equatable {
    let id: String
    let requestId: String
    let legs: [GoLegSnapshot]
    let transferCount: Int
    let feasibility: GoFeasibility
}

struct GoAlertPreferences: Codable, Equatable {
    var leaveByReminder: Bool = false
    var getOffAlert: Bool = false
    var disruptionAlert: Bool = false
}

/// The persisted live session. Field names/order mirror the shared `ActiveJourney`.
struct GoActiveJourney: Codable, Equatable {
    var schemaVersion: Int = GoActiveJourneyContract.schemaVersion
    var id: String
    var revision: Int
    var itinerarySnapshot: GoOptionSnapshot
    var phase: String
    var legId: String
    var startedAt: String
    var updatedAt: String
    var confirmedStopId: String?
    var progressSource: String
    var progressObservedAt: String?
    var alertedEventIds: [String]
    var alertPreferences: GoAlertPreferences
}

/// Versioning, (de)serialization and the pure session lifecycle. Free of
/// persistence so it is unit-testable, mirroring the shared `ActiveJourneyStore`.
enum GoActiveJourneyContract {
    static let schemaVersion = 1
    static let storageKey = "syrmos.active-journey.v1"

    // Phase wire values (match the shared JourneyPhase @SerialName values).
    static let phaseReadyToBoard = "readyToBoard"
    static let phaseRiding = "riding"
    static let phaseAlightSoon = "alightSoon"
    static let phaseTransfer = "transfer"
    static let phaseArrived = "arrived"
    static let phaseEnded = "ended"

    private static func iso(_ date: Date) -> String {
        let f = ISO8601DateFormatter()
        return f.string(from: date)
    }

    private static func rideLegs(_ option: GoOptionSnapshot) -> [GoLegSnapshot] {
        option.legs.filter { $0.kind == "ride" }
    }

    /// Map the engine instruction at `pos` to the persisted phase.
    static func phaseFor(_ journey: GuidanceJourney, _ pos: GuidancePosition) -> String {
        switch (try? JourneyGuidance.at(journey, pos)) {
        case .board: return phaseReadyToBoard
        case .ride: return phaseRiding
        case .getOffNext: return phaseAlightSoon
        case .transfer: return phaseTransfer
        case .arrived: return phaseArrived
        default: return phaseRiding
        }
    }

    private static func stopId(_ journey: GuidanceJourney, _ pos: GuidancePosition) -> String? {
        guard journey.legs.indices.contains(pos.legIndex) else { return nil }
        let stops = journey.legs[pos.legIndex].stops
        return stops.indices.contains(pos.stopIndex) ? stops[pos.stopIndex].id : nil
    }

    /// Build the frozen snapshot from a GuidanceJourney (iOS starts from guidance).
    static func snapshot(from journey: GuidanceJourney, serviceDate: String) -> GoOptionSnapshot {
        let legs = journey.legs.enumerated().map { (i, leg) -> GoLegSnapshot in
            GoLegSnapshot(
                id: "leg-\(i)", kind: "ride",
                fromId: leg.stops.first?.id ?? "", toId: leg.stops.last?.id ?? "",
                lineId: leg.lineId, orderedStopIds: leg.stops.map { $0.id }, serviceDate: serviceDate)
        }
        let rid = "go-" + String(Int(Date().timeIntervalSince1970 * 1000), radix: 36)
        return GoOptionSnapshot(
            id: rid, requestId: rid, legs: legs,
            transferCount: max(0, legs.count - 1),
            feasibility: GoFeasibility(status: "unknown", explanationCode: "go_session"))
    }

    /// Rebuild a GuidanceJourney from a snapshot, re-resolving names in `language`
    /// (the resume seam). Mirrors `GuidanceJourney.from(_:language:)`.
    static func guidance(from option: GoOptionSnapshot, language: AppLanguage) -> GuidanceJourney {
        let name = GuidanceJourney.stationName(language: language)
        let legs = rideLegs(option).map { leg -> GuidanceLeg in
            let stops = leg.orderedStopIds.map { GuidanceStop(id: $0, name: name($0)) }
            return GuidanceLeg(lineId: leg.lineId ?? "", towards: stops.last?.name ?? "", stops: stops)
        }
        return GuidanceJourney(legs: legs)
    }

    /// Begin a session at the origin (leg 0, board stop).
    static func start(journey: GuidanceJourney, language: AppLanguage, now: Date = Date()) -> GoActiveJourney {
        let serviceDate = String(iso(now).prefix(10))
        let option = snapshot(from: journey, serviceDate: serviceDate)
        let pos = GuidancePosition(legIndex: 0, stopIndex: 0)
        let legId = rideLegs(option).first?.id ?? option.legs.first?.id ?? option.id
        let ts = iso(now)
        return GoActiveJourney(
            id: option.id, revision: 0, itinerarySnapshot: option,
            phase: phaseFor(journey, pos), legId: legId,
            startedAt: ts, updatedAt: ts,
            confirmedStopId: stopId(journey, pos),
            progressSource: "manual", progressObservedAt: ts,
            alertedEventIds: [], alertPreferences: GoAlertPreferences())
    }

    /// The engine position for a persisted session, mapping ids back to indices.
    /// An unknown legId/confirmedStopId resolves to the origin of its leg.
    static func positionOf(_ active: GoActiveJourney, _ journey: GuidanceJourney) -> GuidancePosition {
        guard !journey.legs.isEmpty else { return GuidancePosition(legIndex: 0, stopIndex: 0) }
        let rides = rideLegs(active.itinerarySnapshot)
        var legIndex = rides.firstIndex { $0.id == active.legId } ?? 0
        if !journey.legs.indices.contains(legIndex) { legIndex = 0 }
        let stops = journey.legs[legIndex].stops
        var stopIndex = 0
        if let cs = active.confirmedStopId, let idx = stops.firstIndex(where: { $0.id == cs }) { stopIndex = idx }
        stopIndex = min(max(0, stopIndex), max(0, stops.count - 1))
        return GuidancePosition(legIndex: legIndex, stopIndex: stopIndex)
    }

    /// Rewrite the session onto `pos`, refreshing legId/confirmedStopId/phase/clock.
    static func withPosition(_ active: GoActiveJourney, _ journey: GuidanceJourney, _ pos: GuidancePosition,
                             now: Date = Date(), source: String = "manual") -> GoActiveJourney {
        let rides = rideLegs(active.itinerarySnapshot)
        let legId = rides.indices.contains(pos.legIndex) ? rides[pos.legIndex].id : active.legId
        var copy = active
        copy.phase = phaseFor(journey, pos)
        copy.legId = legId
        copy.confirmedStopId = stopId(journey, pos)
        copy.progressSource = source
        copy.progressObservedAt = iso(now)
        copy.updatedAt = iso(now)
        return copy
    }

    static func advance(_ active: GoActiveJourney, _ journey: GuidanceJourney,
                        now: Date = Date(), source: String = "manual") -> GoActiveJourney {
        let next = JourneyGuidance.advance(journey, positionOf(active, journey))
        return withPosition(active, journey, next, now: now, source: source)
    }

    static func back(_ active: GoActiveJourney, _ journey: GuidanceJourney, now: Date = Date()) -> GoActiveJourney {
        let cur = positionOf(active, journey)
        var prev = cur
        if cur.stopIndex > 0 {
            prev = GuidancePosition(legIndex: cur.legIndex, stopIndex: cur.stopIndex - 1)
        } else if cur.legIndex > 0 {
            let p = cur.legIndex - 1
            prev = GuidancePosition(legIndex: p, stopIndex: max(0, journey.legs[p].stops.count - 1))
        }
        return withPosition(active, journey, prev, now: now, source: "manual")
    }

    static func end(_ active: GoActiveJourney, now: Date = Date()) -> GoActiveJourney {
        var copy = active
        copy.phase = phaseEnded
        copy.updatedAt = iso(now)
        return copy
    }

    static func isEnded(_ active: GoActiveJourney) -> Bool { active.phase == phaseEnded }
    static func isResumable(_ active: GoActiveJourney) -> Bool { active.phase != phaseEnded }

    static func encode(_ active: GoActiveJourney) -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.withoutEscapingSlashes]
        guard let data = try? encoder.encode(active), let s = String(data: data, encoding: .utf8) else { return "" }
        return s
    }

    enum DecodeResult {
        case ok(GoActiveJourney?)
        case unsupported(foundVersion: Int)
        case corrupt(reason: String)
    }

    static func decode(_ raw: String?) -> DecodeResult {
        guard let raw = raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return .ok(nil) }
        guard let data = raw.data(using: .utf8) else { return .corrupt(reason: "not utf8") }
        if let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let v = obj["schemaVersion"] as? Int, v > schemaVersion {
            return .unsupported(foundVersion: v)
        }
        guard let value = try? JSONDecoder().decode(GoActiveJourney.self, from: data) else {
            return .corrupt(reason: "decode failed")
        }
        return .ok(value)
    }
}

/// UserDefaults-backed, observable store the GO UI and the resume banner bind to.
@MainActor
final class GoActiveJourneyStore: ObservableObject {
    static let shared = GoActiveJourneyStore()

    /// The live, resumable session or nil (an ENDED / absent session reads as nil).
    @Published private(set) var active: GoActiveJourney?
    private(set) var lastDecodeFailed = false

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        refresh()
    }

    func refresh() { active = load() }

    private func load() -> GoActiveJourney? {
        switch GoActiveJourneyContract.decode(defaults.string(forKey: GoActiveJourneyContract.storageKey)) {
        case .ok(let value):
            lastDecodeFailed = false
            if let v = value, GoActiveJourneyContract.isResumable(v) { return v }
            return nil
        default:
            lastDecodeFailed = true
            return nil
        }
    }

    /// Persist and publish a live session. An ENDED session is not a live session,
    /// so setting one clears the store (mirrors what `load` returns on next read).
    func set(_ journey: GoActiveJourney) {
        guard GoActiveJourneyContract.isResumable(journey) else { clear(); return }
        defaults.set(GoActiveJourneyContract.encode(journey), forKey: GoActiveJourneyContract.storageKey)
        lastDecodeFailed = false
        active = journey
    }

    /// End the live session: nothing to resume afterwards.
    func clear() {
        defaults.removeObject(forKey: GoActiveJourneyContract.storageKey)
        lastDecodeFailed = false
        active = nil
    }

    /// Start (overwriting any prior) a fresh session from a GuidanceJourney.
    @discardableResult
    func startSession(journey: GuidanceJourney, language: AppLanguage) -> GoActiveJourney {
        let session = GoActiveJourneyContract.start(journey: journey, language: language)
        set(session)
        return session
    }
}
