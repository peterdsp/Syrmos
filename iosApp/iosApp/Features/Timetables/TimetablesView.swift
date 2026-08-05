import SwiftUI

/// Universal departures screen: pick any line, any station, see the
/// next trains in both directions. Glass-card layout: 7-day pill row,
/// line picker, station picker, two direction sections with expand
/// controls. Replaces the old airport-only screen.
struct TimetablesView: View {
    @ObservedObject private var loc = LocalizationManager.shared
    @StateObject private var locationService = LocationService()
    @State private var selectedLineId: String = "M3"
    @State private var selectedStationId: String = ""
    @State private var dayOffset: Int = 0
    @State private var departures: [Departure] = []
    @State private var nowTick = Date()
    @State private var didAutoPickNearest: Bool = false

    private let refreshTimer = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    private var operationalLines: [TransitLine] {
        SyrmosData.operationalLines
    }

    private var selectedLine: TransitLine? {
        SyrmosData.line(for: selectedLineId)
    }

    private var stationsOnLine: [TransitStation] {
        SyrmosData.stations(for: selectedLineId)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 10) {
                    DayPickerRow(selectedOffset: $dayOffset)
                        .padding(.top, 2)

                    LinePickerCard(
                        selectedLineId: Binding(
                            get: { selectedLineId },
                            set: { newLine in
                                selectedLineId = newLine
                                if let first = SyrmosData.stations(for: newLine).first {
                                    selectedStationId = first.id
                                }
                                didAutoPickNearest = true
                            }
                        ),
                        lines: operationalLines
                    )

                    StationPickerCard(
                        stations: stationsOnLine,
                        selectedStationId: Binding(
                            get: { selectedStationId },
                            set: { newValue in
                                selectedStationId = newValue
                                didAutoPickNearest = true
                            }
                        )
                    )

                    let outbound = departures.filter { isOutboundDirection($0.direction) }
                    let inbound = departures.filter { !isOutboundDirection($0.direction) }

                    DirectionSection(
                        kind: .outbound,
                        departures: outbound,
                        isToday: dayOffset == 0,
                        destinationLabel: selectedLine?.terminalB ?? "",
                        tint: SyrmosData.lineColor(for: selectedLineId)
                    )

                    DirectionSection(
                        kind: .inbound,
                        departures: inbound,
                        isToday: dayOffset == 0,
                        destinationLabel: selectedLine?.terminalA ?? "",
                        tint: SyrmosData.lineColor(for: selectedLineId)
                    )

                    Text(footerText)
                        .font(.footnote)
                        .foregroundStyle(.tertiary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                        .padding(.top, 4)
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 20)
            }
            .background(Color.syrmosBackground)
            .safeAreaInset(edge: .top, spacing: 8) {
                CompactTabHeader(
                    loc.language == .greek ? "Αναχωρήσεις" :
                    loc.language == .albanian ? "Nisjet" :
                    loc.language == .italian ? "Partenze" : "Departures"
                )
            }
            .toolbar(.hidden, for: .navigationBar)
            .onAppear {
                locationService.requestIfNeeded()
                if selectedStationId.isEmpty, let first = stationsOnLine.first {
                    selectedStationId = first.id
                }
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
        guard let nearest = locationService.nearbyStations.first else { return }
        let candidateId = nearest.station.id
        if let line = nearest.station.lineIds.first,
           SyrmosData.stations(for: line).contains(where: { $0.id == candidateId }) {
            selectedLineId = line
            selectedStationId = candidateId
            didAutoPickNearest = true
        }
    }

    private func isOutboundDirection(_ dir: String) -> Bool {
        guard let line = selectedLine else { return true }
        let termA = line.terminalA.lowercased()
        let dirLow = dir.lowercased()
        return !dirLow.contains(termA) && !dirLow.hasPrefix(termA)
    }

    private var footerText: String {
        switch loc.language {
        case .greek: return "Επομενα δρομολογια απο τον επιλεγμενο σταθμο."
        case .albanian: return "Nisjet e ardhshme nga stacioni i zgjedhur."
        case .italian: return "Prossime partenze dalla stazione selezionata."
        case .english: return "Next departures from the selected station."
        }
    }

    private func reload() {
        let offset = dayOffset
        let lineId = selectedLineId
        let stationId = selectedStationId
        guard !stationId.isEmpty else { return }
        Task { @MainActor in
            let lineIds = lineId == "M3" ? ["M3", "M3_AIR"] : [lineId]
            departures = ScheduleProjector.nextDepartures(
                for: stationId,
                lineIds: lineIds,
                limit: 200,
                dayOffset: offset
            )
        }
    }
}

// MARK: - Glass cards

