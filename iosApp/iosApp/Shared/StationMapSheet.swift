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
                    // Subtitle only meaningful in Greek mode (the Greek title
                    // benefits from a Latin transliteration underneath). Non-
                    // Greek users get a single Latin name, no Greek subtitle.
                    if loc.language == .greek {
                        Text(station.name)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
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

            VStack(spacing: 12) {
                HStack(spacing: 6) {
                    ForEach(station.lineIds, id: \.self) { lineId in
                        HStack(spacing: 5) {
                            Circle()
                                .fill(SyrmosData.lineColor(for: lineId))
                                .frame(width: 7, height: 7)
                            Text(shortLineLabel(for: lineId))
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.primary)
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(
                            Capsule()
                                .fill(.ultraThinMaterial)
                                .overlay(
                                    Capsule().strokeBorder(
                                        SyrmosData.lineColor(for: lineId).opacity(0.4),
                                        lineWidth: 0.8
                                    )
                                )
                        )
                    }
                    Spacer()
                }

                Button(action: openDirections) {
                    HStack(spacing: 8) {
                        Image(systemName: "arrow.triangle.turn.up.right.circle.fill")
                            .font(.headline)
                        Text(loc.language == .greek ? "Οδηγίες πλοήγησης" : loc.language == .albanian ? "Udhëzime navigimi" : "Get directions")
                            .font(.body.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .foregroundStyle(Color.accentColor)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(.ultraThinMaterial)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .strokeBorder(Color.accentColor.opacity(0.45), lineWidth: 1)
                    )
                    .shadow(color: .black.opacity(0.12), radius: 6, x: 0, y: 3)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 12)
        }
        .background(
            // Liquid-glass sheet body. The material picks up colour from
            // whatever's behind the sheet (map tiles) and keeps the sheet
            // feeling translucent rather than a solid card pasted on top.
            Rectangle()
                .fill(.ultraThinMaterial)
                .ignoresSafeArea()
        )
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationBackground(.ultraThinMaterial)
    }

    private func shortLineLabel(for lineId: String) -> String {
        switch lineId {
        case "M1": return "M1"
        case "M2": return "M2"
        case "M3", "M3_AIR": return "M3"
        case "T6": return "T6"
        case "T7": return "T7"
        case "A1": return "A1"
        case "A2": return "A2"
        case "A3": return "A3"
        case "A4": return "A4"
        default:   return lineId
        }
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
