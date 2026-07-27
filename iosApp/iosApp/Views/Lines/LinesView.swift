import SwiftUI

struct LinesView: View {
    let lines = SyrmosData.lines
    @ObservedObject private var loc = LocalizationManager.shared
    @State private var searchText = ""
    @State private var selectedRegion: TransitRegion? = nil
    @State private var selectedType: TransitType? = nil

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
                Section {
                    LinesSearchBar(
                        text: $searchText,
                        placeholder: searchPlaceholder
                    )
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                }

                Section {
                    RegionFilterChips(
                        selected: $selectedRegion,
                        lang: loc.language
                    )
                    .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                }

                Section {
                    TypeFilterChips(
                        selected: $selectedType,
                        lang: loc.language
                    )
                    .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 4, trailing: 16))
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                }

                if filteredLines.isEmpty {
                    Section {
                        Text(emptyMessage)
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.vertical, 24)
                    }
                } else {
                    ForEach(TransitType.allCases, id: \.self) { type in
                        let filtered = filteredLines.filter { $0.type == type }
                        if !filtered.isEmpty {
                            Section(type.localizedName(loc.language)) {
                                ForEach(filtered) { line in
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
            .scrollContentBackground(.hidden)
            .background(Color.syrmosBackground)
            .safeAreaInset(edge: .top, spacing: 8) {
                CompactTabHeader(loc[.lines])
            }
            .toolbar(.hidden, for: .navigationBar)
        }
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

// MARK: - Search Bar

private struct LinesSearchBar: View {
    @Binding var text: String
    let placeholder: String

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(.secondary)
                .font(.subheadline)

            TextField(placeholder, text: $text)
                .textFieldStyle(.plain)
                .font(.subheadline)
                .autocorrectionDisabled()

            if !text.isEmpty {
                Button {
                    text = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(.secondary)
                        .font(.subheadline)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(.ultraThinMaterial)
        )
        .padding(.horizontal, 16)
        .padding(.top, 4)
    }
}

// MARK: - Region Filter

private struct RegionFilterChips: View {
    @Binding var selected: TransitRegion?
    let lang: AppLanguage

    private var options: [(TransitRegion?, String)] {
        [
            (nil, lang == .greek ? "Ολα" : lang == .albanian ? "Te gjitha" : "All"),
            (.athens, lang == .greek ? "Αθηνα" : lang == .albanian ? "Athine" : "Athens"),
            (.thessaloniki, lang == .greek ? "Θεσσαλονικη" : lang == .albanian ? "Selanik" : "Thessaloniki"),
            (.patras, lang == .greek ? "Πατρα" : lang == .albanian ? "Patra" : "Patras"),
            (.national, lang == .greek ? "Υπεραστικα" : lang == .albanian ? "Nderqytetese" : "Intercity"),
        ]
    }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(options.enumerated()), id: \.offset) { _, option in
                    FilterChipView(
                        label: option.1,
                        isSelected: selected == option.0,
                        accentColor: .syrmosPrimary
                    ) {
                        selected = option.0
                    }
                }
            }
        }
    }
}

// MARK: - Type Filter

private struct TypeFilterChips: View {
    @Binding var selected: TransitType?
    let lang: AppLanguage

    private var options: [(TransitType?, String)] {
        [
            (nil, lang == .greek ? "Ολα" : lang == .albanian ? "Te gjitha" : "All"),
            (.metro, "Metro"),
            (.tram, lang == .greek ? "Τραμ" : lang == .albanian ? "Tramvaj" : "Tram"),
            (.suburban, lang == .greek ? "Προαστιακος" : lang == .albanian ? "Periferike" : "Suburban"),
        ]
    }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(options.enumerated()), id: \.offset) { _, option in
                    FilterChipView(
                        label: option.1,
                        isSelected: selected == option.0,
                        accentColor: .orange
                    ) {
                        selected = option.0
                    }
                }
            }
        }
    }
}

// MARK: - Chip View

private struct FilterChipView: View {
    let label: String
    let isSelected: Bool
    let accentColor: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.caption)
                .fontWeight(isSelected ? .semibold : .regular)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(
                    Capsule(style: .continuous)
                        .fill(isSelected ? accentColor.opacity(0.18) : Color(.systemGray6))
                )
                .overlay(
                    Capsule(style: .continuous)
                        .strokeBorder(isSelected ? accentColor.opacity(0.4) : Color.clear, lineWidth: 1)
                )
                .foregroundStyle(isSelected ? accentColor : .primary)
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
