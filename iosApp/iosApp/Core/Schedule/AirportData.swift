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
            case ("M3", .italian): return "Metro Linea 3"
            case ("M3", _): return "Metro Line 3"
            case ("A1", .greek): return "Προαστιακός A1 (Πειραιάς - Αεροδρόμιο)"
            case ("A1", .albanian): return "Treni periferik A1 (Pireu - Aeroporti)"
            case ("A1", .italian): return "Suburbano A1 (Pireo - Aeroporto)"
            case ("A1", _): return "Suburban A1 (Piraeus - Airport)"
            case ("A2", .greek): return "Προαστιακός A2 (Άνω Λιόσια - Αεροδρόμιο)"
            case ("A2", .albanian): return "Treni periferik A2 (Ano Liosia - Aeroporti)"
            case ("A2", .italian): return "Suburbano A2 (Ano Liosia - Aeroporto)"
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

// MARK: - Multi-airport hubs

/// The airport hubs Syrmos covers. Athens (Eleftherios Venizelos) has direct rail
/// (M3 metro, A1/A2 suburban) plus 24h express buses. Thessaloniki (Makedonia)
/// has no direct rail: you ride the metro to a terminus and finish on a short
/// shuttle bus, or take a direct OASTH/OSETH bus. The Thessaloniki airport station
/// and its X3/2X shuttles are seeded by
/// ops/syrmos-api/scripts/seed_thessaloniki_airport_buses.py.
enum AirportCity: String, CaseIterable, Identifiable {
    case athens
    case thessaloniki
    var id: String { rawValue }
}

/// A localized 4-language string, in the same (en, el, al, it) order as the
/// airportText() helper used throughout the timetables view.
struct AirportL10n {
    let en: String
    let el: String
    let al: String
    let it: String
    func text(_ language: AppLanguage) -> String {
        switch language {
        case .greek: return el
        case .albanian: return al
        case .italian: return it
        default: return en
        }
    }
}

struct AirportHeroPill {
    let title: String
    let icon: String
}

/// One way to reach or leave an airport, rendered as a row in the connections
/// card. `metroBus` means "ride the metro, then a shuttle bus".
struct AirportConnection: Identifiable {
    enum Mode { case metro, rail, bus, metroBus }
    let id: String
    let mode: Mode
    let badge: String
    let colorHex: UInt
    let title: AirportL10n
    let detail: AirportL10n
}

/// A metro leg whose real timetable we can surface for an airport connection.
/// The shuttle bus itself has no per-stop schedule, but the metro that feeds it
/// does, so we show the next metro departures at the interchange station.
struct AirportMetroLeg: Identifiable {
    let stationId: String
    let lineIds: [String]
    let badge: String
    let colorHex: UInt
    let stationName: AirportL10n
    let towards: AirportL10n
    var id: String { stationId }
}

struct AirportHub: Identifiable {
    let city: AirportCity
    let code: String
    let name: String
    let cityName: AirportL10n
    let subtitle: AirportL10n
    let gradient: [UInt]
    let pills: [AirportHeroPill]
    /// Station the offline projector reads for direct rail departures FROM the
    /// terminal. Empty for Thessaloniki, which is reached by bus.
    let airportStationId: String
    let directRailLineIds: [String]
    let connections: [AirportConnection]
    let metroLegs: [AirportMetroLeg]
    var id: String { city.rawValue }
    var hasDirectRail: Bool { !airportStationId.isEmpty }

    static func hub(_ city: AirportCity) -> AirportHub {
        switch city {
        case .athens: return athens
        case .thessaloniki: return thessaloniki
        }
    }

    static let all: [AirportHub] = [athens, thessaloniki]

    static let athens = AirportHub(
        city: .athens,
        code: "ATH",
        name: "Eleftherios Venizelos",
        cityName: AirportL10n(en: "Athens", el: "Αθήνα", al: "Athinë", it: "Atene"),
        subtitle: AirportL10n(
            en: "Routes, scheduled departures and trip planning",
            el: "Διαδρομές, προγραμματισμένες αναχωρήσεις και σχεδιασμός",
            al: "Linja, nisje të programuara dhe planifikim udhëtimi",
            it: "Percorsi, partenze programmate e pianificazione"
        ),
        gradient: [0x0B3D71, 0x155E9F, 0x45398F],
        pills: [
            AirportHeroPill(title: "M3", icon: "tram.fill"),
            AirportHeroPill(title: "A1", icon: "tram.fill"),
            AirportHeroPill(title: "X95", icon: "bus.fill"),
            AirportHeroPill(title: "24/7", icon: "clock.fill"),
        ],
        airportStationId: "M3_AER",
        directRailLineIds: ["M3", "M3_AIR", "A1", "A2"],
        connections: [],
        metroLegs: []
    )

