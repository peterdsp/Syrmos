import Foundation

/// Live OASA airport express-bus ETAs, from the Pi at /api/oasa-airport-buses.
///
/// The Pi's `oasa-airport-bus-watcher` polls OASA Telematics `getStopArrivals`
/// for the airport stop (10705) every 30s and writes real per-vehicle ETAs to
/// the airport stop. `minutesAway` is OASA's own `btime2` estimate for that bus
/// to reach the airport, so the soonest per line is genuinely the next X-bus a
/// rider can board at the airport. That makes these `.live`, not scheduled - we
/// no longer fall back to a passive "check OASA" message when the feed answers.
@MainActor
enum AirportBusService {
    private static let base = "https://api-syrmos.peterdsp.dev"
    /// Short TTL so the 15s Airport refresh timer picks up fresh ETAs but a
    /// burst of reloads in the same tick reuses one response.
    private static let ttl: TimeInterval = 12
    private static var cache: (Date, LiveAirportBuses)?

    /// Soonest-first live ETAs (minutes) per airport express line (X93/X95/X96/X97).
    struct LiveAirportBuses: Equatable {
        let updatedAt: Date?
        let etasByLine: [String: [Int]]

        var isEmpty: Bool { etasByLine.isEmpty }
        /// The next tracked bus of `line` reaching the airport stop, if any.
        func soonest(_ line: String) -> Int? { etasByLine[line]?.first }
    }

    /// Fetch live ETAs. Returns nil on any failure or in offline-only mode so the
    /// caller falls back to the neutral 24/7 presentation. Never throws to the UI.
    static func fetch() async -> LiveAirportBuses? {
        if SyrmosSchedulesStore.shared.offlineOnly { return nil }
        if let cached = cache, Date().timeIntervalSince(cached.0) < ttl {
            return cached.1
        }
        guard let url = URL(string: "\(base)/api/oasa-airport-buses") else { return nil }
        var req = URLRequest(url: url)
        req.timeoutInterval = 6
        do {
            let (data, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return nil }
            let payload = try JSONDecoder().decode(Payload.self, from: data)
            let live = reduce(payload)
            cache = (Date(), live)
            return live
        } catch {
            return nil
        }
    }

    // MARK: - Pure mapping (unit-tested, no network)

    /// Collapse the raw feed into soonest-first ETAs per line. Negative ETAs are
    /// clamped to 0 (bus at the stop). Lines with no tracked vehicle are absent.
    static func reduce(_ payload: Payload) -> LiveAirportBuses {
        var byLine: [String: [Int]] = [:]
        for arrival in payload.airportArrivals where !arrival.lineId.isEmpty {
            byLine[arrival.lineId, default: []].append(max(0, arrival.minutesAway))
        }
        for (line, etas) in byLine {
            byLine[line] = etas.sorted()
        }
        return LiveAirportBuses(updatedAt: parseTimestamp(payload.updatedAt), etasByLine: byLine)
    }

    static func parseTimestamp(_ value: String) -> Date? {
        guard !value.isEmpty else { return nil }
        let withFraction = ISO8601DateFormatter()
        withFraction.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = withFraction.date(from: value) { return date }
        return ISO8601DateFormatter().date(from: value)
    }

    // MARK: - Decoding

    struct Payload: Decodable {
        let updatedAt: String
        let airportArrivals: [Arrival]

        init(updatedAt: String, airportArrivals: [Arrival]) {
            self.updatedAt = updatedAt
            self.airportArrivals = airportArrivals
        }

        enum CodingKeys: String, CodingKey { case updatedAt, airportArrivals }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            updatedAt = (try? c.decode(String.self, forKey: .updatedAt)) ?? ""
            airportArrivals = (try? c.decode([Arrival].self, forKey: .airportArrivals)) ?? []
        }

        struct Arrival: Decodable {
            let lineId: String
            let minutesAway: Int
        }
    }
}