private struct LinePickerCard: View {
    @Binding var selectedLineId: String
    let lines: [TransitLine]
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        Menu {
            ForEach(lines, id: \.id) { line in
                Button {
                    selectedLineId = line.id
                } label: {
                    HStack {
                        Text(lineLabel(line))
                        if line.id == selectedLineId {
                            Spacer()
                            Image(systemName: "checkmark")
                        }
                    }
                }
            }
        } label: {
            HStack(alignment: .center, spacing: 10) {
                Image(systemName: iconName)
                    .font(.subheadline)
                    .foregroundStyle(tint)
                    .frame(width: 28, height: 28)
                    .background(tint.opacity(0.15), in: Circle())

                VStack(alignment: .leading, spacing: 1) {
                    Text(lineHeaderLabel.uppercased())
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .tracking(0.6)
                    Text(currentLineLabel)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }

                Spacer()

                Image(systemName: "chevron.up.chevron.down")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .glassCardBackground(cornerRadius: 16)
            .contentShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }

    private var currentLineLabel: String {
        guard let line = lines.first(where: { $0.id == selectedLineId }) else { return selectedLineId }
        return lineLabel(line)
    }

    private func lineLabel(_ line: TransitLine) -> String {
        line.localizedName(loc.language)
    }

    private var iconName: String {
        let line = lines.first { $0.id == selectedLineId }
        switch line?.type {
        case .metro: return "tram.tunnel.fill"
        case .tram: return "tram.fill"
        case .bus: return "bus.fill"
        case .scenic: return "mountain.2.fill"
        default: return "train.side.front.car"
        }
    }

    private var tint: Color {
        SyrmosData.lineColor(for: selectedLineId)
    }

    private var lineHeaderLabel: String {
        switch loc.language {
        case .greek: return "Γραμμη"
        case .albanian: return "Linja"
        case .italian: return "Linea"
        case .english: return "Line"
        }
    }
}

private struct StationPickerCard: View {
    let stations: [TransitStation]
    @Binding var selectedStationId: String
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        Menu {
            ForEach(stations, id: \.id) { st in
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
            let current = stations.first { $0.id == selectedStationId }
            HStack(alignment: .center, spacing: 10) {
                Image(systemName: "mappin.circle.fill")
                    .font(.subheadline)
                    .foregroundStyle(.tint)
                    .frame(width: 28, height: 28)
                    .background(.tint.opacity(0.12), in: Circle())

                VStack(alignment: .leading, spacing: 1) {
                    Text(stationLabel.uppercased())
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .tracking(0.6)
                    Text(loc.language == .greek ? (current?.nameEl ?? "") : (current?.name ?? ""))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                }

                Spacer()

                Image(systemName: "chevron.up.chevron.down")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .glassCardBackground(cornerRadius: 16)
            .contentShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }

    private var stationLabel: String {
        switch loc.language {
        case .greek: return "Σταθμος"
        case .albanian: return "Stacioni"
        case .italian: return "Stazione"
        case .english: return "Station"
        }
    }
}

private struct DirectionSection: View {
    enum Kind { case outbound, inbound }

    let kind: Kind
    let departures: [Departure]
    let isToday: Bool
    let destinationLabel: String
    let tint: Color

    @ObservedObject private var loc = LocalizationManager.shared
    @State private var mode: Mode = .featured
    enum Mode { case featured, showPast, showAll }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
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
                            Divider().opacity(0.18).padding(.leading, 36)
                        }
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }

            HStack(spacing: 8) {
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
        }
        .padding(12)
        .glassCardBackground(cornerRadius: 16)
    }

