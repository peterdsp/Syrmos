import Foundation

/// Fetches and caches `/api/station-offsets` — per-station cumulative minutes
/// from origin for M1 / M2 / M3 / T6 / T7, scraped from STASY's HTML
/// timetable pages.
///
/// The Timetables tab and StationDetailView use this to convert the band
/// projector's origin-terminal HH:MM into the actual time the train passes
/// through any given metro or tram station. Without it the apps would
/// show the same minute at every stop on the line, which is wrong.
///
/// Offline-first: bundled seed at `seed-schedules-v2/station-offsets.json`
/// hydrates the in-memory cache before any network call; `refresh()`
/// overlays anything newer.
@MainActor
final class SyrmosStationOffsetsStore: ObservableObject {
    static let shared = SyrmosStationOffsetsStore()

    @Published private(set) var groups: [Group] = []

    private let base = "https://api-syrmos.peterdsp.dev"
    private let session: URLSession

    private init(session: URLSession = .shared) {
        self.session = session
        hydrateFromBundleIfNeeded()
    }

    /// Lookup the cumulative minutes-from-origin for a (line, direction,
    /// stationId). Returns 0 when no offset is known, which lets the
    /// caller fall back to origin times without a special-case branch.
    func offsetMinutes(lineId: String, direction: String, stationId: String) -> Int {
        guard !stationId.isEmpty else { return 0 }
        let dir = direction.lowercased()
        let group = groups.first { $0.lineId == lineId && $0.direction == dir }
        return group?.stops.first { $0.stationId == stationId }?.minutesFromOrigin ?? 0
    }

    /// All stops for a (line, direction) in stop_sequence order, used by
    /// the Timetables expanded card to show real per-stop times.
    func stops(lineId: String, direction: String) -> [Stop] {
        let dir = direction.lowercased()
        return groups.first { $0.lineId == lineId && $0.direction == dir }?.stops ?? []
    }

    /// Direction-agnostic helper used by ScheduleProjector which doesn't
    /// know which way the train it's projecting is going. Tries outbound
    /// first (the canonical direction in our data), falls back to inbound.
    /// Returns 0 when neither direction has an entry for this station.
    func bestOffsetMinutes(lineId: String, stationId: String) -> Int {
        let outbound = offsetMinutes(lineId: lineId, direction: "outbound", stationId: stationId)
        if outbound > 0 { return outbound }
        return offsetMinutes(lineId: lineId, direction: "inbound", stationId: stationId)
    }

    func refresh() async {
        guard let url = URL(string: base + "/api/station-offsets") else { return }
        var req = URLRequest(url: url)
        req.timeoutInterval = 10
        do {
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return }
            let payload = try JSONDecoder().decode(Payload.self, from: data)
            self.groups = payload.lines
        } catch {
            // Silent: keep whatever the bundle hydrated.
        }
    }

    private func hydrateFromBundleIfNeeded() {
        guard groups.isEmpty else { return }
        guard let url = Bundle.main.url(
            forResource: "station-offsets",
            withExtension: "json",
            subdirectory: "seed-schedules-v2"
        ) else { return }
        guard let data = try? Data(contentsOf: url),
              let payload = try? JSONDecoder().decode(Payload.self, from: data)
        else { return }
        self.groups = payload.lines
    }

    struct Payload: Decodable {
        let lines: [Group]
    }

    struct Group: Decodable {
        let lineId: String
        let direction: String
        let origin: String
        let destination: String
        let stops: [Stop]
    }

    struct Stop: Decodable, Identifiable {
        let stationId: String
        let stationEn: String
        let stopSequence: Int
        let minutesFromOrigin: Int

        var id: String { "\(stationId)-\(stopSequence)" }
    }
}
