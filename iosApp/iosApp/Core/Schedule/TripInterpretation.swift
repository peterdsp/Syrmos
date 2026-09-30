import Foundation

// MARK: - Trip interpretation

/// Reads a published trip the way a rider standing on a platform reads it.
///
/// The bundled trip data is not consistent about array direction: the suburban
/// A-lines list an inbound trip's stops in outbound geographic order, so the
/// times descend, while the intercity and regional corridors list them in travel
/// order. Taking `stops.first` for inbound and `stops.last` for outbound is
/// therefore right for A1-A4 and wrong for IC1/RG1, where it reports the train's
/// ORIGIN as its destination.
///
/// The travel order is derived from the times, which mean the same thing in both
/// layouts. It also catches short turns the direction flag cannot: A1 3201 runs
/// Airport to Tavros late at night and never reaches the Piraeus terminal.
///
/// Mirrors `SyrmosStationBoard.tripTravelOrder` / `.tripBoarding` on the web and
/// `TripInterpretation` in Kotlin.
enum TripInterpretation {

    struct Stop: Equatable {
        let stationId: String
        /// Minutes from the trip's own start, unwrapped past midnight.
        let minutes: Int
    }

    struct Boarding: Equatable {
        /// Minute of day at this stop.
        let departureMinutes: Int
        let destinationStopId: String
        /// False at the trip's own final stop: a terminal arrival is not a
        /// departure.
        let boardsHere: Bool
    }

    static func minutesOfDay(_ hhmm: String) -> Int? {
        let parts = hhmm.split(separator: ":")
        guard parts.count >= 2, let h = Int(parts[0]), let m = Int(parts[1]) else { return nil }
        return h * 60 + m
    }

    /// The trip's stops in travel order, with minutes unwrapped past midnight.
    static func travelOrder(_ stops: [SyrmosSchedulesService.TripStop]) -> [Stop] {
        let raw: [(String, Int)] = stops.compactMap { s in
            guard let m = minutesOfDay(s.departureTime), !s.stationId.isEmpty else { return nil }
            return (s.stationId, m)
        }
        guard raw.count >= 2 else { return [] }

        func unwrap(_ list: [(String, Int)]) -> (stops: [Stop], crossings: Int, span: Int) {
            var day = 0
            var crossings = 0
            var out: [Stop] = []
            for (index, entry) in list.enumerated() {
                if index > 0, entry.1 + day < out[index - 1].minutes {
                    day += 24 * 60
                    crossings += 1
                }
                out.append(Stop(stationId: entry.0, minutes: entry.1 + day))
            }
            return (out, crossings, (out.last?.minutes ?? 0) - (out.first?.minutes ?? 0))
        }

        let forward = unwrap(raw)
        let backward = unwrap(raw.reversed())
        // A real trip spans a few hours. The wrong orientation has to cross
        // midnight at nearly every step, so the smaller span wins.
        let useBackward = backward.crossings < forward.crossings
            || (backward.crossings == forward.crossings && backward.span < forward.span)
        return useBackward ? backward.stops : forward.stops
    }

    /// What this trip offers a rider at `stopId`, or nil when it does not serve
    /// the stop or carries no usable times.
    static func boarding(_ trip: SyrmosSchedulesService.TripEntry, at stopId: String) -> Boarding? {
        let order = travelOrder(trip.stops)
        guard let index = order.firstIndex(where: { $0.stationId == stopId }),
              let last = order.last
        else { return nil }
        return Boarding(
            departureMinutes: order[index].minutes % (24 * 60),
            destinationStopId: last.stationId,
            boardsHere: index < order.count - 1
        )
    }
}

/// Resolves a bundled seed file.
///
/// Searches every loaded bundle rather than only `Bundle.main`: under a test
/// host the main bundle is the host app, and the widget extension carries its own
/// copy of the seed. A resource that silently fails to resolve would leave the
/// station-complex registry empty, which quietly turns Athens back into five
/// unrelated stops, so the lookup is deliberately generous.
enum SyrmosSeedBundle {
    /// Anchors `Bundle(for:)` to the module that ships the seed.
    private final class Token {}

    static func url(named name: String) -> URL? {
        var bundles: [Bundle] = [Bundle.main, Bundle(for: Token.self)]
        bundles.append(contentsOf: Bundle.allBundles)
        for bundle in bundles {
            if let url = bundle.url(forResource: name, withExtension: "json",
                                    subdirectory: "seed-schedules-v2") {
                return url
            }
            if let url = bundle.url(forResource: name, withExtension: "json") {
                return url
            }
        }
        return nil
    }

    /// What the seed directory actually contains, for diagnosing a resource that
    /// is present in the repository but absent from a built bundle.
    static func seedDirectoryListing() -> String {
        var lines: [String] = []
        for bundle in [Bundle.main, Bundle(for: Token.self)] {
            let root = bundle.bundleURL.appendingPathComponent("seed-schedules-v2")
            let names = (try? FileManager.default.contentsOfDirectory(atPath: root.path)) ?? []
            lines.append("\(bundle.bundleURL.lastPathComponent)/seed-schedules-v2: \(names.count) files")
            if !names.isEmpty {
                lines.append("  " + names.sorted().joined(separator: ", "))
            }
        }
        return lines.joined(separator: "\n")
    }
}

// MARK: - Line stop order

/// The ordered boarding stops of every line, read from `lines.json`'s nested
/// `stations[]`. This is the only authoritative statement that a line serves a
/// stop; `stations.json:line_ids` carries interchange unions, which is how an A1
/// lookup used to resolve to the metro stop `M2_STA`.
enum SyrmosLineStops {
    static let orderedStopIds: [String: [String]] = load()
    /// stopId -> every line that actually boards there, sorted.
    static let boardingLines: [String: [String]] = {
        var out: [String: Set<String>] = [:]
        for (lineId, stops) in orderedStopIds {
            for stopId in stops { out[stopId, default: []].insert(lineId) }
        }
        return out.mapValues { $0.sorted() }
    }()

    private static func load() -> [String: [String]] {
        struct Payload: Decodable {
            struct Line: Decodable {
                let id: String
                let stations: [Station]?
            }
            struct Station: Decodable {
                let id: String
                let stopSequence: Int?
            }
            let lines: [Line]
        }
        guard let url = SyrmosSeedBundle.url(named: "lines"),
              let data = try? Data(contentsOf: url),
              let payload = try? JSONDecoder().decode(Payload.self, from: data)
        else { return [:] }
        var out: [String: [String]] = [:]
        for line in payload.lines {
            let stations = line.stations ?? []
            let ordered = stations.enumerated()
                .sorted { ($0.element.stopSequence ?? $0.offset) < ($1.element.stopSequence ?? $1.offset) }
                .map(\.element.id)
            if !ordered.isEmpty { out[line.id] = ordered }
        }
        return out
    }

    /// The directions a rider can actually leave in from `stopId`. A terminal
    /// offers one, not two: projecting both is how a board invents a train to
    /// nowhere.
    static func directions(lineId: String, stopId: String) -> [(destination: String, directionKey: String)] {
        guard let ordered = orderedStopIds[lineId],
              let index = ordered.firstIndex(of: stopId),
              let line = SyrmosData.line(for: lineId)
        else { return [] }
        var out: [(String, String)] = []
        if index < ordered.count - 1, !line.terminalB.isEmpty {
            out.append((line.terminalB, "outbound"))
        }
        if index > 0, !line.terminalA.isEmpty {
            out.append((line.terminalA, "inbound"))
        }
        return out
    }
}
