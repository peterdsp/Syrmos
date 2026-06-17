import SwiftUI

struct StationDetailView: View {
    let station: TransitStation
    @ObservedObject private var loc = LocalizationManager.shared
    @State private var departures: [Departure] = []
    @State private var nowTick = Date()
    @State private var showMapSheet = false
    @State private var safariURL: URL?

    // Recompute departures every 15 seconds so the "5 min / 10 min" countdowns
    // tick down in real time while the user is viewing this screen.
    private let refreshTimer = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    var body: some View {
        List {
            Section(loc[.stations]) {
                Button {
                    showMapSheet = true
                } label: {
                    HStack(alignment: .top) {
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
                        Spacer()
                        Image(systemName: "map.fill")
                            .font(.title3)
                            .foregroundStyle(.tertiary)
                    }
                }
                .buttonStyle(.plain)
            }

            if station.isInterchange {
                Section(loc.language == .greek ? "Ανταπόκριση" : loc.language == .albanian ? "Korrespondencë" : "Interchange") {
                    Label(
                        loc.language == .greek ? "Σταθμός ανταπόκρισης" : loc.language == .albanian ? "Stacion korrespondence" : "Transfer station",
                        systemImage: "arrow.triangle.2.circlepath"
                    )
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                }
            }

            if isSuburbanStation {
                Section {
                    Button {
                        safariURL = URL(string: "https://newtickets.hellenictrain.gr/Channels.HellenicTrainWeb/")
                    } label: {
                        Label(
                            loc.language == .greek ? "Αγορά εισιτηρίου στην Hellenic Train" : loc.language == .albanian ? "Bli biletë në Hellenic Train" : "Buy ticket on Hellenic Train",
                            systemImage: "ticket"
                        )
                    }
                } footer: {
                    Text(loc.language == .greek
                         ? "Η πληρωμή και η έκδοση εισιτηρίου γίνονται 100% στον ιστότοπο της Hellenic Train. Το Syrmos απλώς παρέχει τον σύνδεσμο, δεν συλλέγει στοιχεία πληρωμής και δεν έχει καμία ευθύνη για την κράτηση."
                         : loc.language == .albanian
                         ? "Pagesa dhe lëshimi i biletës bëhen 100% në faqen e Hellenic Train. Syrmos thjesht ofron lidhjen, nuk mbledh të dhëna pagesash dhe nuk ka asnjë përgjegjësi për rezervimin."
                         : "Payment and ticket issuance happen entirely on Hellenic Train's website. Syrmos only provides the link, does not collect any payment data, and has no responsibility for the booking.")
                        .font(.caption2)
                }
            }

            Section(loc.language == .greek ? "Επόμενα Δρομολόγια" : loc.language == .albanian ? "Nisjet e ardhshme" : "Next Departures") {
                if departures.isEmpty {
                    Text(loc.language == .greek ? "Φόρτωση δρομολογίων..." : loc.language == .albanian ? "Duke ngarkuar oraret..." : "Loading departures...")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(departures.prefix(10)) { departure in
                        let iconName = TimetablesIcons.vehicleImageName(
                            lineId: departure.lineId,
                            direction: departure.direction,
                            isAirport: departure.serviceType == "airport"
                        )
                        HStack {
                            Group {
                                if let iconName, UIImage(named: iconName) != nil {
                                    Image(iconName)
                                        .resizable()
                                        .scaledToFit()
                                        .frame(width: 44, height: 30)
                                } else {
                                    Circle()
                                        .fill(SyrmosData.lineColor(for: departure.lineId))
                                        .frame(width: 12, height: 12)
                                }
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 4) {
                                    Text(SyrmosData.line(for: departure.lineId)?.name ?? departure.lineId)
                                        .font(.subheadline)
                                        .fontWeight(.medium)
                                    if departure.serviceType == "airport" {
                                        Text(loc.language == .greek ? "Αεροδρόμιο" : loc.language == .albanian ? "Aeroporti" : "Airport")
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
                                    : loc.language == .albanian
                                    ? "drejt \(departure.direction)"
                                    : "towards \(departure.direction)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            Spacer()

                            VStack(alignment: .trailing, spacing: 2) {
                                Text(departure.minutesAway <= 1
                                    ? (loc.language == .greek ? "Τώρα" : loc.language == .albanian ? "Tani" : "Now")
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
            reloadDepartures()
        }
        .onReceive(refreshTimer) { _ in
            nowTick = Date()
            reloadDepartures()
        }
        .sheet(isPresented: $showMapSheet) {
            StationMapSheet(station: station)
        }
        .inAppSafari(url: $safariURL)
    }

    /// True when this station belongs to a Hellenic Train suburban line (A1-A4).
    private var isSuburbanStation: Bool {
        station.lineIds.contains { ["A1", "A2", "A3", "A4"].contains($0) }
    }

    /// Server projector first, synced-bundle projector as offline fallback.
    private func reloadDepartures() {
        let fallback = currentDepartures()
        if departures.isEmpty {
            departures = fallback
        }
        Task { @MainActor in
            if let remote = await SyrmosDeparturesService.nextDepartures(
                for: station.id,
                lineIds: station.lineIds,
                limit: 10
            ), !remote.isEmpty {
                departures = remote
            } else {
                departures = fallback
            }
        }
    }

    /// Local synced-bundle fallback. Empty result means the bundles haven't
    /// loaded yet.
    private func currentDepartures() -> [Departure] {
        return ScheduleProjector.nextDepartures(
            for: station.id,
            lineIds: station.lineIds,
            limit: 10
        )
    }

    private func arrivalColor(_ minutes: Int) -> Color {
        switch minutes {
        case 0...2: return .arrivalSoon
        case 3...5: return .arrivalModerate
        default: return .arrivalFar
        }
    }
}
