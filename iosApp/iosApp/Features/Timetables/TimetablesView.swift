import SwiftUI

/// Airport-focused departures. The previous full timetables browser was
/// noisy: a user trying to catch a plane had to know to pick line M3,
/// type "airport" in the search box, and visually filter the resulting
/// 60-row scroll. This screen replaces that workflow with a single
/// purpose-built view: pick the station you're at, see only airport
/// trains, split by direction.
///
/// The file is still named TimetablesView.swift / TimetablesView so the
/// Xcode project membership doesn't need to change; we just rebadge the
/// tab label and rewrite the body.
struct TimetablesView: View {
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var schedules = SyrmosSchedulesStore.shared
    @State private var selectedStationId: String = AirportData.defaultStationId
    @State private var departures: [Departure] = []
    @State private var nowTick = Date()

    private let refreshTimer = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationStack {
            List {
                Section {
                    StationPickerRow(
                        selectedStationId: $selectedStationId,
                        loc: loc
                    )
                } header: {
                    Text(loc.language == .greek ? "Σταθμός" : loc.language == .albanian ? "Stacioni" : "Station")
                } footer: {
                    Text(footerText)
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                }

                Section {
                    if toAirport.isEmpty {
                        emptyRow
                    } else {
                        ForEach(toAirport.prefix(8)) { dep in
                            AirportRow(departure: dep, loc: loc)
                        }
                    }
                } header: {
                    SectionHeader(
                        icon: "airplane.departure",
                        title: loc.language == .greek ? "Προς Αεροδρόμιο" : loc.language == .albanian ? "Drejt Aeroportit" : "To Airport",
                        tint: .metroBlue
                    )
                }

                Section {
                    if fromAirport.isEmpty {
                        emptyRow
                    } else {
                        ForEach(fromAirport.prefix(8)) { dep in
                            AirportRow(departure: dep, loc: loc)
                        }
                    }
                } header: {
                    SectionHeader(
                        icon: "airplane.arrival",
                        title: loc.language == .greek ? "Από Αεροδρόμιο" : loc.language == .albanian ? "Nga Aeroporti" : "From Airport",
                        tint: .arrivalModerate
                    )
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.syrmosBackground)
            .safeAreaInset(edge: .top, spacing: 8) {
                CompactTabHeader(
                    loc.language == .greek ? "Αεροδρόμιο" :
                    loc.language == .albanian ? "Aeroporti" : "Airport"
                )
            }
            .toolbar(.hidden, for: .navigationBar)
            .onAppear(perform: reload)
            .onReceive(refreshTimer) { _ in
                nowTick = Date()
                reload()
            }
            .onChange(of: selectedStationId) { _, _ in reload() }
        }
    }

    private var footerText: String {
        switch loc.language {
        case .greek: return "Επόμενα δρομολόγια αεροδρομίου από τον επιλεγμένο σταθμό. Αλλάξτε σταθμό για να δείτε άλλη τοποθεσία."
        case .albanian: return "Nisjet e ardhshme për aeroportin nga stacioni i zgjedhur. Ndrysho stacionin për një vendndodhje tjetër."
        case .english: return "Next airport-train departures from the selected station. Change station to see another stop."
        }
    }

    private var emptyRow: some View {
        Text(loc.language == .greek ? "Δεν υπάρχουν διαθέσιμα δρομολόγια." :
             loc.language == .albanian ? "Nuk ka nisje të disponueshme." :
             "No departures available.")
            .font(.subheadline)
            .foregroundStyle(.secondary)
            .padding(.vertical, 6)
    }

    private var toAirport: [Departure] {
        departures.filter {
            $0.serviceType == "airport" && AirportData.isAirportBoundDirection($0.direction)
        }
    }

    private var fromAirport: [Departure] {
        departures.filter {
            $0.serviceType == "airport" && !AirportData.isAirportBoundDirection($0.direction)
        }
    }

