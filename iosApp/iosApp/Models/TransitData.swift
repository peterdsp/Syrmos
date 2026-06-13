import SwiftUI
import CoreLocation

// MARK: - Models

struct TransitLine: Identifiable {
    let id: String
    let name: String
    let nameEl: String
    let terminalA: String
    let terminalB: String
    let stationCount: Int
    let color: Color
    let type: TransitType
}

enum TransitType: String, CaseIterable {
    case metro = "Metro"
    case tram = "Tram"
    case suburban = "Suburban Railway"
}

struct TransitStation: Identifiable {
    let id: String
    let name: String
    let nameEl: String
    let coordinate: CLLocationCoordinate2D
    let lineIds: [String]
    let isInterchange: Bool
}

struct Departure: Identifiable {
    let id = UUID()
    let time: String
    let lineId: String
    let direction: String
    let minutesAway: Int
    let serviceType: String
}

// MARK: - Service patterns from official STASY/Hellenic Train timetables

struct ServicePattern {
    let lineId: String
    let direction: String
    let frequencyMinutes: Int
    let serviceType: String
}

// MARK: - Static Data

enum SyrmosData {

    static let lines: [TransitLine] = [
        .init(id: "M1", name: "Line 1", nameEl: "Γραμμή 1", terminalA: "Piraeus", terminalB: "Kifissia", stationCount: 24, color: .metroGreen, type: .metro),
        .init(id: "M2", name: "Line 2", nameEl: "Γραμμή 2", terminalA: "Anthoupoli", terminalB: "Elliniko", stationCount: 20, color: .metroRed, type: .metro),
        .init(id: "M3", name: "Line 3", nameEl: "Γραμμή 3", terminalA: "Dimotiko Theatro", terminalB: "Doukissis Plakentias", stationCount: 27, color: .metroBlue, type: .metro),
        .init(id: "T6", name: "Tram T6", nameEl: "Τραμ Τ6", terminalA: "Syntagma", terminalB: "Pikrodafni", stationCount: 19, color: .tramOrange, type: .tram),
        .init(id: "T7", name: "Tram T7", nameEl: "Τραμ Τ7", terminalA: "Akti Poseidonos", terminalB: "Asklipiio Voulas", stationCount: 43, color: .tramOrange, type: .tram),
        .init(id: "A1", name: "A1 Piraeus-Airport", nameEl: "Α1 Πειραιάς-Αεροδρόμιο", terminalA: "Piraeus", terminalB: "Airport", stationCount: 19, color: .suburbanPurple, type: .suburban),
        .init(id: "A2", name: "A2 Ano Liosia-Airport", nameEl: "Α2 Άνω Λιόσια-Αεροδρόμιο", terminalA: "Ano Liosia", terminalB: "Airport", stationCount: 12, color: .suburbanPurple, type: .suburban),
        .init(id: "A3", name: "A3 Athens-Chalcis", nameEl: "Α3 Αθήνα-Χαλκίδα", terminalA: "Athens", terminalB: "Chalcis", stationCount: 17, color: .suburbanPurple, type: .suburban),
        .init(id: "A4", name: "A4 Piraeus-Kiato", nameEl: "Α4 Πειραιάς-Κιάτο", terminalA: "Piraeus", terminalB: "Kiato", stationCount: 20, color: .suburbanPurple, type: .suburban),
    ]

    static func line(for id: String) -> TransitLine? {
        lines.first { $0.id == id }
    }

    static func lineColor(for id: String) -> Color {
        switch id {
        case "M1": return .metroGreen
        case "M2": return .metroRed
        case "M3": return .metroBlue
        case "T6", "T7": return .tramOrange
        default: return .suburbanPurple
        }
    }

    // MARK: - Stations per Line (uses StationCoords for map data)

