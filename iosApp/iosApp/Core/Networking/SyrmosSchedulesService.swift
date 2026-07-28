import Foundation

/// Offline-first loader for schedule rules + frequency bands.
///
/// Mirrors the KMP `SyrmosSchedulesService` in `core/network`. Fetches
/// `/api/schedules/manifest` from the Pi, then per-line bundles whose
/// per-line hash changed. Everything is cached in memory; on next cold
/// start `cachedManifestETag` is preserved through `UserDefaults` so we
/// can short-circuit when nothing changed server-side.
///
/// Failures are silent: the app falls through to the bundled seed data.
@MainActor
final class SyrmosSchedulesService: ObservableObject {
    @Published private(set) var manifest: Manifest?
    @Published private(set) var bundles: [String: LineSchedule] = [:]
    @Published private(set) var lastSyncAt: Date?
    @Published var offlineOnly: Bool {
        didSet { defaults.set(offlineOnly, forKey: kOfflineKey) }
    }

    private let defaults: UserDefaults
    private let session: URLSession
    private let kETagKey = "syrmos.schedules.etag"
    private let kLastSyncKey = "syrmos.schedules.lastSync"
    private let kOfflineKey = "syrmos.schedules.offlineOnly"

    private let base = "https://api-syrmos.peterdsp.dev"

    init(defaults: UserDefaults = .standard, session: URLSession = .shared) {
        self.defaults = defaults
        self.session = session
        self.offlineOnly = defaults.bool(forKey: kOfflineKey)
        if let ts = defaults.object(forKey: kLastSyncKey) as? Date {
            self.lastSyncAt = ts
        }
        hydrateFromBundleIfNeeded()
    }

    /// Loads the bundled snapshot in `Resources/seed-schedules-v2/` so the
    /// projector has correct data even before the first network refresh.
    private func hydrateFromBundleIfNeeded() {
        guard bundles.isEmpty else { return }
        let lineIds = [
            "M1", "M2", "M3", "T6", "T7", "A1", "A2", "A3", "A4", "M3_AIR",
            "IC1", "TP1", "TP2", "TP3", "TP4", "RG1", "AL1", "KB1", "VL1",
            "DX1", "KP1", "TL1", "KO1", "PL1", "DK1", "PS1", "PS2", "PSB",
            "PU1", "PU2", "TM1", "TM2",
        ]
        var out: [String: LineSchedule] = [:]
        for lid in lineIds {
            guard let url = Bundle.main.url(
                forResource: lid,
                withExtension: "json",
                subdirectory: "seed-schedules-v2"
            ) else { continue }
            guard let data = try? Data(contentsOf: url),
                  let bundle = try? JSONDecoder().decode(LineSchedule.self, from: data)
            else { continue }
            out[lid] = bundle
        }
        if !out.isEmpty { bundles = out }

        if let url = Bundle.main.url(
            forResource: "manifest",
            withExtension: "json",
            subdirectory: "seed-schedules-v2"
        ),
        let data = try? Data(contentsOf: url),
        let m = try? JSONDecoder().decode(Manifest.self, from: data) {
            self.manifest = m
        }
    }

    private var lastSeenETag: String? {
        get { defaults.string(forKey: kETagKey) }
        set { defaults.set(newValue, forKey: kETagKey) }
    }

    @discardableResult
    func refresh() async -> RefreshOutcome {
        if offlineOnly { return .skipped }
        guard let manifestURL = URL(string: base + "/api/schedules/manifest") else {
            return .failure("bad url")
        }
        var req = URLRequest(url: manifestURL)
        req.timeoutInterval = 8
        if let etag = lastSeenETag {
            req.setValue(etag, forHTTPHeaderField: "If-None-Match")
        }
        do {
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse else { return .failure("no http") }
            if http.statusCode == 304 {
                bump(lastSync: Date())
                return .upToDate
            }
            guard http.statusCode == 200 else { return .failure("status \(http.statusCode)") }
            let m = try JSONDecoder().decode(Manifest.self, from: data)
            if m.etag == lastSeenETag {
                bump(lastSync: Date())
                return .upToDate
            }
            // Fetch each line bundle whose hash changed.
            var next = bundles
            var refreshed = 0
            for (lineId, hash) in m.perLineHashes {
                if let cached = next[lineId], cached.etag == hash { continue }
                if let bundle = try await fetchBundle(lineId: lineId) {
                    next[lineId] = bundle.withETag(hash)
                    refreshed += 1
                }
            }
            self.manifest = m
            self.bundles = next
            lastSeenETag = m.etag
            bump(lastSync: Date())
            return .refreshed(refreshed)
        } catch {
            return .failure(error.localizedDescription)
        }
    }

