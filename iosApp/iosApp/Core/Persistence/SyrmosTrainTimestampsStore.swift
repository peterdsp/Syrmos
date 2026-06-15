import Foundation

/// Fetches and caches `/api/train-timestamps` - per-train per-station HH:MM
/// times parsed from the official Hellenic Train PDFs (currently A1 / A2 /
/// A3 / A4). When this store has data for a given (lineId, dayType), the
/// Timetables view uses it as ground truth and skips band projection.
///
/// Offline-first: ships a bundled snapshot in Resources/seed-schedules-v2/
/// (added in a follow-up). Network refresh overlays anything newer.
@MainActor
final class SyrmosTrainTimestampsStore: ObservableObject {
    static let shared = SyrmosTrainTimestampsStore()

    @Published private(set) var trains: [TrainEntry] = []

    private let base = "https://api-syrmos.peterdsp.dev"
    private let session: URLSession

    private init(session: URLSession = .shared) {
        self.session = session
    }

    /// All trains for a given (line, day_type). The Timetables view groups
    /// these by direction. Returned in departure order.
    func trains(lineId: String, dayType: String) -> [TrainEntry] {
        trains.filter { entry in
            entry.lineId == lineId && matchesDayType(entry.dayType, requested: dayType)
        }
    }

    func refresh() async {
        guard let url = URL(string: base + "/api/train-timestamps") else { return }
        var req = URLRequest(url: url)
        req.timeoutInterval = 10
        do {
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return }
            let payload = try JSONDecoder().decode(Payload.self, from: data)
            self.trains = payload.trains
        } catch {
            // Silent fall through to bundled / empty - the band projector
            // remains the fallback path.
        }
    }

    /// PDF dayType labels are coarse (mon_fri / weekend / all). Map our
    /// finer-grained internal day_types onto them.
    private func matchesDayType(_ entryDay: String, requested: String) -> Bool {
        if entryDay == "all" { return true }
        switch requested {
        case "mon_thu", "fri": return entryDay == "mon_fri"
        case "sat", "sun":     return entryDay == "weekend"
        default:               return entryDay == requested
        }
    }

    struct Payload: Decodable {
        let trains: [TrainEntry]
    }

    struct TrainEntry: Decodable, Identifiable {
        let lineId: String
        let dayType: String
        let direction: String        // outbound, inbound, both, extension
        let trainNo: String
        let stops: [Stop]
        let firstTime: String

        var id: String { "\(lineId)-\(dayType)-\(direction)-\(trainNo)" }
    }

    struct Stop: Decodable {
        let stationId: String
        let stationNameEn: String
        let stationNameEl: String
        let time: String
        let sequence: Int
    }
}
