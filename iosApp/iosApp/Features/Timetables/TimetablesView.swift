import SwiftUI

/// Airport-focused departures. Glass-card layout: pick a day, pick a
/// station (auto-snaps to nearest airport-serving stop on first appear),
/// then each direction surfaces the next departure on top with two
/// expand controls underneath — "Earlier" reveals trains that have
/// already left today, "All upcoming" reveals every remaining slot in
/// the day. Future days hide the "Earlier" pill since "before now"
/// doesn't apply.
struct TimetablesView: View {
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var schedules = SyrmosSchedulesStore.shared
    @StateObject private var locationService = LocationService()
    @State private var selectedLineId: String = "M3"
    @State private var selectedStationId: String = AirportData.defaultStationId
    @State private var dayOffset: Int = 0
    @State private var departures: [Departure] = []
    @State private var nowTick = Date()
    @State private var didAutoPickNearest: Bool = false

    private let refreshTimer = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    DayPickerRow(selectedOffset: $dayOffset)
                        .padding(.top, 4)

                    LinePickerCard(
                        selectedLineId: Binding(
                            get: { selectedLineId },
                            set: { newLine in
                                selectedLineId = newLine
                                // Snap station to first stop on the new
                                // line; never leave the previous line's
                                // station id dangling on a foreign line.
                                if let first = AirportData.stations(for: newLine).first {
                                    selectedStationId = first.id
                                }
                                didAutoPickNearest = true
                            }
                        )
                    )

                    StationPickerCard(
                        lineId: selectedLineId,
                        selectedStationId: Binding(
                            get: { selectedStationId },
                            set: { newValue in
                                selectedStationId = newValue
                                didAutoPickNearest = true
                            }
                        )
                    )

                    AirportSection(
                        kind: .toAirport,
                        departures: toAirport,
                        isToday: dayOffset == 0
                    )

                    AirportSection(
                        kind: .fromAirport,
                        departures: fromAirport,
                        isToday: dayOffset == 0
                    )

                    Text(footerText)
                        .font(.footnote)
                        .foregroundStyle(.tertiary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                        .padding(.top, 4)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 32)
            }
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
        let nearest = locationService.nearbyStations.first { ns in
            ns.station.lineIds.contains { AirportData.airportLines.contains($0) }
        }
        if let target = nearest {
            let candidate = target.station.stationIds.first { AirportData.knows(id: $0) }
                ?? target.station.id
            if let station = AirportData.optional(id: candidate) {
                // Set the line first so the station picker is already
                // filtered to the right line when the station snaps in.
                if let line = station.lineIds.first(where: { AirportData.airportLines.contains($0) }) {
                    selectedLineId = line
                }
                selectedStationId = candidate
                didAutoPickNearest = true
            }
        }
    }

    private var footerText: String {
        switch loc.language {
        case .greek: return "Επόμενα δρομολόγια αεροδρομίου από τον επιλεγμένο σταθμό."
        case .albanian: return "Nisjet e ardhshme për aeroportin nga stacioni i zgjedhur."
        case .english: return "Next airport-train departures from the selected station."
        }
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
        let offset = dayOffset
        Task { @MainActor in
            departures = ScheduleProjector.airportDeparturesForDay(
                stationId: selectedStationId,
                dayOffset: offset
            )
        }
    }
}

// MARK: - Glass cards

private struct LinePickerCard: View {
    @Binding var selectedLineId: String
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        Menu {
            ForEach(AirportData.stationsByGroup, id: \.line) { group in
                Button {
                    selectedLineId = group.line
                } label: {
                    HStack {
                        Text(group.label(loc.language))
                        if group.line == selectedLineId {
                            Spacer()
                            Image(systemName: "checkmark")
                        }
                    }
                }
            }
        } label: {
            HStack(alignment: .center) {
                Image(systemName: iconName)
                    .font(.title3)
                    .foregroundStyle(tint)
                    .frame(width: 36, height: 36)
                    .background(tint.opacity(0.15), in: Circle())

                VStack(alignment: .leading, spacing: 2) {
                    Text(label(.line).uppercased())
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .tracking(0.6)
                    Text(currentGroupLabel)
                        .font(.headline)
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }

                Spacer()

                Image(systemName: "chevron.up.chevron.down")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .glassCardBackground()
            .contentShape(RoundedRectangle(cornerRadius: 20))
        }
        .buttonStyle(.plain)
    }

