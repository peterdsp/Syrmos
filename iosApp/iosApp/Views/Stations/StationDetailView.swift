import SwiftUI

struct StationDetailView: View {
    let station: TransitStation
    @ObservedObject private var loc = LocalizationManager.shared
    @State private var departures: [Departure] = []
    @State private var hasLoadedOnce: Bool = false
    @State private var nowTick = Date()
    @State private var showMapSheet = false
    @State private var safariURL: URL?
    @State private var apiFailed = false

    private let refreshTimer = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    var body: some View {
        List {
            Section(loc[.stations]) {
                Button {
                    showMapSheet = true
                } label: {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 4) {
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

            if isNonAthensRegion && apiFailed {
                Section {
                    Label(
                        loc.language == .greek
                        ? "Δεν ήταν δυνατή η φόρτωση δρομολογίων. Ελέγξτε τη σύνδεσή σας."
                        : loc.language == .albanian
                        ? "Nuk u arrit te ngarkoheshin oraret. Kontrollo lidhjen."
                        : "Could not load departures. Check your connection.",
                        systemImage: "wifi.exclamationmark"
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
                }
            }

            Section(loc.language == .greek ? "Επόμενα Δρομολόγια" : loc.language == .albanian ? "Nisjet e ardhshme" : "Next Departures") {
                if departures.isEmpty && !(isNonAthensRegion && apiFailed) {
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
                        .accessibilityElement(children: .combine)
                        .accessibilityLabel("\(departure.lineId) towards \(departure.direction), \(departure.minutesAway) minutes, at \(departure.time)")
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.syrmosBackground)
        .navigationTitle(loc.language == .greek ? station.nameEl : station.name)
        .onAppear {
            reloadDepartures()
        }
        .onReceive(refreshTimer) { _ in
            nowTick = Date()
            reloadDepartures()
        }
        .sheet(isPresented: $showMapSheet) {
            StationMapSheet(station: station)
        }
        .inAppSafari(url: $safariURL)
    }

    private var isSuburbanStation: Bool {
        station.lineIds.contains { ["A1", "A2", "A3", "A4"].contains($0) }
    }

    private var stationRegion: TransitRegion {
        let regions = station.lineIds.compactMap { SyrmosData.line(for: $0)?.region }
        guard !regions.isEmpty else { return .athens }
        let unique = Set(regions)
        if unique.count == 1, let single = unique.first { return single }
        return .athens
    }

    private var isNonAthensRegion: Bool {
        stationRegion != .athens
    }

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

    private func reloadDepartures() {
        if isNonAthensRegion {
            Task {
                if let apiResult = await SyrmosDeparturesService.nextDepartures(
                    for: station.id,
                    lineIds: station.lineIds,
                    limit: 20
                ) {
                    departures = apiResult
                    apiFailed = false
                } else {
                    departures = ScheduleProjector.nextDepartures(
                        for: station.id,
                        lineIds: station.lineIds,
                        limit: 20
                    )
                    apiFailed = departures.isEmpty
                }
                hasLoadedOnce = true
            }
        } else {
            departures = currentDepartures()
            hasLoadedOnce = true
        }
    }

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
