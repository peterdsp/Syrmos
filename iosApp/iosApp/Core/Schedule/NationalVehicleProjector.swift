import Foundation
import CoreLocation

/// Projects moving vehicles for national-rail and rail-replacement-bus lines
/// straight from the bundled timetables, fully offline.
///
/// Why this exists separately from `TrainSimulatorService.projectTrains`: the
/// Pi's `/api/live-positions` feed (and its `station_offsets` table) covers
/// only metro, tram and the Athens suburban A1-A4. National intercity
/// (IC/RG/KO/PL/DK/AL) and the rail-replacement buses (KB/VL/DX/KP/TL/PU/PSB),
/// plus the Thessaloniki and Patras suburban corridors, have neither a live
/// feed nor an offsets table. So their vehicles are projected client-side from
/// `seed-schedules-v2/{lineId}.json` `trips[]` exactly the way the web map's
/// `projectScheduledTrains` and the Android KMP projector do: for every trip
/// whose day-type matches today, find the segment the wall clock currently
/// falls in and interpolate the chord between its two stations.
///
/// The station coordinates for these lines live in `seed-schedules-v2/lines.json`
/// under each line's nested `stations[]` (with lat/lng), which `SyrmosData`'s
/// line decoder ignores, so this projector parses them itself into a lookup.
///
/// Not @MainActor: read-only after init, safe to call from the simulator's
/// detached loop. `@unchecked Sendable` because the two stored dictionaries are
/// immutable value types.
final class NationalVehicleProjector: @unchecked Sendable {
    static let shared = NationalVehicleProjector()

    /// Metro/tram lines projected from the band grid + offsets by
    /// `TrainSimulatorService.projectTrains`; they carry no `trips[]` and must
    /// never be trip-projected here. Athens suburban A1-A4 ARE projected here
    /// now (from their bundled trips) so they still appear on the map offline;
    /// online the map dedupes them per line against the real-GPS LiveTrainService
    /// feed (MapView `coveredLines`), so there is never a double marker.
    private static let liveFeedLineIds: Set<String> = [
        "M1", "M2", "M3", "M3_AIR", "T6", "T7",
    ]

    private struct Trip: Decodable {
        let trainNo: String
        let dayType: String
        let direction: String
        let serviceLabel: String?
        let stops: [Stop]

        struct Stop: Decodable {
            let stationId: String
            let stopSequence: Int
            let departureTime: String
        }
    }

    private struct StationPoint {
        let lat: Double
        let lon: Double
        let name: String
    }

    /// trips keyed by line id, only for suburban/bus lines that carry an
    /// explicit `trips[]` and are not on the live feed.
    private let tripsByLine: [String: [Trip]]
    /// stationId -> coordinate + English name, including national stations.
    private let stationCoords: [String: StationPoint]

    private init() {
        // Only project the triangle-eligible modes: national rail and the
        // regional suburban corridors both carry `type == suburban`,
        // rail-replacement services carry `type == bus`. Metro/tram never
        // reach this path (and have no triangle sprite), so excluding them
        // here keeps the render honest.
        let eligibleLineIds = SyrmosData.lines
            .filter { $0.isOperational }
            .filter { $0.type == .suburban || $0.type == .bus }
            .map(\.id)
            .filter { !Self.liveFeedLineIds.contains($0) }

        self.tripsByLine = Self.loadTrips(lineIds: eligibleLineIds)
        self.stationCoords = Self.loadStations()
    }

    /// Build the moving vehicles for right now. `nowEpoch` is passed in so the
    /// simulator's single clock reading drives every projector on a tick.
    func project(nowEpoch: TimeInterval) -> [SimulatedTrain] {
        if tripsByLine.isEmpty { return [] }

        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/Athens") ?? .current
        let now = Date(timeIntervalSince1970: nowEpoch)
        let comps = cal.dateComponents([.weekday, .hour, .minute, .month, .day], from: now)
        guard let weekday = comps.weekday,
              let hour = comps.hour,
              let minute = comps.minute,
              let month = comps.month,
              let day = comps.day
        else { return [] }

        let today = Self.dayType(weekday: weekday, holiday: Self.holidayDayType(month: month, day: day))
        let nowMinutes = hour * 60 + minute

        var result: [SimulatedTrain] = []
        for (lineId, trips) in tripsByLine {
            guard let line = SyrmosData.line(for: lineId), line.isOperational else { continue }
            for trip in trips where Self.matchesDayType(trip: trip.dayType, today: today) {
                guard let train = projectTrip(trip, line: line, nowMinutes: nowMinutes, nowEpoch: nowEpoch) else { continue }
                result.append(train)
            }
        }
        return result
    }