    private var currentGroupLabel: String {
        AirportData.group(for: selectedLineId)?.label(loc.language) ?? selectedLineId
    }

    private var iconName: String {
        switch selectedLineId {
        case "M3", "M3_AIR": return "tram.tunnel.fill"
        default: return "train.side.front.car"
        }
    }

    private var tint: Color {
        SyrmosData.lineColor(for: selectedLineId)
    }

    private enum CardLabel { case line, station }
    private func label(_ which: CardLabel) -> String {
        switch (which, loc.language) {
        case (.line, .greek): return "Γραμμή"
        case (.line, .albanian): return "Linja"
        case (.line, .english): return "Line"
        case (.station, .greek): return "Σταθμός"
        case (.station, .albanian): return "Stacioni"
        case (.station, .english): return "Station"
        }
    }
}

private struct StationPickerCard: View {
    let lineId: String
    @Binding var selectedStationId: String
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        Menu {
            ForEach(AirportData.stations(for: lineId), id: \.id) { st in
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
        } label: {
            let current = AirportData.station(for: selectedStationId)
            HStack(alignment: .center) {
                Image(systemName: "mappin.circle.fill")
                    .font(.title3)
                    .foregroundStyle(.tint)
                    .frame(width: 36, height: 36)
                    .background(.tint.opacity(0.12), in: Circle())

                VStack(alignment: .leading, spacing: 2) {
                    Text(stationLabel.uppercased())
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .tracking(0.6)
                    Text(loc.language == .greek ? current.nameEl : current.name)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.primary)
                }

                Spacer()

                Image(systemName: "chevron.up.chevron.down")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .glassCardBackground()
            .contentShape(RoundedRectangle(cornerRadius: 20))
        }
        .buttonStyle(.plain)
    }

    private var stationLabel: String {
        switch loc.language {
        case .greek: return "Σταθμός"
        case .albanian: return "Stacioni"
        case .english: return "Station"
        }
    }
}

private struct AirportSection: View {
    enum Kind { case toAirport, fromAirport }

    let kind: Kind
    let departures: [Departure]
    let isToday: Bool

    @ObservedObject private var loc = LocalizationManager.shared
    @State private var mode: Mode = .featured
    enum Mode { case featured, showPast, showAll }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            header

            if let featured = featuredDeparture {
                FeaturedRow(departure: featured, isToday: isToday, tint: tint)
                    .transition(.opacity)
            } else {
                EmptyRow()
            }

            if mode != .featured, !expandedDepartures.isEmpty {
                Divider()
                    .opacity(0.3)
                VStack(spacing: 0) {
                    ForEach(Array(expandedDepartures.enumerated()), id: \.offset) { idx, dep in
                        ExpandedRow(departure: dep, isToday: isToday, tint: tint)
                        if idx < expandedDepartures.count - 1 {
                            Divider().opacity(0.18).padding(.leading, 44)
                        }
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }

            HStack(spacing: 10) {
                if isToday, !pastDepartures.isEmpty {
                    GlassPill(
                        label: earlierLabel,
                        systemImage: "clock.arrow.circlepath",
                        isActive: mode == .showPast,
                        tint: tint
                    ) {
                        withAnimation(.easeInOut(duration: 0.22)) {
                            mode = (mode == .showPast) ? .featured : .showPast
                        }
                    }
                }
                if upcomingDepartures.count > 1 {
                    GlassPill(
                        label: allUpcomingLabel,
                        systemImage: "list.bullet",
                        isActive: mode == .showAll,
                        tint: tint
                    ) {
                        withAnimation(.easeInOut(duration: 0.22)) {
                            mode = (mode == .showAll) ? .featured : .showAll
                        }
                    }
                }
            }
            .padding(.top, 2)
        }
        .padding(16)
        .glassCardBackground()
    }

