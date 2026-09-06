import Foundation
import CoreLocation
import Combine

/// One live airport express-bus position for the map layer. `toAirport` is nil
/// when the route code doesn't resolve to a known direction.
struct AirportBusVehicle: Identifiable {
    let id: String
    let lineId: String
    let coordinate: CLLocationCoordinate2D
    let toAirport: Bool?
}

/// Polls /api/oasa-airport-buses for live X93/95/96/97 vehicle positions and
/// publishes them for the map. Mirrors LiveTrainService (a single shared poller)
/// and the web `airportBusVehicles` transform, so all clients plot the same
/// vehicles. ETAs for the departure lists come from `AirportBusService`; this is
/// only the positions[] side of the same feed.
@MainActor
final class LiveAirportBusService: ObservableObject {
    static let shared = LiveAirportBusService()

    @Published var vehicles: [AirportBusVehicle] = []

    private var task: Task<Void, Never>?

    init() { startPolling() }

    func startPolling() {
        guard task == nil else { return }
        task = Task { [weak self] in
            var failures = 0
            while !Task.isCancelled {
                let ok = await self?.fetchOnce() ?? false
                failures = ok ? 0 : failures + 1
                // Backoff + jitter: 15s healthy, escalating on failure (capped
                // 60s), reset on success. Keeps the last frame meanwhile.
                try? await Task.sleep(nanoseconds: PollBackoff.nextDelayNanos(
                    consecutiveFailures: failures, baseDelaySeconds: 15))
            }
        }
    }

    @discardableResult
    private func fetchOnce() async -> Bool {
        guard !SyrmosSchedulesStore.shared.offlineOnly else {
            vehicles = []
            return true // intentional offline-only mode, not a poll failure
        }
        guard let url = URL(string: "https://api-syrmos.peterdsp.dev/api/oasa-airport-buses") else { return false }
        var req = URLRequest(url: url)
        req.timeoutInterval = 8
        req.cachePolicy = .reloadIgnoringLocalCacheData
        do {
            let (data, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return false }
            let payload = try JSONDecoder().decode(Payload.self, from: data)
            vehicles = Self.parse(payload)
            return true
        } catch {
            // Keep the last frame on transient errors.
            return false
        }
    }

    // MARK: - Pure mapping (unit-tested via AirportServiceTests)

    /// Extract plottable vehicles: drop rows without a finite non-zero coordinate
    /// or a line id, and resolve to/from-airport from the route code.
    static func parse(_ payload: Payload) -> [AirportBusVehicle] {
        payload.vehicles.compactMap { v in
            guard !v.lineId.isEmpty else { return nil }
            guard v.lat.isFinite, v.lng.isFinite, !(v.lat == 0 && v.lng == 0) else { return nil }
            let dir = payload.routes[v.lineId].flatMap { route -> Bool? in
                if route.toAirport.contains(v.routeCode) { return true }
                if route.fromAirport.contains(v.routeCode) { return false }
                return nil
            }
            return AirportBusVehicle(
                id: v.vehicleId,
                lineId: v.lineId,
                coordinate: CLLocationCoordinate2D(latitude: v.lat, longitude: v.lng),
                toAirport: dir
            )
        }
    }

    struct Payload: Decodable {
        let vehicles: [Vehicle]
        let routes: [String: RouteDirs]

        init(vehicles: [Vehicle], routes: [String: RouteDirs]) {
            self.vehicles = vehicles
            self.routes = routes
        }

        enum CodingKeys: String, CodingKey { case vehicles, routes }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            vehicles = (try? c.decode([Vehicle].self, forKey: .vehicles)) ?? []
            routes = (try? c.decode([String: RouteDirs].self, forKey: .routes)) ?? [:]
        }

        struct Vehicle: Decodable {
            let vehicleId: String
            let lat: Double
            let lng: Double
            let routeCode: Int
            let lineId: String

            enum CodingKeys: String, CodingKey { case vehicleId, lat, lng, routeCode, lineId }
            init(vehicleId: String, lat: Double, lng: Double, routeCode: Int, lineId: String) {
                self.vehicleId = vehicleId; self.lat = lat; self.lng = lng
                self.routeCode = routeCode; self.lineId = lineId
            }
            init(from decoder: Decoder) throws {
                let c = try decoder.container(keyedBy: CodingKeys.self)
                vehicleId = (try? c.decode(String.self, forKey: .vehicleId)) ?? ""
                lat = (try? c.decode(Double.self, forKey: .lat)) ?? .nan
                lng = (try? c.decode(Double.self, forKey: .lng)) ?? .nan
                routeCode = (try? c.decode(Int.self, forKey: .routeCode)) ?? 0
                lineId = (try? c.decode(String.self, forKey: .lineId)) ?? ""
            }
        }

        struct RouteDirs: Decodable {
            let toAirport: [Int]
            let fromAirport: [Int]
            init(toAirport: [Int], fromAirport: [Int]) {
                self.toAirport = toAirport; self.fromAirport = fromAirport
            }
            enum CodingKeys: String, CodingKey { case toAirport, fromAirport }
            init(from decoder: Decoder) throws {
                let c = try decoder.container(keyedBy: CodingKeys.self)
                toAirport = (try? c.decode([Int].self, forKey: .toAirport)) ?? []
                fromAirport = (try? c.decode([Int].self, forKey: .fromAirport)) ?? []
            }
        }
    }
}
