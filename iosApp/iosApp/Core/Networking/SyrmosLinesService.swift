import Foundation
import CoreLocation

/// Offline-first overlay loader for transit lines.
///
/// Embedded `StationCoords` / `SyrmosData.lines` keeps the app fully
/// functional offline from second 0. This service fetches the latest
/// snapshot from the Pi (`/api/lines`) in the background and exposes
/// any *new* stations the server knows about that aren't in the
/// bundled data yet (e.g. the 2022 Piraeus tram extension stops).
///
/// We deliberately do NOT overwrite the bundled data — we only add
/// stations whose ids aren't already known locally, so the app
/// gains data over time without ever depending on the network.
@MainActor
final class SyrmosLinesService: ObservableObject {
    @Published private(set) var extraStations: [String: [RemoteStation]] = [:]

    struct RemotePayload: Decodable {
        let version: Int?
        let updatedAt: String?
        let lines: [RemoteLine]
    }

    struct RemoteLine: Decodable, Identifiable {
        let id: String
        let name: String
        let nameEl: String
        let type: String
        let color: String
        let terminalA: String
        let terminalB: String
        let stationCount: Int
        let stations: [RemoteStation]
    }

    struct RemoteStation: Decodable, Identifiable {
        let id: String
        let name: String
        let nameEl: String
        let lat: Double
        let lng: Double

        var coordinate: CLLocationCoordinate2D {
            CLLocationCoordinate2D(latitude: lat, longitude: lng)
        }
    }

    private let apiURL = URL(string: "https://api-syrmos.peterdsp.dev/api/lines")!

    func refresh() async {
        var request = URLRequest(url: apiURL)
        request.timeoutInterval = 8
        request.cachePolicy = .reloadIgnoringLocalCacheData
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return }
            let payload = try JSONDecoder().decode(RemotePayload.self, from: data)
            let knownIds = Set(PreloadedData.stationsById.keys)
            var out: [String: [RemoteStation]] = [:]
            for line in payload.lines {
                let novel = line.stations.filter { !knownIds.contains($0.id) }
                if !novel.isEmpty {
                    out[line.id] = novel
                }
            }
            if !out.isEmpty {
                extraStations = out
            }
        } catch {
            // Silent — bundled data already serves the user.
        }
    }
}
