import SwiftUI

struct StationDetailView: View {
    let station: TransitStation
    @ObservedObject private var loc = LocalizationManager.shared
    @State private var departures: [Departure] = []
    @State private var nowTick = Date()

    // Recompute departures every 15 seconds so the "5 min / 10 min" countdowns
    // tick down in real time while the user is viewing this screen.
    private let refreshTimer = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    var body: some View {
        List {
            Section(loc[.stations]) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(loc.language == .greek ? station.name : station.nameEl)
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
                Section(loc.language == .greek ? "Ανταπόκριση" : "Interchange") {
                    Label(
                        loc.language == .greek ? "Σταθμός ανταπόκρισης" : "Transfer station",
                        systemImage: "arrow.triangle.2.circlepath"
                    )
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                }
            }

            if isSuburbanStation {
                Section {
                    Link(destination: URL(string: "https://newtickets.hellenictrain.gr/Channels.HellenicTrainWeb/")!) {
                        Label(
                            loc.language == .greek ? "Αγορά εισιτηρίου στην Hellenic Train" : "Buy ticket on Hellenic Train",
                            systemImage: "ticket"
                        )
                    }
                } footer: {
                    Text(loc.language == .greek
                         ? "Η πληρωμή και η έκδοση εισιτηρίου γίνονται 100% στον ιστότοπο της Hellenic Train. Το Syrmos απλώς παρέχει τον σύνδεσμο, δεν συλλέγει στοιχεία πληρωμής και δεν έχει καμία ευθύνη για την κράτηση."
                         : "Payment and ticket issuance happen entirely on Hellenic Train's website. Syrmos only provides the link, does not collect any payment data, and has no responsibility for the booking.")
                        .font(.caption2)
                }
            }

            Section(loc.language == .greek ? "Επόμενα Δρομολόγια" : "Next Departures") {
                if departures.isEmpty {
                    Text(loc.language == .greek ? "Φόρτωση δρομολογίων..." : "Loading departures...")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(departures.prefix(10)) { departure in
                        HStack {
                            Circle()
                                .fill(SyrmosData.lineColor(for: departure.lineId))
                                .frame(width: 10, height: 10)

                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 4) {
                                    Text(SyrmosData.line(for: departure.lineId)?.name ?? departure.lineId)
                                        .font(.subheadline)
                                        .fontWeight(.medium)
                                    if departure.serviceType == "airport" {
                                        Text(loc.language == .greek ? "Αεροδρόμιο" : "Airport")
                                            .font(.caption2)
                                            .fontWeight(.semibold)
                                            .padding(.horizontal, 5)
                                            .padding(.vertical, 1)
                                            .background(Color.metroBlue.opacity(0.15))
                                            .clipShape(Capsule())
                                    }
                                }
                                Text(loc.language == .greek
                                    ? "προς \(departure.direction)"
                                    : "towards \(departure.direction)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            Spacer()

                            VStack(alignment: .trailing, spacing: 2) {
                                Text(departure.minutesAway <= 1
                                    ? (loc.language == .greek ? "Τώρα" : "Now")
                                    : "\(departure.minutesAway) min")
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
        .scrollContentBackground(.hidden)
        .background(Color.syrmosBackground)
        .navigationTitle(loc.language == .greek ? station.nameEl : station.name)
        .onAppear {
            departures = currentDepartures()
        }
        .onReceive(refreshTimer) { _ in
            nowTick = Date()
            departures = currentDepartures()
        }
    }

    /// True when this station belongs to a Hellenic Train suburban line (A1-A4).
    private var isSuburbanStation: Bool {
        station.lineIds.contains { ["A1", "A2", "A3", "A4"].contains($0) }
    }

    /// API-first source of truth, with a seed fallback when bundles aren't loaded yet.
    private func currentDepartures() -> [Departure] {
        let live = ScheduleProjector.nextDepartures(
            for: station.id,
            lineIds: station.lineIds,
            limit: 10
        )
        if !live.isEmpty { return live }
        return SyrmosData.sampleDepartures(for: station.id, lineIds: station.lineIds)
    }

    private func arrivalColor(_ minutes: Int) -> Color {
        switch minutes {
        case 0...2: return .arrivalSoon
        case 3...5: return .arrivalModerate
        default: return .arrivalFar
        }
    }
}
