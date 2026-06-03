import SwiftUI
import MapKit

struct TransitMapView: View {
    @State private var position: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.980, longitude: 23.730),
            span: MKCoordinateSpan(latitudeDelta: 0.12, longitudeDelta: 0.12)
        )
    )
    @State private var resolvedStations: [ResolvedStation] = []
    @State private var selectedId: String?
    @State private var tappedStation: ResolvedStation?
    @State private var showSheet = false
    @State private var isLoading = true

    var body: some View {
        NavigationStack {
            ZStack {
                Map(position: $position, selection: $selectedId) {
                    UserAnnotation()

                    ForEach(resolvedStations) { station in
                        Marker(
                            station.name,
                            systemImage: station.isInterchange ? "arrow.triangle.2.circlepath" : "tram.fill",
                            coordinate: station.coordinate
                        )
                        .tint(station.color)
                        .tag(station.id)
                    }
                }
                .mapStyle(.standard(pointsOfInterest: .excludingAll, showsTraffic: false))
                .mapControls {
                    MapUserLocationButton()
                    MapCompass()
                    MapScaleView()
                }
                .onChange(of: selectedId) { _, newId in
                    guard let id = newId,
                          let station = resolvedStations.first(where: { $0.id == id }) else { return }
                    tappedStation = station
                    showSheet = true
                }

                if isLoading {
                    ProgressView("Loading stations...")
                        .padding()
                        .background(.ultraThinMaterial)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
            .navigationTitle("Transit Map")
            .task {
                await resolveAllStations()
            }
            .sheet(isPresented: $showSheet, onDismiss: { selectedId = nil }) {
                if let station = tappedStation {
                    StationMapSheet(station: station)
                        .presentationDetents([.medium])
                        .presentationDragIndicator(.visible)
                }
            }
        }
    }

    // MARK: - Resolve stations via MapKit search

    private func resolveAllStations() async {
        let stationNames = SyrmosData.stationsByLine.values
            .flatMap { $0 }
            .reduce(into: [String: TransitStation]()) { $0[$1.id] = $1 }
            .values.sorted { $0.name < $1.name }

        let athensRegion = MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.980, longitude: 23.730),
            span: MKCoordinateSpan(latitudeDelta: 0.3, longitudeDelta: 0.3)
        )

        var results: [ResolvedStation] = []

        for station in stationNames {
            let searchQuery = "\(station.name) station Athens metro"
            let request = MKLocalSearch.Request()
            request.naturalLanguageQuery = searchQuery
            request.region = athensRegion
            request.resultTypes = .pointOfInterest

            do {
                let search = MKLocalSearch(request: request)
                let response = try await search.start()
                if let item = response.mapItems.first {
                    results.append(ResolvedStation(
                        id: station.id,
                        name: station.name,
                        nameEl: station.nameEl,
                        coordinate: item.placemark.coordinate,
                        lineIds: station.lineIds,
                        isInterchange: station.isInterchange,
                        color: SyrmosData.lineColor(for: station.lineIds.first ?? "M3")
                    ))
                } else {
                    // Fallback to our stored coordinate
                    results.append(ResolvedStation(
                        id: station.id,
                        name: station.name,
                        nameEl: station.nameEl,
                        coordinate: station.coordinate,
                        lineIds: station.lineIds,
                        isInterchange: station.isInterchange,
                        color: SyrmosData.lineColor(for: station.lineIds.first ?? "M3")
                    ))
                }
            } catch {
                results.append(ResolvedStation(
                    id: station.id,
                    name: station.name,
                    nameEl: station.nameEl,
                    coordinate: station.coordinate,
                    lineIds: station.lineIds,
                    isInterchange: station.isInterchange,
                    color: SyrmosData.lineColor(for: station.lineIds.first ?? "M3")
                ))
            }

            // Small delay to avoid rate limiting
            try? await Task.sleep(for: .milliseconds(100))
        }

        await MainActor.run {
            resolvedStations = results
            isLoading = false
        }
    }
}

// MARK: - Resolved Station

struct ResolvedStation: Identifiable {
    let id: String
    let name: String
    let nameEl: String
    let coordinate: CLLocationCoordinate2D
    let lineIds: [String]
    let isInterchange: Bool
    let color: Color
}

// MARK: - Station Map Sheet

struct StationMapSheet: View {
    let station: ResolvedStation
    @Environment(\.dismiss) private var dismiss
    @State private var departures: [Departure] = []

    var body: some View {
        NavigationStack {
            List {
                Section {
                    stationInfo
                }

                if !departures.isEmpty {
                    Section("Next Departures") {
                        ForEach(departures.prefix(6)) { dep in
                            DepartureRow(departure: dep)
                        }
                    }
                }

                Section {
                    directionsButton
                }
            }
            .navigationTitle(station.name)
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .onAppear {
                if !station.lineIds.isEmpty {
                    departures = SyrmosData.sampleDepartures(for: station.id, lineIds: station.lineIds)
                }
            }
        }
    }

    private var stationInfo: some View {
        VStack(alignment: .leading, spacing: 6) {
            if !station.nameEl.isEmpty {
                Text(station.nameEl)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            if !station.lineIds.isEmpty {
                HStack(spacing: 6) {
                    ForEach(station.lineIds, id: \.self) { lineId in
                        lineBadge(lineId)
                    }
                }
            }
        }
    }

    private func lineBadge(_ lineId: String) -> some View {
        HStack(spacing: 4) {
            Circle()
                .fill(SyrmosData.lineColor(for: lineId))
                .frame(width: 8, height: 8)
            Text(SyrmosData.line(for: lineId)?.name ?? lineId)
                .font(.caption)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color(uiColor: .tertiarySystemGroupedBackground))
        .clipShape(Capsule())
    }

    private var directionsButton: some View {
        Button {
            let dest = MKMapItem(placemark: MKPlacemark(coordinate: station.coordinate))
            dest.name = station.name
            dest.openInMaps(launchOptions: [
                MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeTransit,
            ])
        } label: {
            Label("Get Directions", systemImage: "arrow.triangle.turn.up.right.diamond")
        }
    }
}

// MARK: - Departure Row

struct DepartureRow: View {
    let departure: Departure

    var body: some View {
        HStack {
            Circle()
                .fill(SyrmosData.lineColor(for: departure.lineId))
                .frame(width: 8, height: 8)
            VStack(alignment: .leading, spacing: 1) {
                Text(lineName)
                    .font(.subheadline)
                Text("towards \(departure.direction)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text(arrivalText)
                .font(.headline)
                .foregroundStyle(arrivalColor)
        }
    }

    private var lineName: String {
        SyrmosData.line(for: departure.lineId)?.name ?? departure.lineId
    }

    private var arrivalText: String {
        departure.minutesAway <= 1 ? "Now" : "\(departure.minutesAway) min"
    }

    private var arrivalColor: Color {
        if departure.minutesAway <= 2 { return .arrivalSoon }
        if departure.minutesAway <= 5 { return .arrivalModerate }
        return .arrivalFar
    }
}
