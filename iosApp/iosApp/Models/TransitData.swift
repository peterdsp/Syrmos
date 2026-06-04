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
        .init(id: "M3", name: "Line 3", nameEl: "Γραμμή 3", terminalA: "Dimotiko Theatro", terminalB: "Airport", stationCount: 27, color: .metroBlue, type: .metro),
        .init(id: "T6", name: "Tram T6", nameEl: "Τραμ Τ6", terminalA: "Syntagma", terminalB: "Pikrodafni", stationCount: 19, color: .tramOrange, type: .tram),
        .init(id: "T7", name: "Tram T7", nameEl: "Τραμ Τ7", terminalA: "Akti Poseidonos", terminalB: "Asklipiio Voulas", stationCount: 37, color: .tramOrange, type: .tram),
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
        let now = Calendar.current.component(.hour, from: Date()) * 60 +
                  Calendar.current.component(.minute, from: Date())
        var departures: [Departure] = []

        for lineId in lineIds {
            let patterns = servicePatterns(for: lineId, stationId: stationId)
            for pattern in patterns {
                for i in 1...4 {
                    let mins = pattern.frequencyMinutes * i
                    let depTime = now + mins
                    let h = (depTime / 60) % 24
                    let m = depTime % 60
                    departures.append(Departure(
                        time: String(format: "%02d:%02d", h, m),
                        lineId: pattern.lineId,
                        direction: pattern.direction,
                        minutesAway: mins,
                        serviceType: pattern.serviceType
                    ))
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
        let grouped = Dictionary(grouping: StationCoords.allStations) { station in
            station.displayKey
        }

        return grouped.map { key, group in
            let primary = group.first!
            let lineIds = Array(Set(group.flatMap { $0.lineIds })).sorted()
            var stationIdByLineId: [String: String] = [:]

            for station in group {
                for lineId in station.lineIds where stationIdByLineId[lineId] == nil {
                    stationIdByLineId[lineId] = station.id
                }
            }

            return MapStationNode(
                id: key,
                stationIds: group.map { $0.id },
                stationIdByLineId: stationIdByLineId,
                name: primary.name,
                nameEl: primary.nameEl,
                coordinate: CLLocationCoordinate2D(
                    latitude: group.map(\.coordinate.latitude).reduce(0, +) / Double(group.count),
                    longitude: group.map(\.coordinate.longitude).reduce(0, +) / Double(group.count)
                ),
                lineIds: lineIds,
                isInterchange: lineIds.count > 1 || group.contains(where: { $0.isInterchange })
            )
        }
        .sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
    }
}

@MainActor
final class LiveTrainService: ObservableObject {
    @Published var trains: [LiveTrain] = []

    private var task: Task<Void, Never>?

    init() {
        task = Task { [weak self] in
            await self?.run()
        }
    }

    deinit {
        task?.cancel()
    }

    private func run() async {
        while !Task.isCancelled {
            do {
                try await observeStream()
            } catch {
                try? await Task.sleep(nanoseconds: 2_000_000_000)
            }
        }
    }

    private func observeStream() async throws {
        let url = URL(string: "https://railway.gov.gr/api/train-stream")!
        let request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 45)
        let (bytes, _) = try await URLSession.shared.bytes(for: request)

        var currentEvent: String?
        var dataLines: [String] = []

        for try await rawLine in bytes.lines {
            let line = String(rawLine)
            if line.isEmpty {
                if currentEvent == "trainPositionsUx" {
                    await updateTrains(from: dataLines.joined(separator: "\n"))
                }
                currentEvent = nil
                dataLines = []
                continue
            }

            if line.hasPrefix("event:") {
                currentEvent = String(line.dropFirst(6)).trimmingCharacters(in: .whitespaces)
            } else if line.hasPrefix("data:") {
                dataLines.append(String(line.dropFirst(5)).trimmingCharacters(in: .whitespaces))
            }
        }
    }

    @MainActor
    private func updateTrains(from payload: String) {
        guard let data = payload.data(using: .utf8) else {
            trains = []
            return
        }

        do {
            let decoded = try JSONDecoder().decode(TrainPositionsPayload.self, from: data)
            trains = decoded.positions.compactMap { position in
                guard let lineId = inferLineId(position: position),
                      let lat = position.lat,
                      let lng = position.lng else { return nil }

                return LiveTrain(
                    id: position.id ?? position.trainId ?? position.locomotiveId ?? position.name ?? UUID().uuidString,
                    lineId: lineId,
                    trainNumber: position.trainNumber ?? position.name ?? position.locomotiveNumber ?? "Train",
                    origin: position.origin ?? "",
                    destination: position.destination ?? "",
                    nextStation: position.nextStation ?? "",
                    delayMinutes: position.delay ?? 0,
                    coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lng)
                )
            }
        } catch {
            trains = []
        }
    }

    private func inferLineId(position: TrainPositionPayload) -> String? {
        let text = [
            position.origin,
            position.destination,
            position.nextStation,
            position.corridor,
        ]
        .compactMap { $0?.lowercased() }
        .joined(separator: " ")

        if text.contains("pirair") || (text.contains("πειραι") && text.contains("αεροδρομ")) {
            return "A1"
        }
        if text.contains("ανω λιοσια") && text.contains("αεροδρομ") {
            return "A2"
        }
        if text.contains("αθην") && text.contains("χαλκιδ") {
            return "A3"
        }
        if text.contains("πειραι") && text.contains("κιατ") {
            return "A4"
        }
        return nil
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
    var displayKey: String {
        let source = name.isEmpty ? nameEl : name
        return source
            .lowercased()
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "-", with: "")
            .replacingOccurrences(of: ".", with: "")
    }
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
