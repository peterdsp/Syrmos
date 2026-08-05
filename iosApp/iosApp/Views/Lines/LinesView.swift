import SwiftUI

enum ExploreSegment: String, CaseIterable {
    case destinations
    case yourNetwork
}

struct LinesView: View {
    let lines = SyrmosData.lines
    @ObservedObject private var loc = LocalizationManager.shared
    @State private var searchText = ""
    @State private var selectedRegion: TransitRegion? = nil
    @State private var selectedType: TransitType? = nil
    @State private var segment: ExploreSegment = .destinations
    @State private var recentStations: [RecentStation] = RecentStationStore.load()
    @State private var presentedSheet: ExploreSheet?
    @State private var railPulseDestination: RailPulseDestination?
    @StateObject private var stasyService = STASYService()

    private var filteredLines: [TransitLine] {
        lines.filter { line in
            let matchesRegion = selectedRegion == nil || line.region == selectedRegion
            let matchesType = selectedType == nil || line.type == selectedType
            let matchesSearch = searchText.isEmpty || {
                let q = searchText.lowercased()
                return line.name.lowercased().contains(q)
                    || line.nameEl.lowercased().contains(q)
                    || line.terminalA.lowercased().contains(q)
                    || line.terminalB.lowercased().contains(q)
                    || line.id.lowercased().contains(q)
            }()
            return matchesRegion && matchesType && matchesSearch
        }
    }

    private var filteredDestinations: [CuratedDestination] {
        guard !searchText.isEmpty else { return CuratedDestination.all }
        let query = searchText.lowercased()
        return CuratedDestination.all.filter { destination in
            destination.name(loc.language).lowercased().contains(query)
                || destination.hook(loc.language).lowercased().contains(query)
                || destination.stationId.lowercased().contains(query)
                || destination.lineId.lowercased().contains(query)
                || destination.connections.contains { $0.lowercased().contains(query) }
        }
    }

