import SwiftUI
import MapKit

struct TransitMapView: View {
    @State private var position: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.980, longitude: 23.730),
            span: MKCoordinateSpan(latitudeDelta: 0.12, longitudeDelta: 0.12)
        )
    )
    @State private var selectedStation: TransitStation?
    @State private var showStationSheet = false
    @State private var navigateToStation = false

    var body: some View {
        NavigationStack {
            ZStack {
                Map(position: $position, selection: Binding<MKMapItem?>(
                    get: { nil },
                    set: { item in
                        if let item {
                            handleMapItemSelection(item)
                        }
                    }
                )) {
                    UserAnnotation()
                }
                .mapStyle(.standard(emphasis: .automatic, pointsOfInterest: .including([.publicTransport]), showsTraffic: false))
                .mapControls {
                    MapUserLocationButton()
                    MapCompass()
                    MapScaleView()
                }
            }
            .navigationTitle("Transit Map")
            .sheet(isPresented: $showStationSheet) {
                if let station = selectedStation {
                    StationQuickSheet(
                        station: station,
                        onViewDepartures: {
                            showStationSheet = false
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                                navigateToStation = true
                            }
                        },
                        onGetDirections: {
                            showStationSheet = false
                            openDirections(to: station)
                        }
                    )
                    .presentationDetents([.height(280)])
                    .presentationDragIndicator(.visible)
                }
            }
            .navigationDestination(isPresented: $navigateToStation) {
                if let station = selectedStation {
                    StationDetailView(station: station)
                }
            }
        }
    }

    private func handleMapItemSelection(_ item: MKMapItem) {
        let name = item.name ?? ""
        let coord = item.placemark.coordinate

        // Try to match to a known station
        let allStations = SyrmosData.stationsByLine.values.flatMap { $0 }
        if let match = allStations.first(where: { station in
            let dist = abs(station.coordinate.latitude - coord.latitude) + abs(station.coordinate.longitude - coord.longitude)
            return dist < 0.005 || station.name.lowercased().contains(name.lowercased().prefix(5))
        }) {
            selectedStation = match
            showStationSheet = true
        } else {
            // Unknown station, create a temporary one from the map item
            selectedStation = TransitStation(
                id: "MAP_\(name.hashValue)",
                name: name,
                nameEl: "",
                coordinate: coord,
                lineIds: [],
                isInterchange: false
            )
            showStationSheet = true
        }
    }

    private func openDirections(to station: TransitStation) {
        let destination = MKMapItem(placemark: MKPlacemark(coordinate: station.coordinate))
        destination.name = station.name
        destination.openInMaps(launchOptions: [
            MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeTransit,
        ])
    }
}

// MARK: - Station Quick Sheet

struct StationQuickSheet: View {
    let station: TransitStation
    let onViewDepartures: () -> Void
    let onGetDirections: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            // Station name
            VStack(spacing: 4) {
                Text(station.name)
                    .font(.title2)
                    .fontWeight(.bold)
                if !station.nameEl.isEmpty {
                    Text(station.nameEl)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.top, 8)

            // Line badges
            if !station.lineIds.isEmpty {
                HStack(spacing: 8) {
                    ForEach(station.lineIds, id: \.self) { lineId in
                        HStack(spacing: 4) {
                            Circle()
                                .fill(SyrmosData.lineColor(for: lineId))
                                .frame(width: 10, height: 10)
                            Text(SyrmosData.line(for: lineId)?.name ?? lineId)
                                .font(.caption)
                                .fontWeight(.medium)
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(Color(uiColor: .tertiarySystemGroupedBackground))
                        .clipShape(Capsule())
                    }
                }
            }

            Spacer()

            // Action buttons
            VStack(spacing: 12) {
                Button(action: onViewDepartures) {
                    Label("View Departures", systemImage: "clock")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.borderedProminent)
                .tint(.syrmosPrimary)

                Button(action: onGetDirections) {
                    Label("Get Directions", systemImage: "arrow.triangle.turn.up.right.diamond")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.bordered)
            }
            .padding(.bottom, 8)
        }
        .padding(.horizontal)
    }
}