    static let thessaloniki = AirportHub(
        city: .thessaloniki,
        code: "SKG",
        name: "Makedonia",
        cityName: AirportL10n(en: "Thessaloniki", el: "Θεσσαλονίκη", al: "Selanik", it: "Salonicco"),
        subtitle: AirportL10n(
            en: "Metro plus a shuttle, or a direct bus to the terminal",
            el: "Μετρό και λεωφορείο, ή απευθείας λεωφορείο στον τερματικό",
            al: "Metro plus autobus, ose autobus i drejtpërdrejtë te terminali",
            it: "Metro più navetta, o bus diretto al terminal"
        ),
        gradient: [0x0B5563, 0x0E7C8B, 0x1E5FA0],
        pills: [
            AirportHeroPill(title: "L2", icon: "tram.fill"),
            AirportHeroPill(title: "X3", icon: "bus.fill"),
            AirportHeroPill(title: "1X", icon: "bus.fill"),
            AirportHeroPill(title: "24/7", icon: "clock.fill"),
        ],
        airportStationId: "",
        directRailLineIds: [],
        connections: [
            AirportConnection(
                id: "l2x3",
                mode: .metroBus,
                badge: "L2 + X3",
                colorHex: 0x0070FF,
                title: AirportL10n(
                    en: "Metro Line 2 + X3 shuttle",
                    el: "Μετρό Γραμμή 2 + λεωφορείο Χ3",
                    al: "Metro Linja 2 + autobusi X3",
                    it: "Metro Linea 2 + navetta X3"
                ),
                detail: AirportL10n(
                    en: "Ride Line 2 to Mikra, then the X3 shuttle to the terminal (about 10 min).",
                    el: "Με τη Γραμμή 2 ως τη Μίκρα, μετά το λεωφορείο Χ3 στον τερματικό (περίπου 10 λεπτά).",
                    al: "Merr Linjën 2 deri te Mikra, pastaj autobusin X3 te terminali (rreth 10 min).",
                    it: "Con la Linea 2 fino a Mikra, poi la navetta X3 al terminal (circa 10 min)."
                )
            ),
            AirportConnection(
                id: "l1_2x",
                mode: .metroBus,
                badge: "L1 + 2X",
                colorHex: 0xFF0000,
                title: AirportL10n(
                    en: "Metro Line 1 + 2X shuttle",
                    el: "Μετρό Γραμμή 1 + λεωφορείο 2Χ",
                    al: "Metro Linja 1 + autobusi 2X",
                    it: "Metro Linea 1 + navetta 2X"
                ),
                detail: AirportL10n(
                    en: "Ride Line 1 to Nea Elvetia, then the 2X shuttle to the terminal.",
                    el: "Με τη Γραμμή 1 ως τη Νέα Ελβετία, μετά το λεωφορείο 2Χ στον τερματικό.",
                    al: "Merr Linjën 1 deri te Nea Elvetia, pastaj autobusin 2X te terminali.",
                    it: "Con la Linea 1 fino a Nea Elvetia, poi la navetta 2X al terminal."
                )
            ),
            AirportConnection(
                id: "bus1x",
                mode: .bus,
                badge: "1X / 1N",
                colorHex: 0x0E7490,
                title: AirportL10n(
                    en: "Direct airport bus",
                    el: "Απευθείας λεωφορείο αεροδρομίου",
                    al: "Autobus i drejtpërdrejtë",
                    it: "Bus diretto aeroporto"
                ),
                detail: AirportL10n(
                    en: "1X links the terminal with the city centre, the New Railway Station and KTEL Makedonia. 1N runs overnight.",
                    el: "Το 1Χ συνδέει τον τερματικό με το κέντρο, τον Νέο Σιδηροδρομικό Σταθμό και τα ΚΤΕΛ Μακεδονία. Το 1Ν λειτουργεί τη νύχτα.",
                    al: "1X lidh terminalin me qendrën, Stacionin e Ri Hekurudhor dhe KTEL Makedonia. 1N punon natën.",
                    it: "1X collega il terminal con il centro, la nuova stazione ferroviaria e KTEL Makedonia. 1N opera di notte."
                )
            ),
            AirportConnection(
                id: "bus79",
                mode: .bus,
                badge: "79",
                colorHex: 0x0E7490,
                title: AirportL10n(
                    en: "Bus 79",
                    el: "Λεωφορείο 79",
                    al: "Autobusi 79",
                    it: "Autobus 79"
                ),
                detail: AirportL10n(
                    en: "Connects the terminal with the eastern bus station (IKEA / Pylaia).",
                    el: "Συνδέει τον τερματικό με τον ανατολικό σταθμό υπεραστικών (IKEA / Πυλαία).",
                    al: "Lidh terminalin me stacionin lindor të autobusëve (IKEA / Pylaia).",
                    it: "Collega il terminal con la stazione bus orientale (IKEA / Pylaia)."
                )
            ),
        ],
        metroLegs: [
            AirportMetroLeg(
                stationId: "TM2_MIK",
                lineIds: ["TM2"],
                badge: "L2",
                colorHex: 0x0070FF,
                stationName: AirportL10n(en: "Mikra", el: "Μίκρα", al: "Mikra", it: "Mikra"),
                towards: AirportL10n(en: "X3 shuttle to the terminal", el: "Λεωφορείο Χ3 στον τερματικό", al: "Autobusi X3 te terminali", it: "Navetta X3 al terminal")
            ),
            AirportMetroLeg(
                stationId: "TM1_NEL",
                lineIds: ["TM1"],
                badge: "L1",
                colorHex: 0xFF0000,
                stationName: AirportL10n(en: "Nea Elvetia", el: "Νέα Ελβετία", al: "Nea Elvetia", it: "Nea Elvetia"),
                towards: AirportL10n(en: "2X shuttle to the terminal", el: "Λεωφορείο 2Χ στον τερματικό", al: "Autobusi 2X te terminali", it: "Navetta 2X al terminal")
            ),
        ]
    )
}
