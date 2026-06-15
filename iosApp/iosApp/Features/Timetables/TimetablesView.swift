import SwiftUI

/// Full timetables browser. Pick a line, see every projected departure
/// for the chosen day (today by default). Search by destination ("airport")
/// or station name. Powered by the same ScheduleProjector that drives the
/// station detail screen, so what you see here matches reality.
struct TimetablesView: View {
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var schedules = SyrmosSchedulesStore.shared
    @ObservedObject private var timestamps = SyrmosTrainTimestampsStore.shared
    @ObservedObject private var offsets = SyrmosStationOffsetsStore.shared

    @State private var selectedLineId: String = "M3"
    @State private var selectedDayOffset: Int = 0  // 0 = today, 1 = tomorrow, ...
    @State private var searchText: String = ""

    private let lineIds: [String] = ["M1", "M2", "M3", "T6", "T7", "A1", "A2", "A3", "A4"]

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                CompactTabHeader(loc.language == .greek ? "Δρομολόγια" : "Timetables")
                linePicker
                dayPicker
                searchBar
                departuresList
            }
            .scrollContentBackground(.hidden)
            .background(Color.syrmosBackground)
            .toolbar(.hidden, for: .navigationBar)
        }
    }

    private var linePicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(lineIds, id: \.self) { lineId in
                    let color = SyrmosData.lineColor(for: lineId)
                    // Outbound icon as the chip's mode glyph so the chip
                    // matches the row artwork the user sees below.
                    let iconName = TimetablesIcons.vehicleImageName(
                        lineId: lineId,
                        direction: SyrmosData.line(for: lineId)?.terminalB ?? "",
                        isAirport: false
                    )
                    Button {
                        selectedLineId = lineId
                    } label: {
                        HStack(spacing: 6) {
                            if let iconName, UIImage(named: iconName) != nil {
                                Image(iconName)
                                    .resizable()
                                    .scaledToFit()
                                    .frame(width: 28, height: 18)
                            } else {
                                Circle().fill(color).frame(width: 8, height: 8)
                            }
                            Text(SyrmosData.line(for: lineId)?.name ?? lineId)
                                .font(.callout)
                                .fontWeight(selectedLineId == lineId ? .semibold : .regular)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(
                            Capsule().fill(
                                selectedLineId == lineId
                                    ? color.opacity(0.18)
                                    : Color(uiColor: .tertiarySystemGroupedBackground)
                            )
                        )
                        .overlay(
                            Capsule().strokeBorder(
                                selectedLineId == lineId ? color : .clear, lineWidth: 1
                            )
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private var dayPicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(0..<7, id: \.self) { offset in
                    let day = Calendar.current.date(byAdding: .day, value: offset, to: Date()) ?? Date()
                    Button {
                        selectedDayOffset = offset
                    } label: {
                        VStack(spacing: 2) {
                            Text(dayShortName(for: day))
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                            Text("\(Calendar.current.component(.day, from: day))")
                                .font(.body)
                                .fontWeight(.semibold)
                        }
                        .frame(width: 44, height: 44)
                        .background(
                            Circle().fill(
                                selectedDayOffset == offset
                                    ? Color.syrmosPrimary.opacity(0.18)
                                    : Color(uiColor: .tertiarySystemGroupedBackground)
                            )
                        )
                        .overlay(
                            Circle().strokeBorder(
                                selectedDayOffset == offset ? Color.syrmosPrimary : .clear, lineWidth: 1.5
                            )
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
            TextField(
                loc.language == .greek
                    ? "Αναζήτηση προορισμού (Αεροδρόμιο, Σύνταγμα...)"
                    : "Search destination (Airport, Syntagma...)",
                text: $searchText
            )
            if !searchText.isEmpty {
                Button { searchText = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(.tertiary)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(Color(uiColor: .tertiarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .padding(.horizontal, 16)
    }

    private var departuresList: some View {
        let projected = projectDay()
        let filtered = applySearch(to: projected)
        return ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    if filtered.isEmpty {
                        Text(loc.language == .greek
                             ? "Δεν υπάρχουν διαθέσιμα δρομολόγια για την επιλογή σας."
                             : "No departures available for this selection.")
                            .foregroundStyle(.secondary)
                            .padding(40)
                    } else {
                        ForEach(filtered) { dep in
                            row(for: dep)
                                .id(dep.id)
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
            .onAppear {
                if let next = filtered.first(where: { $0.minutesAway >= 0 && !isPast($0) }) {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                        withAnimation { proxy.scrollTo(next.id, anchor: .top) }
                    }
                }
            }
        }
    }

    private func row(for dep: Departure) -> some View {
        let isPastDep = isPast(dep)
        let isAirport = dep.serviceType == "airport"
        let iconName = TimetablesIcons.vehicleImageName(lineId: dep.lineId, direction: dep.direction, isAirport: isAirport)
        return HStack(spacing: 12) {
            Group {
                if let iconName, UIImage(named: iconName) != nil {
                    Image(iconName)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 44, height: 30)
                } else {
                    Circle()
                        .fill(SyrmosData.lineColor(for: dep.lineId))
                        .frame(width: 12, height: 12)
                }
            }
            .opacity(isPastDep ? 0.4 : 1.0)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(SyrmosData.line(for: dep.lineId)?.name ?? dep.lineId)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                    if isAirport {
                        Text("Airport")
                            .font(.caption2).fontWeight(.bold)
                            .padding(.horizontal, 5).padding(.vertical, 1)
                            .background(Color.metroBlue.opacity(0.15))
                            .clipShape(Capsule())
                    }
                }
                Text(dep.direction)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Text(dep.time)
                .font(.body.monospacedDigit())
                .foregroundStyle(isPastDep ? .secondary : .primary)
                .opacity(isPastDep ? 0.5 : 1.0)
        }
        .padding(.vertical, 10)
        .overlay(
            Rectangle().fill(Color.secondary.opacity(0.12)).frame(height: 0.5),
            alignment: .bottom
        )
    }

    // MARK: - Helpers

    private func dayShortName(for date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE"
        formatter.locale = Locale(identifier: loc.language == .greek ? "el_GR" : "en_GB")
        return formatter.string(from: date).uppercased()
    }

    /// Project the entire day for the selected line. For suburban A1-A4 we
    /// have real PDF-grounded timestamps from Hellenic Train and use those
    /// directly. For metro and tram we still use the band projector (STASY
    /// PDFs are next on the list). M3 stays special-cased: city + airport
    /// branches projected separately and merged.
    /// Filter the projected list against the search box. Two modes:
    /// - destination / line filter: substring match on direction or lineId
    /// - station filter: substring match on a station name along the
    ///   selected line. When a station match wins, the displayed time
    ///   shifts from origin-departure to station-arrival (origin slot
    ///   plus minutes_from_origin) so the user sees when trains actually
    ///   pass through the station they searched for.
    private func applySearch(to departures: [Departure]) -> [Departure] {
        let query = searchText.trimmingCharacters(in: .whitespaces)
        if query.isEmpty { return departures }

        // Resolve query against stations on the currently-selected line.
        // Use both the offset store (canonical EN) and SyrmosData (EL).
        let stationsForLine = SyrmosData.stations(for: selectedLineId)
        let matchedStation = stationsForLine.first { st in
            st.name.localizedCaseInsensitiveContains(query)
                || st.nameEl.localizedCaseInsensitiveContains(query)
        }
        if let st = matchedStation {
            // Build a (direction-label -> minutes-from-origin) map so we
            // know how to shift each departure to its station arrival.
            // SyrmosStationOffsetsStore uses "outbound"/"inbound"; the
            // departure rows use the line's terminalA/B label.
            let line = SyrmosData.line(for: selectedLineId)
            let outboundMin = offsets.offsetMinutes(
                lineId: selectedLineId, direction: "outbound", stationId: st.id
            )
            let inboundMin = offsets.offsetMinutes(
                lineId: selectedLineId, direction: "inbound", stationId: st.id
            )
            let outboundLabel = line?.terminalB ?? ""
            let inboundLabel = line?.terminalA ?? ""

            var shifted: [Departure] = []
            for dep in departures {
                let shiftMinutes: Int
                if dep.direction == outboundLabel {
                    shiftMinutes = outboundMin
                } else if dep.direction == inboundLabel {
                    shiftMinutes = inboundMin
                } else if dep.direction == "Airport" {
                    // M3_AIR outbound: DPL-origin, so Airport-bound trains
                    // don't normally stop at upstream city stations like
                    // Nikaia. Skip unless the station IS on the airport
                    // extension (DPL, Pallini, Kantza, Koropi, Airport).
                    let airportOffset = offsets.offsetMinutes(
                        lineId: "M3_AIR", direction: "outbound", stationId: st.id
                    )
                    if airportOffset == 0 && !st.id.hasSuffix("_DOY") {
                        continue
                    }
                    shiftMinutes = airportOffset
                } else {
                    shiftMinutes = 0
                }
                guard let baseMinute = minutesOfDay(dep.time) else { continue }
                let total = baseMinute + shiftMinutes
                let display = ((total % (24 * 60)) + 24 * 60) % (24 * 60)
                let h = display / 60
                let m = display % 60
                shifted.append(Departure(
                    time: String(format: "%02d:%02d", h, m),
                    lineId: dep.lineId,
                    direction: dep.direction,
                    minutesAway: dep.minutesAway,
                    serviceType: dep.serviceType
                ))
            }
            return shifted
        }

        // Fall back to the original destination / line substring filter.
        return departures.filter { dep in
            dep.direction.localizedCaseInsensitiveContains(query)
                || dep.lineId.localizedCaseInsensitiveContains(query)
        }
    }

    private func projectDay() -> [Departure] {
        if ["A1", "A2", "A3", "A4"].contains(selectedLineId) {
            let real = projectFromTimestamps()
            if !real.isEmpty { return real }
        }
        let primary = projectBundle(lineId: selectedLineId, displayLineId: selectedLineId)
        guard selectedLineId == "M3" else { return primary }
        let airport = projectBundle(lineId: "M3_AIR", displayLineId: "M3")
        return (primary + airport).sorted { lhs, rhs in
            if lhs.time != rhs.time { return lhs.time < rhs.time }
            return lhs.direction < rhs.direction
        }
        .enumerated()
        .map { idx, dep in
            Departure(
                time: dep.time, lineId: dep.lineId, direction: dep.direction,
                minutesAway: idx, serviceType: dep.serviceType
            )
        }
    }

    /// Build departures straight from the train-timestamps API. Each train
    /// in the store contributes one Departure per stop: the user sees the
    /// HH:MM at every station on every real train, not synthesised slots.
    private func projectFromTimestamps() -> [Departure] {
        let target = Calendar.current.date(byAdding: .day, value: selectedDayOffset, to: Date()) ?? Date()
        let dayType = athensDayType(for: target)
        let entries = timestamps.trains(lineId: selectedLineId, dayType: dayType)
        guard !entries.isEmpty else { return [] }

        // For Timetables we surface one row per train at its FIRST station,
        // labelled with its final destination. The user can drill into a
        // specific station view to see when that train calls there.
        var out: [Departure] = []
        for (idx, train) in entries.enumerated() {
            guard let first = train.stops.first, let last = train.stops.last else { continue }
            let direction = loc.language == .greek ? last.stationNameEl : last.stationNameEn
            let originLabel = loc.language == .greek ? first.stationNameEl : first.stationNameEn
            out.append(Departure(
                time: first.time,
                lineId: selectedLineId,
                direction: loc.language == .greek
                    ? "από \(originLabel) προς \(direction)"
                    : "from \(originLabel) to \(direction)",
                minutesAway: idx,
                serviceType: train.direction == "outbound" ? "outbound" : "inbound"
            ))
        }
        return out.sorted { lhs, rhs in
            if lhs.time != rhs.time { return lhs.time < rhs.time }
            return lhs.direction < rhs.direction
        }
        .enumerated()
        .map { idx, dep in
            Departure(
                time: dep.time, lineId: dep.lineId, direction: dep.direction,
                minutesAway: idx, serviceType: dep.serviceType
            )
        }
    }

    private func athensDayType(for date: Date) -> String {
        let athens = TimeZone(identifier: "Europe/Athens")!
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = athens
        let weekday = cal.component(.weekday, from: date)
        switch weekday {
        case 1: return "sun"
        case 2, 3, 4, 5: return "mon_thu"
        case 6: return "fri"
        case 7: return "sat"
        default: return "mon_thu"
        }
    }

    private func projectBundle(lineId: String, displayLineId: String) -> [Departure] {
        let target = Calendar.current.date(byAdding: .day, value: selectedDayOffset, to: Date()) ?? Date()
        let bundle = schedules.service.bundles[lineId]
        guard let bundle = bundle else { return [] }

        let athens = TimeZone(identifier: "Europe/Athens")!
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = athens
        let comp = cal.dateComponents([.year, .month, .day, .weekday], from: target)
        let weekday = comp.weekday ?? 1
        let mmdd = String(format: "%02d-%02d", comp.month ?? 1, comp.day ?? 1)
        let holiday: String? = ["01-01": "sun", "05-01": "sun", "10-28": "sun",
                                "12-25": "sun", "12-26": "sun",
                                "08-15": "aug_15", "12-24": "dec_24_31", "12-31": "dec_24_31",
                                "01-02": "sat", "01-06": "sat", "11-17": "sat"][mmdd]
        let dayType = holiday ?? {
            switch weekday {
            case 1: return "sun"
            case 2, 3, 4, 5: return "mon_thu"
            case 6: return "fri"
            case 7: return "sat"
            default: return "mon_thu"
            }
        }()

        guard let rule = bundle.rules.first(where: { $0.dayType == dayType }) else { return [] }

        // Compute open / close (handle past-midnight close like "00:30").
        let openM = minutesOfDay(rule.openTime) ?? 0
        let closeM = minutesOfDay(rule.closeTime) ?? (24 * 60)
        let effClose = closeM <= openM ? closeM + 24 * 60 : closeM

        let bands = bundle.bands
            .filter { $0.dayType == dayType }
            .sorted { (a, b) in
                (minutesOfDay(a.timeStart) ?? 0) < (minutesOfDay(b.timeStart) ?? 0)
            }

        // For a real-life timetable, a frequency band describes how often
        // trains pass in EACH direction. So a 6-minute headway means a train
        // every 6 min toward terminalA AND every 6 min toward terminalB, with
        // the two directions offset by half the headway at the same station.
        // Emit both, interleaved by departure time, so the user sees the full
        // picture instead of only the terminalB-bound trains.
        let line = SyrmosData.line(for: displayLineId)
        // M3_AIR has direction-tagged bands: outbound is a DPL -> Airport
        // run, inbound is an Airport -> Dim. Theatro run. Treating both as
        // "Airport" was the bug — an inbound train heading WEST was being
        // labelled with the destination "Airport". Now we read the band's
        // direction field and pick the right destination terminal for each.
        let terminalA = line?.terminalA ?? ""
        let terminalB = line?.terminalB ?? ""
        let directionLabel: (String?) -> String = { dir in
            switch dir {
            case "outbound": return terminalB.isEmpty ? "Airport" : terminalB
            case "inbound": return terminalA.isEmpty ? "Dimotiko Theatro" : terminalA
            default: return "Airport"
            }
        }
        let pairedDirections: [String] = [terminalA, terminalB].compactMap { $0 }.filter { !$0.isEmpty }

        struct Slot { let minute: Int; let direction: String; let label: String }
        var slots: [Slot] = []
        for band in bands {
            guard let rawStart = minutesOfDay(band.timeStart),
                  let rawEnd = minutesOfDay(band.timeEnd),
                  band.headwayMinutes > 0 else { continue }
            // M3_AIR bands are already split per direction on the server,
            // so we emit one stream per band using that direction. Other
            // lines use a single band per dayType for both directions; we
            // emit both with a half-headway offset.
            let bandDirections: [String]
            let directionOffsets: [Double]
            if lineId == "M3_AIR" {
                bandDirections = [directionLabel(band.direction)]
                directionOffsets = [0]
            } else {
                bandDirections = pairedDirections
                directionOffsets = pairedDirections.count == 2
                    ? [0, band.headwayMinutes / 2.0]
                    : [0]
            }
            for (i, direction) in bandDirections.enumerated() {
                let offset = directionOffsets[min(i, directionOffsets.count - 1)]
                var slot = Double(rawStart) + offset
                let end = Double(rawEnd)
                while slot <= end {
                    let slotMin = Int(slot.rounded())
                    if rule.is247 || (slotMin >= openM && slotMin <= effClose) {
                        slots.append(Slot(minute: slotMin, direction: direction, label: band.label))
                    }
                    slot += band.headwayMinutes
                }
            }
        }
        // Adjacent bands share a boundary minute — e.g. M3 city Mon-Thu
        // 10:30-13:30 (6 min) ends at 13:30 and 13:30-14:00 (5'30") starts
        // at 13:30, producing two identical 13:30 rows. Dedup by
        // (minute, direction) before sorting.
        var seen = Set<String>()
        slots = slots.filter { s in
            let key = "\(s.minute)|\(s.direction)"
            return seen.insert(key).inserted
        }
        slots.sort { lhs, rhs in
            if lhs.minute != rhs.minute { return lhs.minute < rhs.minute }
            return lhs.direction < rhs.direction
        }

        var out: [Departure] = []
        for (idx, s) in slots.enumerated() {
            let display = ((s.minute % (24 * 60)) + 24 * 60) % (24 * 60)
            let h = display / 60
            let m = display % 60
            out.append(Departure(
                time: String(format: "%02d:%02d", h, m),
                lineId: displayLineId,
                direction: s.direction,
                minutesAway: idx,
                serviceType: lineId == "M3_AIR" ? "airport" : (s.label.contains("late") ? "late_night" : "regular")
            ))
        }
        return out
    }

    private func minutesOfDay(_ hhmm: String) -> Int? {
        let p = hhmm.split(separator: ":")
        guard p.count == 2, let h = Int(p[0]), let m = Int(p[1]) else { return nil }
        return h * 60 + m
    }

    private func isPast(_ dep: Departure) -> Bool {
        guard selectedDayOffset == 0 else { return false }
        let now = Date()
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        formatter.timeZone = TimeZone(identifier: "Europe/Athens")
        let nowStr = formatter.string(from: now)
        return dep.time < nowStr
    }
}