    private func projectTrip(_ trip: Trip, line: TransitLine, nowMinutes: Int, nowEpoch: TimeInterval) -> SimulatedTrain? {
        guard trip.stops.count >= 2 else { return nil }

        // Pair each stop with its minute-of-day in CHRONOLOGICAL order. The seed
        // stores stops in canonical (outbound) stop_sequence order, so an INBOUND
        // trip runs time-descending; sorting by stop_sequence + unwrapping would
        // corrupt it. Instead: if the trip spans > 12h it genuinely crosses
        // midnight, so pre-05:00 stops belong to the next day (+24h); then sort by
        // time. Mirrors the Pi projector._project_scheduled_trip_active and the
        // web chronologicalStops.
        var paired: [(stop: Trip.Stop, m: Int)] = []
        for s in trip.stops {
            guard let m = Self.minutesOfDay(s.departureTime) else { return nil }
            paired.append((s, m))
        }
        if let mx = paired.map(\.m).max(), let mn = paired.map(\.m).min(), mx - mn > 12 * 60 {
            paired = paired.map { $0.m < 5 * 60 ? (stop: $0.stop, m: $0.m + 1440) : $0 }
        }
        paired.sort { $0.m < $1.m }
        let stops = paired.map(\.stop)
        let mins = paired.map(\.m)

        let originMin = mins[0]
        let lastMin = mins[mins.count - 1]

        // Is the train on the rails right now? Try today's clock, and if now is
        // before this trip's departure, try the same clock a day later so an
        // after-midnight leg of a late-evening departure is still caught.
        var elapsedClock = nowMinutes
        if elapsedClock < originMin { elapsedClock += 1440 }
        guard elapsedClock >= originMin, elapsedClock <= lastMin else { return nil }

        var seg = 0
        for i in 0..<(stops.count - 1) {
            if mins[i] <= elapsedClock && elapsedClock < mins[i + 1] { seg = i; break }
            if i == stops.count - 2 { seg = i }
        }

        let from = stops[seg]
        let to = stops[seg + 1]
        guard let a = stationCoords[from.stationId], let b = stationCoords[to.stationId] else { return nil }

        let segDuration = Double(mins[seg + 1] - mins[seg])
        let frac = segDuration > 0 ? min(max(Double(elapsedClock - mins[seg]) / segDuration, 0), 1) : 0

        // Straight chord between the two stations: national/bus lines ship no
        // OSM route shape (only Athens M/T/A do), so there is no arc to hug and
        // the chord is what the web projector draws too.
        let lat = a.lat + (b.lat - a.lat) * frac
        let lon = a.lon + (b.lon - a.lon) * frac

        let destination = trip.direction == "outbound" ? line.terminalB : line.terminalA
        let stopTuples = stops.enumerated().map { idx, s in
            (stationId: s.stationId, minutesFromOrigin: Double(mins[idx] - originMin))
        }

        return SimulatedTrain(
            id: "\(line.id)-\(trip.trainNo)-\(trip.direction)",
            lineId: line.id,
            lineName: line.name,
            lineType: line.type,
            direction: trip.direction,
            destinationName: destination,
            currentStationName: a.name,
            nextStationName: b.name,
            coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon),
            isAirportService: false,
            originEpoch: nowEpoch - Double(elapsedClock - originMin) * 60.0,
            totalTravelMinutes: lastMin - originMin,
            stops: stopTuples,
            bearing: compassBearing(fromLat: a.lat, fromLon: a.lon, toLat: b.lat, toLon: b.lon)
        )
    }

    // MARK: - Bundle loading

    private struct LineTripsPayload: Decodable {
        let trips: [Trip]?
    }

    private static func loadTrips(lineIds: [String]) -> [String: [Trip]] {
        var out: [String: [Trip]] = [:]
        for lid in lineIds {
            guard let url = Bundle.main.url(
                forResource: lid,
                withExtension: "json",
                subdirectory: "seed-schedules-v2"
            ) ?? Bundle.main.url(forResource: lid, withExtension: "json"),
                  let data = try? Data(contentsOf: url),
                  let payload = try? JSONDecoder().decode(LineTripsPayload.self, from: data),
                  let trips = payload.trips,
                  !trips.isEmpty
            else { continue }
            out[lid] = trips
        }
        return out
    }

    private struct StationsPayload: Decodable {
        struct Line: Decodable {
            let stations: [Station]?
        }
        struct Station: Decodable {
            let id: String
            let name: String
            let nameEl: String?
            let lat: Double
            let lng: Double
        }
        let lines: [Line]
    }

    private static func loadStations() -> [String: StationPoint] {
        guard let url = Bundle.main.url(
            forResource: "lines",
            withExtension: "json",
            subdirectory: "seed-schedules-v2"
        ) ?? Bundle.main.url(forResource: "lines", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let payload = try? JSONDecoder().decode(StationsPayload.self, from: data)
        else { return [:] }

        var out: [String: StationPoint] = [:]
        for line in payload.lines {
            for s in line.stations ?? [] {
                out[s.id] = StationPoint(lat: s.lat, lon: s.lng, name: s.name)
            }
        }
        return out
    }

    // MARK: - Day-type + time helpers
    // Replicated from ScheduleProjector (its copies are `private static`).

    private static func holidayDayType(month: Int, day: Int) -> String? {
        let key = String(format: "%02d-%02d", month, day)
        switch key {
        case "01-01", "05-01", "10-28", "12-25", "12-26": return "sun"
        case "08-15": return "aug_15"
        case "12-24", "12-31": return "dec_24_31"
        case "01-02", "01-06", "11-17": return "sat"
        default: return nil
        }
    }

    private static func dayType(weekday: Int, holiday: String?) -> String {
        if let h = holiday { return h }
        switch weekday {
        case 1: return "sun"
        case 2, 3, 4, 5: return "mon_thu"
        case 6: return "fri"
        case 7: return "sat"
        default: return "mon_thu"
        }
    }

    /// National/bus timetables only define mon_thu/fri/sat/sun. On the two
    /// special holiday day-types (aug_15, dec_24_31) fall back to the Sunday
    /// service, which is how those days are staffed on the intercity network.
    private static func matchesDayType(trip: String, today: String) -> Bool {
        if trip == today { return true }
        if today == "aug_15" || today == "dec_24_31" { return trip == "sun" }
        return false
    }

    private static func minutesOfDay(_ hhmm: String) -> Int? {
        let parts = hhmm.split(separator: ":")
        guard parts.count == 2, let h = Int(parts[0]), let m = Int(parts[1]) else { return nil }
        return h * 60 + m
    }
}
