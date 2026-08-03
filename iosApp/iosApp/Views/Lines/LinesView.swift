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

    var body: some View {
        NavigationStack {
            List {
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
            .safeAreaInset(edge: .top, spacing: 8) {
                CompactTabHeader(loc[.explore])
            }
            .toolbar(.hidden, for: .navigationBar)
        }
    }

    // MARK: - Segmented Control

    private var segmentedControl: some View {
        HStack(spacing: 4) {
            ForEach(ExploreSegment.allCases, id: \.self) { seg in
                let isSelected = seg == segment
                let label: String = {
                    switch seg {
                    case .destinations:
                        return destinationsLabel
                    case .yourNetwork:
                        return yourNetworkLabel
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
        ForEach(Array(CuratedDestination.all.enumerated()), id: \.element.id) { index, dest in
            DestinationCard(destination: dest, language: loc.language)
                .syrmosEntrance(index: index)
                .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
        }

        browseAllRow
            .syrmosEntrance(index: CuratedDestination.all.count)
            .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
    }

    private var browseAllRow: some View {
        HStack(spacing: 12) {
            Text("📍")
                .font(.title3)
            Text(browseAllLabel)
                .font(.subheadline)
                .fontWeight(.medium)
                .foregroundStyle(Color.syrmosPrimary)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    // MARK: - Your Network (lines catalog)

    @ViewBuilder
    private var networkContent: some View {
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
                                LineRow(line: line)
                            }
                        }
                    }
                }
            }
        }
    }

    // MARK: - Lines Toolbar (search + filters)

    private var linesToolbar: some View {
        VStack(spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(.tertiary)
                    .font(.subheadline)

                TextField(searchPlaceholder, text: $searchText)
                    .textFieldStyle(.plain)
                    .font(.subheadline)
                    .autocorrectionDisabled()

                if !searchText.isEmpty {
                    Button { searchText = "" } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.tertiary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(Color.syrmosSurfaceMuted, in: RoundedRectangle(cornerRadius: 10, style: .continuous))

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
        case .english: return "Destinations"
        }
    }

    private var yourNetworkLabel: String {
        switch loc.language {
        case .greek: return "Το δίκτυό σου"
        case .albanian: return "Rrjeti yt"
        case .english: return "Your Network"
        }
    }

    private var browseAllLabel: String {
        switch loc.language {
        case .greek: return "Περιήγηση σε όλους τους 389 σταθμούς"
        case .albanian: return "Shfleto te gjitha 389 stacionet"
        case .english: return "Browse all 389 stations"
        }
    }

    private var regionOptions: [(TransitRegion?, String)] {
        let l = loc.language
        return [
            (nil, l == .greek ? "Ολα" : l == .albanian ? "Te gjitha" : "All"),
            (.athens, l == .greek ? "Αθηνα" : l == .albanian ? "Athine" : "Athens"),
            (.thessaloniki, l == .greek ? "Θεσσαλονικη" : l == .albanian ? "Selanik" : "Thessaloniki"),
            (.patras, l == .greek ? "Πατρα" : l == .albanian ? "Patra" : "Patras"),
            (.national, l == .greek ? "Υπεραστικα" : l == .albanian ? "Nderqytetese" : "Intercity"),
        ]
    }

    private var typeOptions: [(TransitType?, String)] {
        let l = loc.language
        return [
            (nil, l == .greek ? "Ολα" : l == .albanian ? "Te gjitha" : "All"),
            (.metro, "Metro"),
            (.tram, l == .greek ? "Τραμ" : l == .albanian ? "Tramvaj" : "Tram"),
            (.suburban, l == .greek ? "Προαστιακος" : l == .albanian ? "Periferike" : "Suburban"),
            (.bus, l == .greek ? "Λεωφορεια" : l == .albanian ? "Autobuse" : "Bus"),
        ]
    }

    private var searchPlaceholder: String {
        switch loc.language {
        case .greek: return "Αναζητηση γραμμης η σταθμου..."
        case .albanian: return "Kerko linje ose stacion..."
        case .english: return "Search line or station..."
        }
    }

    private var emptyMessage: String {
        switch loc.language {
        case .greek: return "Δεν βρεθηκαν γραμμες"
        case .albanian: return "Nuk u gjeten linja"
        case .english: return "No lines found"
        }
    }
}

// MARK: - Curated Destination

private struct CuratedDestination: Identifiable, Sendable {
    let id: String
    let emoji: String
    let name: @Sendable (AppLanguage) -> String
    let hook: @Sendable (AppLanguage) -> String
    let connection: String

    nonisolated static let all: [CuratedDestination] = [
        CuratedDestination(
            id: "airport",
            emoji: "✈️",
            name: { l in l == .greek ? "Αεροδρόμιο Αθηνών" : l == .albanian ? "Aeroporti i Athinës" : "Athens Airport" },
            hook: { l in l == .greek ? "Η πιο γρήγορη διαδρομή στο τερματικό" : l == .albanian ? "Rruga jote më e shpejtë drejt terminalit" : "Your fastest route to the terminal" },
            connection: "A3"
        ),
        CuratedDestination(
            id: "piraeus",
            emoji: "⛴️",
            name: { l in l == .greek ? "Πειραιάς" : l == .albanian ? "Pireu" : "Piraeus Port" },
            hook: { l in l == .greek ? "Πλοία, κρουαζιέρες, παραλιακές συνδέσεις" : l == .albanian ? "Tragete, kroaziera, lidhje bregdetare" : "Ferries, cruises, coastal connections" },
            connection: "M1 / A1"
        ),
        CuratedDestination(
            id: "monastiraki",
            emoji: "🏛️",
            name: { l in l == .greek ? "Μοναστηράκι" : l == .albanian ? "Monastiraki" : "Monastiraki" },
            hook: { l in l == .greek ? "Ιστορική καρδιά, δύο γραμμές μετρό" : l == .albanian ? "Zemra historike, dy linja metroje" : "Historic heart, two metro lines" },
            connection: "M1 + M3"
        ),
        CuratedDestination(
            id: "kifisia",
            emoji: "🌳",
            name: { l in l == .greek ? "Κηφισιά" : l == .albanian ? "Kifisia" : "Kifisia" },
            hook: { l in l == .greek ? "Βόρεια προάστια, τέρμα πράσινης γραμμής" : l == .albanian ? "Periferia veriore, terminali i linjës së gjelbër" : "Northern suburbs, green line terminus" },
            connection: "M1"
        ),
        CuratedDestination(
            id: "thessaloniki",
            emoji: "🌆",
            name: { l in l == .greek ? "Θεσσαλονίκη" : l == .albanian ? "Selanik" : "Thessaloniki Central" },
            hook: { l in l == .greek ? "Η δεύτερη πόλη της Ελλάδας με τρένο" : l == .albanian ? "Qyteti i dytë i Greqisë me tren" : "Greece's second city by rail" },
            connection: "IC"
        ),
        CuratedDestination(
            id: "meteora",
            emoji: "⛰️",
            name: { l in l == .greek ? "Μετέωρα / Καλαμπάκα" : l == .albanian ? "Meteora / Kalambaka" : "Meteora / Kalampaka" },
            hook: { l in l == .greek ? "Μοναστήρια στον ουρανό" : l == .albanian ? "Manastire në qiell" : "Monasteries in the sky" },
            connection: "IC"
        ),
        CuratedDestination(
            id: "patras",
            emoji: "🌉",
            name: { l in l == .greek ? "Πάτρα" : l == .albanian ? "Patra" : "Patras" },
            hook: { l in l == .greek ? "Η πύλη της Πελοποννήσου" : l == .albanian ? "Porta e Peloponezit" : "Gateway to the Peloponnese" },
            connection: "Suburban"
        ),
        CuratedDestination(
            id: "diakopto",
            emoji: "🚂",
            name: { l in l == .greek ? "Οδοντωτός Διακοπτού" : l == .albanian ? "Hekurudha e dhëmbëzuar Diakopto" : "Diakopto Rack Railway" },
            hook: { l in l == .greek ? "Μία από τις πιο γραφικές διαδρομές της Ευρώπης" : l == .albanian ? "Një nga udhetimet me piktoreske te Europes" : "One of Europe's most scenic rides" },
            connection: "Rack"
        ),
    ]
}

// MARK: - Destination Card

private struct DestinationCard: View {
    let destination: CuratedDestination
    let language: AppLanguage

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            Text(destination.emoji)
                .font(.title)

            VStack(alignment: .leading, spacing: 2) {
                Text(destination.name(language))
                    .font(.subheadline)
                    .fontWeight(.semibold)

                Text(destination.hook(language))
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Text(destination.connection)
                    .font(.caption2)
                    .fontWeight(.semibold)
                    .foregroundStyle(Color.syrmosPrimary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color.syrmosPrimary.opacity(0.10), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                    .padding(.top, 6)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
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
        case (.metro, .english): return "Metro"
        case (.metro, .albanian): return "Metro"
        case (.tram, .greek): return "Τραμ"
        case (.tram, .english): return "Tram"
        case (.tram, .albanian): return "Tramvaj"
        case (.suburban, .greek): return "Προαστιακος Σιδηροδρομος"
        case (.suburban, .english): return "Suburban Railway"
        case (.suburban, .albanian): return "Hekurudha periferike"
        case (.bus, .greek): return "Λεωφορειο (αντικατασταση)"
        case (.bus, .english): return "Bus (rail replacement)"
        case (.bus, .albanian): return "Autobus (zevendesim)"
        }
    }
}

struct LineRow: View {
    let line: TransitLine
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(line.color)
                .frame(width: 12, height: 12)

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
                : "\(line.stationCount) stations")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }
}
