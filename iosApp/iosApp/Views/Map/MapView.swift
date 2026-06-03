import SwiftUI
import MapKit

// MARK: - Preloaded station data (computed once at app start)

enum PreloadedData {
    static let stations: [TransitStation] = StationCoords.allStations
    static let stationsById: [String: TransitStation] = Dictionary(
        uniqueKeysWithValues: stations.map { ($0.id, $0) }
    )
}

struct TransitMapView: View {
    @ObservedObject private var loc = LocalizationManager.shared
    @State private var position: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.980, longitude: 23.730),
            span: MKCoordinateSpan(latitudeDelta: 0.06, longitudeDelta: 0.06)
        )
    )
    @State private var selectedId: String?
    @State private var tappedStation: TransitStation?

    private let stations = PreloadedData.stations

    var body: some View {
        NavigationStack {
            ZStack(alignment: .trailing) {
                Map(position: $position, selection: $selectedId) {
                    UserAnnotation()

                    ForEach(stations) { station in
                        Annotation(station.name, coordinate: station.coordinate) {
                            StationDot(station: station, isSelected: selectedId == station.id)
                                .onTapGesture {
                                    selectedId = station.id
                                }
                        }
                        .tag(station.id)
                    }
                }
                .mapStyle(.standard(elevation: .flat, pointsOfInterest: .excludingAll, showsTraffic: false))
                .mapControls {
                    MapUserLocationButton()
                    MapCompass()
                    MapScaleView()
                }
                .onChange(of: selectedId) { _, newId in
                    guard let id = newId,
                          let station = stations.first(where: { $0.id == id }) else { return }
                    tappedStation = station
                }

                // Zoom controls
                VStack(spacing: 0) {
                    Button {
                        zoomIn()
                    } label: {
                        Image(systemName: "plus")
                            .font(.system(size: 18, weight: .semibold))
                            .frame(width: 44, height: 44)
                    }
                    .background(.regularMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))

                    Divider().frame(width: 44)

                    Button {
                        zoomOut()
                    } label: {
                        Image(systemName: "minus")
                            .font(.system(size: 18, weight: .semibold))
                            .frame(width: 44, height: 44)
                    }
                    .background(.regularMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
                .padding(.trailing, 12)
                .padding(.top, 130)
            }
            .navigationTitle(loc[.map])
            .sheet(item: $tappedStation, onDismiss: { selectedId = nil }) { station in
                StationSheetView(station: station)
                    .presentationDetents([.medium])
                    .presentationDragIndicator(.visible)
            }
        }
    }

    private func zoomIn() {
        withAnimation(.easeInOut(duration: 0.3)) {
            let r = currentRegion()
            position = .region(MKCoordinateRegion(
                center: r.center,
                span: MKCoordinateSpan(
                    latitudeDelta: max(r.span.latitudeDelta * 0.5, 0.001),
                    longitudeDelta: max(r.span.longitudeDelta * 0.5, 0.001)
                )
            ))
        }
    }

    private func zoomOut() {
        withAnimation(.easeInOut(duration: 0.3)) {
            let r = currentRegion()
            position = .region(MKCoordinateRegion(
                center: r.center,
                span: MKCoordinateSpan(
                    latitudeDelta: min(r.span.latitudeDelta * 2.0, 180),
                    longitudeDelta: min(r.span.longitudeDelta * 2.0, 360)
                )
            ))
        }
    }

    private func currentRegion() -> MKCoordinateRegion {
        position.region ?? MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.980, longitude: 23.730),
            span: MKCoordinateSpan(latitudeDelta: 0.06, longitudeDelta: 0.06)
        )
    }
}

// MARK: - Station Sheet

struct StationSheetView: View {
    let station: TransitStation
    @ObservedObject private var loc = LocalizationManager.shared
    @Environment(\.dismiss) private var dismiss
    @State private var departures: [Departure] = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                lineBadges
                if !departures.isEmpty { departuresList }
                directionsButton
            }
            .padding()
        }
        .onAppear {
            if !station.lineIds.isEmpty {
                departures = SyrmosData.sampleDepartures(for: station.id, lineIds: station.lineIds)
            }
        }
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(loc.language == .greek ? station.nameEl : station.name)
                    .font(.title2)
                    .fontWeight(.bold)
                Text(loc.language == .greek ? station.name : station.nameEl)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Button(loc.language == .greek ? "Κλείσιμο" : "Done") { dismiss() }
        }
    }

    private var lineBadges: some View {
        HStack(spacing: 6) {
            ForEach(station.lineIds, id: \.self) { lineId in
                HStack(spacing: 4) {
                    Circle()
                        .fill(SyrmosData.lineColor(for: lineId))
                        .frame(width: 8, height: 8)
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

    private var departuresList: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(loc.language == .greek ? "Επόμενα Δρομολόγια" : "Next Departures")
                .font(.headline)
            ForEach(departures.prefix(6)) { dep in
                DepartureRowView(departure: dep)
                Divider()
            }
        }
    }

    private var directionsButton: some View {
        Button {
            let dest = MKMapItem(placemark: MKPlacemark(coordinate: station.coordinate))
            dest.name = station.name
            dest.openInMaps(launchOptions: [
                MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeTransit,
            ])
        } label: {
            Label(
                loc.language == .greek ? "Οδηγίες" : "Get Directions",
                systemImage: "arrow.triangle.turn.up.right.diamond"
            )
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
        }
        .buttonStyle(.bordered)
    }
}

// MARK: - Station Dot (map annotation)

struct StationDot: View {
    let station: TransitStation
    let isSelected: Bool

    var body: some View {
        if station.isInterchange {
            ZStack {
                ForEach(Array(station.lineIds.enumerated()), id: \.element) { index, lineId in
                    Circle()
                        .fill(SyrmosData.lineColor(for: lineId))
                        .frame(width: 18, height: 18)
                        .offset(x: CGFloat(index - station.lineIds.count / 2) * 6)
                }
                Circle()
                    .fill(.white)
                    .frame(width: 8, height: 8)
            }
            .scaleEffect(isSelected ? 1.3 : 1.0)
            .animation(.easeInOut(duration: 0.2), value: isSelected)
        } else {
            ZStack {
                Circle()
                    .fill(SyrmosData.lineColor(for: station.lineIds.first ?? "M3"))
                    .frame(width: 16, height: 16)
                Circle()
                    .fill(.white)
                    .frame(width: 6, height: 6)
            }
            .scaleEffect(isSelected ? 1.3 : 1.0)
            .animation(.easeInOut(duration: 0.2), value: isSelected)
        }
    }
}

// MARK: - Departure Row

struct DepartureRowView: View {
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
            Text(departure.minutesAway <= 1 ? "Now" : "\(departure.minutesAway) min")
                .font(.headline)
                .foregroundStyle(arrivalColor)
        }
    }

    private var arrivalColor: Color {
        if departure.minutesAway <= 2 { return Color.arrivalSoon }
        if departure.minutesAway <= 5 { return Color.arrivalModerate }
        return Color.arrivalFar
    }
}
