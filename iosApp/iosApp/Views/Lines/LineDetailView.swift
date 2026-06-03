import SwiftUI

struct LineDetailView: View {
    let line: TransitLine
    let stations: [TransitStation]

    var body: some View {
        List {
            Section {
                HStack(spacing: 12) {
                    Circle()
                        .fill(line.color)
                        .frame(width: 16, height: 16)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(line.terminalA) - \(line.terminalB)")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Text("\(stations.count) stations")
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                    }
                }
            }

            Section("Stations") {
                ForEach(Array(stations.enumerated()), id: \.element.id) { index, station in
                    NavigationLink {
                        StationDetailView(station: station)
                    } label: {
                        HStack(spacing: 12) {
                            // Line indicator with dots
                            VStack(spacing: 0) {
                                Rectangle()
                                    .fill(index == 0 ? .clear : line.color)
                                    .frame(width: 3, height: 12)
                                Circle()
                                    .fill(station.isInterchange ? .white : line.color)
                                    .frame(width: 12, height: 12)
                                    .overlay(
                                        Circle()
                                            .stroke(line.color, lineWidth: 2)
                                    )
                                Rectangle()
                                    .fill(index == stations.count - 1 ? .clear : line.color)
                                    .frame(width: 3, height: 12)
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                Text(station.name)
                                    .font(.body)
                                Text(station.nameEl)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            Spacer()

                            if station.isInterchange {
                                HStack(spacing: 4) {
                                    ForEach(station.lineIds.filter { $0 != line.id }, id: \.self) { lid in
                                        Circle()
                                            .fill(SyrmosData.lineColor(for: lid))
                                            .frame(width: 8, height: 8)
                                    }
                                }
                            }
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
        }
        .navigationTitle(line.name)
    }
}
