import SwiftUI

struct StationDetailView: View {
    let station: TransitStation
    @ObservedObject private var loc = LocalizationManager.shared
    @State private var departures: [Departure] = []
    @State private var hasLoadedOnce: Bool = false
    @State private var nowTick = Date()
    @State private var showMapSheet = false
    @State private var safariURL: URL?

    // Recompute departures every 15 seconds so the "5 min / 10 min" countdowns
    // tick down in real time while the user is viewing this screen.
    private let refreshTimer = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    var body: some View {
        List {
            Section(loc[.stations]) {
                Button {
                    showMapSheet = true
                } label: {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 4) {
                            // Show Latin name in non-Greek modes. The original
                            // logic painted Greek text underneath the Latin nav
                            // title for English+Albanian, which surprised Albanian
                            // users who expect zero Greek when their app language
                            // is not Greek.
                            Text(loc.language == .greek ? station.name : station.name)
                                .font(.title3)
                                .foregroundStyle(.secondary)

                            LinePillsRow(lineIds: station.lineIds)
                        }
                        Spacer()
                        Image(systemName: "map.fill")
                            .font(.title3)
                            .foregroundStyle(.tertiary)
                    }
                }
                .buttonStyle(.plain)
            }

            if station.isInterchange {
                Section(loc.language == .greek ? "Ανταπόκριση" : loc.language == .albanian ? "Korrespondencë" : "Interchange") {
                    Label(
                        loc.language == .greek ? "Σταθμός ανταπόκρισης" : loc.language == .albanian ? "Stacion korrespondence" : "Transfer station",
                        systemImage: "arrow.triangle.2.circlepath"
                    )
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                }
            }

            if isSuburbanStation {
                Section {
                    Button {
                        safariURL = URL(string: "https://newtickets.hellenictrain.gr/Channels.HellenicTrainWeb/")
                    } label: {
                        Label(
                            loc.language == .greek ? "Αγορά εισιτηρίου στην Hellenic Train" : loc.language == .albanian ? "Bli biletë në Hellenic Train" : "Buy ticket on Hellenic Train",
                            systemImage: "ticket"
                        )
                    }
                } footer: {
                    Text(loc.language == .greek
                         ? "Η πληρωμή και η έκδοση εισιτηρίου γίνονται 100% στον ιστότοπο της Hellenic Train. Το Syrmos απλώς παρέχει τον σύνδεσμο, δεν συλλέγει στοιχεία πληρωμής και δεν έχει καμία ευθύνη για την κράτηση."
                         : loc.language == .albanian
                         ? "Pagesa dhe lëshimi i biletës bëhen 100% në faqen e Hellenic Train. Syrmos thjesht ofron lidhjen, nuk mbledh të dhëna pagesash dhe nuk ka asnjë përgjegjësi për rezervimin."
                         : "Payment and ticket issuance happen entirely on Hellenic Train's website. Syrmos only provides the link, does not collect any payment data, and has no responsibility for the booking.")
                        .font(.caption2)
                }
            }

            if isUnsupportedRegion {
                Section(loc.language == .greek ? "Δρομολόγια" : loc.language == .albanian ? "Oraret" : "Timetables") {
                    Label(
                        loc.language == .greek
                        ? "Τα δρομολόγια για αυτό το δίκτυο δεν είναι ακόμα διαθέσιμα στο Syrmos."
                        : loc.language == .albanian
                        ? "Oraret per kete rrjet nuk jane ende te disponueshme ne Syrmos."
                        : "Timetable data for this network is not yet available in Syrmos.",
                        systemImage: "info.circle"
                    )
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                    if let url = externalTimetableURL {
                        Button {
                            safariURL = url
                        } label: {
                            Label(
                                loc.language == .greek
                                ? "Δείτε δρομολόγια στο \(externalOperatorName)"
                                : loc.language == .albanian
                                ? "Shiko oraret ne \(externalOperatorName)"
                                : "Check timetables on \(externalOperatorName)",
                                systemImage: "safari"
                            )
                        }
                    }
                } footer: {
                    Text(loc.language == .greek
                         ? "Τα δρομολόγια για αυτό το δίκτυο θα προστεθούν σε μελλοντική ενημέρωση."
                         : loc.language == .albanian
                         ? "Oraret per kete rrjet do te shtohen ne nje perditesim te ardhshem."
                         : "Timetables for this network will be added in a future update.")
                        .font(.caption2)
                }
            }