    var body: some View {
        NavigationStack {
            List {
                ExploreUniversalSearchField(text: $searchText, language: loc.language)
                    .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)

                segmentedControl
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)

                switch segment {
                case .destinations:
                    destinationsContent
                case .yourNetwork:
                    networkContent
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.syrmosBackground)
            .safeAreaInset(edge: .top, spacing: 0) {
                exploreHeader
            }
            .toolbar(.hidden, for: .navigationBar)
            .task { await stasyService.fetchAnnouncements() }
            .navigationDestination(item: $railPulseDestination) { destination in
                switch destination {
                case .station:
                    RailPulseStationDetailView(language: loc.language) { context in
                        presentedSheet = .quickReport(context)
                    }
                case .train:
                    RailPulseTrainDetailView(language: loc.language) { context in
                        presentedSheet = .quickReport(context)
                    }
                case .contribution:
                    RailPulseContributionView(language: loc.language)
                }
            }
        }
        .sheet(item: $presentedSheet) { sheet in
            switch sheet {
            case .quickReport(let context):
                RailPulseQuickReportSheet(context: context, language: loc.language)
                    .presentationDetents([.large])
                    .presentationDragIndicator(.visible)
            }
        }
    }

    // MARK: - Segmented Control

    private var exploreHeader: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(loc[.explore])
                    .font(.title2.weight(.bold))
                Text(
                    pulseText(
                        loc.language,
                        "Greece, live and community powered",
                        "Ελλαδα, ζωντανα και με τη δυναμη της κοινοτητας",
                        "Greqia, live dhe me fuqine e komunitetit",
                        "Grecia, live e alimentata dalla comunita"
                    )
                )
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            }

            Spacer(minLength: 8)

            Button {
                railPulseDestination = .contribution
            } label: {
                Image(systemName: "person.crop.circle.fill")
                    .font(.title2)
                    .foregroundStyle(Color.syrmosPrimary)
                    .frame(width: 44, height: 44)
                    .background(Color.syrmosPrimary.opacity(0.10), in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(
                pulseText(
                    loc.language,
                    "Local contribution",
                    "Τοπικη συνεισφορα",
                    "Kontributi lokal",
                    "Contributo locale"
                )
            )
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 10)
        .background(Color.syrmosBackground)
        .overlay(alignment: .bottom) {
            Divider().opacity(0.35)
        }
    }

    private var segmentedControl: some View {
        HStack(spacing: 4) {
            ForEach(ExploreSegment.allCases, id: \.self) { seg in
                let isSelected = seg == segment
                let label: String = {
                    switch seg {
                    case .destinations:
                        return pulseText(loc.language, "Discover", "Ανακαλυψε", "Zbulo", "Scopri")
                    case .yourNetwork:
                        return pulseText(loc.language, "Network", "Δικτυο", "Rrjeti", "Rete")
                    }
                }()

                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        segment = seg
                    }
                } label: {
                    Text(label)
                        .font(.subheadline)
                        .fontWeight(isSelected ? .semibold : .medium)
                        .foregroundStyle(isSelected ? .primary : .secondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(isSelected ? Color.syrmosSurface : .clear)
                                .shadow(color: isSelected ? .black.opacity(0.06) : .clear, radius: 2, y: 1)
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(Color.syrmosSurfaceMuted, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
    }

    // MARK: - Destinations

    @ViewBuilder
    private var destinationsContent: some View {
        if searchText.isEmpty {
            ExploreRailPulseContent(
                language: loc.language,
                onReport: { context in presentedSheet = .quickReport(context) },
                onOpenStation: { railPulseDestination = .station },
                onOpenTrain: { railPulseDestination = .train }
            )
            .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 8, trailing: 16))
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
        }

        HStack(spacing: 8) {
            Text(
                searchText.isEmpty
                    ? pulseText(loc.language, "Explore farther", "Εξερευνησε περισσοτερα", "Eksploro me tej", "Esplora oltre")
                    : pulseText(loc.language, "Search results", "Αποτελεσματα αναζητησης", "Rezultatet", "Risultati")
            )
            .font(.headline)

            Text("\(filteredDestinations.count)")
                .font(.caption2.weight(.bold))
                .foregroundStyle(Color.syrmosPrimary)
                .padding(.horizontal, 7)
                .padding(.vertical, 3)
                .background(Color.syrmosPrimary.opacity(0.10), in: Capsule())

            Spacer()

            if filteredDestinations.count > 1 {
                Image(systemName: "arrow.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
        }
        .listRowInsets(EdgeInsets(top: 10, leading: 16, bottom: 2, trailing: 16))
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)

        if filteredDestinations.isEmpty {
            ContentUnavailableView.search(text: searchText)
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 12) {
                    ForEach(Array(filteredDestinations.enumerated()), id: \.element.id) { index, dest in
                        NavigationLink {
                            DestinationDetailView(
                                destination: dest,
                                onStationViewed: { stationId, lineId in
                                    RecentStationStore.record(stationId: stationId, lineId: lineId)
                                    recentStations = RecentStationStore.load()
                                }
                            )
                        } label: {
                            DestinationCard(destination: dest, language: loc.language)
                        }
                        .buttonStyle(.plain)
                        .syrmosEntrance(index: index)
                    }
                }
                .scrollTargetLayout()
                .padding(.horizontal, 16)
                .padding(.vertical, 6)
            }
            .scrollTargetBehavior(.viewAligned)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
        }

        if searchText.isEmpty {
            NavigationLink {
                BrowseAllStationsView()
            } label: {
                browseAllRow
            }
            .syrmosEntrance(index: filteredDestinations.count)
            .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
        }
    }

    private var browseAllRow: some View {
        HStack(spacing: 13) {
            Image(systemName: "map.fill")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Color.syrmosPrimary)
                .frame(width: 40, height: 40)
                .background(Color.syrmosPrimary.opacity(0.10), in: Circle())

            VStack(alignment: .leading, spacing: 2) {
                Text(browseAllLabel)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                Text(browseAllSubtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.syrmosPrimary)
                .frame(width: 30, height: 30)
                .background(Color.syrmosPrimary.opacity(0.08), in: Circle())
        }
        .padding(.trailing, 38)
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(Color.syrmosPrimary.opacity(0.10), lineWidth: 1)
        }
    }

    // MARK: - Your Network (lines catalog)

    @ViewBuilder
    private var networkContent: some View {
        if !recentStations.isEmpty {
            Section(recentLabel) {
                ForEach(recentStations) { recent in
                    if let line = SyrmosData.line(for: recent.lineId) {
                        NavigationLink {
                            DestinationDetailView(
                                stationId: recent.stationId,
                                lineId: recent.lineId,
                                onStationViewed: { sid, lid in
                                    RecentStationStore.record(stationId: sid, lineId: lid)
                                    recentStations = RecentStationStore.load()
                                }
                            )
                        } label: {
                            RecentStationRow(recent: recent, line: line)
                        }
                    }
                }
            }
            .listRowBackground(Color.clear)
        }

        linesToolbar
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)

        if filteredLines.isEmpty {
            Text(emptyMessage)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.vertical, 32)
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
        } else {
            ForEach(TransitType.allCases, id: \.self) { type in
                let typed = filteredLines.filter { $0.type == type }
                if !typed.isEmpty {
                    Section(type.localizedName(loc.language)) {
                        ForEach(typed) { line in
                            NavigationLink {
                                LineDetailView(
                                    line: line,
                                    stations: SyrmosData.stations(for: line.id)
                                )
                            } label: {
                                LineRow(
                                    line: line,
                                    disruptionSeverity: stasyService.lineDisruptions[line.id]
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // MARK: - Lines Toolbar

    private var linesToolbar: some View {
        VStack(spacing: 10) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(Array(regionOptions.enumerated()), id: \.offset) { _, opt in
                        ChipButton(
                            label: opt.1,
                            isSelected: selectedRegion == opt.0,
                            tint: .syrmosPrimary
                        ) { selectedRegion = opt.0 }
                    }
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(Array(typeOptions.enumerated()), id: \.offset) { _, opt in
                        ChipButton(
                            label: opt.1,
                            isSelected: selectedType == opt.0,
                            tint: .secondary
                        ) { selectedType = opt.0 }
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
    }

    // MARK: - Localized labels

    private var destinationsLabel: String {
        switch loc.language {
        case .greek: return "Προορισμοί"
        case .albanian: return "Destinacione"
        case .italian: return "Destinazioni"
        case .english: return "Destinations"
        }
    }

    private var yourNetworkLabel: String {
        switch loc.language {
        case .greek: return "Το δίκτυό σου"
        case .albanian: return "Rrjeti yt"
        case .italian: return "La tua rete"
        case .english: return "Your Network"
        }
    }

    private var browseAllLabel: String {
        switch loc.language {
        case .greek: return "Περιήγηση σε όλους τους 389 σταθμούς"
        case .albanian: return "Shfleto te gjitha 389 stacionet"
        case .italian: return "Sfoglia tutte le 389 stazioni"
        case .english: return "Browse all 389 stations"
        }
    }

    private var browseAllSubtitle: String {
        switch loc.language {
        case .greek: return "Μετρο, τραμ, προαστιακος και υπεραστικα"
        case .albanian: return "Metro, tramvaj, periferike dhe nderqytetese"
        case .italian: return "Metro, tram, suburbano e intercity"
        case .english: return "Metro, tram, suburban and intercity"
        }
    }

    private var regionOptions: [(TransitRegion?, String)] {
        let l = loc.language
        return [
            (nil, l == .greek ? "Ολα" : l == .albanian ? "Te gjitha" : l == .italian ? "Tutti" : "All"),
            (.athens, l == .greek ? "Αθηνα" : l == .albanian ? "Athine" : l == .italian ? "Atene" : "Athens"),
            (.thessaloniki, l == .greek ? "Θεσσαλονικη" : l == .albanian ? "Selanik" : l == .italian ? "Salonicco" : "Thessaloniki"),
            (.patras, l == .greek ? "Πατρα" : l == .albanian ? "Patra" : l == .italian ? "Patrasso" : "Patras"),
            (.national, l == .greek ? "Υπεραστικα" : l == .albanian ? "Nderqytetese" : l == .italian ? "Intercity" : "Intercity"),
        ]
    }

    private var typeOptions: [(TransitType?, String)] {
        let l = loc.language
        return [
            (nil, l == .greek ? "Ολα" : l == .albanian ? "Te gjitha" : l == .italian ? "Tutti" : "All"),
            (.metro, "Metro"),
            (.tram, l == .greek ? "Τραμ" : l == .albanian ? "Tramvaj" : l == .italian ? "Tram" : "Tram"),
            (.suburban, l == .greek ? "Προαστιακος" : l == .albanian ? "Periferike" : l == .italian ? "Suburbano" : "Suburban"),
            (.bus, l == .greek ? "Λεωφορεια" : l == .albanian ? "Autobuse" : l == .italian ? "Bus" : "Bus"),
            (.scenic, l == .greek ? "Οδοντωτος" : l == .albanian ? "Malore" : l == .italian ? "Panoramico" : "Scenic"),
        ]
    }

    private var recentLabel: String {
        switch loc.language {
        case .greek: return "Προσφατα"
        case .albanian: return "Se fundmi"
        case .italian: return "Recenti"
        case .english: return "Recent"
        }
    }

    private var searchPlaceholder: String {
        switch loc.language {
        case .greek: return "Αναζητηση γραμμης η σταθμου..."
        case .albanian: return "Kerko linje ose stacion..."
        case .italian: return "Cerca linea o stazione..."
        case .english: return "Search line or station..."
        }
    }

    private var emptyMessage: String {
        switch loc.language {
        case .greek: return "Δεν βρεθηκαν γραμμες"
        case .albanian: return "Nuk u gjeten linja"
        case .italian: return "Nessuna linea trovata"
        case .english: return "No lines found"
        }
    }
}

// MARK: - Curated Destination

struct CuratedDestination: Identifiable, Sendable {
    let id: String
    let emoji: String
    let stationId: String
    let lineId: String
    let name: @Sendable (AppLanguage) -> String
    let hook: @Sendable (AppLanguage) -> String
    let connections: [String]

    nonisolated static let all: [CuratedDestination] = [
        CuratedDestination(
            id: "airport", emoji: "✈️", stationId: "A1_AIR", lineId: "A1",
            name: { l in l == .greek ? "Αεροδρομιο Αθηνων" : l == .albanian ? "Aeroporti i Athines" : l == .italian ? "Aeroporto di Atene" : "Athens Airport" },
            hook: { l in l == .greek ? "Η πιο γρηγορη διαδρομη στο τερματικο" : l == .albanian ? "Rruga jote me e shpejte drejt terminalit" : l == .italian ? "La via piu veloce per il terminal" : "Your fastest route to the terminal" },
            connections: ["M3", "A1", "A2"]
        ),
        CuratedDestination(
            id: "piraeus", emoji: "⛴️", stationId: "M1_PIR", lineId: "M1",
            name: { l in l == .greek ? "Πειραιας" : l == .albanian ? "Pireu" : l == .italian ? "Porto del Pireo" : "Piraeus Port" },
            hook: { l in l == .greek ? "Πλοια, κρουαζιερες, παραλιακες συνδεσεις" : l == .albanian ? "Tragete, kroaziera, lidhje bregdetare" : l == .italian ? "Traghetti, crociere, collegamenti costieri" : "Ferries, cruises, coastal connections" },
            connections: ["M1", "M3", "A1"]
        ),
        CuratedDestination(
            id: "monastiraki", emoji: "🏛️", stationId: "M1_MON", lineId: "M1",
            name: { l in l == .greek ? "Μοναστηρακι" : l == .albanian ? "Monastiraki" : l == .italian ? "Monastiraki" : "Monastiraki" },
            hook: { l in l == .greek ? "Ιστορικη καρδια, δυο γραμμες μετρο" : l == .albanian ? "Zemra historike, dy linja metroje" : l == .italian ? "Cuore storico, due linee metro" : "Historic heart, two metro lines" },
            connections: ["M1", "M3"]
        ),
        CuratedDestination(
            id: "kifisia", emoji: "🌳", stationId: "M1_KIF", lineId: "M1",
            name: { l in l == .greek ? "Κηφισια" : l == .albanian ? "Kifisia" : l == .italian ? "Kifisia" : "Kifisia" },
            hook: { l in l == .greek ? "Βορεια προαστια, τερμα πρασινης γραμμης" : l == .albanian ? "Periferia veriore, terminali i linjes se gjelber" : l == .italian ? "Periferia nord, capolinea linea verde" : "Northern suburbs, green line terminus" },
            connections: ["M1"]
        ),
        CuratedDestination(
            id: "thessaloniki", emoji: "🌆", stationId: "GR_THE", lineId: "IC1",
            name: { l in l == .greek ? "Θεσσαλονικη" : l == .albanian ? "Selanik" : l == .italian ? "Salonicco Centrale" : "Thessaloniki Central" },
            hook: { l in l == .greek ? "Η δευτερη πολη της Ελλαδας με τρενο" : l == .albanian ? "Qyteti i dyte i Greqise me tren" : l == .italian ? "La seconda citta della Grecia in treno" : "Greece's second city by rail" },
            connections: ["IC", "TM1"]
        ),
        CuratedDestination(
            id: "meteora", emoji: "⛰️", stationId: "KB_KAL", lineId: "KB1",
            name: { l in l == .greek ? "Μετεωρα / Καλαμπακα" : l == .albanian ? "Meteora / Kalambaka" : l == .italian ? "Meteora / Kalampaka" : "Meteora / Kalampaka" },
            hook: { l in l == .greek ? "Μοναστηρια στον ουρανο" : l == .albanian ? "Manastire ne qiell" : l == .italian ? "Monasteri nel cielo" : "Monasteries in the sky" },
            connections: ["IC"]
        ),
        CuratedDestination(
            id: "patras", emoji: "🌉", stationId: "PA_AND", lineId: "PS1",
            name: { l in l == .greek ? "Πατρα" : l == .albanian ? "Patra" : l == .italian ? "Patrasso" : "Patras" },
            hook: { l in l == .greek ? "Η πυλη της Πελοποννησου" : l == .albanian ? "Porta e Peloponezit" : l == .italian ? "La porta del Peloponneso" : "Gateway to the Peloponnese" },
            connections: ["PS1"]
        ),
        CuratedDestination(
            id: "diakopto", emoji: "🚂", stationId: "KI_DIA", lineId: "DK1",
            name: { l in l == .greek ? "Οδοντωτος Διακοπτου" : l == .albanian ? "Hekurudha e dhembezuar Diakopto" : l == .italian ? "Ferrovia a cremagliera di Diakopto" : "Diakopto Rack Railway" },
            hook: { l in l == .greek ? "Μια απο τις πιο γραφικες διαδρομες της Ευρωπης" : l == .albanian ? "Nje nga udhetimet me piktoreske te Europes" : l == .italian ? "Uno dei percorsi piu panoramici d'Europa" : "One of Europe's most scenic rides" },
            connections: ["DK1"]
        ),
    ]
}

// MARK: - Destination Card

private struct DestinationCard: View {
    let destination: CuratedDestination
    let language: AppLanguage

    private var primaryColor: Color {
        SyrmosLineTokens.color(for: destination.connections.first ?? destination.lineId)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack(alignment: .top) {
                Text(destination.emoji)
                    .font(.system(size: 30))
                    .frame(width: 54, height: 54)
                    .background(primaryColor.opacity(0.14), in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                Spacer()

                Image(systemName: "arrow.up.right")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(primaryColor)
                    .frame(width: 32, height: 32)
                    .background(primaryColor.opacity(0.10), in: Circle())
            }

            Text(destination.name(language))
                .font(.headline.weight(.bold))
                .foregroundStyle(.primary)
                .lineLimit(2)
                .multilineTextAlignment(.leading)

            Text(destination.hook(language))
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(2)

            Spacer(minLength: 0)

            HStack(spacing: 5) {
                ForEach(destination.connections, id: \.self) { lineId in
                    Text(SyrmosLineTokens.label(for: lineId))
                        .font(.system(size: 10, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(SyrmosLineTokens.color(for: lineId), in: Capsule())
                }
            }
        }
        .padding(15)
        .frame(width: 242, height: 184, alignment: .topLeading)
        .background {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color.syrmosSurface)
                .overlay {
                    LinearGradient(
                        colors: [primaryColor.opacity(0.16), .clear, .clear],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                }
        }
        .overlay {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(primaryColor.opacity(0.14), lineWidth: 1)
        }
        .shadow(color: .black.opacity(0.07), radius: 10, y: 4)
        .contentShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Chip

private struct ChipButton: View {
    let label: String
    let isSelected: Bool
    let tint: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.caption2)
                .fontWeight(isSelected ? .semibold : .medium)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .foregroundStyle(isSelected ? tint : .secondary)
                .background(
                    Capsule(style: .continuous)
                        .fill(isSelected ? tint.opacity(0.12) : Color.syrmosSurfaceMuted)
                )
                .overlay(
                    Capsule(style: .continuous)
                        .strokeBorder(isSelected ? tint.opacity(0.3) : .clear, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Localization

extension TransitType {
    func localizedName(_ lang: AppLanguage) -> String {
        switch (self, lang) {
        case (.metro, .greek): return "Μετρο"
        case (.metro, .albanian): return "Metro"
        case (.metro, .italian): return "Metro"
        case (.metro, .english): return "Metro"
        case (.tram, .greek): return "Τραμ"
        case (.tram, .albanian): return "Tramvaj"
        case (.tram, .italian): return "Tram"
        case (.tram, .english): return "Tram"
        case (.suburban, .greek): return "Προαστιακος Σιδηροδρομος"
        case (.suburban, .albanian): return "Hekurudha periferike"
        case (.suburban, .italian): return "Ferrovia suburbana"
        case (.suburban, .english): return "Suburban Railway"
        case (.bus, .greek): return "Λεωφορειο (αντικατασταση)"
        case (.bus, .albanian): return "Autobus (zevendesim)"
        case (.bus, .italian): return "Autobus (sostitutivo)"
        case (.bus, .english): return "Bus (rail replacement)"
        case (.scenic, .greek): return "Οδοντωτος Σιδηροδρομος"
        case (.scenic, .albanian): return "Hekurudha malore"
        case (.scenic, .italian): return "Ferrovia panoramica"
        case (.scenic, .english): return "Scenic Railway"
        }
    }
}

struct LineRow: View {
    let line: TransitLine
    var disruptionSeverity: String? = nil
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        HStack(spacing: 12) {
            ZStack(alignment: .topTrailing) {
                Circle()
                    .fill(line.color)
                    .frame(width: 12, height: 12)
                LineDisruptionDot(severity: disruptionSeverity)
                    .offset(x: 3, y: -3)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(line.localizedName(loc.language))
                    .font(.headline)
                Text("\(line.terminalA) - \(line.terminalB)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Text(loc.language == .greek
                ? "\(line.stationCount) σταθμοι"
                : loc.language == .albanian
                ? "\(line.stationCount) stacione"
                : loc.language == .italian
                ? "\(line.stationCount) stazioni"
                : "\(line.stationCount) stations")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Recent Station Store

struct RecentStation: Identifiable, Codable {
    var id: String { "\(stationId)_\(lineId)" }
    let stationId: String
    let lineId: String
    let timestamp: TimeInterval
}

enum RecentStationStore {
    private static let key = "syrmos.explore.recentStations"
    private static let maxRecents = 8

    static func load() -> [RecentStation] {
        guard let data = UserDefaults.standard.data(forKey: key),
              let items = try? JSONDecoder().decode([RecentStation].self, from: data) else {
            return []
        }
        return items
    }

    static func record(stationId: String, lineId: String) {
        var items = load()
        items.removeAll { $0.stationId == stationId && $0.lineId == lineId }
        items.insert(RecentStation(stationId: stationId, lineId: lineId, timestamp: Date().timeIntervalSince1970), at: 0)
        if items.count > maxRecents { items = Array(items.prefix(maxRecents)) }
        if let data = try? JSONEncoder().encode(items) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }
}

// MARK: - Recent Station Row

private struct RecentStationRow: View {
    let recent: RecentStation
    let line: TransitLine
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        let station = SyrmosData.stations(for: recent.lineId).first { $0.id == recent.stationId }
        HStack(spacing: 12) {
            Circle()
                .fill(line.color)
                .frame(width: 10, height: 10)
            VStack(alignment: .leading, spacing: 2) {
                Text(loc.language == .greek ? (station?.nameEl ?? recent.stationId) : (station?.name ?? recent.stationId))
                    .font(.subheadline.weight(.medium))
                Text(line.localizedName(loc.language))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: "clock.arrow.circlepath")
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Destination Detail View

struct DestinationDetailView: View {
    let stationId: String
    let lineId: String
    let onStationViewed: (String, String) -> Void
    @ObservedObject private var loc = LocalizationManager.shared
    @State private var departures: [Departure] = []
    @State private var dayOffset: Int = 0

    init(destination: CuratedDestination, onStationViewed: @escaping (String, String) -> Void) {
        self.stationId = destination.stationId
        self.lineId = destination.lineId
        self.onStationViewed = onStationViewed
    }

    init(stationId: String, lineId: String, onStationViewed: @escaping (String, String) -> Void) {
        self.stationId = stationId
        self.lineId = lineId
        self.onStationViewed = onStationViewed
    }

    private var station: TransitStation? {
        SyrmosData.stations(for: lineId).first { $0.id == stationId }
            ?? SyrmosData.bundleStations.first { $0.id == stationId }
    }

    private var line: TransitLine? {
        SyrmosData.line(for: lineId)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                stationHeader

                DayPickerRow(selectedOffset: $dayOffset)

                if departures.isEmpty {
                    Text(noDeparturesLabel)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .padding(.vertical, 32)
                } else {
                    let outbound = departures.filter { isOutbound($0.direction) }
                    let inbound = departures.filter { !isOutbound($0.direction) }

                    if !outbound.isEmpty {
                        DepartureGroup(
                            title: "\(towardsLabel) \(line?.terminalB ?? "")",
                            departures: outbound,
                            tint: line?.color ?? .primary,
                            isToday: dayOffset == 0,
                            language: loc.language
                        )
                    }

                    if !inbound.isEmpty {
                        DepartureGroup(
                            title: "\(towardsLabel) \(line?.terminalA ?? "")",
                            departures: inbound,
                            tint: line?.color ?? .primary,
                            isToday: dayOffset == 0,
                            language: loc.language
                        )
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 32)
        }
        .background(Color.syrmosBackground)
        .navigationTitle(loc.language == .greek ? (station?.nameEl ?? stationId) : (station?.name ?? stationId))
        .navigationBarTitleDisplayMode(.large)
        .onAppear {
            onStationViewed(stationId, lineId)
            reload()
        }
        .onChange(of: dayOffset) { _, _ in reload() }
    }

    private var stationHeader: some View {
        HStack(spacing: 14) {
            if let line {
                Circle()
                    .fill(line.color)
                    .frame(width: 14, height: 14)
                Text(line.localizedName(loc.language))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if let st = station, st.isInterchange {
                Label(
                    loc.language == .greek ? "Μετεπιβιβαση" :
                    loc.language == .albanian ? "Transferim" :
                    loc.language == .italian ? "Interscambio" : "Interchange",
                    systemImage: "arrow.triangle.branch"
                )
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.orange)
            }
        }
        .padding(.horizontal, 4)
        .padding(.top, 8)
    }

    private func isOutbound(_ dir: String) -> Bool {
        guard let line else { return true }
        let termA = line.terminalA.lowercased()
        return !dir.lowercased().contains(termA)
    }

    private func reload() {
        Task { @MainActor in
            let lineIds = lineId == "M3" ? ["M3", "M3_AIR"] : [lineId]
            departures = ScheduleProjector.nextDepartures(
                for: stationId,
                lineIds: lineIds,
                limit: 50,
                dayOffset: dayOffset
            )
        }
    }

    private var towardsLabel: String {
        switch loc.language {
        case .greek: return "Προς"
        case .albanian: return "Drejt"
        case .italian: return "Verso"
        case .english: return "Towards"
        }
    }

    private var noDeparturesLabel: String {
        switch loc.language {
        case .greek: return "Δεν υπαρχουν δρομολογια"
        case .albanian: return "Nuk ka nisje"
        case .italian: return "Nessuna partenza"
        case .english: return "No departures"
        }
    }
}

private struct DepartureGroup: View {
    let title: String
    let departures: [Departure]
    let tint: Color
    let isToday: Bool
    let language: AppLanguage
    @State private var showAll = false

    private var moreLabel: String {
        switch language {
        case .greek: return "ακόμη"
        case .albanian: return "më shumë"
        case .italian: return "altri"
        default: return "more"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Circle()
                    .fill(tint)
                    .frame(width: 8, height: 8)
                Text(title)
                    .font(.headline)
            }

            let visible = showAll ? departures : Array(departures.prefix(5))
            ForEach(Array(visible.enumerated()), id: \.offset) { _, dep in
                HStack {
                    Text(dep.time)
                        .font(.subheadline.weight(.semibold).monospacedDigit())
                    if isToday, dep.minutesAway > 0 {
                        Text(dep.minutesAwayDisplay(language: language))
                            .font(.caption.weight(.medium))
                            .foregroundStyle(tint)
                    }
                    Spacer()
                    Text(dep.direction)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)
                Divider().opacity(0.2)
            }

            if departures.count > 5, !showAll {
                Button {
                    withAnimation { showAll = true }
                } label: {
                    Text("+\(departures.count - 5) \(moreLabel)")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(tint)
                }
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(.ultraThinMaterial)
                .shadow(color: .black.opacity(0.06), radius: 6, y: 3)
        )
    }
}

// MARK: - Day Picker (shared with TimetablesView)

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
                        .frame(width: 50, height: 50)
                        .background(
                            Circle()
                                .fill(isSelected ? AnyShapeStyle(Color.metroBlue) : AnyShapeStyle(.thinMaterial))
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 4)
        }
    }

    private func dayName(_ offset: Int) -> String {
        if offset == 0 {
            switch loc.language {
            case .greek: return "ΣΗΜ"
            case .albanian: return "SOT"
            case .italian: return "OGGI"
            case .english: return "TODAY"
            }
        }
        let date = Calendar.current.date(byAdding: .day, value: offset, to: Date()) ?? Date()
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: loc.language == .greek ? "el_GR" : loc.language == .albanian ? "sq_AL" : loc.language == .italian ? "it_IT" : "en_US")
        fmt.dateFormat = "EEE"
        return fmt.string(from: date).uppercased()
    }

    private func dayNumber(_ offset: Int) -> String {
        let date = Calendar.current.date(byAdding: .day, value: offset, to: Date()) ?? Date()
        let fmt = DateFormatter()
        fmt.dateFormat = "d"
        return fmt.string(from: date)
    }
}

// MARK: - Browse All Stations

struct BrowseAllStationsView: View {
    @ObservedObject private var loc = LocalizationManager.shared
    @State private var searchText = ""

    private var allStations: [TransitStation] {
        SyrmosData.bundleStations
    }

    private var filtered: [TransitStation] {
        guard !searchText.isEmpty else { return allStations }
        let q = searchText.lowercased()
        return allStations.filter {
            $0.name.lowercased().contains(q)
            || $0.nameEl.lowercased().contains(q)
            || ($0.nameSq?.lowercased().contains(q) ?? false)
            || $0.id.lowercased().contains(q)
        }
    }

    var body: some View {
        List {
            ForEach(filtered) { station in
                NavigationLink {
                    if let lineId = station.lineIds.first {
                        DestinationDetailView(
                            stationId: station.id,
                            lineId: lineId,
                            onStationViewed: { sid, lid in
                                RecentStationStore.record(stationId: sid, lineId: lid)
                            }
                        )
                    }
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(loc.language == .greek ? station.nameEl : station.name)
                            .font(.subheadline.weight(.medium))
                        if !station.lineIds.isEmpty {
                            Text(station.lineIds.joined(separator: ", "))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 2)
                }
            }
        }
        .searchable(
            text: $searchText,
            prompt: loc.language == .greek ? "Αναζητηση σταθμου..." :
                    loc.language == .albanian ? "Kerko stacion..." :
                    loc.language == .italian ? "Cerca stazione..." : "Search station..."
        )
        .navigationTitle(
            loc.language == .greek ? "Ολοι οι σταθμοι" :
            loc.language == .albanian ? "Te gjitha stacionet" :
            loc.language == .italian ? "Tutte le stazioni" : "All Stations"
        )
    }
}