    static func stations(for lineId: String) -> [TransitStation] {
        switch lineId {
        case "M1": return StationCoords.line1.map { makeStation($0, primaryLine: "M1") }
        case "M2": return StationCoords.line2.map { makeStation($0, primaryLine: "M2") }
        case "M3": return StationCoords.line3.map { makeStation($0, primaryLine: "M3") }
        case "T6": return StationCoords.tramT6.map { makeStation($0, primaryLine: "T6") }
        case "T7": return StationCoords.tramT7.map { makeStation($0, primaryLine: "T7") }
        case "A1": return StationCoords.suburbanA1.map { makeStation($0, primaryLine: "A1") }
        case "A2": return StationCoords.suburbanA2.map { makeStation($0, primaryLine: "A2") }
        case "A3": return StationCoords.suburbanA3.map { makeStation($0, primaryLine: "A3") }
        case "A4": return StationCoords.suburbanA4.map { makeStation($0, primaryLine: "A4") }
        default: return []
        }
    }

    private static func makeStation(_ s: (id: String, name: String, nameEl: String, lat: Double, lon: Double), primaryLine: String) -> TransitStation {
        let allLines = StationCoords.lineAssociations[s.id] ?? [primaryLine]
        return TransitStation(
            id: s.id,
            name: s.name,
            nameEl: s.nameEl,
            coordinate: CLLocationCoordinate2D(latitude: s.lat, longitude: s.lon),
            lineIds: allLines,
            isInterchange: allLines.count > 1
        )
    }

    // MARK: - Departures (with correct service patterns)

    // Line 3 airport section: stations past Douk. Plakentias
    static let line3AirportOnlyStations: Set<String> = [
        "M3_PAL", "M3_PEK", "M3_KRP", "M3_AER"
    ]

    static func sampleDepartures(for stationId: String, lineIds: [String]) -> [Departure] {
        // Anchor next departures to clock-aligned slots so the countdown
        // actually ticks down between refreshes instead of always reporting
        // "5 min / 10 min / 15 min / 20 min" from the moment of call.
        // e.g. on a 5-minute frequency at 14:31 the next departures are
        // 14:35 (4 min), 14:40 (9 min), 14:45 (14 min), 14:50 (19 min);
        // 30 seconds later they become 14:35 (3 min), and so on.
        let date = Date()
        let calendar = Calendar.current
        let nowComponents = calendar.dateComponents([.hour, .minute, .second], from: date)
        let nowMinutes = (nowComponents.hour ?? 0) * 60 + (nowComponents.minute ?? 0)
        let secondOffset = (nowComponents.second ?? 0) >= 30 ? 1 : 0
        var departures: [Departure] = []

        for lineId in lineIds {
            let patterns = servicePatterns(for: lineId, stationId: stationId)
            for pattern in patterns {
                let freq = max(pattern.frequencyMinutes, 1)
                // The next clock-aligned slot in the future for this line.
                // We treat slot t such that t % freq == 0 since midnight.
                var nextSlot = ((nowMinutes / freq) + 1) * freq
                for _ in 0..<4 {
                    let mins = nextSlot - nowMinutes - secondOffset
                    let depTime = nextSlot % (24 * 60)
                    let h = depTime / 60
                    let m = depTime % 60
                    departures.append(Departure(
                        time: String(format: "%02d:%02d", h, m),
                        lineId: pattern.lineId,
                        direction: pattern.direction,
                        minutesAway: max(mins, 0),
                        serviceType: pattern.serviceType
                    ))
                    nextSlot += freq
                }
            }
        }

        return departures.sorted { $0.minutesAway < $1.minutesAway }
    }

