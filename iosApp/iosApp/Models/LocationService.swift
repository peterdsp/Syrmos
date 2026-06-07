import CoreLocation
import SwiftUI

@MainActor
final class LocationService: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var nearbyStations: [NearbyStation] = []
    @Published var hasPermission = false

    private let manager = CLLocationManager()
    private var lastLocation: CLLocation?

    struct NearbyStation: Identifiable {
        let id: String
        let station: MapStationNode
        let distanceMeters: Double
    }

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        manager.distanceFilter = 50
        let status = manager.authorizationStatus
        hasPermission = status == .authorizedWhenInUse || status == .authorizedAlways
        if hasPermission {
            manager.startUpdatingLocation()
        }
    }

    func requestIfNeeded() {
        let status = manager.authorizationStatus
        if status == .notDetermined {
            manager.requestWhenInUseAuthorization()
        } else if status == .authorizedWhenInUse || status == .authorizedAlways {
            manager.startUpdatingLocation()
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        let granted = status == .authorizedWhenInUse || status == .authorizedAlways
        Task { @MainActor [weak self] in
            guard let self else { return }
            self.hasPermission = granted
            if granted {
                self.manager.startUpdatingLocation()
            } else {
                self.nearbyStations = []
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        Task { @MainActor [weak self] in
            self?.lastLocation = location
            self?.updateNearby(from: location)
        }
    }

    private func updateNearby(from location: CLLocation) {
        let allStations = PreloadedData.stations
        let sorted = allStations
            .map { station in
                let stationLoc = CLLocation(latitude: station.coordinate.latitude, longitude: station.coordinate.longitude)
                let dist = location.distance(from: stationLoc)
                return NearbyStation(id: station.id, station: station, distanceMeters: dist)
            }
            .sorted { $0.distanceMeters < $1.distanceMeters }

        nearbyStations = Array(sorted.prefix(5))
    }
}
