import SwiftUI

struct StationDetailView: View {
    let station: TransitStation
    @State private var departures: [Departure] = []

    var body: some View {
        List {
            Section("Station") {
                VStack(alignment: .leading, spacing: 4) {
                    Text(station.nameEl)
                        .font(.title3)
                        .foregroundStyle(.secondary)

                    HStack(spacing: 8) {
                        ForEach(station.lineIds, id: \.self) { lineId in
                            HStack(spacing: 4) {
                                Circle()
                                    .fill(SyrmosData.lineColor(for: lineId))
                                    .frame(width: 10, height: 10)
                                Text(SyrmosData.line(for: lineId)?.name ?? lineId)
                                    .font(.caption)
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color(uiColor: .tertiarySystemGroupedBackground))
                            .clipShape(Capsule())
                        }
                    }
                }
            }

            if station.isInterchange {
                Section("Interchange") {
                    Label("Transfer station", systemImage: "arrow.triangle.2.circlepath")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }

            Section("Next Departures") {
                if departures.isEmpty {
                    Text("Loading departures...")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(departures.prefix(10)) { departure in
                        HStack {
                            Circle()
                                .fill(SyrmosData.lineColor(for: departure.lineId))
                                .frame(width: 10, height: 10)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(SyrmosData.line(for: departure.lineId)?.name ?? departure.lineId)
                                    .font(.subheadline)
                                    .fontWeight(.medium)
                                Text("towards \(departure.direction)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            Spacer()

                            VStack(alignment: .trailing, spacing: 2) {
                                Text(departure.minutesAway <= 1 ? "Now" : "\(departure.minutesAway) min")
                                    .font(.headline)
                                    .foregroundStyle(arrivalColor(departure.minutesAway))
                                Text(departure.time)
                                    .font(.caption2)
                                    .foregroundStyle(.tertiary)
                            }
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
        }
        .navigationTitle(station.name)
        .onAppear {
            departures = SyrmosData.sampleDepartures(for: station.id, lineIds: station.lineIds)
        }
    }

    private func arrivalColor(_ minutes: Int) -> Color {
        switch minutes {
        case 0...2: return .arrivalSoon
        case 3...5: return .arrivalModerate
        default: return .arrivalFar
        }
    }
}