    private static func servicePatterns(for lineId: String, stationId: String) -> [ServicePattern] {
        switch lineId {
        case "M1":
            return [
                ServicePattern(lineId: "M1", direction: "Kifisia", frequencyMinutes: 5, serviceType: "regular"),
                ServicePattern(lineId: "M1", direction: "Piraeus", frequencyMinutes: 5, serviceType: "regular"),
            ]
        case "M2":
            return [
                ServicePattern(lineId: "M2", direction: "Elliniko", frequencyMinutes: 4, serviceType: "regular"),
                ServicePattern(lineId: "M2", direction: "Anthoupoli", frequencyMinutes: 4, serviceType: "regular"),
            ]
        case "M3", "M3A":
            if line3AirportOnlyStations.contains(stationId) {
                // Past Douk. Plakentias: only airport trains, every 36 min
                return [
                    ServicePattern(lineId: "M3", direction: "Airport", frequencyMinutes: 36, serviceType: "airport"),
                    ServicePattern(lineId: "M3", direction: "Dimotiko Theatro", frequencyMinutes: 36, serviceType: "airport"),
                ]
            } else {
                // Regular service to Douk. Plakentias + airport trains
                return [
                    ServicePattern(lineId: "M3", direction: "Douk. Plakentias", frequencyMinutes: 5, serviceType: "regular"),
                    ServicePattern(lineId: "M3", direction: "Dimotiko Theatro", frequencyMinutes: 5, serviceType: "regular"),
                    ServicePattern(lineId: "M3", direction: "Airport", frequencyMinutes: 36, serviceType: "airport"),
                ]
            }
        case "T6":
            return [
                ServicePattern(lineId: "T6", direction: "Pikrodafni", frequencyMinutes: 9, serviceType: "regular"),
                ServicePattern(lineId: "T6", direction: "Syntagma", frequencyMinutes: 9, serviceType: "regular"),
            ]
        case "T7":
            return [
                ServicePattern(lineId: "T7", direction: "Asklipiio Voulas", frequencyMinutes: 12, serviceType: "regular"),
                ServicePattern(lineId: "T7", direction: "Akti Posidonos", frequencyMinutes: 12, serviceType: "regular"),
            ]
        case "A1":
            return [
                ServicePattern(lineId: "A1", direction: "Airport", frequencyMinutes: 30, serviceType: "suburban"),
                ServicePattern(lineId: "A1", direction: "Piraeus", frequencyMinutes: 30, serviceType: "suburban"),
            ]
        case "A2":
            return [
                ServicePattern(lineId: "A2", direction: "Airport", frequencyMinutes: 60, serviceType: "suburban"),
                ServicePattern(lineId: "A2", direction: "Ano Liosia", frequencyMinutes: 60, serviceType: "suburban"),
            ]
        case "A3":
            return [
                ServicePattern(lineId: "A3", direction: "Chalcis", frequencyMinutes: 90, serviceType: "regional"),
                ServicePattern(lineId: "A3", direction: "Athens", frequencyMinutes: 90, serviceType: "regional"),
            ]
        case "A4":
            return [
                ServicePattern(lineId: "A4", direction: "Kiato", frequencyMinutes: 60, serviceType: "regional"),
                ServicePattern(lineId: "A4", direction: "Piraeus", frequencyMinutes: 60, serviceType: "regional"),
            ]
        default:
            return []
        }
    }
}

extension SyrmosData {
    static var mapStations: [MapStationNode] {
        let grouped = Dictionary(grouping: StationCoords.allStations.sorted {
            if $0.coordinate.latitude != $1.coordinate.latitude {
                return $0.coordinate.latitude < $1.coordinate.latitude
            }
            if $0.coordinate.longitude != $1.coordinate.longitude {
                return $0.coordinate.longitude < $1.coordinate.longitude
            }
            return $0.id < $1.id
        }, by: { $0.clusterKey })

        return grouped.flatMap { _, group in
            group.clusterByProximity().enumerated().map { index, cluster in
                let primary = cluster.first!
                let lineIds = Array(Set(cluster.flatMap { $0.lineIds })).sorted()
                var stationIdByLineId: [String: String] = [:]

                for station in cluster {
                    for lineId in station.lineIds where stationIdByLineId[lineId] == nil {
                        stationIdByLineId[lineId] = station.id
                    }
                }

                return MapStationNode(
                    id: "\(primary.clusterKey)_\(index)_\(cluster.latitudeBucket)_\(cluster.longitudeBucket)",
                    stationIds: cluster.map { $0.id },
                    stationIdByLineId: stationIdByLineId,
                    name: primary.name,
                    nameEl: primary.nameEl,
                    coordinate: CLLocationCoordinate2D(
                        latitude: cluster.map(\.coordinate.latitude).reduce(0, +) / Double(cluster.count),
                        longitude: cluster.map(\.coordinate.longitude).reduce(0, +) / Double(cluster.count)
                    ),
                    lineIds: lineIds,
                    isInterchange: lineIds.count > 1 || cluster.contains(where: { $0.isInterchange })
                )
            }
        }
        .sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
    }
}

