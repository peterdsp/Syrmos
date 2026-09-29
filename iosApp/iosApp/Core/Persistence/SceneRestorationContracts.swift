import Foundation

// Phase A of iOS scene state restoration.
// See docs/design/IOS-SCENE-STATE-RESTORATION-SCOPE.md.
//
// The app rebuilds its UIHostingController on every background return (a
// deliberate CAMetalLayer fix in SyrmosApp.swift), and a cold launch takes the
// same fresh-mount path, so in-view @State is discarded. Only UserDefaults
// backed state survives. These are the pure, versioned contracts that let each
// tab restore its "place" across that rebuild: encode to a single string,
// decode with versioning, and validate restored ids against the current seed.
//
// This file is Phase A only: primitives plus unit tests. Nothing reads or writes
// these yet, so there is no behaviour change. Phase B/C wire them into the tabs
// (persist on selection change, restore on mount). The shape mirrors
// SavedJourneyContract: a versioned root, a decode result, encode -> String,
// decode(String?) -> result. Foundation only, no SwiftUI or seed dependency, so
// the whole file is unit-testable in CI without a device.

/// A single restorable navigation push. Restoration keeps one level and only ids
/// the seed can validate: a station or line detail is worth restoring, an
/// arbitrary deep stack is not (scope section 5).
struct RestorablePush: Codable, Equatable {
    /// One of `RestorablePushKind`.
    var kind: String
    var id: String

    /// Whether this push still points at something that exists, using caller
    /// supplied predicates so this stays free of any seed dependency.
    func exists(stationExists: (String) -> Bool, lineExists: (String) -> Bool) -> Bool {
        switch kind {
        case RestorablePushKind.station: return stationExists(id)
        case RestorablePushKind.line: return lineExists(id)
        default: return false
        }
    }
}

enum RestorablePushKind {
    static let station = "station"
    static let line = "line"
}

/// Outcome of decoding a persisted restoration blob. `empty` is the normal fresh
/// state (nothing persisted yet, use defaults); `unsupported` keeps a newer blob
/// untouched; `corrupt` reports an unparseable one.
enum RestorationDecode<T: Equatable>: Equatable {
    case empty
    case ok(T)
    case unsupported(foundVersion: Int)
    case corrupt(reason: String)
}

/// Versioned encode/decode shared by every tab contract. Keeps the schemaVersion
/// at the root so a future schema is reported, never silently coerced.
enum SceneRestorationCodec {
    private struct VersionedRoot<T: Codable>: Codable {
        var schemaVersion: Int
        var state: T
    }

    static func encode<T: Codable>(_ value: T, schemaVersion: Int) -> String {
        let root = VersionedRoot(schemaVersion: schemaVersion, state: value)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.withoutEscapingSlashes]
        guard let data = try? encoder.encode(root), let s = String(data: data, encoding: .utf8) else {
            return ""
        }
        return s
    }

    static func decode<T: Codable & Equatable>(
        _ raw: String?, schemaVersion: Int, as type: T.Type
    ) -> RestorationDecode<T> {
        guard let raw = raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return .empty
        }
        guard let data = raw.data(using: .utf8) else { return .corrupt(reason: "not utf8") }
        // Peek the version first so a future schema is reported, not coerced.
        if let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let v = obj["schemaVersion"] as? Int, v > schemaVersion {
            return .unsupported(foundVersion: v)
        }
        guard let root = try? JSONDecoder().decode(VersionedRoot<T>.self, from: data) else {
            return .corrupt(reason: "decode failed")
        }
        return .ok(root.state)
    }
}

/// Thin UserDefaults adapter for restoration blobs. The pure contracts above do
/// the encoding, versioning and validation; this only reads and writes the string
/// under a key, so the tabs have one call site each and the testable logic stays
/// free of persistence.
enum SceneRestorationStore {
    static func load(_ key: String, defaults: UserDefaults = .standard) -> String? {
        defaults.string(forKey: key)
    }

    static func save(_ key: String, _ value: String, defaults: UserDefaults = .standard) {
        defaults.set(value, forKey: key)
    }
}

/// The freshness gate for deep pushes. Tab selection restores always; a pushed
/// destination restores only within this window after the app was backgrounded
/// (scope decision: 30 minutes). The timestamp is persisted so a cold launch
/// after a kill can still honour it; a nil timestamp (never recorded) means no
/// deep restore.
enum SceneRestorationFreshness {
    static let deepPushWindow: TimeInterval = 30 * 60

