import Foundation

/// Talks to the server-side projector at /api/departures/next.
///
/// The Pi runs the canonical projection logic in Python; this service
/// calls it and turns the JSON into Departure values. StationDetailView
/// and the Map station sheet call `nextDepartures(...)` here and fall back
/// to the local `ScheduleProjector` when the network is unreachable. That
/// way bug fixes to projection land server-side without an app release.
@MainActor
enum SyrmosDeparturesService {
    private static let base = "https://api-syrmos.peterdsp.dev"
    /// In-memory cache so quick reloads (the 15s refresh timer in
    /// StationDetailView, Map sheet's countdown ticker) don't hammer the
    /// API every cycle. Keyed by (station+lines), 10s TTL.
    private static var cache: [String: (Date, [Departure])] = [:]
    private static let ttl: TimeInterval = 10

    /// Fetch and decode. On any failure, returns nil so the caller can
    /// fall back to the local projector. Never throws to the UI.
    static func nextDepartures(
        for stationId: String,
        lineIds: [String],
        limit: Int = 8
    ) async -> [Departure]? {
        if SyrmosSchedulesStore.shared.offlineOnly { return nil }
        let key = cacheKey(stationId: stationId, lineIds: lineIds, limit: limit)
        if let cached = cache[key], Date().timeIntervalSince(cached.0) < ttl {
            return cached.1
        }
        guard var components = URLComponents(string: "\(base)/api/departures/next") else { return nil }
        components.queryItems = [
            URLQueryItem(name: "stationId", value: stationId),
            URLQueryItem(name: "lineIds", value: lineIds.joined(separator: ",")),
            URLQueryItem(name: "limit", value: "\(limit)"),
        ]
        guard let url = components.url else { return nil }
        var req = URLRequest(url: url)
        req.timeoutInterval = 6
        do {
            let (data, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
                return nil
            }
            let payload = try JSONDecoder().decode(Payload.self, from: data)
            let mapped = payload.departures.map { entry in
                Departure(
                    time: entry.time,
                    lineId: entry.resolvedLineId,
                    direction: entry.direction,
                    minutesAway: entry.minutesAway,
                    serviceType: entry.serviceType
                )
            }
            cache[key] = (Date(), mapped)
            return mapped
        } catch {
            return nil
        }
    }

    private static func cacheKey(stationId: String, lineIds: [String], limit: Int) -> String {
        "\(stationId)|\(lineIds.joined(separator: ","))|\(limit)"
    }

    private struct Payload: Decodable {
        let departures: [Entry]
    }
    private struct Entry: Decodable {
        let lineId: String
        let line: String?
        let direction: String
        let time: String
        let minutesAway: Int
        let serviceType: String

        var resolvedLineId: String {
            if !lineId.isEmpty { return lineId }
            return line ?? ""
        }
    }
}
