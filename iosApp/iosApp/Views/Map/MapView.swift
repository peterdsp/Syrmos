import SwiftUI
import MapKit

// MapStation is local to the map view to avoid conflict with the shared TransitStation model
struct MapStation: Identifiable {
    let id: String
    let name: String
    let coordinate: CLLocationCoordinate2D
    let color: Color
}

struct TransitMapView: View {
    @State private var position: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.975, longitude: 23.735),
            span: MKCoordinateSpan(latitudeDelta: 0.15, longitudeDelta: 0.15)
        )
    )

    var body: some View {
        NavigationStack {
            Map(position: $position) {
                // Metro Line 1 (Green)
                ForEach(Self.metroLine1) { station in
                    Annotation(station.name, coordinate: station.coordinate) {
                        StationPin(color: station.color, isInterchange: Self.interchangeIds.contains(station.id))
                    }
                }
                MapPolyline(coordinates: Self.metroLine1.map(\.coordinate))
                    .stroke(Color.metroGreen, lineWidth: 3)

                // Metro Line 2 (Red)
                ForEach(Self.metroLine2) { station in
                    Annotation(station.name, coordinate: station.coordinate) {
                        StationPin(color: station.color, isInterchange: Self.interchangeIds.contains(station.id))
                    }
                }
                MapPolyline(coordinates: Self.metroLine2.map(\.coordinate))
                    .stroke(Color.metroRed, lineWidth: 3)

                // Metro Line 3 (Blue)
                ForEach(Self.metroLine3) { station in
                    Annotation(station.name, coordinate: station.coordinate) {
                        StationPin(color: station.color, isInterchange: Self.interchangeIds.contains(station.id))
                    }
                }
                MapPolyline(coordinates: Self.metroLine3.map(\.coordinate))
                    .stroke(Color.metroBlue, lineWidth: 3)
            }
            .mapStyle(.standard(pointsOfInterest: .excludingAll))
            .navigationTitle("Transit Map")
        }
    }

    // MARK: - Station Data

    static let interchangeIds: Set<String> = [
        "M1_MON", "M1_OMO", "M1_ATT", "M1_PIR", "M1_NER",
        "M2_SYN", "M3_SYN", "M3_MON", "M3_DPL", "M3_AER"
    ]

    static let metroLine1: [MapStation] = [
        MapStation(id: "M1_PIR", name: "Piraeus", coordinate: .init(latitude: 37.9475, longitude: 23.6431), color: .metroGreen),
        MapStation(id: "M1_FAL", name: "Faliro", coordinate: .init(latitude: 37.9426, longitude: 23.6633), color: .metroGreen),
        MapStation(id: "M1_MOS", name: "Moschato", coordinate: .init(latitude: 37.9486, longitude: 23.6756), color: .metroGreen),
        MapStation(id: "M1_KAL", name: "Kallithea", coordinate: .init(latitude: 37.9562, longitude: 23.6965), color: .metroGreen),
        MapStation(id: "M1_TAV", name: "Tavros", coordinate: .init(latitude: 37.9644, longitude: 23.7069), color: .metroGreen),
        MapStation(id: "M1_PET", name: "Petralona", coordinate: .init(latitude: 37.9683, longitude: 23.7107), color: .metroGreen),
        MapStation(id: "M1_THE", name: "Thissio", coordinate: .init(latitude: 37.9764, longitude: 23.7210), color: .metroGreen),
        MapStation(id: "M1_MON", name: "Monastiraki", coordinate: .init(latitude: 37.9763, longitude: 23.7256), color: .metroGreen),
        MapStation(id: "M1_OMO", name: "Omonia", coordinate: .init(latitude: 37.9844, longitude: 23.7282), color: .metroGreen),
        MapStation(id: "M1_VIC", name: "Victoria", coordinate: .init(latitude: 37.9933, longitude: 23.7294), color: .metroGreen),
        MapStation(id: "M1_ATT", name: "Attiki", coordinate: .init(latitude: 37.9998, longitude: 23.7221), color: .metroGreen),
        MapStation(id: "M1_NER", name: "Neratziotissa", coordinate: .init(latitude: 38.0583, longitude: 23.7672), color: .metroGreen),
        MapStation(id: "M1_MAR", name: "Maroussi", coordinate: .init(latitude: 38.0678, longitude: 23.7878), color: .metroGreen),
        MapStation(id: "M1_KHE", name: "Kifisia", coordinate: .init(latitude: 38.0856, longitude: 23.8011), color: .metroGreen),
    ]

    static let metroLine2: [MapStation] = [
        MapStation(id: "M2_ANT", name: "Anthoupoli", coordinate: .init(latitude: 38.0139, longitude: 23.7056), color: .metroRed),
        MapStation(id: "M2_SEP", name: "Sepolia", coordinate: .init(latitude: 37.9978, longitude: 23.7139), color: .metroRed),
        MapStation(id: "M2_ATT", name: "Attiki", coordinate: .init(latitude: 37.9998, longitude: 23.7221), color: .metroRed),
        MapStation(id: "M2_LAR", name: "Larissa Station", coordinate: .init(latitude: 37.9914, longitude: 23.7217), color: .metroRed),
        MapStation(id: "M2_OMO", name: "Omonia", coordinate: .init(latitude: 37.9844, longitude: 23.7282), color: .metroRed),
        MapStation(id: "M2_PAN", name: "Panepistimio", coordinate: .init(latitude: 37.9807, longitude: 23.7334), color: .metroRed),
        MapStation(id: "M2_SYN", name: "Syntagma", coordinate: .init(latitude: 37.9755, longitude: 23.7353), color: .metroRed),
        MapStation(id: "M2_AKR", name: "Akropoli", coordinate: .init(latitude: 37.9694, longitude: 23.7288), color: .metroRed),
        MapStation(id: "M2_SYG", name: "Syngrou-Fix", coordinate: .init(latitude: 37.9640, longitude: 23.7266), color: .metroRed),
        MapStation(id: "M2_NEK", name: "Neos Kosmos", coordinate: .init(latitude: 37.9559, longitude: 23.7311), color: .metroRed),
        MapStation(id: "M2_DAF", name: "Dafni", coordinate: .init(latitude: 37.9422, longitude: 23.7378), color: .metroRed),
        MapStation(id: "M2_ELL", name: "Elliniko", coordinate: .init(latitude: 37.8919, longitude: 23.7472), color: .metroRed),
    ]

    static let metroLine3: [MapStation] = [
        MapStation(id: "M3_DIM", name: "Dim. Theatro", coordinate: .init(latitude: 37.9483, longitude: 23.6444), color: .metroBlue),
        MapStation(id: "M3_NIK", name: "Nikaia", coordinate: .init(latitude: 37.9633, longitude: 23.6586), color: .metroBlue),
        MapStation(id: "M3_KOR", name: "Korydallos", coordinate: .init(latitude: 37.9756, longitude: 23.6581), color: .metroBlue),
        MapStation(id: "M3_AMA", name: "Agia Marina", coordinate: .init(latitude: 37.9892, longitude: 23.6736), color: .metroBlue),
        MapStation(id: "M3_EGA", name: "Egaleo", coordinate: .init(latitude: 37.9919, longitude: 23.6844), color: .metroBlue),
        MapStation(id: "M3_KER", name: "Kerameikos", coordinate: .init(latitude: 37.9789, longitude: 23.7139), color: .metroBlue),
        MapStation(id: "M3_MON", name: "Monastiraki", coordinate: .init(latitude: 37.9763, longitude: 23.7256), color: .metroBlue),
        MapStation(id: "M3_SYN", name: "Syntagma", coordinate: .init(latitude: 37.9755, longitude: 23.7353), color: .metroBlue),
        MapStation(id: "M3_EVA", name: "Evangelismos", coordinate: .init(latitude: 37.9758, longitude: 23.7444), color: .metroBlue),
        MapStation(id: "M3_ETH", name: "Ethniki Amyna", coordinate: .init(latitude: 38.0008, longitude: 23.7819), color: .metroBlue),
        MapStation(id: "M3_DPL", name: "Douk. Plakentias", coordinate: .init(latitude: 38.0072, longitude: 23.8394), color: .metroBlue),
        MapStation(id: "M3_PAL", name: "Pallini", coordinate: .init(latitude: 38.0022, longitude: 23.8750), color: .metroBlue),
        MapStation(id: "M3_AER", name: "Airport", coordinate: .init(latitude: 37.9364, longitude: 23.9475), color: .metroBlue),
    ]
}

struct StationPin: View {
    let color: Color
    let isInterchange: Bool

    var body: some View {
        ZStack {
            Circle()
                .fill(.white)
                .frame(width: isInterchange ? 14 : 10, height: isInterchange ? 14 : 10)
            Circle()
                .fill(color)
                .frame(width: isInterchange ? 10 : 6, height: isInterchange ? 10 : 6)
            if isInterchange {
                Circle()
                    .stroke(.white, lineWidth: 1.5)
                    .frame(width: 14, height: 14)
            }
        }
    }
}