    private func fetchBundle(lineId: String) async throws -> LineSchedule? {
        guard let url = URL(string: base + "/api/schedules/" + lineId) else { return nil }
        var req = URLRequest(url: url)
        req.timeoutInterval = 8
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return nil }
        return try JSONDecoder().decode(LineSchedule.self, from: data)
    }

    private func bump(lastSync: Date) {
        self.lastSyncAt = lastSync
        defaults.set(lastSync, forKey: kLastSyncKey)
    }

    // MARK: Wire types

    enum RefreshOutcome {
        case upToDate
        case refreshed(Int)
        case skipped
        case failure(String)
    }

    struct Manifest: Decodable, Equatable {
        let version: Int
        let updatedAt: String
        let clientMinVersion: Int
        let etag: String
        let perLineHashes: [String: String]
        let linesHash: String
        let holidaysHash: String
        let overridesHash: String
    }

    struct LineSchedule: Decodable {
        let lineId: String
        let rules: [RuleEntry]
        let bands: [BandEntry]
        let trips: [TripEntry]
        /// Per-(direction, fromStation, time) endpoint scraped from
        /// stasy.gr. When a band's emitted slot matches one of these
        /// rows, the projector overrides the displayed destination
        /// (M1 inbound 00:30 from Kifissia terminates at Omonia, not
        /// the line's normal Piraeus terminal). Empty when the line
        /// has no published short-turn data yet.
        let lastTrains: [LastTrainEntry]
        var etag: String = ""

        func withETag(_ hash: String) -> LineSchedule {
            var copy = self
            copy.etag = hash
            return copy
        }

        private enum CodingKeys: String, CodingKey { case lineId, rules, bands, trips, lastTrains }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            lineId = try c.decode(String.self, forKey: .lineId)
            rules = try c.decode([RuleEntry].self, forKey: .rules)
            bands = try c.decode([BandEntry].self, forKey: .bands)
            trips = (try? c.decode([TripEntry].self, forKey: .trips)) ?? []
            lastTrains = (try? c.decode([LastTrainEntry].self, forKey: .lastTrains)) ?? []
        }
    }

    struct TripEntry: Decodable {
        let trainNo: String
        let dayType: String
        let direction: String
        let serviceLabel: String
        let validDates: String?
        let stops: [TripStop]

        private enum CodingKeys: String, CodingKey {
            case trainNo, dayType, direction, serviceLabel, validDates, stops
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            trainNo = try c.decode(String.self, forKey: .trainNo)
            dayType = try c.decode(String.self, forKey: .dayType)
            direction = try c.decode(String.self, forKey: .direction)
            serviceLabel = (try? c.decode(String.self, forKey: .serviceLabel)) ?? ""
            validDates = try? c.decode(String.self, forKey: .validDates)
            stops = try c.decode([TripStop].self, forKey: .stops)
        }
    }

    struct TripStop: Decodable {
        let stationId: String
        let stopSequence: Int
        let departureTime: String
    }

    struct LastTrainEntry: Decodable {
        let dayType: String
        let direction: String
        let fromStationId: String
        let time: String
        let endStationId: String
        let label: String
    }

    struct RuleEntry: Decodable {
        let dayType: String
        let openTime: String
        let closeTime: String
        let is247: Bool
        let notes: String
    }

    struct BandEntry: Decodable {
        let dayType: String
        let timeStart: String
        let timeEnd: String
        let headwayMinutes: Double
        let label: String
        let direction: String?

        private enum CodingKeys: String, CodingKey {
            case dayType, timeStart, timeEnd, headwayMinutes, label, direction
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            dayType = try c.decode(String.self, forKey: .dayType)
            timeStart = try c.decode(String.self, forKey: .timeStart)
            timeEnd = try c.decode(String.self, forKey: .timeEnd)
            headwayMinutes = try c.decode(Double.self, forKey: .headwayMinutes)
            label = try c.decode(String.self, forKey: .label)
            direction = try? c.decode(String.self, forKey: .direction)
        }
    }
}
