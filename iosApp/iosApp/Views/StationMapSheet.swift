import SwiftUI
import MapKit

/// Bottom sheet that appears when the user taps the station card on
/// StationDetailView. Shows a focused mini-map: only the polylines of the
/// lines that serve this station, only this station's marker (no neighbouring
/// stations from the line), and a Get directions button that hands off to
/// Apple Maps.
///
/// Implementation note: we wrap MKMapView directly via UIViewRepresentable
/// rather than SwiftUI's `Map` because the SwiftUI variant combined with
/// `presentationDetents` had a lifecycle bug where the underlying CAMetalLayer
/// stopped rendering after a screenshot or sheet dismiss/re-present cycle,
/// turning the whole app's window black until cold launch. UIViewRepresentable
/// gives us deterministic teardown.
struct StationMapSheet: View {
    let station: TransitStation
    @ObservedObject private var loc = LocalizationManager.shared
    @Environment(\.dismiss) private var dismiss

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

            StationFocusedMap(station: station, routes: routePolylines)
                .frame(maxWidth: .infinity)
                .frame(minHeight: 240)

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
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

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

/// Stable MKMapView wrapper. Single marker for the focused station, plus
/// thin polylines for every line that calls here. We deliberately rebuild
/// overlays on every update (cheap for our scale, avoids stale state).
private struct StationFocusedMap: UIViewRepresentable {
    let station: TransitStation
    let routes: [RouteLine]

    func makeUIView(context: Context) -> MKMapView {
        let mv = MKMapView()
        mv.isPitchEnabled = false
        mv.isRotateEnabled = false
        mv.showsCompass = false
        mv.showsScale = false
        mv.pointOfInterestFilter = .excludingAll
        mv.delegate = context.coordinator
        return mv
    }

    func updateUIView(_ mv: MKMapView, context: Context) {
        mv.removeOverlays(mv.overlays)
        mv.removeAnnotations(mv.annotations)

        for route in routes {
            let poly = ColoredPolyline(coordinates: route.coordinates, count: route.coordinates.count)
            poly.color = UIColor(route.color)
            poly.weight = route.lineWeight
            mv.addOverlay(poly)
        }

        let pin = MKPointAnnotation()
        pin.coordinate = station.coordinate
        pin.title = station.name
        mv.addAnnotation(pin)

        let region = MKCoordinateRegion(
            center: station.coordinate,
            span: MKCoordinateSpan(latitudeDelta: 0.012, longitudeDelta: 0.012)
        )
        mv.setRegion(region, animated: false)
    }

    static func dismantleUIView(_ mv: MKMapView, coordinator: Coordinator) {
        mv.removeOverlays(mv.overlays)
        mv.removeAnnotations(mv.annotations)
        mv.delegate = nil
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, MKMapViewDelegate {
        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let p = overlay as? ColoredPolyline {
                let r = MKPolylineRenderer(polyline: p)
                r.strokeColor = p.color
                r.lineWidth = p.weight
                return r
            }
            return MKOverlayRenderer(overlay: overlay)
        }

        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            let id = "stationFocused"
            let v = mapView.dequeueReusableAnnotationView(withIdentifier: id)
                ?? MKAnnotationView(annotation: annotation, reuseIdentifier: id)
            v.annotation = annotation
            v.image = UIImage(systemName: "circle.fill")?
                .withTintColor(.systemBlue, renderingMode: .alwaysOriginal)
            v.frame.size = CGSize(width: 18, height: 18)
            return v
        }
    }
}

private final class ColoredPolyline: MKPolyline {
    var color: UIColor = .systemBlue
    var weight: CGFloat = 4
}
