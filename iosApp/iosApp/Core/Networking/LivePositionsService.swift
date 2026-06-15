import Foundation
import CoreLocation

/// Pulls the canonical list of currently-active trains from the API
/// (`/api/live-positions`) and pairs it with the bundled
/// `/api/station-offsets` table so the map can interpolate each train's
/// position exactly where the projector says it should be.
///
/// Old approach: a local Swift simulator spawned trains every N minutes
/// using a hardcoded frequency and haversine distances. It drifted hard
/// from the bottom-sheet "X min" because the sheet uses real station
/// offsets and the projector's band data, while the simulator did its
/// own physics. Result: the dot crept into Nikaia while the sheet still
/// said "9 min."
///
/// New approach: one source of truth (the projector). The API tells us
/// *which* trains are active and *when each one left origin*. The
/// station-offsets table tells us *when* each train reaches each
/// station. Position = linear lerp between adjacent stations on the
/// route polyline. Sheet and map agree by construction.
@MainActor
final class LivePositionsService: ObservableObject {
    static let shared = LivePositionsService()

    struct Train: Identifiable, Equatable {
        let id: String
        let lineId: String
        let directionKey: String
        let originDepartureEpoch: TimeInterval   // seconds since 1970, Athens-local mapped to wall clock
        let totalTravelMinutes: Int
        let serviceType: String
    }

    struct OffsetStop: Equatable {
        let stationId: String
        let minutesFromOrigin: Double
    }

    @Published private(set) var trains: [Train] = []
    /// (lineId, directionKey) -> ordered stops with their offsets.
    @Published private(set) var offsets: [String: [String: [OffsetStop]]] = [:]
    private var lastOffsetsFetch: Date?
    private var pollTask: Task<Void, Never>?

    private let base = "https://api-syrmos.peterdsp.dev"
    /// Lines we project ourselves. A1-A4 come from the live SSE feed
    /// (railway.gov.gr) so they're rendered by LiveTrainService instead.
    private let projectedLineIds = ["M1", "M2", "M3", "M3_AIR", "T6", "T7"]

    private init() {
        pollTask = Task { [weak self] in
            await self?.runLoop()
        }
    }

    deinit { pollTask?.cancel() }

    private func runLoop() async {
        // Offsets are cheap to fetch and almost-static. Refresh hourly.
        await ensureOffsets()
        await fetchActiveTrains()
        while !Task.isCancelled {
            // 15s matches Departures' refresh cadence. Between fetches the
            // map uses elapsedMinutes derived from wall-clock to advance
            // each dot smoothly.
            try? await Task.sleep(nanoseconds: 15_000_000_000)
            if Task.isCancelled { return }
            if let last = lastOffsetsFetch, Date().timeIntervalSince(last) > 3600 {
                await ensureOffsets()
            }
            await fetchActiveTrains()
        }
    }

    private func ensureOffsets() async {
        if !offsets.isEmpty, let last = lastOffsetsFetch, Date().timeIntervalSince(last) < 3600 {
            return
        }
        guard let url = URL(string: "\(base)/api/station-offsets") else { return }
        var req = URLRequest(url: url)
        req.timeoutInterval = 8
        do {
            let (data, _) = try await URLSession.shared.data(for: req)
            let decoded = try JSONDecoder().decode(StationOffsetsResponse.self, from: data)
            var grouped: [String: [String: [OffsetStop]]] = [:]
            for line in decoded.lines {
                let stops = line.stops
                    .sorted { $0.stopSequence < $1.stopSequence }
                    .map { OffsetStop(stationId: $0.stationId, minutesFromOrigin: Double($0.minutesFromOrigin)) }
                grouped[line.lineId, default: [:]][line.direction] = stops
            }
            offsets = grouped
            lastOffsetsFetch = Date()
        } catch {
            // Keep stale offsets if we have any; otherwise the simulator
            // will simply emit no dots until the next try, which is fine.
        }
    }

    private func fetchActiveTrains() async {
        guard var components = URLComponents(string: "\(base)/api/live-positions") else { return }
        components.queryItems = [
            URLQueryItem(name: "lineIds", value: projectedLineIds.joined(separator: ",")),
        ]
        guard let url = components.url else { return }
        var req = URLRequest(url: url)
        req.timeoutInterval = 6
        do {
            let (data, _) = try await URLSession.shared.data(for: req)
            let decoded = try JSONDecoder().decode(LivePositionsResponse.self, from: data)
            // The API returns originDepartureMinute as minute-of-clock relative
            // to today's Athens midnight (negative => yesterday). Convert to
            // a Unix-epoch second so the wall-clock lerp on the next frame is
            // just (now - epoch) in seconds.
            let serverNow = ISO8601DateFormatter.athensLocal.date(from: decoded.generatedAt) ?? Date()
            let athensMidnight = serverNow.athensStartOfDay()
            let mapped: [Train] = decoded.trains.compactMap { raw in
                let epoch = athensMidnight.timeIntervalSince1970 + raw.originDepartureMinute * 60
                return Train(
                    id: "\(raw.lineId)_\(raw.directionKey)_\(Int(raw.originDepartureMinute * 100))",
                    lineId: raw.lineId,
                    directionKey: raw.directionKey,
                    originDepartureEpoch: epoch,
                    totalTravelMinutes: raw.totalTravelMinutes,
                    serviceType: raw.serviceType
                )
            }
            trains = mapped
        } catch {
            // Leave the existing trains in place so animation continues
            // smoothly through a brief network glitch.
        }
    }
}

private struct LivePositionsResponse: Decodable {
    struct Train: Decodable {
        let lineId: String
        let directionKey: String
        let originDepartureMinute: Double
        let totalTravelMinutes: Int
        let serviceType: String
    }
    let generatedAt: String
    let trains: [Train]
}

private struct StationOffsetsResponse: Decodable {
    struct Stop: Decodable {
        let stationId: String
        let stopSequence: Int
        let minutesFromOrigin: Int
    }
    struct Line: Decodable {
        let lineId: String
        let direction: String
        let stops: [Stop]
    }
    let lines: [Line]
}

extension ISO8601DateFormatter {
    /// Athens timestamps include an offset (e.g. "+03:00"), so the default
    /// ISO8601 formatter without `.withFractionalSeconds` handles them.
    /// `nonisolated(unsafe)` because the value is created once and never
    /// mutated after; the underlying formatter is reentrant for parsing.
    nonisolated(unsafe) static let athensLocal: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()
}

extension Date {
    /// Midnight of the calendar day this Date falls on, in Europe/Athens.
    func athensStartOfDay() -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/Athens") ?? .current
        return cal.startOfDay(for: self)
    }
}