final class LiveTrainService: ObservableObject, @unchecked Sendable {
    /// Shared instance so the whole app uses a single polling task — avoids
    /// duplicate work that was contributing to UI freezes on iOS.
    static let shared = LiveTrainService()

    @MainActor @Published var trains: [LiveTrain] = []

    private var task: Task<Void, Never>?

    private struct TrainsPayload: Decodable {
        let updatedAt: String?
        let count: Int
        let trains: [TrainItem]
    }

    private struct TrainItem: Decodable {
        let id: String
        let lineId: String
        let trainNumber: String
        let origin: String
        let destination: String
        let nextStation: String
        let delayMinutes: Int
        let lat: Double
        let lng: Double
    }

    init() {
        task = Task.detached(priority: .utility) { @Sendable [weak self] in
            await LiveTrainService.pollLoop(self)
        }
    }

    deinit {
        task?.cancel()
    }

    private static func pollLoop(_ instance: LiveTrainService?) async {
        let url = URL(string: "https://api-syrmos.peterdsp.dev/api/trains")!
        while !Task.isCancelled {
            do {
                var req = URLRequest(url: url)
                req.timeoutInterval = 10
                req.cachePolicy = .reloadIgnoringLocalCacheData
                let (data, response) = try await URLSession.shared.data(for: req)
                guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
                    throw URLError(.badServerResponse)
                }
                let payload = try JSONDecoder().decode(TrainsPayload.self, from: data)
                let parsed: [LiveTrain] = payload.trains.map { t in
                    LiveTrain(
                        id: t.id,
                        lineId: t.lineId,
                        trainNumber: t.trainNumber,
                        origin: t.origin,
                        destination: t.destination,
                        nextStation: t.nextStation,
                        delayMinutes: t.delayMinutes,
                        coordinate: CLLocationCoordinate2D(latitude: t.lat, longitude: t.lng)
                    )
                }
                await MainActor.run { instance?.trains = parsed }
            } catch {
                // ignore — keep showing previous trains until next poll succeeds
            }
            try? await Task.sleep(nanoseconds: 10_000_000_000)  // 10s
        }
    }
}

struct LiveTrain: Identifiable {
    let id: String
    let lineId: String
    let trainNumber: String
    let origin: String
    let destination: String
    let nextStation: String
    let delayMinutes: Int
    let coordinate: CLLocationCoordinate2D
}

struct MapStationNode: Identifiable {
    let id: String
    let stationIds: [String]
    let stationIdByLineId: [String: String]
    let name: String
    let nameEl: String
    let coordinate: CLLocationCoordinate2D
    let lineIds: [String]
    let isInterchange: Bool

    var displayName: String {
        name.isEmpty ? nameEl : name
    }
}

private extension TransitStation {
    var clusterKey: String {
        [name.normalizeStationText(), nameEl.normalizeStationText()]
            .filter { !$0.isEmpty }
            .sorted()
            .joined(separator: "|")
    }

    var displayKey: String { clusterKey }
}

private extension Array where Element == TransitStation {
    func clusterByProximity(radiusMeters: Double = 300.0) -> [[TransitStation]] {
        var clusters: [[TransitStation]] = []
        for station in self {
            if let index = clusters.firstIndex(where: { cluster in
                cluster.contains(where: {
                    distanceMeters(
                        $0.coordinate.latitude,
                        $0.coordinate.longitude,
                        station.coordinate.latitude,
                        station.coordinate.longitude
                    ) <= radiusMeters
                })
            }) {
                clusters[index].append(station)
            } else {
                clusters.append([station])
            }
        }
        return clusters
    }

    var latitudeBucket: Int {
        Int((map(\.coordinate.latitude).reduce(0, +) / Double(count)) * 10000)
    }

    var longitudeBucket: Int {
        Int((map(\.coordinate.longitude).reduce(0, +) / Double(count)) * 10000)
    }
}

private extension String {
    func normalizeStationText() -> String {
        lowercased()
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "-", with: "")
            .replacingOccurrences(of: ".", with: "")
    }
}

private func distanceMeters(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
    let earthRadius = 6_371_000.0
    let dLat = (lat2 - lat1) * .pi / 180
    let dLon = (lon2 - lon1) * .pi / 180
    let a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) *
        sin(dLon / 2) * sin(dLon / 2)
    return 2 * earthRadius * atan2(sqrt(a), sqrt(1 - a))
}

