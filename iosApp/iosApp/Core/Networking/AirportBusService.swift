import Foundation
import CoreLocation

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
        if let cached = cache, SyrmosClock.now.timeIntervalSince(cached.0) < ttl {
            return cached.1
        }
        do {
            guard let unified = await fetchUnified() else { return nil }
            let etas = unified.operators["oasa"]?.etasByLine ?? [:]
            let live = LiveAirportBuses(
                updatedAt: unified.updatedAt,
                etasByLine: etas
            )
            cache = (SyrmosClock.now, live)
            return live
        } catch {
            return nil
        }
    }

    // MARK: - Unified Normalized Model

    /// Unified operator data from the new normalized endpoint
    struct UnifiedOperatorData: Decodable {
        let operatorId: String
        let routes: [UnifiedRoute]
        let vehicles: [UnifiedVehicle]
        let arrivals: [UnifiedArrival]
        let alerts: [UnifiedAlert]
        let source: String?
        let attribution: String?
        enum CodingKeys: String, CodingKey {
            case operatorId = "operator"
            case routes, vehicles, arrivals, alerts, source, attribution
        }
    }

    /// Normalized route metadata
    struct UnifiedRoute: Decodable, Identifiable {
        let operatorId: String
        let route_id: String
        let public_name: String
        let public_name_en: String?
        let public_name_el: String?
        let origin: String
        let destination: String
        let airport: String
        let color: String
        let direction: String?
        let is_operational: Bool
        let source: String?
        let source_url: String?
        let attribution: String?
        let observed_at: String?
        let confidence: String?
        let stops: [UnifiedStop]?
        let shape: [UnifiedShapePoint]?
        var id: String { route_id }

        enum CodingKeys: String, CodingKey {
            case operatorId = "operator"
            case route_id
            case public_name
            case public_name_en
            case public_name_el
            case origin
            case destination
            case airport
            case color
            case direction
            case is_operational
            case source
            case source_url
            case attribution
            case observed_at, confidence, stops, shape
        }
    }

    struct UnifiedStop: Decodable, Identifiable {
        let stop_id: String
        let stop_name: String
        let latitude: Double
        let longitude: Double
        let sequence: Int?
        var id: String { stop_id }
    }

    struct UnifiedShapePoint: Decodable {
        let latitude: Double
        let longitude: Double
        let sequence: Int?
    }

    /// Normalized vehicle position
    struct UnifiedVehicle: Decodable, Identifiable {
        let vehicle_id: String
        let line_id: String
        let route_id: String
        let operatorId: String
        let latitude: Double
        let longitude: Double
        let heading: Double
        let observed_at: String
        let source: String?
        let source_url: String?
        let confidence: String?
        let attribution: String?
        let to_airport: Bool?
        var id: String { vehicle_id }

        enum CodingKeys: String, CodingKey {
            case vehicle_id
            case line_id
            case route_id
            case operatorId = "operator"
            case latitude
            case longitude
            case heading
            case observed_at
            case source
            case source_url
            case confidence
            case attribution
            case to_airport
        }
    }

    /// Normalized arrival/ETA
    struct UnifiedArrival: Decodable {
        let line_id: String
        let route_id: String
        let operatorId: String
        let stop_id: String
        let stop_name: String
        let destination: String
        let direction: String?
        let scheduled_time: String?
        let estimated_minutes: Int?
        let vehicle_id: String?
        let source: String?
        let source_url: String?
        let observed_at: String
        let freshness: Double?
        let confidence: String?
        let service_status: String?
        let attribution: String?
        enum CodingKeys: String, CodingKey {
            case line_id, route_id, operatorId = "operator", stop_id, stop_name
            case destination, direction, scheduled_time, estimated_minutes, vehicle_id
            case source, source_url, observed_at, freshness, confidence, service_status, attribution
        }
    }

    /// Normalized alert
    struct UnifiedAlert: Decodable {
        let alert_id: String
        let operatorId: String
        let title: String
        let summary: String
        let url: String?
        let severity: String?
        let source: String?
        let source_url: String?
        let observed_at: String?
        let attribution: String?
        let route_id: String?
        let title_en: String?
        let summary_en: String?
        let valid_from: String?
        let valid_until: String?
        enum CodingKeys: String, CodingKey {
            case alert_id, operatorId = "operator", title, summary, url, severity
            case source, source_url, observed_at, attribution, route_id, title_en, summary_en
            case valid_from, valid_until
        }
    }

    /// Unified response from /api/airport-buses
    struct UnifiedPayload: Decodable {
        let updated_at: String
        let operators: [String: UnifiedOperatorData]?
        let sources: [UnifiedSource]?
        let generated_at: String?
        let generation_duration: Double?

        enum CodingKeys: String, CodingKey {
            case updated_at
            case operators
            case sources
            case generated_at
            case generation_duration
        }
    }

    /// Source information
    struct UnifiedSource: Decodable {
        let operatorId: String?
        let name: String?
        let source_url: String?
        let attribution: String?
        let is_live: Bool?
        let last_tested: String?
        let notes: String?
        enum CodingKeys: String, CodingKey {
            case operatorId = "operator"
            case name, source_url, attribution, is_live, last_tested, notes
        }
    }

    /// Unified airport bus data for all operators
    struct UnifiedAirportBuses {
        let updatedAt: Date?
        let operators: [String: OperatorData]
        let sources: [SourceInfo]
        let vehiclesByLine: [String: [Int]]
        let linkForOASA: URL?
        let linkForOSETH: URL?

        var isEmpty: Bool { operators.isEmpty }
        func soonest(_ line: String, operator operatorId: String) -> Int? { operators[operatorId]?.etasByLine[line]?.first }
    }

    struct OperatorData {
        let updatedAt: Date?
        let etasByLine: [String: [Int]]
        let vehicles: [AirportBusVehicle]
        let alerts: [ServiceAlert]
        let routes: [UnifiedRoute]
        let source: String?
        let attribution: String?
        let operatorId: String
    }

    struct ServiceAlert: Identifiable, Equatable {
        let id: String
        let title: String
        let summary: String
        let operatorId: String
        let severity: String
        let url: URL?

        // For Equatable conformity
        static func == (lhs: ServiceAlert, rhs: ServiceAlert) -> Bool {
            lhs.id == rhs.id && lhs.title == rhs.title && lhs.summary == rhs.summary
        }
    }

    /// Source information for display
    struct SourceInfo: Equatable {
        let operatorId: String
        let name: String
        let sourceUrl: URL?
        let attribution: String
        let isLive: Bool
        let lastTested: Date?
        let notes: String?
    }

    /// Fetch unified airport bus data from the new normalized endpoint
    static func fetchUnified() async -> UnifiedAirportBuses? {
        if SyrmosSchedulesStore.shared.offlineOnly { return nil }

        guard let url = URL(string: "\(base)/api/airport-buses") else { return nil }
        var req = URLRequest(url: url)
        req.timeoutInterval = 8
        do {
            let (data, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return nil }
            let payload = try JSONDecoder().decode(UnifiedPayload.self, from: data)
            return reduceUnified(payload)
        } catch {
            return nil
        }
    }

    /// Fetch data for a specific operator
    static func fetchUnified(operator operatorId: String) async -> OperatorData? {
        if SyrmosSchedulesStore.shared.offlineOnly { return nil }

        guard let url = URL(string: "\(base)/api/airport-buses/\(operatorId)") else { return nil }
        var req = URLRequest(url: url)
        req.timeoutInterval = 8
        do {
            let (data, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return nil }
            let payload = try JSONDecoder().decode(UnifiedOperatorPayload.self, from: data)
            return reduceOperator(payload)
        } catch {
            return nil
        }
    }

    /// Single operator payload
    struct UnifiedOperatorPayload: Decodable {
        let operatorId: String
        let updated_at: String?
        let routes: [UnifiedRoute]
        let vehicles: [UnifiedVehicle]
        let arrivals: [UnifiedArrival]
        let alerts: [UnifiedAlert]
        let source: String?
        let attribution: String?
        let generated_at: String?
        let generation_duration: Double?
        enum CodingKeys: String, CodingKey {
            case operatorId = "operator", updated_at, routes, vehicles, arrivals, alerts
            case source, attribution, generated_at, generation_duration
        }
    }

    /// Reduce unified payload to simple structure
    static func reduceUnified(_ payload: UnifiedPayload) -> UnifiedAirportBuses {
        var operatorsData: [String: OperatorData] = [:]
        var allSources: [SourceInfo] = []
        var vehiclesByLine: [String: [Int]] = [:]
        var linkForOASA: URL? = nil
        var linkForOSETH: URL? = nil

        // Process each operator
        for (opKey, opData) in payload.operators ?? [:] {
            var etasByLine: [String: [Int]] = [:]
            var vehiclesForOp: [AirportBusVehicle] = []
            var alertsForOp: [ServiceAlert] = []
            var routesForOp: [UnifiedRoute] = opData.routes

            // Process arrivals
            for arrival in opData.arrivals {
                let line = arrival.line_id
                if let minutes = arrival.estimated_minutes, minutes >= 0 {
                    etasByLine[line, default: []].append(minutes)
                }
            }

            // Sort ETAs for each line
            for line in etasByLine.keys {
                etasByLine[line] = etasByLine[line]?.sorted() ?? []
                vehiclesByLine[line] = etasByLine[line] ?? []
            }

            // Process vehicles
            for vehicle in opData.vehicles {
                if vehicle.latitude != 0 && vehicle.longitude != 0 {
                    let coord = CLLocationCoordinate2D(latitude: vehicle.latitude, longitude: vehicle.longitude)
                    let vehicleId = AirportBusVehicle(
                        id: vehicle.vehicle_id,
                        lineId: vehicle.line_id,
                        coordinate: coord,
                        toAirport: vehicle.to_airport
                    )
                    vehiclesForOp.append(vehicleId)
                }
            }

            // Process alerts
            for alert in opData.alerts {
                let serviceAlert = ServiceAlert(
                    id: alert.alert_id,
                    title: alert.title,
                    summary: alert.summary,
                    operatorId: alert.operatorId,
                    severity: alert.severity ?? "info",
                    url: alert.url.flatMap { URL(string: $0) }
                )
                alertsForOp.append(serviceAlert)
            }

            // Create operator data
            let opUpdatedAt = parseTimestamp(payload.updated_at)
            let operatorData = OperatorData(
                updatedAt: opUpdatedAt,
                etasByLine: etasByLine,
                vehicles: vehiclesForOp,
                alerts: alertsForOp,
                routes: routesForOp,
                source: opData.source,
                attribution: opData.attribution,
                operatorId: opKey
            )
            operatorsData[opKey] = operatorData

            // Build source info
            if opKey == "oasa" {
                linkForOASA = opData.source.flatMap { URL(string: $0) }
            } else if opKey == "oseth" {
                linkForOSETH = opData.source.flatMap { URL(string: $0) }
            }
        }

        // Process sources from payload
        for source in payload.sources ?? [] {
            let sourceInfo = SourceInfo(
                operatorId: source.operatorId ?? "",
                name: source.name ?? "",
                sourceUrl: source.source_url.flatMap { URL(string: $0) },
                attribution: source.attribution ?? "",
                isLive: source.is_live ?? false,
                lastTested: parseTimestamp(source.last_tested ?? ""),
                notes: source.notes
            )
            allSources.append(sourceInfo)
        }

        return UnifiedAirportBuses(
            updatedAt: parseTimestamp(payload.updated_at),
            operators: operatorsData,
            sources: allSources,
            vehiclesByLine: vehiclesByLine,
            linkForOASA: linkForOASA,
            linkForOSETH: linkForOSETH
        )
    }

    /// Reduce single operator payload
    static func reduceOperator(_ payload: UnifiedOperatorPayload) -> OperatorData {
        var etasByLine: [String: [Int]] = [:]
        var vehiclesForOp: [AirportBusVehicle] = []
        var alertsForOp: [ServiceAlert] = []

        // Process arrivals
        for arrival in payload.arrivals {
            let line = arrival.line_id
            if let minutes = arrival.estimated_minutes, minutes >= 0 {
                etasByLine[line, default: []].append(minutes)
            }
        }

        // Sort ETAs
        for line in etasByLine.keys {
            etasByLine[line] = etasByLine[line]?.sorted() ?? []
        }

        // Process vehicles
        for vehicle in payload.vehicles {
            if vehicle.latitude != 0 && vehicle.longitude != 0 {
                let coord = CLLocationCoordinate2D(latitude: vehicle.latitude, longitude: vehicle.longitude)
                let vehicleId = AirportBusVehicle(
                    id: vehicle.vehicle_id,
                    lineId: vehicle.line_id,
                    coordinate: coord,
                    toAirport: vehicle.to_airport
                )
                vehiclesForOp.append(vehicleId)
            }
        }

        // Process alerts
        for alert in payload.alerts {
            let serviceAlert = ServiceAlert(
                id: alert.alert_id,
                title: alert.title,
                summary: alert.summary,
                operatorId: alert.operatorId,
                severity: alert.severity ?? "info",
                url: alert.url.flatMap { URL(string: $0) }
            )
            alertsForOp.append(serviceAlert)
        }

        return OperatorData(
            updatedAt: parseTimestamp(payload.updated_at ?? ""),
            etasByLine: etasByLine,
            vehicles: vehiclesForOp,
            alerts: alertsForOp,
            routes: payload.routes,
            source: payload.source,
            attribution: payload.attribution,
            operatorId: payload.operatorId
        )
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
