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

    /// Human-friendly arrival countdown. "Now" for the train that's
    /// already at the platform, "5 min" for the close ones, and
    /// "3h 21min" when the next service is hours away (typical for
    /// late-night views or stations far downstream of a terminus).
    func minutesAwayDisplay(language: AppLanguage) -> String {
        if minutesAway <= 1 {
            switch language {
            case .greek: return "Τώρα"
            case .albanian: return "Tani"
            default: return "Now"
            }
        }
        if minutesAway < 60 {
            return "\(minutesAway) min"
        }
        let h = minutesAway / 60
        let m = minutesAway % 60
        if m == 0 { return "\(h)h" }
        return "\(h)h \(m)min"
    }
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

        let nodes: [MapStationNode] = grouped.flatMap { _, group in
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
        return mergeColocatedNodes(initial: nodes)
            .sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
    }

    /// Second-pass merge for stations that share a physical location but
    /// have different names. The primary clustering groups stations by
    /// normalized name first, then by proximity — so M3 "Dimotiko Theatro"
    /// and T7 "Dimarhio / Dimotiko Theatro" sit ~32 m apart but never
    /// compare against each other because their names differ. This pass
    /// runs a final 60 m distance sweep over the produced nodes and
    /// folds any two that are clearly the same hub. The threshold is
    /// tight enough to never collapse adjacent-but-separate stops.
    private static func mergeColocatedNodes(initial: [MapStationNode]) -> [MapStationNode] {
        let radiusMeters = 60.0
        var merged: [MapStationNode] = []
        for node in initial {
            if let idx = merged.firstIndex(where: {
                distanceMeters(
                    $0.coordinate.latitude, $0.coordinate.longitude,
                    node.coordinate.latitude, node.coordinate.longitude
                ) <= radiusMeters
            }) {
                let existing = merged[idx]
                let combinedLineIds = Array(Set(existing.lineIds + node.lineIds)).sorted()
                var combinedMap = existing.stationIdByLineId
                for (lineId, stationId) in node.stationIdByLineId where combinedMap[lineId] == nil {
                    combinedMap[lineId] = stationId
                }
                let count = 2.0
                let lat = (existing.coordinate.latitude + node.coordinate.latitude) / count
                let lon = (existing.coordinate.longitude + node.coordinate.longitude) / count
                // Pick the more descriptive name for the merged node — the
                // longer one is usually the "Dimarhio / Dimotiko Theatro"
                // style dual label rather than a single mode's shorthand.
                let pickName = node.name.count > existing.name.count ? node : existing
                merged[idx] = MapStationNode(
                    id: existing.id,
                    stationIds: existing.stationIds + node.stationIds,
                    stationIdByLineId: combinedMap,
                    name: pickName.name,
                    nameEl: pickName.nameEl,
                    coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon),
                    lineIds: combinedLineIds,
                    isInterchange: combinedLineIds.count > 1 || existing.isInterchange || node.isInterchange
                )
            } else {
                merged.append(node)
            }
        }
        return merged
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
        // Offline-first: no auto-poll. The suburban live-trains feed is
        // refreshed only when the user taps Check now in Settings.
    }

    deinit {
        task?.cancel()
    }

    /// Public single-shot refresh — Settings -> Check now wires this up.
    func refresh() async {
        await LiveTrainService.fetchOnce(self)
    }

    private static func fetchOnce(_ instance: LiveTrainService?) async {
        let url = URL(string: "https://api-syrmos.peterdsp.dev/api/trains")!
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
            await MainActor.run {
                instance?.trains = parsed
                // Live suburban positions came back from the API: we're
                // online. Surface it on the home offline-alive pill.
                LiveDataFreshness.shared.markLive()
            }
        } catch {
            // Silent — the user's previous Check now still has whatever
            // the last poll returned. Map keeps rendering bundled
            // polylines + stations regardless.
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
    /// Last-known coordinate snapshot. Useful for the bottom sheet
    /// distance display. The Map view IGNORES this field and recomputes
    /// the live coordinate every frame from `originEpoch + stops` so
    /// the train moves continuously instead of jumping between
    /// simulator ticks.
    let coordinate: CLLocationCoordinate2D
    let isAirportService: Bool
    // Per-frame position inputs. Filled by TrainSimulatorService when
    // the train is built. Empty stops + 0 epoch are treated as "not
    // enough info, fall back to coordinate snapshot".
    let originEpoch: TimeInterval
    let totalTravelMinutes: Int
    /// Pre-resolved (stationId, minutesFromOrigin) for the train's
    /// direction. Already ordered by stop_sequence.
    let stops: [(stationId: String, minutesFromOrigin: Double)]
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
        let first = await projectTrains()
        await MainActor.run { instance?.trains = first }
        while !Task.isCancelled {
            // 5s cadence — fast enough for a believable live feel, slow
            // enough that we don't thrash SwiftUI Map annotations with
            // dozens of @Published updates per second across all tabs.
            // LivePositionsService runs its own 15s API poll on top; this
            // loop just re-interpolates from the cached snapshot.
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            if Task.isCancelled { return }
            let next = await projectTrains()
            await MainActor.run { instance?.trains = next }
        }
    }

    /// Build SimulatedTrain values by interpolating each API-reported active
    /// train along its line's station_offsets table. The map dot lands on
    /// the same minute the bottom sheet projects, because both are derived
    /// from the same projector.
    private static func projectTrains() async -> [SimulatedTrain] {
        let service = await LivePositionsService.shared
        let activeTrains = await service.trains
        let offsetsByLine = await service.offsets
        if activeTrains.isEmpty || offsetsByLine.isEmpty { return [] }

        let stationCoords = StationCoordinateLookup.shared
        let nowEpoch = Date().timeIntervalSince1970

        let lineMeta: [String: (name: String, type: TransitType, terminalA: String, terminalB: String)] = [
            "M1":     ("Line 1",      .metro, "Piraeus",          "Kifissia"),
            "M2":     ("Line 2",      .metro, "Anthoupoli",       "Elliniko"),
            "M3":     ("Line 3",      .metro, "Dimotiko Theatro", "Doukissis Plakentias"),
            "M3_AIR": ("Line 3",      .metro, "Doukissis Plakentias", "Airport"),
            "T6":     ("Tram T6",     .tram,  "Syntagma",         "Pikrodafni"),
            "T7":     ("Tram T7",     .tram,  "Akti Poseidonos",  "Asklipiio Voulas"),
        ]

        var result: [SimulatedTrain] = []
        for train in activeTrains {
            guard let meta = lineMeta[train.lineId] else { continue }
            // station_offsets keys M3_AIR under M3; the projector uses that
            // remap internally so do the same here.
            let offsetsLineKey = (train.lineId == "M3_AIR") ? "M3" : train.lineId
            guard let stops = offsetsByLine[offsetsLineKey]?[train.directionKey], stops.count >= 2 else { continue }

            let elapsed = (nowEpoch - train.originDepartureEpoch) / 60.0
            if elapsed < 0 || elapsed > Double(train.totalTravelMinutes) + 0.5 { continue }

            var segIdx = 0
            for i in 0..<(stops.count - 1) {
                if stops[i].minutesFromOrigin <= elapsed && elapsed < stops[i + 1].minutesFromOrigin {
                    segIdx = i
                    break
                }
                if i == stops.count - 2 { segIdx = i }
            }

            let from = stops[segIdx]
            let to = stops[segIdx + 1]
            guard let fromCoord = stationCoords.coordinate(for: from.stationId),
                  let toCoord = stationCoords.coordinate(for: to.stationId) else { continue }

            let segDuration = to.minutesFromOrigin - from.minutesFromOrigin
            let frac = segDuration > 0 ? min(max((elapsed - from.minutesFromOrigin) / segDuration, 0), 1) : 0

            // Walk along the OSM-derived polyline arc between the two
            // stations instead of straight-lerping their coordinates.
            // The straight lerp cuts the chord of every track curve,
            // which is exactly why trains were drawn alongside the
            // blue M3 line rather than on it. The polyline already
            // hugs the real track so a polyline-relative interpolation
            // keeps the moving icon on the rails.
            let polylineLineId = train.lineId == "M3_AIR" ? "M3_AIR" : train.lineId
            let polyline = SyrmosRouteShapesStore.shared.coordinates(for: polylineLineId)
                ?? SyrmosRouteShapesStore.shared.coordinates(for: offsetsLineKey)
            let arcPosition = pointOnPolylineArc(
                polyline: polyline,
                from: CLLocationCoordinate2D(latitude: fromCoord.lat, longitude: fromCoord.lon),
                to: CLLocationCoordinate2D(latitude: toCoord.lat, longitude: toCoord.lon),
                fraction: frac
            )
            let lat = arcPosition.latitude
            let lon = arcPosition.longitude

            let destination = train.directionKey == "outbound" ? meta.terminalB : meta.terminalA
            let displayLineId = train.lineId == "M3_AIR" ? "M3" : train.lineId
            let fromName = stationCoords.englishName(for: from.stationId) ?? from.stationId
            let toName = stationCoords.englishName(for: to.stationId) ?? to.stationId
            let isAirport = train.lineId == "M3_AIR"

            let stopTuples = stops.map { (stationId: $0.stationId, minutesFromOrigin: $0.minutesFromOrigin) }
            result.append(SimulatedTrain(
                id: train.id,
                lineId: displayLineId,
                lineName: meta.name,
                lineType: meta.type,
                direction: train.directionKey,
                destinationName: destination,
                currentStationName: fromName,
                nextStationName: toName,
                coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon),
                isAirportService: isAirport,
                originEpoch: train.originDepartureEpoch,
                totalTravelMinutes: train.totalTravelMinutes,
                stops: stopTuples
            ))
        }
        return result
    }

    /// Interpolate a point along the polyline arc between two station
    /// anchors. Finds the closest polyline vertex to each anchor,
    /// measures cumulative haversine distance along the polyline
    /// between those vertices, then walks `fraction` of that arc
    /// distance from the `from` end. Falls back to a straight chord
    /// lerp when the polyline is empty or both anchors snap to the
    /// same vertex (very short inter-station spacing).
    fileprivate static func pointOnPolylineArc(
        polyline: [CLLocationCoordinate2D]?,
        from: CLLocationCoordinate2D,
        to: CLLocationCoordinate2D,
        fraction: Double
    ) -> CLLocationCoordinate2D {
        let f = min(max(fraction, 0), 1)
        guard let line = polyline, line.count >= 2 else {
            return CLLocationCoordinate2D(
                latitude: from.latitude + (to.latitude - from.latitude) * f,
                longitude: from.longitude + (to.longitude - from.longitude) * f
            )
        }
        let fromIdx = closestPolylineIndex(line, to: from)
        let toIdx = closestPolylineIndex(line, to: to)
        if fromIdx == toIdx {
            return CLLocationCoordinate2D(
                latitude: from.latitude + (to.latitude - from.latitude) * f,
                longitude: from.longitude + (to.longitude - from.longitude) * f
            )
        }
        let reversed = fromIdx > toIdx
        let start = min(fromIdx, toIdx)
        let end = max(fromIdx, toIdx)
        var cumulative: [Double] = [0]
        cumulative.reserveCapacity(end - start + 1)
        for i in start..<end {
            cumulative.append(cumulative.last! + haversineMeters(line[i], line[i + 1]))
        }
        guard let total = cumulative.last, total > 0 else { return line[start] }
        let target = total * (reversed ? (1 - f) : f)
        for i in 0..<(cumulative.count - 1) {
            if cumulative[i + 1] >= target {
                let segLen = cumulative[i + 1] - cumulative[i]
                let segFrac = segLen > 0 ? (target - cumulative[i]) / segLen : 0
                let a = line[start + i]
                let b = line[start + i + 1]
                return CLLocationCoordinate2D(
                    latitude: a.latitude + (b.latitude - a.latitude) * segFrac,
                    longitude: a.longitude + (b.longitude - a.longitude) * segFrac
                )
            }
        }
        return line[end]
    }

    private static func closestPolylineIndex(_ line: [CLLocationCoordinate2D], to point: CLLocationCoordinate2D) -> Int {
        var bestIdx = 0
        var bestDist = Double.greatestFiniteMagnitude
        for (i, p) in line.enumerated() {
            let d = haversineMeters(p, point)
            if d < bestDist { bestDist = d; bestIdx = i }
        }
        return bestIdx
    }

    private static func haversineMeters(_ a: CLLocationCoordinate2D, _ b: CLLocationCoordinate2D) -> Double {
        let r = 6371000.0
        let dLat = (b.latitude - a.latitude) * .pi / 180
        let dLon = (b.longitude - a.longitude) * .pi / 180
        let lat1 = a.latitude * .pi / 180
        let lat2 = b.latitude * .pi / 180
        let h = sin(dLat / 2) * sin(dLat / 2)
            + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(min(1, sqrt(h)))
    }
}
