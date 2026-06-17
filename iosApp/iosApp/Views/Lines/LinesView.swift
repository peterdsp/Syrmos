import SwiftUI

struct LinesView: View {
    let lines = SyrmosData.lines
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        NavigationStack {
            List {
                ForEach(TransitType.allCases, id: \.self) { type in
                    let filtered = lines.filter { $0.type == type }
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
            .scrollContentBackground(.hidden)
            .background(Color.syrmosBackground)
            .safeAreaInset(edge: .top, spacing: 8) {
                CompactTabHeader(loc[.lines])
            }
            .toolbar(.hidden, for: .navigationBar)
        }
    }
}

extension TransitType {
    func localizedName(_ lang: AppLanguage) -> String {
        switch (self, lang) {
        case (.metro, .greek): return "Μετρό"
        case (.metro, .english): return "Metro"
        case (.metro, .albanian): return "Metro"
        case (.tram, .greek): return "Τραμ"
        case (.tram, .english): return "Tram"
        case (.tram, .albanian): return "Tramvaj"
        case (.suburban, .greek): return "Προαστιακός Σιδηρόδρομος"
        case (.suburban, .english): return "Suburban Railway"
        case (.suburban, .albanian): return "Hekurudha periferike"
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
                ? "\(line.stationCount) σταθμοί"
                : loc.language == .albanian
                ? "\(line.stationCount) stacione"
                : "\(line.stationCount) stations")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }
}
