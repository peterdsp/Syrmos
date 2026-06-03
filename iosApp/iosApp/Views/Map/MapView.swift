import SwiftUI
import MapKit

struct TransitMapView: View {
    @State private var position: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.980, longitude: 23.730),
            span: MKCoordinateSpan(latitudeDelta: 0.12, longitudeDelta: 0.12)
        )
    )
    @State private var selectedMapItem: MKMapItem?

    var body: some View {
        NavigationStack {
            Map(position: $position, selection: $selectedMapItem) {
                UserAnnotation()
            }
            .mapStyle(.standard(
                emphasis: .automatic,
                pointsOfInterest: .including([.publicTransport]),
                showsTraffic: false
            ))
            .mapControls {
                MapUserLocationButton()
                MapCompass()
                MapScaleView()
            }
            .mapFeatureSelectionAccessory_compat()
            .navigationTitle("Transit Map")
            .onChange(of: selectedMapItem) { _, item in
                // Apple Maps handles the detail card natively
                // for transit stations with departures and directions
            }
        }
    }
}

extension View {
    @ViewBuilder
    func mapFeatureSelectionAccessory_compat() -> some View {
        if #available(iOS 18.0, *) {
            self.mapFeatureSelectionAccessory(.automatic)
        } else {
            self
        }
    }
}
