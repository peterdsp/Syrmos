import SwiftUI
import MapKit

struct TransitMapView: View {
    @State private var position: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.980, longitude: 23.730),
            span: MKCoordinateSpan(latitudeDelta: 0.12, longitudeDelta: 0.12)
        )
    )

    var body: some View {
        NavigationStack {
            Map(position: $position) {
                // User location if available
                UserAnnotation()
            }
            .mapStyle(.standard(emphasis: .automatic, pointsOfInterest: .including([.publicTransport]), showsTraffic: false))
            .mapControls {
                MapUserLocationButton()
                MapCompass()
                MapScaleView()
            }
            .navigationTitle("Transit Map")
        }
    }
}
