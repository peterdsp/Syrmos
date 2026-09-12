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