    private func reload() {
        let station = AirportData.station(for: selectedStationId)
        let lineIds = station.lineIds.filter { AirportData.airportLines.contains($0) }
        Task { @MainActor in
            departures = ScheduleProjector.nextDepartures(
                for: selectedStationId,
                lineIds: lineIds,
                limit: 20
            )
        }
    }
}

private struct StationPickerRow: View {
    @Binding var selectedStationId: String
    let loc: LocalizationManager

    var body: some View {
        Menu {
            ForEach(AirportData.stationsByGroup, id: \.line) { group in
                Section(group.label(loc.language)) {
                    ForEach(group.stations, id: \.id) { st in
                        Button {
                            selectedStationId = st.id
                        } label: {
                            HStack {
                                Text(loc.language == .greek ? st.nameEl : st.name)
                                if st.id == selectedStationId {
                                    Spacer()
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                }
            }
        } label: {
            let current = AirportData.station(for: selectedStationId)
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(loc.language == .greek ? current.nameEl : current.name)
                        .font(.headline)
                        .foregroundStyle(.primary)
                    Text(current.lineIds
                        .filter { AirportData.airportLines.contains($0) }
                        .joined(separator: " · "))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.up.chevron.down")
                    .font(.footnote)
                    .foregroundStyle(.tertiary)
            }
            .padding(.vertical, 4)
            .contentShape(Rectangle())
        }
    }
}

private struct SectionHeader: View {
    let icon: String
    let title: String
    let tint: Color
    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .foregroundStyle(tint)
            Text(title)
        }
    }
}

private struct AirportRow: View {
    let departure: Departure
    let loc: LocalizationManager

    var body: some View {
        HStack(spacing: 12) {
            Group {
                if let iconName = TimetablesIcons.vehicleImageName(
                    lineId: departure.lineId,
                    direction: departure.direction,
                    isAirport: true
                ), UIImage(named: iconName) != nil {
                    Image(iconName)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 44, height: 30)
                } else {
                    Circle()
                        .fill(SyrmosData.lineColor(for: departure.lineId))
                        .frame(width: 12, height: 12)
                }
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(SyrmosData.line(for: departure.lineId)?.name ?? departure.lineId)
                    .font(.subheadline)
                    .fontWeight(.medium)
                Text(directionLabel)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 2) {
                Text(departure.minutesAway <= 1
                     ? (loc.language == .greek ? "Τώρα" : loc.language == .albanian ? "Tani" : "Now")
                     : "\(departure.minutesAway) min")
                    .font(.headline)
                    .foregroundStyle(color(for: departure.minutesAway))
                Text(departure.time)
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
        }
        .padding(.vertical, 2)
    }

    private var directionLabel: String {
        let dir = departure.direction
        switch loc.language {
        case .greek: return "προς \(dir)"
        case .albanian: return "drejt \(dir)"
        case .english: return "towards \(dir)"
        }
    }

    private func color(for minutes: Int) -> Color {
        switch minutes {
        case 0...2: return .arrivalSoon
        case 3...5: return .arrivalModerate
        default: return .arrivalFar
        }
    }
}

// MARK: - Static airport data

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
            case ("A1", .greek): return "Προαστιακός A1"
            case ("A1", .albanian): return "Treni periferik A1"
            case ("A1", _): return "Suburban A1"
            default: return line
            }
        }
    }

    /// Lines whose schedule actually contains airport-tagged service
    /// (serviceType == "airport"). Used both to filter station picker
    /// entries and to pass the right lineIds to ScheduleProjector.
    static let airportLines: Set<String> = ["M3", "M3_AIR", "A1"]

    /// Direction labels emitted by the projector that count as
    /// "airport-bound". Anything else (Dimotiko Theatro, Piraeus, etc)
    /// is treated as "from airport". Case-insensitive substring match.
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

    static let stationsByGroup: [Group] = [
        Group(line: "M3", stations: m3Stations),
        Group(line: "A1", stations: a1Stations),
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
}
