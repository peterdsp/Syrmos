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
    /// Lines we project ourselves. Suburban A1-A4 were originally excluded
    /// because they had raw GPS from railway.gov.gr (LiveTrainService). They
    /// are now ALSO projected so they keep moving when the SSE feed is
    /// offline or empty. The TransitMapView dedupes per line: whenever
    /// LiveTrainService emits ANY suburban train for line X, the projected
    /// dots for line X are hidden so we never draw two markers for the
    /// same physical train.
    let projectedLineIds = ["M1", "M2", "M3", "M3_AIR", "T6", "T7", "A1", "A2", "A3", "A4"]

    private init() {
        // Offline-first: the live-positions feed is ONLY hit when the
        // user explicitly taps Check now in Settings. No auto-poll on
        // app launch. Map dots stay empty (the polylines + stations are
        // bundled so the map still renders fully) until a refresh is
        // requested. Once a refresh fires, `trains` stays populated for
        // the rest of the session — the per-frame interpolation already
        // advances each dot from its own originDepartureEpoch so the
        // dots keep moving smoothly without re-hitting the API.
    }

    deinit { pollTask?.cancel() }

    /// Public entry. Settings' Check now calls this; the in-app
    /// schedule of polling is the user, not a timer.
    @discardableResult
    func refresh() async -> Bool {
        await ensureOffsets()
        return await fetchActiveTrains()
    }

    /// Re-fetch active positions on a cadence so metro / tram / A1-A4 dots stay
    /// present. Each projected train expires after its own travelMinutes (see
    /// TrainSimulatorService.projectTrains), so a single launch fetch empties
    /// the map within the hour — and shows nothing at all if the app was
    /// launched while service was closed and the feed was empty. This brings
    /// metro/tram to parity with the auto-polling suburban feed (LiveTrainService)
    /// and the web map, which re-projects from the schedule continuously.
    func startPolling(intervalSeconds: UInt64 = 60) {
        guard pollTask == nil else { return }
        pollTask = Task { [weak self] in
            var failures = 0
            let base = TimeInterval(intervalSeconds)
            while !Task.isCancelled {
                // Backoff + jitter: base cadence while healthy, escalating on
                // repeated failure (capped 5 min) with jitter, reset on success.
                // The map keeps projecting metro/tram from the bundle meanwhile.
                try? await Task.sleep(nanoseconds: PollBackoff.nextDelayNanos(
                    consecutiveFailures: failures,
                    baseDelaySeconds: base,
                    maxDelaySeconds: max(base, 300)))
                if Task.isCancelled { return }
                let ok = await self?.refresh() ?? false
                failures = ok ? 0 : failures + 1
            }
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
            // Offline: hydrate offsets from the bundled snapshot so the client
            // can still interpolate the projected metro/tram dots with zero
            // network. Keeps whatever we already have if the bundle is missing.
            if offsets.isEmpty,
               let url = Bundle.main.url(forResource: "station-offsets", withExtension: "json", subdirectory: "seed-schedules-v2"),
               let data = try? Data(contentsOf: url),
               let decoded = try? JSONDecoder().decode(StationOffsetsResponse.self, from: data) {
                var grouped: [String: [String: [OffsetStop]]] = [:]
                for line in decoded.lines {
                    let stops = line.stops
                        .sorted { $0.stopSequence < $1.stopSequence }
                        .map { OffsetStop(stationId: $0.stationId, minutesFromOrigin: Double($0.minutesFromOrigin)) }
                    grouped[line.lineId, default: [:]][line.direction] = stops
                }
                offsets = grouped
            }
        }
    }

    @discardableResult
    private func fetchActiveTrains() async -> Bool {
        guard var components = URLComponents(string: "\(base)/api/live-positions") else { return false }
        components.queryItems = [
            URLQueryItem(name: "lineIds", value: projectedLineIds.joined(separator: ",")),
        ]
        guard let url = components.url else { return false }
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
            // Live positions came back from the API: we're online. Surface
            // it on the home offline-alive pill.
            LiveDataFreshness.shared.markLive()
            return true
        } catch {
            // Offline (or Pi down): project metro/tram from the bundled
            // timetable so the map still shows live-moving dots, including
            // Saturday's 24h overnight service after midnight. Mirrors the web
            // offline fallback; a successful poll replaces these with real
            // positions. Suburban/national keep their own paths.
            let projected = ScheduleProjector.activeTrains()
            if !projected.isEmpty {
                let athensMidnight = Date().athensStartOfDay()
                trains = projected.map { p in
                    Train(
                        id: "\(p.lineId)_\(p.directionKey)_\(Int(p.originDepartureMinute * 100))",
                        lineId: p.lineId,
                        directionKey: p.directionKey,
                        originDepartureEpoch: athensMidnight.timeIntervalSince1970 + p.originDepartureMinute * 60,
                        totalTravelMinutes: p.totalTravelMinutes,
                        serviceType: p.serviceType
                    )
                }
            }
            // else: keep existing trains so a brief glitch animates smoothly.
            return false
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
