import SwiftUI

struct RailPulseReportContext: Identifiable, Hashable {
    let scopeId: String
    let title: String
    let subtitle: String

    var id: String { "\(title)|\(subtitle)" }
}

struct IchnosCommunityIssue: Codable, Identifiable {
    let scopeId: String
    let scopeLabel: String
    let signal: String
    let detail: String
    let count: Int
    let latestAt: String

    var id: String { "\(scopeId):\(signal):\(detail)" }
}

struct IchnosCommunitySummary: Codable {
    let displayMode: String
    let scopeId: String?
    let activeIssueCount: Int
    let normalReportCount: Int
    let totalReportsThisWeek: Int
    let estimatedJourneysToday: Int?
    let estimatedDailyJourneys: Int?
    let issues: [IchnosCommunityIssue]
    let updatedAt: String

    var hasIssues: Bool { displayMode == "issues" && !issues.isEmpty }
}

struct IchnosHistoryBucket: Codable, Identifiable {
    let period: String
    let totalReports: Int
    let positiveReports: Int
    let issueReports: Int
    let counts: [String: Int]

    var id: String { period }
}

struct IchnosCommunityHistory: Codable {
    let granularity: String
    let scopeId: String?
    let buckets: [IchnosHistoryBucket]
    let updatedAt: String
    let privacy: String
}

actor IchnosCommunityService {
    static let shared = IchnosCommunityService()
    private let baseURL = URL(string: "https://api-syrmos.peterdsp.dev")!

    func fetchSummary(scopeId: String? = nil) async -> IchnosCommunitySummary? {
        var components = URLComponents(url: baseURL.appendingPathComponent("api/community/summary"), resolvingAgainstBaseURL: false)
        if let scopeId, !scopeId.isEmpty {
            components?.queryItems = [URLQueryItem(name: "scopeId", value: scopeId)]
        }
        guard let url = components?.url else { return nil }
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
            return try JSONDecoder().decode(IchnosCommunitySummary.self, from: data)
        } catch {
            return nil
        }
    }

    func fetchHistory(period: String = "day", scopeId: String? = nil, limit: Int = 31) async -> IchnosCommunityHistory? {
        var components = URLComponents(url: baseURL.appendingPathComponent("api/community/history"), resolvingAgainstBaseURL: false)
        var queryItems = [
            URLQueryItem(name: "period", value: period),
            URLQueryItem(name: "limit", value: String(limit)),
        ]
        if let scopeId, !scopeId.isEmpty {
            queryItems.append(URLQueryItem(name: "scopeId", value: scopeId))
        }
        components?.queryItems = queryItems
        guard let url = components?.url else { return nil }
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
            return try JSONDecoder().decode(IchnosCommunityHistory.self, from: data)
        } catch {
            return nil
        }
    }

    func submit(
        reportId: String,
        context: RailPulseReportContext,
        signal: String,
        detail: String,
        language: AppLanguage
    ) async -> Bool {
        var request = URLRequest(url: baseURL.appendingPathComponent("api/community/reports"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: [
            "reportId": reportId,
            "scopeId": context.scopeId,
            "scopeLabel": context.title,
            "signal": signal,
            "detail": detail,
            "platform": "ios",
            "locale": language.rawValue,
        ])
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            return (response as? HTTPURLResponse)?.statusCode == 200
        } catch {
            return false
        }
    }

    func delete(reportId: String) async -> Bool {
        var request = URLRequest(url: baseURL.appendingPathComponent("api/community/reports/\(reportId)"))
        request.httpMethod = "DELETE"
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            return (response as? HTTPURLResponse)?.statusCode == 200
        } catch {
            return false
        }
    }
}

enum ExploreSheet: Identifiable {
    case quickReport(RailPulseReportContext)
    case originPicker

    var id: String {
        switch self {
        case .quickReport(let context): return "quick-report|\(context.id)"
        case .originPicker: return "origin-picker"
        }
    }
}

private struct PulseFeedItem: Identifiable {
    let id: String
    let title: String
    let detail: String
    let status: String
    let color: Color
}

private enum QuickReportSignal: String, CaseIterable, Identifiable {
    case normal
    case delayed
    case crowded
    case stopped
    case tooHot
    case clean
    case access
    case facilities
    case safety
    case other

    var id: String { rawValue }

