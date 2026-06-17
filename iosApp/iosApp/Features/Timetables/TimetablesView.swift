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
    @StateObject private var locationService = LocationService()
    @State private var selectedStationId: String = AirportData.defaultStationId
    @State private var dayOffset: Int = 0
    @State private var departures: [Departure] = []
    @State private var nowTick = Date()
    /// Once the user manually picks a station we stop auto-snapping
    /// to the nearest one on every location ping; respect their choice.
    @State private var didAutoPickNearest: Bool = false

    private let refreshTimer = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationStack {
            List {
                Section {
                    DayPickerRow(selectedOffset: $dayOffset)
                        .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
                        .listRowBackground(Color.clear)
                } header: {
                    Text(loc.language == .greek ? "Ημέρα" : loc.language == .albanian ? "Dita" : "Day")
                }

                Section {
                    StationPickerRow(
                        selectedStationId: Binding(
                            get: { selectedStationId },
                            set: { newValue in
                                selectedStationId = newValue
                                didAutoPickNearest = true  // lock in user choice
                            }
                        )
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
            .onAppear {
                locationService.requestIfNeeded()
                reload()
            }
            .onReceive(refreshTimer) { _ in
                nowTick = Date()
                reload()
            }
            .onChange(of: selectedStationId) { _, _ in reload() }
            .onChange(of: dayOffset) { _, _ in reload() }
            .onChange(of: locationService.nearbyStations.first?.id) { _, _ in
                autoSelectNearest()
            }
        }
    }

    private func autoSelectNearest() {
        guard !didAutoPickNearest else { return }
        // Pick the closest airport-serving stop. The nearest station in
        // LocationService might be e.g. a tram-only stop that has no
        // airport service; in that case fall back to the next-nearest
        // one that does.
        let nearest = locationService.nearbyStations.first { ns in
            ns.station.lineIds.contains { AirportData.airportLines.contains($0) }
        }
        if let target = nearest {
            // node.stationIds is the cluster's per-line ids; pick whichever
            // one our AirportData groups know about so the picker can
            // display it without resorting to fallback formatting.
            let candidate = target.station.stationIds.first { AirportData.knows(id: $0) }
                ?? target.station.id
            if AirportData.knows(id: candidate) {
                selectedStationId = candidate
                didAutoPickNearest = true
            }
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
        let offset = dayOffset
        Task { @MainActor in
            departures = ScheduleProjector.nextDepartures(
                for: selectedStationId,
                lineIds: lineIds,
                limit: offset == 0 ? 20 : 60,
                dayOffset: offset
            )
        }
    }
}

private struct DayPickerRow: View {
    @Binding var selectedOffset: Int
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(0..<7, id: \.self) { offset in
                    let isSelected = selectedOffset == offset
                    Button {
                        selectedOffset = offset
                    } label: {
                        VStack(spacing: 2) {
                            Text(dayName(offset))
                                .font(.caption2)
                                .foregroundStyle(isSelected ? .white : .secondary)
                            Text(dayNumber(offset))
                                .font(.headline)
                                .foregroundStyle(isSelected ? .white : .primary)
                        }
                        .frame(width: 50, height: 50)
                        .background(
                            Circle()
                                .fill(isSelected ? Color.metroBlue : Color(uiColor: .secondarySystemGroupedBackground))
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private func dayName(_ offset: Int) -> String {
        let date = Calendar.current.date(byAdding: .day, value: offset, to: Date()) ?? Date()
        if offset == 0 {
            switch loc.language {
            case .greek: return "ΣΗΜ"
            case .albanian: return "SOT"
            case .english: return "TODAY"
            }
        }
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: localeCode)
        fmt.dateFormat = "EEE"
        return fmt.string(from: date).uppercased()
    }

    private func dayNumber(_ offset: Int) -> String {
        let date = Calendar.current.date(byAdding: .day, value: offset, to: Date()) ?? Date()
        let fmt = DateFormatter()
        fmt.dateFormat = "d"
        return fmt.string(from: date)
    }

    private var localeCode: String {
        switch loc.language {
        case .greek: return "el_GR"
        case .albanian: return "sq_AL"
        case .english: return "en_US"
        }
    }
}

private struct StationPickerRow: View {
    @Binding var selectedStationId: String
    /// MUST be @ObservedObject locally. Receiving loc as a plain `let`
    /// reference from the parent does not subscribe this child to its
    /// changes, so the Menu label kept rendering whichever name the
    /// view was first built with (Greek "Σύνταγμα" persisting after a
    /// switch to Albanian). Re-observing here triggers a body recompute
    /// the moment loc.language flips.
    @ObservedObject private var loc = LocalizationManager.shared

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

    /// Whether the picker has a known entry for this id. Used by the
    /// nearest-station auto-pick to filter cluster station ids down to
    /// one the picker can actually display.
    static func knows(id: String) -> Bool {
        byId[id] != nil
    }
}