    static func shouldRestoreDeepPush(
        backgroundedAt: Date?, now: Date, window: TimeInterval = deepPushWindow
    ) -> Bool {
        guard let backgroundedAt = backgroundedAt else { return false }
        let elapsed = now.timeIntervalSince(backgroundedAt)
        return elapsed >= 0 && elapsed <= window
    }
}

/// Records when the scene last entered the background, so the freshness gate above
/// works across a real background cycle and a cold launch after a kill. Stored as
/// an epoch double; zero (never recorded) reads back as nil, which closes the gate.
enum SceneRestorationBackground {
    static let storageKey = "syrmos.restore.backgroundedAt"

    static func record(_ date: Date, defaults: UserDefaults = .standard) {
        defaults.set(date.timeIntervalSince1970, forKey: storageKey)
    }

    static func lastBackgrounded(defaults: UserDefaults = .standard) -> Date? {
        let epoch = defaults.double(forKey: storageKey)
        return epoch > 0 ? Date(timeIntervalSince1970: epoch) : nil
    }
}

// MARK: - Home

struct HomeRestorationState: Codable, Equatable {
    var selectedNearbyId: String?
    var push: RestorablePush?

    /// Selection is always kept; the push is dropped when the freshness gate is
    /// closed or its id no longer resolves.
    func validated(
        allowDeepPush: Bool,
        stationExists: (String) -> Bool,
        lineExists: (String) -> Bool
    ) -> HomeRestorationState {
        var copy = self
        if !allowDeepPush {
            copy.push = nil
        } else if let push = copy.push, !push.exists(stationExists: stationExists, lineExists: lineExists) {
            copy.push = nil
        }
        return copy
    }
}

enum HomeRestorationContract {
    static let schemaVersion = 1
    static let storageKey = "syrmos.restore.home.v1"

    static func encode(_ state: HomeRestorationState) -> String {
        SceneRestorationCodec.encode(state, schemaVersion: schemaVersion)
    }

    static func decode(_ raw: String?) -> RestorationDecode<HomeRestorationState> {
        SceneRestorationCodec.decode(raw, schemaVersion: schemaVersion, as: HomeRestorationState.self)
    }
}

// MARK: - Explore

struct ExploreRestorationState: Codable, Equatable {
    var selectedRegion: String?
    var selectedType: String?
    var selectedLineId: String?
    var push: RestorablePush?

    func validated(
        allowDeepPush: Bool,
        stationExists: (String) -> Bool,
        lineExists: (String) -> Bool
    ) -> ExploreRestorationState {
        var copy = self
        // A selected line that no longer exists is dropped too, since it drives a
        // detail panel; region and type are free enums with no seed dependency.
        if let lineId = copy.selectedLineId, !lineExists(lineId) {
            copy.selectedLineId = nil
        }
        if !allowDeepPush {
            copy.push = nil
        } else if let push = copy.push, !push.exists(stationExists: stationExists, lineExists: lineExists) {
            copy.push = nil
        }
        return copy
    }
}

enum ExploreRestorationContract {
    static let schemaVersion = 1
    static let storageKey = "syrmos.restore.explore.v1"

    static func encode(_ state: ExploreRestorationState) -> String {
        SceneRestorationCodec.encode(state, schemaVersion: schemaVersion)
    }

    static func decode(_ raw: String?) -> RestorationDecode<ExploreRestorationState> {
        SceneRestorationCodec.decode(raw, schemaVersion: schemaVersion, as: ExploreRestorationState.self)
    }
}

// MARK: - Departures

/// Departures restores its day-plan selection (city, route, day). The planned
/// flight time is intentionally NOT persisted: it is a one-off planning input
/// that goes stale, unlike the city/route/day the user is browsing.
struct DeparturesRestorationState: Codable, Equatable {
    var selectedCity: String?
    var selectedRoute: String?
    var dayOffset: Int
}

enum DeparturesRestorationContract {
    static let schemaVersion = 1
    static let storageKey = "syrmos.restore.departures.v1"

    static func encode(_ state: DeparturesRestorationState) -> String {
        SceneRestorationCodec.encode(state, schemaVersion: schemaVersion)
    }

    static func decode(_ raw: String?) -> RestorationDecode<DeparturesRestorationState> {
        SceneRestorationCodec.decode(raw, schemaVersion: schemaVersion, as: DeparturesRestorationState.self)
    }
}
