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
        .init(id: "M1", name: "Line 1", nameEl: "Γραμμή 1", terminalA: "Piraeus", terminalB: "Kifisia", stationCount: 24, color: .metroGreen, type: .metro),
        .init(id: "M2", name: "Line 2", nameEl: "Γραμμή 2", terminalA: "Anthoupoli", terminalB: "Elliniko", stationCount: 20, color: .metroRed, type: .metro),
        .init(id: "M3", name: "Line 3", nameEl: "Γραμμή 3", terminalA: "Dimotiko Theatro", terminalB: "Douk. Plakentias", stationCount: 23, color: .metroBlue, type: .metro),
        .init(id: "M3A", name: "Line 3 Airport", nameEl: "Γραμμή 3 Αεροδρόμιο", terminalA: "Dimotiko Theatro", terminalB: "Airport", stationCount: 27, color: .metroBlue, type: .metro),
        .init(id: "T6", name: "Tram T6", nameEl: "Τραμ Τ6", terminalA: "Syntagma", terminalB: "Pikrodafni", stationCount: 19, color: .tramOrange, type: .tram),
        .init(id: "T7", name: "Tram T7", nameEl: "Τραμ Τ7", terminalA: "Akti Posidonos", terminalB: "Asklipiio Voulas", stationCount: 37, color: .tramOrange, type: .tram),
        .init(id: "P1", name: "Suburban Airport-Piraeus", nameEl: "Προαστιακός Αεροδρόμιο-Πειραιάς", terminalA: "Airport", terminalB: "Piraeus", stationCount: 12, color: .suburbanPurple, type: .suburban),
        .init(id: "P2", name: "Suburban Piraeus-Kiato", nameEl: "Προαστιακός Πειραιάς-Κιάτο", terminalA: "Piraeus", terminalB: "Kiato", stationCount: 18, color: .suburbanPurple, type: .suburban),
    ]

    static func line(for id: String) -> TransitLine? {
        if id == "M3A" { return lines.first { $0.id == "M3A" } }
        return lines.first { $0.id == id }
    }

    static func lineColor(for id: String) -> Color {
        switch id {
        case "M1": return .metroGreen
        case "M2": return .metroRed
        case "M3", "M3A": return .metroBlue
        case "T6", "T7": return .tramOrange
        default: return .suburbanPurple
        }
    }

    // MARK: - Stations per Line (uses StationCoords for map data)

    static func stations(for lineId: String) -> [TransitStation] {
        switch lineId {
        case "M1": return StationCoords.line1.map { makeStation($0, primaryLine: "M1") }
        case "M2": return StationCoords.line2.map { makeStation($0, primaryLine: "M2") }
        case "M3", "M3A": return StationCoords.line3.map { makeStation($0, primaryLine: "M3") }
        case "T6": return StationCoords.tramT6.map { makeStation($0, primaryLine: "T6") }
        case "T7": return StationCoords.tramT7.map { makeStation($0, primaryLine: "T7") }
        case "P1": return StationCoords.suburbanP1.map { makeStation($0, primaryLine: "P1") }
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
        case "P1":
            return [
                ServicePattern(lineId: "P1", direction: "Piraeus", frequencyMinutes: 30, serviceType: "suburban"),
                ServicePattern(lineId: "P1", direction: "Airport", frequencyMinutes: 30, serviceType: "suburban"),
            ]
        case "P2":
            return [
                ServicePattern(lineId: "P2", direction: "Kiato", frequencyMinutes: 60, serviceType: "suburban"),
                ServicePattern(lineId: "P2", direction: "Piraeus", frequencyMinutes: 60, serviceType: "suburban"),
            ]
        default:
            return []
        }
    }
}
