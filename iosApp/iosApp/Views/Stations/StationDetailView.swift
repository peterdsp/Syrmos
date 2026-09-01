import SwiftUI

struct StationDetailView: View {
    let station: TransitStation
    @ObservedObject private var loc = LocalizationManager.shared
    @StateObject private var stasyService = STASYService()
    @State private var departures: [Departure] = []
    @State private var hasLoadedOnce: Bool = false
    @State private var nowTick = Date()
    @State private var showMapSheet = false
    @State private var safariURL: URL?
    @State private var apiFailed = false

    private let refreshTimer = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    // Physical-hub transfers, resolved by proximity to each line's REAL station
    // id (never this station's id), so navigation and schedules stay valid.
    private var interchangeTargets: [(line: TransitLine, stationId: String)] {
        SyrmosData.interchangeTargets(from: station, currentLineId: station.lineIds.first ?? "")
    }

    private var stationAlerts: [STASYAnnouncement] {
        stasyService.announcements.filter { ann in
            ann.category == .serviceAlert
            && ann.affectedLines.contains(where: { affected in
                station.lineIds.contains(where: { $0.caseInsensitiveCompare(affected) == .orderedSame })
            })
        }
    }

    var body: some View {
        List {
            if !stationAlerts.isEmpty {
                Section {
                    ServiceAlertBanner(alert: stationAlerts[0], language: loc.language)
                }
            }

            Section(loc[.stations]) {
                Button {
                    showMapSheet = true
                } label: {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(loc.language == .greek ? station.name : station.name)
                                .font(.title3)
                                .foregroundStyle(.secondary)

                            LinePillsRow(
                                lineIds: station.lineIds,
                                disruptions: stasyService.lineDisruptions
                            )
                        }
                        Spacer()
                        Image(systemName: "map.fill")
                            .font(.title3)
                            .foregroundStyle(.tertiary)
                    }
                }
                .buttonStyle(.plain)
            }

            if !interchangeTargets.isEmpty {
                Section(loc.language == .greek ? "Ανταπόκριση" : loc.language == .albanian ? "Korrespondencë" : loc.language == .italian ? "Interscambio" : "Interchange") {
                    ForEach(interchangeTargets, id: \.line.id) { target in
                        NavigationLink {
                            DestinationDetailView(
                                stationId: target.stationId,
                                lineId: target.line.id,
                                onStationViewed: { _, _ in }
                            )
                        } label: {
                            HStack(spacing: 12) {
                                Circle()
                                    .fill(target.line.color)
                                    .frame(width: 12, height: 12)
                                Text(target.line.localizedName(loc.language))
                                    .font(.subheadline)
                            }
                        }
                    }
                }
            }