private struct TrainPositionsPayload: Decodable {
    let positions: [TrainPositionPayload]
}

private struct TrainPositionPayload: Decodable {
    let id: String?
    let trainId: String?
    let name: String?
    let trainNumber: String?
    let origin: String?
    let destination: String?
    let nextStation: String?
    let delay: Int?
    let lat: Double?
    let lng: Double?
    let locomotiveNumber: String?
    let locomotiveId: String?
    let corridor: String?
}

// MARK: - Simulated Train

struct SimulatedTrain: Identifiable {
    let id: String
    let lineId: String
    let lineName: String
    let lineType: TransitType
    let direction: String
    let destinationName: String
    let currentStationName: String
    let nextStationName: String
    let coordinate: CLLocationCoordinate2D
    let isAirportService: Bool
}

// MARK: - Train Simulator Service

final class TrainSimulatorService: ObservableObject, @unchecked Sendable {
    /// Shared instance — single timer powers both Map and any other view.
    static let shared = TrainSimulatorService()

    @MainActor @Published var trains: [SimulatedTrain] = []

    private var task: Task<Void, Never>?

    init() {
        task = Task.detached(priority: .utility) { @Sendable [weak self] in
            await TrainSimulatorService.runLoop(self)
        }
    }

    deinit {
        task?.cancel()
    }

    private static func runLoop(_ instance: TrainSimulatorService?) async {
        let first = simulateTrains()
        await MainActor.run { instance?.trains = first }
        while !Task.isCancelled {
            // 5s cadence — fast enough for a believable live feel, slow
            // enough that we don't thrash SwiftUI Map annotations with
            // dozens of @Published updates per second across all tabs.
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            if Task.isCancelled { return }
            let next = simulateTrains()
            await MainActor.run { instance?.trains = next }
        }
    }

    private static let dwellMetro = 0.5
    private static let dwellTram = 0.4
    private static let dwellTerminal = 1.0

    private static func haversine(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
        let r = 6_371_000.0
        let dLat = (lat2 - lat1) * .pi / 180
        let dLon = (lon2 - lon1) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }

    private static func smoothEase(_ t: Double) -> Double {
        if t < 0.15 {
            let x = t / 0.15
            return x * x * 0.15
        } else if t > 0.85 {
            let x = (t - 0.85) / 0.15
            return 0.85 + (1 - (1 - x) * (1 - x)) * 0.15
        }
        return t
    }