    private var header: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.title3.weight(.semibold))
                .foregroundStyle(tint)
                .frame(width: 36, height: 36)
                .background(tint.opacity(0.15), in: Circle())
            VStack(alignment: .leading, spacing: 0) {
                Text(title)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.primary)
                if let subtitle = subtitle {
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
        }
    }

    private var icon: String {
        switch kind {
        case .toAirport: return "airplane.departure"
        case .fromAirport: return "airplane.arrival"
        }
    }

    private var tint: Color {
        switch kind {
        case .toAirport: return .metroBlue
        case .fromAirport: return .arrivalModerate
        }
    }

    private var title: String {
        switch (kind, loc.language) {
        case (.toAirport, .greek): return "Προς Αεροδρόμιο"
        case (.toAirport, .albanian): return "Drejt Aeroportit"
        case (.toAirport, .english): return "To Airport"
        case (.fromAirport, .greek): return "Από Αεροδρόμιο"
        case (.fromAirport, .albanian): return "Nga Aeroporti"
        case (.fromAirport, .english): return "From Airport"
        }
    }

    private var subtitle: String? {
        let count = upcomingDepartures.count
        guard count > 0 else { return nil }
        switch loc.language {
        case .greek: return "\(count) επόμενα δρομολόγια"
        case .albanian: return "\(count) nisje të radhës"
        case .english: return "\(count) upcoming departures"
        }
    }

    private var earlierLabel: String {
        switch loc.language {
        case .greek: return "Προηγούμενα"
        case .albanian: return "Më parë"
        case .english: return "Earlier"
        }
    }

    private var allUpcomingLabel: String {
        switch loc.language {
        case .greek: return "Όλα τα επόμενα"
        case .albanian: return "Të gjitha"
        case .english: return "All upcoming"
        }
    }

    private var pastDepartures: [Departure] {
        departures.filter { $0.minutesAway == 0 && $0.time < currentTimeString }
    }

    private var upcomingDepartures: [Departure] {
        if isToday {
            return departures.filter { $0.minutesAway > 0 || $0.time >= currentTimeString }
        } else {
            return departures
        }
    }

    private var featuredDeparture: Departure? {
        upcomingDepartures.first
    }

    private var expandedDepartures: [Departure] {
        switch mode {
        case .featured: return []
        case .showPast:
            // Past entries reversed so the most recent past sits first.
            return Array(pastDepartures.reversed())
        case .showAll:
            // Everything after the featured one.
            return Array(upcomingDepartures.dropFirst())
        }
    }

    private var currentTimeString: String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f.string(from: Date())
    }
}

private struct FeaturedRow: View {
    let departure: Departure
    let isToday: Bool
    let tint: Color
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            iconView
                .frame(width: 56, height: 40)

            VStack(alignment: .leading, spacing: 4) {
                Text(SyrmosData.line(for: departure.lineId)?.name ?? departure.lineId)
                    .font(.headline)
                Text(directionLabel)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 2) {
                if isToday {
                    Text(minutesLabel)
                        .font(.system(size: 28, weight: .semibold, design: .rounded))
                        .foregroundStyle(tint)
                        .monospacedDigit()
                    Text(departure.time)
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                } else {
                    Text(departure.time)
                        .font(.system(size: 28, weight: .semibold, design: .rounded))
                        .foregroundStyle(tint)
                        .monospacedDigit()
                }
            }
        }
        .padding(.vertical, 6)
    }

    @ViewBuilder
    private var iconView: some View {
        if let n = TimetablesIcons.vehicleImageName(
            lineId: departure.lineId,
            direction: departure.direction,
            isAirport: true
        ), UIImage(named: n) != nil {
            Image(n).resizable().scaledToFit()
        } else {
            Circle().fill(SyrmosData.lineColor(for: departure.lineId))
                .frame(width: 14, height: 14)
        }
    }

    private var minutesLabel: String {
        if departure.minutesAway <= 1 {
            switch loc.language {
            case .greek: return "Τώρα"
            case .albanian: return "Tani"
            case .english: return "Now"
            }
        }
        return departure.minutesAwayDisplay(language: loc.language)
    }

    private var directionLabel: String {
        switch loc.language {
        case .greek: return "προς \(departure.direction)"
        case .albanian: return "drejt \(departure.direction)"
        case .english: return "towards \(departure.direction)"
        }
    }
}

