import SwiftUI

struct LineDetailView: View {
    let line: TransitLine
    let stations: [TransitStation]
    @ObservedObject private var loc = LocalizationManager.shared

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
                        Text(loc.language == .greek
                            ? "\(stations.count) σταθμοί"
                            : "\(stations.count) stations")
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                    }
                }
            }

            Section(loc[.stations]) {
                ForEach(Array(stations.enumerated()), id: \.element.id) { index, station in
                    NavigationLink {
                        StationDetailView(station: station)
                    } label: {
                        HStack(spacing: 12) {
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
                                Text(loc.language == .greek ? station.nameEl : station.name)
                                    .font(.body)
                                Text(loc.language == .greek ? station.name : station.nameEl)
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
        .navigationTitle(line.localizedName(loc.language))
    }
}
