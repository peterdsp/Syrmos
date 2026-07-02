import Foundation

// Airport station groupings + helpers, extracted from TimetablesView so the
// widget's ScheduleProjector can link without pulling in the SwiftUI view.

enum AirportData {
    struct Station: Identifiable, Hashable {
        let id: String
        let name: String
        let nameEl: String
        let lineIds: [String]
    }

    struct Group {
        let line: String
        let stations: [Station]
        func label(_ lang: AppLanguage) -> String {
            switch (line, lang) {
            case ("M3", .greek): return "Μετρό Γραμμή 3"
            case ("M3", .albanian): return "Metroja Linja 3"
            case ("M3", _): return "Metro Line 3"
            case ("A1", .greek): return "Προαστιακός A1 (Πειραιάς - Αεροδρόμιο)"
            case ("A1", .albanian): return "Treni periferik A1 (Pireu - Aeroporti)"
            case ("A1", _): return "Suburban A1 (Piraeus - Airport)"
            case ("A2", .greek): return "Προαστιακός A2 (Άνω Λιόσια - Αεροδρόμιο)"
            case ("A2", .albanian): return "Treni periferik A2 (Ano Liosia - Aeroporti)"
            case ("A2", _): return "Suburban A2 (Ano Liosia - Airport)"
            default: return line
            }
        }
    }

    static let airportLines: Set<String> = ["M3", "M3_AIR", "A1", "A2"]

    static func isAirportBoundDirection(_ dir: String) -> Bool {
        let d = dir.lowercased()
        return d.contains("airport") || d.contains("αεροδρόμιο") || d.contains("aeroport")
    }

    static let defaultStationId = "M3_SYN"

    private static let m3Stations: [Station] = SyrmosData.stations(for: "M3").map {
        Station(id: $0.id, name: $0.name, nameEl: $0.nameEl, lineIds: $0.lineIds)
    }

    private static let a1Stations: [Station] = SyrmosData.stations(for: "A1").map {
        Station(id: $0.id, name: $0.name, nameEl: $0.nameEl, lineIds: $0.lineIds)
    }

    private static let a2Stations: [Station] = SyrmosData.stations(for: "A2").map {
        Station(id: $0.id, name: $0.name, nameEl: $0.nameEl, lineIds: $0.lineIds)
    }

    static let stationsByGroup: [Group] = [
        Group(line: "M3", stations: m3Stations),
        Group(line: "A1", stations: a1Stations),
        Group(line: "A2", stations: a2Stations),
    ]

    private static let byId: [String: Station] = {
        var m: [String: Station] = [:]
        for g in stationsByGroup {
            for s in g.stations where m[s.id] == nil {
                m[s.id] = s
            }
        }
        return m
    }()

    static func station(for id: String) -> Station {
        byId[id] ?? Station(id: id, name: id, nameEl: id, lineIds: [])
    }

    /// Optional variant: returns nil when the picker has no entry,
    /// used by the nearest-station auto-pick to refuse a candidate
    /// whose id wouldn't show in the menu.
    static func optional(id: String) -> Station? { byId[id] }

    static func knows(id: String) -> Bool { byId[id] != nil }

    /// Stations belonging to a given airport line, in the line's
    /// natural order. Drives the second-step picker once the user has
    /// chosen which line they want to ride.
    static func stations(for lineId: String) -> [Station] {
        stationsByGroup.first(where: { $0.line == lineId })?.stations ?? []
    }

    static func group(for lineId: String) -> Group? {
        stationsByGroup.first { $0.line == lineId }
    }
}
