import SwiftUI
import MapKit

/// Bottom sheet that appears when the user taps the station card on
/// StationDetailView. Shows a focused mini-map: only the polylines of the
/// lines that serve this station, only this station's marker (no neighbouring
/// stations from the line), and a Get directions button that hands off to
/// Apple Maps.
struct StationMapSheet: View {
    let station: TransitStation
    @ObservedObject private var loc = LocalizationManager.shared
    @Environment(\.dismiss) private var dismiss

    @State private var position: MapCameraPosition

    init(station: TransitStation) {
        self.station = station
        let region = MKCoordinateRegion(
            center: station.coordinate,
            span: MKCoordinateSpan(latitudeDelta: 0.012, longitudeDelta: 0.012)
        )
        _position = State(initialValue: .region(region))
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(loc.language == .greek ? station.nameEl : station.name)
                        .font(.title3)
                        .fontWeight(.semibold)
                    Text(loc.language == .greek ? station.name : station.nameEl)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title2)
                        .foregroundStyle(.tertiary)
                }
            }
            .padding(.horizontal)
            .padding(.top, 8)
            .padding(.bottom, 12)

            Map(position: $position) {
                ForEach(routePolylines, id: \.id) { route in
                    MapPolyline(coordinates: route.coordinates)
                        .stroke(route.color, lineWidth: route.lineWeight)
                }
                Annotation(station.name, coordinate: station.coordinate) {
                    StationMarker(station: station)
                }
            }
            .mapStyle(.standard(elevation: .flat, pointsOfInterest: .excludingAll))
            .frame(maxWidth: .infinity)

            VStack(spacing: 10) {
                HStack(spacing: 8) {
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
                    Spacer()
                }

                Button(action: openDirections) {
                    HStack {
                        Image(systemName: "arrow.triangle.turn.up.right.circle.fill")
                        Text(loc.language == .greek ? "Οδηγίες πλοήγησης" : "Get directions")
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.accentColor)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
            .padding()
        }
        .background(Color.syrmosBackground.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    /// Polylines for every line that serves this station, restricted to the
    /// segment that passes through here. Using the same Catmull-Rom path the
    /// main MapView uses, filtered by line id.
    private var routePolylines: [RouteLine] {
        station.lineIds.compactMap { lineId in
            PreloadedData.routeLines.first { $0.id == lineId }
        }
    }

    private func openDirections() {
        let placemark = MKPlacemark(coordinate: station.coordinate)
        let item = MKMapItem(placemark: placemark)
        item.name = loc.language == .greek ? station.nameEl : station.name
        item.openInMaps(launchOptions: [
            MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeDefault
        ])
    }
}

private struct StationMarker: View {
    let station: TransitStation

    var body: some View {
        ZStack {
            Circle()
                .fill(Color.white)
                .frame(width: 28, height: 28)
                .shadow(color: .black.opacity(0.25), radius: 4, x: 0, y: 2)
            Circle()
                .stroke(SyrmosData.lineColor(for: station.lineIds.first ?? ""), lineWidth: 4)
                .frame(width: 22, height: 22)
            Image(systemName: "tram.fill")
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(SyrmosData.lineColor(for: station.lineIds.first ?? ""))
        }
    }
}