    private static func simulateTrains() -> [SimulatedTrain] {
        let calendar = Calendar.current
        let tz = TimeZone(identifier: "Europe/Athens")!
        let now = Date()
        let components = calendar.dateComponents(in: tz, from: now)
        let hour = components.hour ?? 0
        let minute = components.minute ?? 0
        let second = components.second ?? 0
        let nanosecond = components.nanosecond ?? 0
        let nowMinutes = Double(hour) * 60 + Double(minute) + Double(second) / 60.0 + (Double(nanosecond) / 1_000_000_000) / 60.0

        let serviceStart = 5.0 * 60
        let serviceEnd = 25.0 * 60
        let adjustedNow = nowMinutes < serviceStart ? nowMinutes + 24 * 60 : nowMinutes
        guard adjustedNow >= serviceStart && adjustedNow <= serviceEnd else { return [] }

        struct LineConfig {
            let id: String
            let name: String
            let type: TransitType
            let terminalA: String
            let terminalB: String
            let stations: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)]
            let travelMinutes: Double
            let dwellMinutes: Double
            let frequency: Double
        }

        let configs: [LineConfig] = [
            LineConfig(id: "M1", name: "Line 1", type: .metro, terminalA: "Piraeus", terminalB: "Kifissia",
                       stations: StationCoords.line1, travelMinutes: 1.8, dwellMinutes: dwellMetro, frequency: 5),
            LineConfig(id: "M2", name: "Line 2", type: .metro, terminalA: "Anthoupoli", terminalB: "Elliniko",
                       stations: StationCoords.line2, travelMinutes: 1.8, dwellMinutes: dwellMetro, frequency: 4),
            LineConfig(id: "M3", name: "Line 3", type: .metro, terminalA: "Dimotiko Theatro", terminalB: "Airport",
                       stations: StationCoords.line3, travelMinutes: 1.8, dwellMinutes: dwellMetro, frequency: 5),
            LineConfig(id: "T6", name: "Tram T6", type: .tram, terminalA: "Syntagma", terminalB: "Pikrodafni",
                       stations: StationCoords.tramT6, travelMinutes: 2.2, dwellMinutes: dwellTram, frequency: 9),
            LineConfig(id: "T7", name: "Tram T7", type: .tram, terminalA: "Akti Poseidonos", terminalB: "Asklipiio Voulas",
                       stations: StationCoords.tramT7, travelMinutes: 2.2, dwellMinutes: dwellTram, frequency: 12),
        ]

        var result: [SimulatedTrain] = []

        for config in configs {
            guard config.stations.count >= 2 else { continue }

            for direction in ["outbound", "inbound"] {
                let stns = direction == "outbound" ? config.stations : config.stations.reversed()

                struct Timing {
                    let station: (id: String, name: String, nameEl: String, lat: Double, lon: Double)
                    let arrival: Double
                    let departure: Double
                }

                var segDists: [Double] = []
                var totalDist = 0.0
                for i in 0..<(stns.count - 1) {
                    let d = Self.haversine(stns[i].lat, stns[i].lon, stns[i+1].lat, stns[i+1].lon)
                    segDists.append(d)
                    totalDist += d
                }
                if totalDist < 1 { totalDist = 1 }
                let totalTravelMins = config.travelMinutes * Double(stns.count - 1)

                var timings: [Timing] = []
                var cumulative = 0.0
                for (i, stn) in stns.enumerated() {
                    let arrival = cumulative
                    let dwell = (i == 0 || i == stns.count - 1) ? dwellTerminal : config.dwellMinutes
                    timings.append(Timing(station: stn, arrival: arrival, departure: arrival + dwell))
                    if i < stns.count - 1 {
                        let segTravel = totalTravelMins * (segDists[i] / totalDist)
                        cumulative = arrival + dwell + segTravel
                    }
                }

                let tripDuration = timings.last!.arrival
                let offset = direction == "inbound" ? config.frequency / 2 : 0
                var departureTime = serviceStart + offset
                var trainIdx = 0

                while departureTime <= serviceEnd {
                    let elapsed = adjustedNow - departureTime
                    if elapsed >= 0 && elapsed <= tripDuration {
                        var segIdx = 0
                        for i in stride(from: timings.count - 1, through: 0, by: -1) {
                            if timings[i].departure <= elapsed { segIdx = i; break }
                        }
                        segIdx = min(segIdx, timings.count - 2)
                        let from = timings[segIdx]
                        let to = timings[segIdx + 1]

                        let lat: Double
                        let lon: Double
                        if elapsed < from.departure {
                            lat = from.station.lat
                            lon = from.station.lon
                        } else {
                            let travelStart = from.departure
                            let travelEnd = to.arrival
                            let travelDuration = travelEnd - travelStart
                            let rawFrac = travelDuration > 0 ? min(max((elapsed - travelStart) / travelDuration, 0), 1) : 0
                            let frac = smoothEase(rawFrac)
                            lat = from.station.lat + (to.station.lat - from.station.lat) * frac
                            lon = from.station.lon + (to.station.lon - from.station.lon) * frac
                        }

                        let isAirport = config.id == "M3" && direction == "outbound" && segIdx >= config.stations.count - 6
                        let dest = direction == "outbound" ? config.terminalB : config.terminalA

                        result.append(SimulatedTrain(
                            id: "\(config.id)_\(direction)_\(trainIdx)",
                            lineId: config.id,
                            lineName: config.name,
                            lineType: config.type,
                            direction: direction,
                            destinationName: dest,
                            currentStationName: from.station.name,
                            nextStationName: to.station.name,
                            coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon),
                            isAirportService: isAirport
                        ))
                    }
                    departureTime += config.frequency
                    trainIdx += 1
                }
            }
        }
        return result
    }
}