private struct ExpandedRow: View {
    let departure: Departure
    let isToday: Bool
    let tint: Color
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        HStack(spacing: 14) {
            Circle()
                .fill(tint.opacity(0.18))
                .overlay(Circle().fill(tint).frame(width: 6, height: 6))
                .frame(width: 14, height: 14)
                .padding(.leading, 22)

            Text(SyrmosData.line(for: departure.lineId)?.name ?? departure.lineId)
                .font(.subheadline)
                .foregroundStyle(.primary)

            Spacer()

            HStack(spacing: 6) {
                if isToday, departure.minutesAway > 0 {
                    Text(departure.minutesAwayDisplay(language: loc.language))
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.secondary)
                }
                Text(departure.time)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                    .monospacedDigit()
            }
        }
        .padding(.vertical, 10)
        .padding(.trailing, 4)
    }
}

private struct EmptyRow: View {
    @ObservedObject private var loc = LocalizationManager.shared
    var body: some View {
        Text(loc.language == .greek ? "Δεν υπάρχουν διαθέσιμα δρομολόγια." :
             loc.language == .albanian ? "Nuk ka nisje të disponueshme." :
             "No departures available.")
            .font(.subheadline)
            .foregroundStyle(.secondary)
            .padding(.vertical, 12)
    }
}

private struct GlassPill: View {
    let label: String
    let systemImage: String
    let isActive: Bool
    let tint: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: systemImage)
                    .font(.caption.weight(.semibold))
                Text(label)
                    .font(.subheadline.weight(.medium))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .foregroundStyle(isActive ? .white : tint)
            .background(
                Capsule()
                    .fill(isActive ? AnyShapeStyle(tint) : AnyShapeStyle(.thinMaterial))
            )
            .overlay(
                Capsule().strokeBorder(tint.opacity(isActive ? 0 : 0.25), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Glass card background helper

private extension View {
    /// iOS 26 has a real Glass shape style. On older OSes fall back to
    /// the existing .ultraThinMaterial which gives a similar translucent
    /// frosted look without breaking the build.
    @ViewBuilder
    func glassCardBackground(cornerRadius: CGFloat = 20) -> some View {
        self.background(
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(.ultraThinMaterial)
                .shadow(color: .black.opacity(0.06), radius: 6, x: 0, y: 3)
        )
        .overlay(
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .strokeBorder(Color.white.opacity(0.08), lineWidth: 0.5)
        )
    }
}

// MARK: - Day picker

private struct DayPickerRow: View {
    @Binding var selectedOffset: Int
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(0..<7, id: \.self) { offset in
                    let isSelected = selectedOffset == offset
                    Button {
                        withAnimation(.easeInOut(duration: 0.18)) {
                            selectedOffset = offset
                        }
                    } label: {
                        VStack(spacing: 2) {
                            Text(dayName(offset))
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(isSelected ? .white : .secondary)
                            Text(dayNumber(offset))
                                .font(.headline)
                                .foregroundStyle(isSelected ? .white : .primary)
                        }
                        .frame(width: 54, height: 54)
                        .background(
                            Circle()
                                .fill(isSelected ? AnyShapeStyle(Color.metroBlue) : AnyShapeStyle(.thinMaterial))
                        )
                        .overlay(
                            Circle().strokeBorder(Color.metroBlue.opacity(isSelected ? 0 : 0.18), lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 4)
            .padding(.vertical, 2)
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

// MARK: - Static airport data