            if !isUnsupportedRegion {
                Section(loc.language == .greek ? "Επόμενα Δρομολόγια" : loc.language == .albanian ? "Nisjet e ardhshme" : "Next Departures") {
                    if departures.isEmpty {
                        Text(hasLoadedOnce
                             ? (loc.language == .greek ? "Δεν υπάρχουν διαθέσιμα δρομολόγια αυτή τη στιγμή. Η γραμμή είναι κλειστή ή έχει τελειώσει η σημερινή υπηρεσία." :
                                loc.language == .albanian ? "Nuk ka nisje të disponueshme tani. Linja është mbyllur ose ka përfunduar shërbimi i sotëm." :
                                "No departures right now. The line is closed or today's service has ended.")
                             : (loc.language == .greek ? "Φόρτωση δρομολογίων..." :
                                loc.language == .albanian ? "Duke ngarkuar oraret..." :
                                "Loading departures..."))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.leading)
                    } else {
                        ForEach(departures.prefix(10)) { departure in
                        let iconName = TimetablesIcons.vehicleImageName(
                            lineId: departure.lineId,
                            direction: departure.direction,
                            isAirport: departure.serviceType == "airport"
                        )
                        HStack {
                            Group {
                                if let iconName, UIImage(named: iconName) != nil {
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
                                HStack(spacing: 4) {
                                    Text(SyrmosData.line(for: departure.lineId)?.name ?? departure.lineId)
                                        .font(.subheadline)
                                        .fontWeight(.medium)
                                    if departure.serviceType == "airport" {
                                        Text(loc.language == .greek ? "Αεροδρόμιο" : loc.language == .albanian ? "Aeroporti" : "Airport")
                                            .font(.caption2)
                                            .fontWeight(.semibold)
                                            .padding(.horizontal, 5)
                                            .padding(.vertical, 1)
                                            .background(Color.metroBlue.opacity(0.15))
                                            .clipShape(Capsule())
                                    }
                                }
                                Text(loc.language == .greek
                                    ? "προς \(departure.direction)"
                                    : loc.language == .albanian
                                    ? "drejt \(departure.direction)"
                                    : "towards \(departure.direction)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                SourceConfidenceChip(confidence: departure.sourceConfidence, language: loc.language)
                            }

                            Spacer()

                            VStack(alignment: .trailing, spacing: 2) {
                                Text(departure.minutesAwayDisplay(language: loc.language))
                                    .font(.headline)
                                    .foregroundStyle(arrivalColor(departure.minutesAway))
                                Text(departure.time)
                                    .font(.caption2)
                                    .foregroundStyle(.tertiary)
                            }
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.syrmosBackground)
        .navigationTitle(loc.language == .greek ? station.nameEl : station.name)
        .onAppear {
            if !isUnsupportedRegion { reloadDepartures() }
        }
        .onReceive(refreshTimer) { _ in
            guard !isUnsupportedRegion else { return }
            nowTick = Date()
            reloadDepartures()
        }
        .sheet(isPresented: $showMapSheet) {
            StationMapSheet(station: station)
        }
        .inAppSafari(url: $safariURL)
    }

    /// True when this station belongs to a Hellenic Train suburban line (A1-A4).
    private var isSuburbanStation: Bool {
        station.lineIds.contains { ["A1", "A2", "A3", "A4"].contains($0) }
    }

    /// The region of this station, derived from the regions of its lines.
    /// If all lines share a single non-Athens region, that is the station's region.
    private var stationRegion: TransitRegion {
        let regions = station.lineIds.compactMap { SyrmosData.line(for: $0)?.region }
        guard !regions.isEmpty else { return .athens }
        let unique = Set(regions)
        if unique.count == 1, let single = unique.first { return single }
        return .athens
    }

    /// True when the station is outside the Athens network and has no local
    /// schedule data. We show external operator links instead of departures.
    private var isUnsupportedRegion: Bool {
        stationRegion != .athens
    }

    /// External operator website for this station's region.
    private var externalTimetableURL: URL? {
        switch stationRegion {
        case .thessaloniki:
            return URL(string: "https://www.oasth.gr")
        case .national, .patras:
            return URL(string: "https://www.hellenictrain.gr")
        case .athens:
            return nil
        }
    }

    private var externalOperatorName: String {
        switch stationRegion {
        case .thessaloniki: return "OASTH"
        case .national, .patras: return "Hellenic Train"
        case .athens: return ""
        }
    }

    /// 100% offline. The station-detail screen is reachable from the
    /// Lines list and from the Home nearest-stations card, both of
    /// which want a deep "what's running today" view — 12 hours of
    /// upcoming departures, drawn from the bundled schedule JSON +
    /// station offsets. NO network call. Previously this method
    /// queried /api/departures/next and replaced the offline result
    /// with whatever the API returned, which meant any clock drift
    /// or downtime on the Pi propagated straight into the UI even
    /// though the offline projector had the right answer in hand.
    private func reloadDepartures() {
        departures = currentDepartures()
        hasLoadedOnce = true
    }

    /// Bundled-projector projection for the next 12 hours. 12 h is
    /// long enough to cover an overnight gap (last train ~01:30, next
    /// train ~05:00 next morning) so the "Next train tomorrow at HH:MM"
    /// state always shows up without a separate lookahead pass.
    private func currentDepartures() -> [Departure] {
        return ScheduleProjector.nextDepartures(
            for: station.id,
            lineIds: station.lineIds,
            limit: 300,
            timeHorizonMinutes: 12 * 60
        )
    }

    private func arrivalColor(_ minutes: Int) -> Color {
        switch minutes {
        case 0...2: return .arrivalSoon
        case 3...5: return .arrivalModerate
        default: return .arrivalFar
        }
    }
}

// MARK: - Line pills

/// Wrapping row of glass-capsule line pills. Short label per pill
/// (M1 / M3 / A1 / A4 / T6 etc.) so nothing ever hyphenates or
/// wraps inside the capsule itself, and a coloured dot keyed to
/// the line tint. Pills flow onto a second row when they overflow.
private struct LinePillsRow: View {
    let lineIds: [String]

    var body: some View {
        FlowLayout(spacing: 6) {
            ForEach(lineIds, id: \.self) { lineId in
                StationLinePill(lineId: lineId)
            }
        }
    }
}

private struct StationLinePill: View {
    let lineId: String

    var body: some View {
        HStack(spacing: 5) {
            Circle()
                .fill(SyrmosData.lineColor(for: lineId))
                .frame(width: 7, height: 7)
            Text(shortLabel)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(
            Capsule()
                .fill(.ultraThinMaterial)
                .overlay(
                    Capsule().strokeBorder(SyrmosData.lineColor(for: lineId).opacity(0.4), lineWidth: 0.8)
                )
        )
    }

    /// Short, never-wrapping label. Strip the verbose suburban suffix
    /// ("A1 Piraeus-Airport" -> "A1") and just keep the line id; users
    /// recognise it from the legend / map.
    private var shortLabel: String {
        switch lineId {
        case "M1": return "M1"
        case "M2": return "M2"
        case "M3", "M3_AIR": return "M3"
        case "T6": return "T6"
        case "T7": return "T7"
        case "A1": return "A1"
        case "A2": return "A2"
        case "A3": return "A3"
        case "A4": return "A4"
        default:   return lineId
        }
    }
}

/// Compact flow layout: lays children left-to-right, wraps when they
/// exceed the proposed width. Avoids the hyphenated mess HStack
/// produced when a long pill label hit the screen edge.
private struct FlowLayout: Layout {
    var spacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        for sub in subviews {
            let size = sub.sizeThatFits(.unspecified)
            if x + size.width > maxWidth, x > 0 {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: maxWidth, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0
        let maxX = bounds.maxX
        for sub in subviews {
            let size = sub.sizeThatFits(.unspecified)
            if x + size.width > maxX, x > bounds.minX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            sub.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
