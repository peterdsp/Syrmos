import SwiftUI
import MapKit

struct TransitMapView: View {
    @State private var position: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.980, longitude: 23.730),
            span: MKCoordinateSpan(latitudeDelta: 0.12, longitudeDelta: 0.12)
        )
    )
    @State private var selectedStationId: String?
    @State private var tappedStation: TransitStation?
    @State private var showSheet = false

    private var allStations: [TransitStation] {
        SyrmosData.stationsByLine.values.flatMap { $0 }
            .reduce(into: [String: TransitStation]()) { dict, s in dict[s.id] = s }
            .values.sorted { $0.name < $1.name }
    }

    var body: some View {
        NavigationStack {
            Map(position: $position, selection: $selectedStationId) {
                UserAnnotation()

                ForEach(allStations) { station in
                    Marker(
                        station.name,
                        systemImage: station.isInterchange ? "arrow.triangle.2.circlepath" : "tram.fill",
                        coordinate: station.coordinate
                    )
                    .tint(SyrmosData.lineColor(for: station.lineIds.first ?? "M3"))
                    .tag(station.id)
                }
            }
            .mapStyle(.standard(pointsOfInterest: .excludingAll, showsTraffic: false))
            .mapControls {
                MapUserLocationButton()
                MapCompass()
                MapScaleView()
            }
            .onChange(of: selectedStationId) { _, newId in
                guard let id = newId else { return }
                tappedStation = allStations.first { $0.id == id }
                if tappedStation != nil {
                    showSheet = true
                }
            }
            .navigationTitle("Transit Map")
            .sheet(isPresented: $showSheet, onDismiss: { selectedStationId = nil }) {
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