    private var header: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(tint)
                .frame(width: 28, height: 28)
                .background(tint.opacity(0.15), in: Circle())
            VStack(alignment: .leading, spacing: 0) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                if let subtitle = subtitle {
                    Text(subtitle)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
        }
    }

    private var icon: String {
        switch kind {
        case .outbound: return "arrow.right"
        case .inbound: return "arrow.left"
        }
    }

    private var title: String {
        let prefix: String
        switch loc.language {
        case .greek: prefix = "Προς"
        case .albanian: prefix = "Drejt"
        case .italian: prefix = "Verso"
        case .english: prefix = "Towards"
        }
        return "\(prefix) \(destinationLabel)"
    }

    private var subtitle: String? {
        let count = upcomingDepartures.count
        guard count > 0 else { return nil }
        switch loc.language {
        case .greek: return "\(count) επομενα δρομολογια"
        case .albanian: return "\(count) nisje te radhes"
        case .italian: return "\(count) partenze imminenti"
        case .english: return "\(count) upcoming departures"
        }
    }

    private var earlierLabel: String {
        switch loc.language {
        case .greek: return "Προηγουμενα"
        case .albanian: return "Me pare"
        case .italian: return "Precedenti"
        case .english: return "Earlier"
        }
    }

    private var allUpcomingLabel: String {
        switch loc.language {
        case .greek: return "Ολα τα επομενα"
        case .albanian: return "Te gjitha"
        case .italian: return "Tutte le prossime"
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
        case .showPast: return Array(pastDepartures.reversed())
        case .showAll: return Array(upcomingDepartures.dropFirst())
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
        HStack(alignment: .center, spacing: 10) {
            Circle()
                .fill(SyrmosData.lineColor(for: departure.lineId))
                .frame(width: 28, height: 28)
                .overlay(
                    Image(systemName: "train.side.front.car")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white)
                )

            VStack(alignment: .leading, spacing: 2) {
                Text(SyrmosData.line(for: departure.lineId)?.localizedName(loc.language) ?? departure.lineId)
                    .font(.subheadline.weight(.semibold))
                Text(directionLabel)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if departure.sourceConfidence != .unknown {
                    SourceConfidenceChip(confidence: departure.sourceConfidence, language: loc.language)
                }
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 1) {
                if isToday {
                    Text(minutesLabel)
                        .font(.system(size: 22, weight: .semibold, design: .rounded))
                        .foregroundStyle(tint)
                        .monospacedDigit()
                    Text(departure.time)
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                } else {
                    Text(departure.time)
                        .font(.system(size: 22, weight: .semibold, design: .rounded))
                        .foregroundStyle(tint)
                        .monospacedDigit()
                }
            }
        }
        .padding(.vertical, 4)
    }

    private var minutesLabel: String {
        if departure.minutesAway <= 1 {
            switch loc.language {
            case .greek: return "Τωρα"
            case .albanian: return "Tani"
            case .italian: return "Ora"
            case .english: return "Now"
            }
        }
        return departure.minutesAwayDisplay(language: loc.language)
    }

    private var directionLabel: String {
        switch loc.language {
        case .greek: return "προς \(departure.direction)"
        case .albanian: return "drejt \(departure.direction)"
        case .italian: return "verso \(departure.direction)"
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
        HStack(spacing: 10) {
            Circle()
                .fill(tint.opacity(0.18))
                .overlay(Circle().fill(tint).frame(width: 5, height: 5))
                .frame(width: 12, height: 12)
                .padding(.leading, 18)

            VStack(alignment: .leading, spacing: 1) {
                Text(SyrmosData.line(for: departure.lineId)?.localizedName(loc.language) ?? departure.lineId)
                    .font(.caption)
                    .foregroundStyle(.primary)
                if departure.sourceConfidence != .unknown {
                    SourceConfidenceChip(confidence: departure.sourceConfidence, language: loc.language)
                }
            }

            Spacer()

            HStack(spacing: 4) {
                if isToday, departure.minutesAway > 0 {
                    Text(departure.minutesAwayDisplay(language: loc.language))
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(.secondary)
                }
                Text(departure.time)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.primary)
                    .monospacedDigit()
            }
        }
        .padding(.vertical, 6)
        .padding(.trailing, 4)
    }
}

private struct EmptyRow: View {
    @ObservedObject private var loc = LocalizationManager.shared
    var body: some View {
        Text(loc.language == .greek ? "Δεν υπαρχουν διαθεσιμα δρομολογια." :
             loc.language == .albanian ? "Nuk ka nisje te disponueshme." :
             loc.language == .italian ? "Nessuna partenza disponibile." :
             "No departures available.")
            .font(.caption)
            .foregroundStyle(.secondary)
            .padding(.vertical, 8)
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
            HStack(spacing: 4) {
                Image(systemName: systemImage)
                    .font(.caption2.weight(.semibold))
                Text(label)
                    .font(.caption.weight(.medium))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
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
            HStack(spacing: 8) {
                ForEach(0..<7, id: \.self) { offset in
                    let isSelected = selectedOffset == offset
                    Button {
                        withAnimation(.easeInOut(duration: 0.18)) {
                            selectedOffset = offset
                        }
                    } label: {
                        VStack(spacing: 1) {
                            Text(dayName(offset))
                                .font(.system(size: 9, weight: .semibold))
                                .foregroundStyle(isSelected ? .white : .secondary)
                            Text(dayNumber(offset))
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(isSelected ? .white : .primary)
                        }
                        .frame(width: 44, height: 44)
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
            .padding(.horizontal, 2)
            .padding(.vertical, 1)
        }
    }

    private func dayName(_ offset: Int) -> String {
        let date = Calendar.current.date(byAdding: .day, value: offset, to: Date()) ?? Date()
        if offset == 0 {
            switch loc.language {
            case .greek: return "ΣΗΜ"
            case .albanian: return "SOT"
            case .italian: return "OGGI"
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
        case .italian: return "it_IT"
        case .english: return "en_US"
        }
    }
}
