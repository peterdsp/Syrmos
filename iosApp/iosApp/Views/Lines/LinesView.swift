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

    private var hasActiveFilters: Bool {
        !searchText.isEmpty || selectedRegion != nil || selectedType != nil
    }

    var body: some View {
        NavigationStack {
            List {
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
            .scrollContentBackground(.hidden)
            .background(Color.syrmosBackground)
            .safeAreaInset(edge: .top, spacing: 8) {
                CompactTabHeader(loc[.explore])
            }
            .toolbar(.hidden, for: .navigationBar)
        }
    }

    // MARK: - Toolbar (search + filters in one block)

    private var linesToolbar: some View {
        VStack(spacing: 10) {
            // Search
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
            .background(Color(.systemGray6), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

            // Region chips
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

            // Type chips
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

    // MARK: - Filter data

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
                        .fill(isSelected ? tint.opacity(0.12) : Color(.systemGray6))
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