    var systemImage: String {
        switch self {
        case .normal: return "checkmark.circle.fill"
        case .delayed: return "clock.badge.exclamationmark"
        case .crowded: return "person.3.fill"
        case .stopped: return "pause.circle.fill"
        case .tooHot: return "thermometer.sun.fill"
        case .clean: return "sparkles"
        case .access: return "figure.roll"
        case .facilities: return "powerplug.fill"
        case .safety: return "exclamationmark.triangle.fill"
        case .other: return "ellipsis"
        }
    }

    func localized(_ language: AppLanguage) -> String {
        switch self {
        case .normal: return pulseText(language, "Everything OK", "Όλα καλά", "Gjithçka në rregull", "Tutto bene")
        case .delayed: return pulseText(language, "Delayed", "Καθυστέρηση", "Vonesë", "Ritardo")
        case .crowded: return pulseText(language, "Crowded", "Κόσμος", "Plot", "Affollato")
        case .stopped: return pulseText(language, "Stopped", "Σταματημένο", "Ndaluar", "Fermo")
        case .tooHot: return pulseText(language, "Too hot", "Πολύ ζέστη", "Shumë nxehtë", "Troppo caldo")
        case .clean: return pulseText(language, "Clean", "Καθαρό", "Pastër", "Pulito")
        case .access: return pulseText(language, "Access", "Πρόσβαση", "Akses", "Accesso")
        case .facilities: return pulseText(language, "Facilities", "Παροχές", "Shërbime", "Servizi")
        case .safety: return pulseText(language, "Safety", "Ασφάλεια", "Siguri", "Sicurezza")
        case .other: return pulseText(language, "Other", "Άλλο", "Tjetër", "Altro")
        }
    }

    var wireName: String {
        self == .tooHot ? "too_hot" : rawValue
    }
}

struct ExploreUniversalSearchField: View {
    @Binding var text: String
    let language: AppLanguage

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(.secondary)
            TextField(
                pulseText(
                    language,
                    "Destination, station, line or train...",
                    "Προορισμός, σταθμός, γραμμή ή τρένο...",
                    "Destinacion, stacion, linjë ose tren...",
                    "Destinazione, stazione, linea o treno..."
                ),
                text: $text
            )
            .textFieldStyle(.plain)
            .font(.subheadline)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()

            if !text.isEmpty {
                Button {
                    text = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(.tertiary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(pulseText(language, "Clear", "Καθαρισμός", "Pastro", "Cancella"))
            }
        }
        .padding(.horizontal, 14)
        .frame(minHeight: 48)
        .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 17, style: .continuous))
        .shadow(color: .black.opacity(0.07), radius: 8, y: 4)
    }
}

struct ExploreRailPulseContent: View {
    let language: AppLanguage
    let onReport: (RailPulseReportContext) -> Void
    let onOpenStation: () -> Void
    let onOpenTrain: () -> Void
    let onSeeAll: () -> Void
    let originId: String?
    let originName: String?
    let originUsesGPS: Bool
    let onChooseOrigin: () -> Void
    @State private var selectedBudget = 30
    @State private var networkSummary: IchnosCommunitySummary?
    @State private var didLoadCommunity = false

    private var selectedOriginName: String? {
        guard let originName = originName?.trimmingCharacters(in: .whitespacesAndNewlines), !originName.isEmpty else {
            return nil
        }
        return originName
    }

    private var reportContext: RailPulseReportContext {
        guard let selectedOriginName else {
            return RailPulseReportContext(
                scopeId: "network",
                title: pulseText(language, "Ichnos nearby", "Ichnos κοντά σου", "Ichnos pranë teje", "Ichnos vicino a te"),
                subtitle: pulseText(language, "Choose an origin to see nearby rail reports", "Επίλεξε αφετηρία για κοντινές αναφορές", "Zgjidh nisjen për raportet pranë", "Scegli una partenza per i report vicini")
            )
        }
        return RailPulseReportContext(
            scopeId: originId ?? stableIchnosScopeId(selectedOriginName),
            title: pulseText(language, "Ichnos at \(selectedOriginName)", "Ichnos στο \(selectedOriginName)", "Ichnos në \(selectedOriginName)", "Ichnos a \(selectedOriginName)"),
            subtitle: pulseText(language, "Community rail status near your origin", "Κατάσταση rail κοντά στην αφετηρία σου", "Gjendja rail pranë nisjes tënde", "Stato ferroviario vicino alla partenza")
        )
    }

    private var feed: [PulseFeedItem] {
        ichnosFeed(language: language, summary: networkSummary, didLoad: didLoadCommunity)
    }

