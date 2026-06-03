import SwiftUI

struct LinesView: View {
    let lines = SyrmosData.lines

    var body: some View {
        NavigationStack {
            List {
                ForEach(TransitType.allCases, id: \.self) { type in
                    let filtered = lines.filter { $0.type == type }
                    if !filtered.isEmpty {
                        Section(type.rawValue) {
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
            .navigationTitle("Lines")
        }
    }
}

struct LineRow: View {
    let line: TransitLine

    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(line.color)
                .frame(width: 12, height: 12)

            VStack(alignment: .leading, spacing: 2) {
                Text(line.name)
                    .font(.headline)
                Text("\(line.terminalA) - \(line.terminalB)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Text("\(line.stationCount) stations")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }
}
