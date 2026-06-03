import SwiftUI
import MapKit

struct TransitMapView: View {
    @State private var position: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.980, longitude: 23.730),
            span: MKCoordinateSpan(latitudeDelta: 0.12, longitudeDelta: 0.12)
        )
    )
    @State private var selectedResult: MKMapItem?
    @State private var showStationDetail = false
    @State private var tappedStation: TransitStation?

    var body: some View {
        NavigationStack {
            Map(position: $position, selection: $selectedResult) {
                UserAnnotation()
            }
            .mapStyle(.standard(emphasis: .automatic, pointsOfInterest: .including([.publicTransport]), showsTraffic: false))
            .mapControls {
                MapUserLocationButton()
                MapCompass()
                MapScaleView()
            }
            .onChange(of: selectedResult) { _, newValue in
                guard let item = newValue else { return }
                let coord = item.placemark.coordinate
                let name = item.name ?? "Station"

                // Match to known station or create temporary
                let allStations = SyrmosData.stationsByLine.values.flatMap { $0 }
                let match = allStations.first { station in
                    let dist = abs(station.coordinate.latitude - coord.latitude) + abs(station.coordinate.longitude - coord.longitude)
                    return dist < 0.003
                }

                tappedStation = match ?? TransitStation(
                    id: "MAP_\(name.hashValue)",
                    name: name,
                    nameEl: "",
                    coordinate: coord,
                    lineIds: [],
                    isInterchange: false
                )
                showStationDetail = true
                selectedResult = nil
            }
            .navigationTitle("Transit Map")
            .sheet(isPresented: $showStationDetail) {
                if let station = tappedStation {
                    StationMapSheet(station: station)
                        .presentationDetents([.medium])
                        .presentationDragIndicator(.visible)
                }
            }
        }
    }
}

// MARK: - Station Map Sheet

struct StationMapSheet: View {
    let station: TransitStation
    @Environment(\.dismiss) private var dismiss
    @State private var departures: [Departure] = []

    var body: some View {
        NavigationStack {
            List {
                // Station info
                Section {
                    VStack(alignment: .leading, spacing: 6) {
                        if !station.nameEl.isEmpty {
                            Text(station.nameEl)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        if !station.lineIds.isEmpty {
                            HStack(spacing: 6) {
                                ForEach(station.lineIds, id: \.self) { lineId in
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
                            }
                        }
                    }
                }

                // Departures
                if !departures.isEmpty {
                    Section("Next Departures") {
                        ForEach(departures.prefix(6)) { dep in
                            DepartureRow(departure: dep)
                        }
                    }
                }

                // Actions
                Section {
                    Button {
                        let destination = MKMapItem(placemark: MKPlacemark(coordinate: station.coordinate))
                        destination.name = station.name
                        destination.openInMaps(launchOptions: [
                            MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeTransit,
                        ])
                    } label: {
                        Label("Get Directions", systemImage: "arrow.triangle.turn.up.right.diamond")
                    }
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
                Text(SyrmosData.line(for: departure.lineId)?.name ?? departure.lineId)
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

    private var arrivalText: String {
        departure.minutesAway <= 1 ? "Now" : "\(departure.minutesAway) min"
    }

    private var arrivalColor: Color {
        if departure.minutesAway <= 2 { return .arrivalSoon }
        if departure.minutesAway <= 5 { return .arrivalModerate }
        return .arrivalFar
    }
}