    var body: some View {
        VStack(spacing: 12) {
            routePulseHero
            sectionTitle(
                pulseText(language, "Ichnos across Greece", "Ichnos σε όλη την Ελλάδα", "Ichnos në gjithë Greqinë", "Ichnos in tutta la Grecia"),
                action: pulseText(language, "History", "Ιστορικό", "Historia", "Storico"),
                onAction: onSeeAll
            )
            ForEach(feed) { item in
                Button {
                    onOpenTrain()
                } label: {
                    pulseFeedRow(item)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(item.title). \(item.detail)")
            }
            sectionTitle(
                pulseText(language, "Explore by time", "Εξερεύνηση με χρόνο", "Eksploro sipas kohës", "Esplora per tempo"),
                action: originActionLabel,
                systemImage: originUsesGPS ? "location.fill" : "mappin.circle.fill",
                trailingInset: 64,
                onAction: onChooseOrigin
            )
            budgetRow
            Text(ichnosAriadneText(language: language, summary: networkSummary, didLoad: didLoadCommunity))
            .font(.caption.weight(.semibold))
            .foregroundStyle(SyrmosTokens.suburban)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 14)
            .padding(.vertical, 11)
            .background(SyrmosTokens.suburban.opacity(0.10), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .task {
            networkSummary = await IchnosCommunityService.shared.fetchSummary()
            didLoadCommunity = true
        }
    }

    private var routePulseHero: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(pulseText(language, "ICHNOS NEAR YOU", "ICHNOS ΚΟΝΤΑ ΣΟΥ", "ICHNOS PRANË TEJE", "ICHNOS VICINO A TE"))
                    .font(.caption2.weight(.bold))
                Spacer()
                Image(systemName: selectedOriginName == nil ? "mappin.circle" : "location.fill")
                    .font(.caption2.weight(.bold))
                Text(selectedOriginName == nil
                    ? pulseText(language, "Choose origin", "Επιλογή αφετηρίας", "Zgjidh nisjen", "Scegli partenza")
                    : pulseText(language, "Selected origin", "Επιλεγμένη αφετηρία", "Nisja e zgjedhur", "Partenza selezionata"))
                    .font(.caption2.weight(.bold))
            }
            Text(reportContext.title)
                .font(.title3.weight(.bold))
            Text(reportContext.subtitle)
                .font(.subheadline)
            Button(action: onChooseOrigin) {
                HStack(spacing: 7) {
                    Image(systemName: selectedOriginName == nil ? "mappin.and.ellipse" : "location.fill")
                    Text(selectedOriginName ?? pulseText(language, "Use GPS or choose a station", "Χρήση GPS ή επιλογή σταθμού", "Përdor GPS ose zgjidh stacion", "Usa il GPS o scegli una stazione"))
                    Spacer()
                    Image(systemName: "chevron.right")
                }
                .font(.caption.weight(.semibold))
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(.white.opacity(0.15), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .buttonStyle(.plain)
            HStack(spacing: 10) {
                Text(pulseText(language, "Official data + community reports", "Επίσημα δεδομένα + αναφορές", "Të dhëna zyrtare + raporte", "Dati ufficiali + segnalazioni"))
                    .font(.caption2)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.white.opacity(0.15), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                Button(selectedOriginName == nil
                    ? pulseText(language, "Choose", "Επιλογή", "Zgjidh", "Scegli")
                    : pulseText(language, "Report", "Αναφορά", "Raporto", "Segnala")) {
                    if selectedOriginName == nil {
                        onChooseOrigin()
                    } else {
                        onReport(reportContext)
                    }
                }
                .buttonStyle(.borderedProminent)
                .tint(.white)
                .foregroundStyle(.black)
                .controlSize(.small)
                .accessibilityHint(pulseText(language, "Opens the Ichnos quick report", "Ανοίγει τη γρήγορη αναφορά Ichnos", "Hap raportin e shpejtë Ichnos", "Apre la segnalazione rapida Ichnos"))
            }
        }
        .foregroundStyle(.white)
        .padding(18)
        .background(
            LinearGradient(
                colors: [Color(hex: 0x153D52), Color(hex: 0x2B6966), SyrmosTokens.suburban],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ),
            in: RoundedRectangle(cornerRadius: 24, style: .continuous)
        )
    }

    private var originActionLabel: String {
        guard let originName, !originName.isEmpty else {
            return pulseText(language, "Choose origin", "Επιλογή αφετηρίας", "Zgjidh nisjen", "Scegli partenza")
        }
        return pulseText(language, "From \(originName)", "Από \(originName)", "Nga \(originName)", "Da \(originName)")
    }

    private func sectionTitle(
        _ title: String,
        action: String,
        systemImage: String? = nil,
        trailingInset: CGFloat = 0,
        onAction: @escaping () -> Void
    ) -> some View {
        HStack {
            Text(title).font(.headline)
            Spacer()
            Button(action: onAction) {
                HStack(spacing: 4) {
                    if let systemImage {
                        Image(systemName: systemImage)
                    }
                    Text(action)
                }
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.syrmosPrimary)
                .padding(.horizontal, 8)
                .padding(.vertical, 6)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.top, 2)
        .padding(.trailing, trailingInset)
    }

    private func pulseFeedRow(_ item: PulseFeedItem) -> some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(item.color.opacity(0.12)).frame(width: 34, height: 34)
                Circle().fill(item.color).frame(width: 9, height: 9)
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(item.title).font(.subheadline.weight(.semibold))
                Text(item.detail).font(.caption).foregroundStyle(.secondary)
            }
            Spacer(minLength: 4)
            Text(item.status).font(.caption2.weight(.bold)).foregroundStyle(item.color)
        }
        .padding(14)
        .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 17, style: .continuous))
        .shadow(color: .black.opacity(0.05), radius: 5, y: 3)
    }

    private var budgetRow: some View {
        HStack(spacing: 8) {
            ForEach([30, 60, 90, 120], id: \.self) { minutes in
                let selected = selectedBudget == minutes
                Button {
                    selectedBudget = minutes
                } label: {
                    Text(minutes == 120 ? "2 h+" : "\(minutes) m")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(selected ? Color.syrmosSurface : .primary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(selected ? Color.primary : Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
    }
}

struct ExploreOriginPickerSheet: View {
    let language: AppLanguage
    @ObservedObject var locationService: LocationService
    let selectedStationId: String?
    let onSelect: (MapStationNode) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var searchText = ""

    private var filteredStations: [MapStationNode] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return Array(PreloadedData.stations.prefix(80)) }
        return PreloadedData.stations.filter { station in
            station.displayName.localizedCaseInsensitiveContains(query)
                || station.nameEl.localizedCaseInsensitiveContains(query)
                || station.lineIds.contains { $0.localizedCaseInsensitiveContains(query) }
        }
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button(action: useCurrentLocation) {
                        HStack(spacing: 12) {
                            Image(systemName: locationService.isDenied ? "location.slash.fill" : "location.fill")
                                .foregroundStyle(Color.syrmosPrimary)
                                .frame(width: 32, height: 32)
                                .background(Color.syrmosPrimary.opacity(0.10), in: Circle())
                            VStack(alignment: .leading, spacing: 2) {
                                Text(currentLocationTitle)
                                    .font(.subheadline.weight(.semibold))
                                Text(currentLocationSubtitle)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }

                Section(pulseText(language, "Choose a station", "Επίλεξε σταθμό", "Zgjidh një stacion", "Scegli una stazione")) {
                    ForEach(filteredStations) { station in
                        Button {
                            onSelect(station)
                            dismiss()
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: "tram.fill")
                                    .foregroundStyle(SyrmosData.lineColor(for: station.lineIds.first ?? "M1"))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(stationName(station))
                                        .foregroundStyle(.primary)
                                    Text(station.lineIds.joined(separator: "  "))
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                if selectedStationId == station.id {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(Color.syrmosPrimary)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .searchable(
                text: $searchText,
                prompt: pulseText(language, "Station or line", "Σταθμός ή γραμμή", "Stacion ose linjë", "Stazione o linea")
            )
            .navigationTitle(pulseText(language, "Explore from", "Εξερεύνηση από", "Eksploro nga", "Esplora da"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(pulseText(language, "Done", "Τέλος", "U krye", "Fine")) { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private var currentLocationTitle: String {
        if locationService.isDenied {
            return pulseText(language, "Enable location", "Ενεργοποίηση τοποθεσίας", "Aktivizo vendndodhjen", "Attiva posizione")
        }
        if let nearest = locationService.nearbyStations.first {
            return pulseText(language, "Use \(stationName(nearest.station))", "Χρήση \(stationName(nearest.station))", "Përdor \(stationName(nearest.station))", "Usa \(stationName(nearest.station))")
        }
        return pulseText(language, "Use my location", "Χρήση τοποθεσίας μου", "Përdor vendndodhjen time", "Usa la mia posizione")
    }

    private var currentLocationSubtitle: String {
        if locationService.isDenied {
            return pulseText(language, "Open Settings to allow GPS", "Άνοιξε τις Ρυθμίσεις για GPS", "Hap Cilësimet per GPS", "Apri Impostazioni per il GPS")
        }
        if let nearest = locationService.nearbyStations.first {
            return pulseText(language, "Nearest station, \(Int(nearest.distanceMeters)) m away", "Πλησιέστερος σταθμός, \(Int(nearest.distanceMeters)) μ", "Stacioni më i afërt, \(Int(nearest.distanceMeters)) m", "Stazione piu vicina, \(Int(nearest.distanceMeters)) m")
        }
        return pulseText(language, "Find the nearest station on this device", "Βρες τον πλησιέστερο σταθμό στη συσκευή", "Gjej stacionin më të afërt në pajisje", "Trova la stazione piu vicina sul dispositivo")
    }

    private func useCurrentLocation() {
        if locationService.isDenied {
            locationService.openSystemSettings()
        } else if let nearest = locationService.nearbyStations.first {
            onSelect(nearest.station)
            dismiss()
        } else {
            locationService.requestIfNeeded()
        }
    }

    private func stationName(_ station: MapStationNode) -> String {
        language == .greek ? station.nameEl : station.displayName
    }
}

struct RailPulseQuickReportSheet: View {
    let context: RailPulseReportContext
    let language: AppLanguage
    @Environment(\.dismiss) private var dismiss
    @State private var selected: QuickReportSignal?
    @State private var crowdLevel = "Standing"
    @State private var hasRecorded = false
    @State private var canUndo = false
    @State private var isSending = false
    @State private var sendFailed = false
    @State private var wasSent = false
    @State private var reportId = "report_\(UUID().uuidString.replacingOccurrences(of: "-", with: ""))"
    @ObservedObject private var contributionStore = RailPulseLocalStore.shared

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 10), count: 3)

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text(pulseText(language, "Quick report", "Γρήγορη αναφορά", "Raport i shpejtë", "Segnalazione rapida"))
                        .font(.title2.weight(.bold))
                    Spacer()
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.headline)
                            .frame(width: 36, height: 36)
                            .background(Color.syrmosSurfaceMuted, in: Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(pulseText(language, "Close", "Κλείσιμο", "Mbyll", "Chiudi"))
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text(context.title).font(.headline)
                    Text(context.subtitle).font(.caption).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 17, style: .continuous))
                Text(pulseText(language, "Tap once. Report only what you can see right now.", "Πάτησε μια φορά. Ανάφερε μόνο ό,τι βλέπεις τώρα.", "Prek një herë. Raporto vetëm atë që sheh tani.", "Un tocco. Segnala solo cio che vedi ora."))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(SyrmosTokens.live)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(SyrmosTokens.live.opacity(0.10), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                Text(pulseText(language, "What is happening?", "Τι συμβαίνει;", "Çfarë po ndodh?", "Cosa sta succedendo?"))
                    .font(.headline)
                LazyVGrid(columns: columns, spacing: 10) {
                    ForEach(QuickReportSignal.allCases) { signal in
                        let active = selected == signal
                        Button {
                            selected = signal
                            if signal == .crowded { crowdLevel = "Standing" }
                            submit(signal)
                        } label: {
                            VStack(spacing: 7) {
                                Image(systemName: signal.systemImage)
                                    .font(.title3)
                                Text(signal.localized(language))
                                    .font(.caption.weight(.semibold))
                                    .lineLimit(1)
                            }
                            .foregroundStyle(active ? .white : .primary)
                            .frame(maxWidth: .infinity, minHeight: 78)
                            .background(active ? SyrmosTokens.suburban : Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .disabled(isSending)
                        .accessibilityAddTraits(active ? .isSelected : [])
                    }
                }
                if selected == .crowded {
                    Text(pulseText(language, "Crowd level", "Επίπεδο πληρότητας", "Niveli i turmës", "Livello affollamento"))
                        .font(.subheadline.weight(.bold))
                    HStack(spacing: 6) {
                        ForEach(["Empty", "Seats", "Half", "Standing", "Packed"], id: \.self) { level in
                            let active = crowdLevel == level
                            Button {
                                crowdLevel = level
                                if wasSent, selected == .crowded {
                                    submit(.crowded)
                                }
                            } label: {
                                Text(localizedCrowdLevel(level))
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundStyle(active ? .white : .primary)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 11)
                                    .background(active ? SyrmosTokens.suburban : Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                            }
                            .buttonStyle(.plain)
                            .disabled(isSending)
                        }
                    }
                }
                if isSending {
                    HStack(spacing: 9) {
                        ProgressView()
                        Text(pulseText(language, "Sending anonymous report...", "Αποστολή ανώνυμης αναφοράς...", "Po dërgohet raporti anonim...", "Invio della segnalazione anonima..."))
                            .font(.caption.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(12)
                }
                if sendFailed {
                    Text(pulseText(language, "Report was not sent. Check your connection and try again.", "Η αναφορά δεν στάλθηκε. Έλεγξε τη σύνδεση και προσπάθησε ξανά.", "Raporti nuk u dërgua. Kontrollo lidhjen dhe provo përsëri.", "Segnalazione non inviata. Controlla la connessione e riprova."))
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(SyrmosTokens.disruption)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(SyrmosTokens.disruption.opacity(0.10), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                if let selected, wasSent {
                    HStack(spacing: 8) {
                        Text("✓ \(pulseText(language, "Report sent", "Η αναφορά στάλθηκε", "Raporti u dërgua", "Segnalazione inviata")) · \(selected.localized(language))")
                            .font(.subheadline.weight(.bold))
                            .frame(maxWidth: .infinity)
                        if canUndo {
                            Button(pulseText(language, "Undo", "Ανάκληση", "Zhbëj", "Annulla")) {
                                undoReport()
                            }
                            .font(.caption.weight(.bold))
                            .buttonStyle(.plain)
                        }
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 14)
                    .background(SyrmosTokens.live, in: RoundedRectangle(cornerRadius: 17, style: .continuous))
                    Text(pulseText(language, "No account, device ID, or location is included. Active reports expire after two hours and are deleted within seven days. An anonymous daily count remains in railway history.", "Δεν περιλαμβάνεται λογαριασμός, αναγνωριστικό συσκευής ή τοποθεσία. Οι ενεργές αναφορές λήγουν σε δυο ώρες και διαγράφονται εντός επτά ημερών. Ένα ανώνυμο ημερήσιο σύνολο παραμένει στο σιδηροδρομικό ιστορικό.", "Nuk përfshihet llogari, ID pajisjeje ose vendndodhje. Raportet aktive skadojnë pas dy orësh dhe fshihen brenda shtatë ditësh. Një numër anonim ditor mbetet në historinë hekurudhore.", "Non vengono inclusi account, ID del dispositivo o posizione. Le segnalazioni attive scadono dopo due ore e vengono eliminate entro sette giorni. Un conteggio giornaliero anonimo resta nello storico ferroviario."))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                }
                Text(pulseText(language, "For immediate danger, contact emergency services. Ichnos is not an emergency channel.", "Για άμεσο κίνδυνο, επικοινώνησε με τις υπηρεσίες έκτακτης ανάγκης. Το Ichnos δεν είναι κανάλι έκτακτης ανάγκης.", "Për rrezik të menjëhershëm, kontakto shërbimet e emergjencës. Ichnos nuk është kanal emergjence.", "Per un pericolo immediato, contatta i servizi di emergenza. Ichnos non e un canale di emergenza."))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(SyrmosTokens.warning)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(SyrmosTokens.warning.opacity(0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .padding(20)
        }
        .background(Color.syrmosBackground)
        .task(id: canUndo) {
            guard canUndo else { return }
            try? await Task.sleep(nanoseconds: 10_000_000_000)
            guard !Task.isCancelled else { return }
            canUndo = false
        }
    }

    private func submit(_ signal: QuickReportSignal) {
        guard !isSending else { return }
        isSending = true
        sendFailed = false
        Task {
            let detail = signal == .crowded ? crowdLevel.lowercased() : ""
            let sent = await IchnosCommunityService.shared.submit(
                reportId: reportId,
                context: context,
                signal: signal.wireName,
                detail: detail,
                language: language
            )
            isSending = false
            sendFailed = !sent
            guard sent else { return }
            if !hasRecorded {
                contributionStore.recordContribution()
                hasRecorded = true
            }
            wasSent = true
            canUndo = true
        }
    }

    private func undoReport() {
        guard wasSent, !isSending else { return }
        isSending = true
        Task {
            let deleted = await IchnosCommunityService.shared.delete(reportId: reportId)
            isSending = false
            guard deleted else {
                sendFailed = true
                return
            }
            if hasRecorded { contributionStore.undoContribution() }
            selected = nil
            hasRecorded = false
            wasSent = false
            canUndo = false
            sendFailed = false
            reportId = "report_\(UUID().uuidString.replacingOccurrences(of: "-", with: ""))"
        }
    }

    private func localizedCrowdLevel(_ level: String) -> String {
        switch level {
        case "Empty": return pulseText(language, "Empty", "Άδειο", "Bosh", "Vuoto")
        case "Seats": return pulseText(language, "Seats", "Θέσεις", "Vende", "Posti")
        case "Half": return pulseText(language, "Half", "Μέτριο", "Gjysmë", "Meta")
        case "Packed": return pulseText(language, "Packed", "Γεμάτο", "Plot", "Pieno")
        default: return pulseText(language, "Standing", "Όρθιοι", "Në këmbë", "In piedi")
        }
    }
}

private func stableIchnosScopeId(_ value: String) -> String {
    var hash: UInt64 = 14_695_981_039_346_656_037
    for byte in value.utf8 {
        hash ^= UInt64(byte)
        hash = hash &* 1_099_511_628_211
    }
    return "scope_\(String(hash, radix: 16))"
}

private func ichnosFeed(language: AppLanguage, summary: IchnosCommunitySummary?, didLoad: Bool) -> [PulseFeedItem] {
    guard let summary else {
        return [PulseFeedItem(
            id: "network-state",
            title: didLoad
                ? pulseText(language, "Community status unavailable", "Η κατάσταση κοινότητας δεν είναι διαθέσιμη", "Gjendja e komunitetit nuk është e disponueshme", "Stato della comunita non disponibile")
                : pulseText(language, "Loading community status", "Φόρτωση κατάστασης κοινότητας", "Po ngarkohet gjendja e komunitetit", "Caricamento stato della comunita"),
            detail: didLoad
                ? pulseText(language, "Check your connection and try again", "Έλεγξε τη σύνδεση και προσπάθησε ξανά", "Kontrollo lidhjen dhe provo përsëri", "Controlla la connessione e riprova")
                : pulseText(language, "Anonymous reports from the last two hours", "Ανώνυμες αναφορές των τελευταίων δυο ωρών", "Raporte anonime nga dy orët e fundit", "Segnalazioni anonime delle ultime due ore"),
            status: didLoad ? pulseText(language, "Offline", "Εκτός σύνδεσης", "Jashtë linje", "Offline") : pulseText(language, "Loading", "Φόρτωση", "Ngarkim", "Caricamento"),
            color: didLoad ? SyrmosTokens.warning : SyrmosTokens.metroBlue
        )]
    }

    if summary.hasIssues {
        return summary.issues.prefix(5).map { issue in
            PulseFeedItem(
                id: issue.id,
                title: issue.scopeLabel,
                detail: ichnosIssueLabel(issue, language: language),
                status: "\(issue.count) \(pulseText(language, "reports", "αναφορες", "raporte", "segnalazioni"))",
                color: ichnosIssueColor(issue.signal)
            )
        }
    }

    let estimate = summary.estimatedJourneysToday ?? 0
    var items = [PulseFeedItem(
        id: "network-clear",
        title: pulseText(language, "No active issues reported", "Δεν αναφέρθηκαν ενεργά προβλήματα", "Nuk ka probleme aktive të raportuara", "Nessun problema attivo segnalato"),
        detail: pulseText(language, "Estimated \(estimate) rail journeys so far today. This is an estimate, not a report count.", "Εκτιμώμενες \(estimate) σιδηροδρομικές διαδρομές σήμερα. Είναι εκτίμηση, όχι αριθμός αναφορών.", "Rreth \(estimate) udhëtime hekurudhore sot. Është vlerësim, jo numër raportesh.", "Circa \(estimate) viaggi ferroviari oggi. E una stima, non un conteggio di segnalazioni."),
        status: pulseText(language, "Clear", "Καθαρά", "Në rregull", "Regolare"),
        color: SyrmosTokens.live
    )]
    if summary.normalReportCount > 0 {
        items.append(PulseFeedItem(
            id: "network-confirmed-normal",
            title: pulseText(language, "Everything OK", "Όλα καλά", "Gjithçka në rregull", "Tutto bene"),
            detail: pulseText(language, "Confirmed by anonymous Ichnos reports", "Επιβεβαιώθηκε από ανώνυμες αναφορές Ichnos", "Konfirmuar nga raporte anonime Ichnos", "Confermato da segnalazioni anonime Ichnos"),
            status: "\(summary.normalReportCount)",
            color: SyrmosTokens.live
        ))
    }
    return items
}

private func ichnosAriadneText(language: AppLanguage, summary: IchnosCommunitySummary?, didLoad: Bool) -> String {
    guard let summary else {
        return didLoad
            ? pulseText(language, "Ichnos community status is temporarily unavailable.", "Η κατάσταση κοινότητας Ichnos δεν είναι προσωρινά διαθέσιμη.", "Gjendja e komunitetit Ichnos nuk është përkohësisht e disponueshme.", "Lo stato della comunita Ichnos non e temporaneamente disponibile.")
            : pulseText(language, "Loading Ichnos community status...", "Φόρτωση κατάστασης κοινότητας Ichnos...", "Po ngarkohet gjendja e komunitetit Ichnos...", "Caricamento dello stato della comunita Ichnos...")
    }
    if summary.hasIssues {
        return pulseText(language, "Ichnos: active community issues are shown above. Estimated normal-journey counts are hidden while any issue is active.", "Ichnos: τα ενεργά προβλήματα κοινότητας εμφανίζονται παραπάνω. Οι εκτιμήσεις κανονικών διαδρομών κρύβονται όσο υπάρχει ενεργό πρόβλημα.", "Ichnos: problemet aktive të komunitetit shfaqen më sipër. Vlerësimet e udhëtimeve normale fshihen kur ka problem aktiv.", "Ichnos: i problemi attivi della comunita sono mostrati sopra. Le stime dei viaggi regolari sono nascoste mentre un problema e attivo.")
    }
    return pulseText(language, "Ichnos: no active issue reports right now.", "Ichnos: δεν υπάρχουν ενεργές αναφορές προβλημάτων τώρα.", "Ichnos: nuk ka raporte aktive problemesh tani.", "Ichnos: nessuna segnalazione attiva di problemi al momento.")
}

func ichnosIssueLabel(_ issue: IchnosCommunityIssue, language: AppLanguage) -> String {
    let label: String
    switch issue.signal {
    case "delayed": label = pulseText(language, "Delay", "Καθυστέρηση", "Vonesë", "Ritardo")
    case "crowded": label = pulseText(language, "Crowded", "Πολύς κόσμος", "Plot", "Affollato")
    case "stopped": label = pulseText(language, "Service stopped", "Η κίνηση σταμάτησε", "Shërbimi u ndal", "Servizio fermo")
    case "too_hot": label = pulseText(language, "Too hot", "Πολύ ζέστη", "Shumë nxehtë", "Troppo caldo")
    case "access": label = pulseText(language, "Accessibility issue", "Πρόβλημα πρόσβασης", "Problem aksesueshmërie", "Problema di accessibilita")
    case "facilities": label = pulseText(language, "Facility issue", "Πρόβλημα παροχών", "Problem shërbimesh", "Problema ai servizi")
    case "safety": label = pulseText(language, "Safety concern", "Θέμα ασφάλειας", "Shqetësim sigurie", "Problema di sicurezza")
    default: label = pulseText(language, "Other issue", "Άλλο πρόβλημα", "Problem tjetër", "Altro problema")
    }
    return issue.detail.isEmpty ? label : "\(label): \(issue.detail)"
}

func ichnosIssueColor(_ signal: String) -> Color {
    switch signal {
    case "delayed", "crowded", "too_hot": return SyrmosTokens.warning
    default: return SyrmosTokens.disruption
    }
}

func pulseText(
    _ language: AppLanguage,
    _ english: String,
    _ greek: String,
    _ albanian: String,
    _ italian: String
) -> String {
    switch language {
    case .english: return english
    case .greek: return greek
    case .albanian: return albanian
    case .italian: return italian
    }
}

#Preview("Explore Ichnos") {
    ScrollView {
        ExploreRailPulseContent(
            language: .english,
            onReport: { _ in },
            onOpenStation: {},
            onOpenTrain: {},
            onSeeAll: {},
            originId: "M2_SYN",
            originName: "Syntagma",
            originUsesGPS: true,
            onChooseOrigin: {}
        )
            .padding()
    }
    .background(Color.syrmosBackground)
}

#Preview("Quick report") {
    RailPulseQuickReportSheet(
        context: RailPulseReportContext(scopeId: "M1_KAL", title: "Kallithea to Monastiraki", subtitle: "M1 toward Kifissia"),
        language: .english
    )
}