            if isSuburbanStation {
                Section {
                    Button {
                        safariURL = URL(string: "https://newtickets.hellenictrain.gr/Channels.HellenicTrainWeb/")
                    } label: {
                        Label(
                            loc.language == .greek ? "Αγορά εισιτηρίου στην Hellenic Train" : loc.language == .albanian ? "Bli biletë në Hellenic Train" : loc.language == .italian ? "Acquista biglietto su Hellenic Train" : "Buy ticket on Hellenic Train",
                            systemImage: "ticket"
                        )
                    }
                } footer: {
                    Text(loc.language == .greek
                         ? "Η πληρωμή και η έκδοση εισιτηρίου γίνονται 100% στον ιστότοπο της Hellenic Train. Το Syrmos απλώς παρέχει τον σύνδεσμο, δεν συλλέγει στοιχεία πληρωμής και δεν έχει καμία ευθύνη για την κράτηση."
                         : loc.language == .albanian
                         ? "Pagesa dhe lëshimi i biletës bëhen 100% në faqen e Hellenic Train. Syrmos thjesht ofron lidhjen, nuk mbledh të dhëna pagesash dhe nuk ka asnjë përgjegjësi për rezervimin."
                         : loc.language == .italian
                         ? "Il pagamento e l'emissione del biglietto avvengono interamente sul sito di Hellenic Train. Syrmos fornisce solo il link, non raccoglie dati di pagamento e non ha alcuna responsabilita per la prenotazione."
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
                        : loc.language == .italian
                        ? "Impossibile caricare le partenze. Controlla la connessione."
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
                                : loc.language == .italian
                                ? "Consulta gli orari su \(externalOperatorName)"
                                : "Check timetables on \(externalOperatorName)",
                                systemImage: "safari"
                            )
                        }
                    }
                }
            }

            Section(loc.language == .greek ? "Επόμενα Δρομολόγια" : loc.language == .albanian ? "Nisjet e ardhshme" : loc.language == .italian ? "Prossime partenze" : "Next Departures") {
                if departures.isEmpty && !(isNonAthensRegion && apiFailed) {
                    Text(hasLoadedOnce
                         ? (stationServiceState.map(serviceStateMessage)
                            ?? (loc.language == .greek ? "Δεν υπάρχουν διαθέσιμα δρομολόγια αυτή τη στιγμή. Η γραμμή είναι κλειστή ή έχει τελειώσει η σημερινή υπηρεσία." :
                            loc.language == .albanian ? "Nuk ka nisje të disponueshme tani. Linja është mbyllur ose ka përfunduar shërbimi i sotëm." :
                            loc.language == .italian ? "Nessuna partenza al momento. La linea e chiusa o il servizio di oggi e terminato." :
                            "No departures right now. The line is closed or today's service has ended."))
                         : (loc.language == .greek ? "Φόρτωση δρομολογίων..." :
                            loc.language == .albanian ? "Duke ngarkuar oraret..." :
                            loc.language == .italian ? "Caricamento partenze..." :
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
                            .overlay(alignment: .topTrailing) {
                                LineDisruptionDot(severity: stasyService.lineDisruptions[departure.lineId])
                                    .offset(x: 3, y: -3)
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 4) {
                                    Text(SyrmosData.line(for: departure.lineId)?.localizedName(loc.language) ?? departure.lineId)
                                        .font(.subheadline)
                                        .fontWeight(.medium)
                                    if departure.serviceType == "airport" {
                                        Text(loc.language == .greek ? "Αεροδρόμιο" : loc.language == .albanian ? "Aeroporti" : loc.language == .italian ? "Aeroporto" : "Airport")
                                            .font(.caption2)
                                            .fontWeight(.semibold)
                                            .padding(.horizontal, 5)
                                            .padding(.vertical, 1)
                                            .background(Color.metroBlue.opacity(0.15))
                                            .clipShape(Capsule())
                                    }
                                }
                                HStack(spacing: 4) {
                                    Text(loc.language == .greek
                                        ? "προς \(departure.direction)"
                                        : loc.language == .albanian
                                        ? "drejt \(departure.direction)"
                                        : loc.language == .italian
                                        ? "per \(departure.direction)"
                                        : "towards \(departure.direction)")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                    if let trainNo = departure.trainNo {
                                        Text("#\(trainNo)")
                                            .font(.caption2)
                                            .fontWeight(.medium)
                                            .foregroundStyle(.secondary)
                                    }
                                }
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

    /// Honest reason a station's departures are empty, when the reason is a line
    /// state rather than a plain "nothing right now". Mirrors the web station
    /// sheet: a suspended or seasonal line must not read as a generic glitch.
    private enum StationServiceState { case suspended, construction, seasonal }
    private var stationServiceState: StationServiceState? {
        let lines = station.lineIds.compactMap { SyrmosData.line(for: $0) }
        guard !lines.isEmpty else { return nil }
        // Suspended / under construction only when NO line here is live, so an
        // interchange that also carries a running line stays a normal sheet.
        if lines.allSatisfy({ !$0.isOperational }) {
            if lines.contains(where: { $0.isSuspended }) { return .suspended }
            if lines.contains(where: { $0.status == .underConstruction }) { return .construction }
        }
        if lines.contains(where: { $0.isSeasonal }) { return .seasonal }
        return nil
    }
    private func serviceStateMessage(_ state: StationServiceState) -> String {
        switch state {
        case .suspended:
            switch loc.language {
            case .greek: return "Προσωρινή αναστολή δρομολογίων. Αυτή η γραμμή δεν λειτουργεί αυτή τη στιγμή."
            case .albanian: return "Shërbimi përkohësisht i pezulluar. Kjo linjë nuk është në punë për momentin."
            case .italian: return "Servizio temporaneamente sospeso. Questa linea non e in servizio in questo momento."
            default: return "Service temporarily suspended. This line is not running right now."
            }
        case .construction:
            switch loc.language {
            case .greek: return "Δεν λειτουργεί ακόμη. Η γραμμή έχει κατασκευαστεί αλλά δεν έχει τεθεί σε επιβατική λειτουργία."
            case .albanian: return "Ende jo e hapur. Hekurudha eshte ndertuar por ende nuk eshte ne sherbim per pasagjere."
            case .italian: return "Non ancora in servizio. Il binario e costruito ma non ancora in servizio passeggeri."
            default: return "Not yet open. The track is built but not yet in passenger service."
            }
        case .seasonal:
            switch loc.language {
            case .greek: return "Εποχικό δρομολόγιο. Λειτουργεί επιλεγμένες ημέρες και εποχές. Δεν υπάρχει προγραμματισμένη αναχώρηση από εδώ αυτή τη στιγμή."
            case .albanian: return "Sherbim sezonal. Funksionon ne dite dhe stine te zgjedhura. Asgje nuk eshte planifikuar nga ketu per momentin."
            case .italian: return "Servizio stagionale. Attivo in giorni e stagioni selezionati. Al momento non e prevista alcuna partenza da qui."
            default: return "Seasonal service. Runs on selected days and seasons. Nothing is scheduled from here right now."
            }
        }
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
    let disruptions: [String: String]

    var body: some View {
        FlowLayout(spacing: 6) {
            ForEach(lineIds, id: \.self) { lineId in
                StationLinePill(lineId: lineId, disruptionSeverity: disruptions[lineId])
            }
        }
    }
}

private struct StationLinePill: View {
    let lineId: String
    let disruptionSeverity: String?

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
        .overlay(alignment: .topTrailing) {
            LineDisruptionDot(severity: disruptionSeverity)
                .offset(x: 3, y: -3)
        }
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

struct ServiceAlertBanner: View {
    let alert: STASYAnnouncement
    let language: AppLanguage

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Text("⚠️")
                .font(.subheadline)
            VStack(alignment: .leading, spacing: 4) {
                Text(LocalizedKey.serviceAlertAffectsLine.text(for: language))
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundStyle(Color.orange)
                Text(alert.displayTitle(language: language))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(3)
            }
        }
        .padding(.vertical, 4)
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
