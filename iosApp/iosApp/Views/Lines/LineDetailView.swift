import SwiftUI

struct LineDetailView: View {
    let line: TransitLine
    let stations: [TransitStation]
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var liveTrainService = LiveTrainService.shared
    @StateObject private var stasyService = STASYService()
    @State private var tappedTrain: LiveTrain?

    private var lineAlerts: [STASYAnnouncement] {
        stasyService.announcements.filter { ann in
            ann.category == .serviceAlert
            && ann.affectedLines.contains(where: { $0.caseInsensitiveCompare(line.id) == .orderedSame })
        }
    }

    private var lineTrains: [LiveTrain] {
        let stationNamesEl = Set(stations.map { $0.nameEl.lowercased() })
        return liveTrainService.trains.filter { train in
            guard !train.origin.isEmpty, !train.destination.isEmpty else { return false }
            if train.lineId.caseInsensitiveCompare(line.id) == .orderedSame { return true }
            if line.id.lowercased().hasPrefix(train.lineId.lowercased()) && train.lineId.count >= 2 { return true }
            if stationNamesEl.contains(train.origin.lowercased())
                && stationNamesEl.contains(train.destination.lowercased()) { return true }
            return false
        }
    }

    private var projectedDepartures: [Departure] {
        guard lineTrains.isEmpty, let first = stations.first else { return [] }
        return ScheduleProjector.nextDepartures(for: first.id, lineIds: [line.id], limit: 4)
    }

    var body: some View {
        List {
            if !lineAlerts.isEmpty {
                Section {
                    ServiceAlertBanner(alert: lineAlerts[0], language: loc.language)
                }
            }

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
                            : loc.language == .albanian
                            ? "\(stations.count) stacione"
                            : "\(stations.count) stations")
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                    }
                }
            }

            if !lineTrains.isEmpty {
                Section {
                    ForEach(lineTrains) { train in
                        Button { tappedTrain = train } label: {
                            HStack(spacing: 10) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("\(SyrmosData.translatedStationName(train.origin, language: loc.language)) \u{2192} \(SyrmosData.translatedStationName(train.destination, language: loc.language))")
                                        .font(.subheadline)
                                        .fontWeight(.medium)
                                        .lineLimit(1)
                                    HStack(spacing: 6) {
                                        Text("#\(train.trainNumber)")
                                            .font(.caption2)
                                            .foregroundStyle(.tertiary)
                                        if train.delayMinutes > 0 {
                                            Text("+\(train.delayMinutes)\u{2032}")
                                                .font(.caption2)
                                                .foregroundStyle(SyrmosTokens.warning)
                                        }
                                    }
                                }
                                Spacer()
                                Circle()
                                    .fill(SyrmosTokens.live)
                                    .frame(width: 8, height: 8)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                } header: {
                    HStack(spacing: 6) {
                        Image(systemName: "tram.fill")
                            .foregroundStyle(line.color)
                        Text(loc.language == .greek ? "Ζωντανα τρενα" : loc.language == .albanian ? "Trenat aktiv" : "Live trains")
                    }
                }
            } else if !projectedDepartures.isEmpty {
                Section {
                    ForEach(projectedDepartures) { dep in
                        HStack(spacing: 10) {
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text(dep.lineId)
                                        .font(.caption2)
                                        .fontWeight(.bold)
                                        .foregroundStyle(.white)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(line.color)
                                        .clipShape(Capsule())
                                    Text(dep.direction)
                                        .font(.subheadline)
                                        .fontWeight(.medium)
                                        .lineLimit(1)
                                }
                                Text(dep.time)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text(dep.minutesAwayDisplay(language: loc.language))
                                .font(.subheadline)
                                .fontWeight(.semibold)
                                .foregroundStyle(line.color)
                        }
                    }
                } header: {
                    HStack(spacing: 6) {
                        Image(systemName: "clock.fill")
                            .foregroundStyle(line.color)
                        Text(loc.language == .greek ? "Επομενες αναχωρησεις" : loc.language == .albanian ? "Nisjet e radhes" : "Upcoming departures")
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
                                    .fill(station.isInterchange ? Color.syrmosSurface : line.color)
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
        .scrollContentBackground(.hidden)
        .background(Color.syrmosBackground)
        .navigationTitle(line.localizedName(loc.language))
        .sheet(item: $tappedTrain) { train in
            TrainDetailSheet(train: train)
                .presentationDetents([.fraction(0.7), .large])
                .presentationDragIndicator(.visible)
                .presentationContentInteraction(.scrolls)
        }
    }
}
