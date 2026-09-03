import Foundation

/// Point-to-point routing across the bundled Athens rail network, the Swift
/// port of the KMP `PlanJourneyUseCase` (Dijkstra over a station graph). Any
/// number of transfers, so iOS matches Android/Web.
///
/// One iOS-specific wrinkle: interchange stations carry a different station id
/// per line here (e.g. M2_SYN vs M3_SYN at Syntagma), so the graph adds explicit
/// transfer edges between co-located stations (same accent-folded name) to let
/// Dijkstra change lines at a physical interchange.
enum JourneyPlanner {
    struct Leg: Equatable {
        let lineId: String
        let toName: String
        let stops: Int
        let minutes: Int
    }

    struct Plan: Equatable {
        let legs: [Leg]
        let totalMinutes: Int
        let transfers: Int
    }

    /// A leg with its full ordered stop id sequence (board..alight inclusive),
    /// the extra detail GO live guidance needs. Kept separate from `Plan` so the
    /// existing (Ariadne-facing) `plan()` API is unchanged.
    struct DetailedLeg: Equatable {
        let lineId: String
        let stationIds: [String]
        var boardId: String { stationIds.first ?? "" }
        var alightId: String { stationIds.last ?? "" }
    }

    struct DetailedPlan: Equatable {
        let legs: [DetailedLeg]
        let totalMinutes: Int
    }

    private struct Edge {
        let to: String
        let lineId: String?   // nil = transfer between co-located stations
        let weight: Int
    }

    /// Fastest route across the whole network.
    static func plan(from fromId: String, to toId: String, language: AppLanguage) -> Plan? {
        // Operational only. Track that is built but not open renders on the map
        // because it is real, but routing through it would hand the user a plan
        // they cannot travel, which is worse than no plan. Mirrors KMP
        // PlanJourneyUseCase.
        compute(from: fromId, to: toId, lines: SyrmosData.operationalLines, language: language)
    }

    /// Metro-only alternative (M1/M2/M3), the sheltered option Ariadne can offer
    /// when the fastest route uses the tram or a surface line and the weather
    /// makes staying underground worth a few extra minutes. Nil when there's no
    /// all-metro path between the two stations. Mirrors KMP `metroOnly`.
    static func metroOnly(from fromId: String, to toId: String, language: AppLanguage) -> Plan? {
        compute(from: fromId, to: toId, lines: SyrmosData.operationalLines.filter { $0.type == .metro }, language: language)
    }

    /// The fastest route as a `DetailedPlan` (per-leg ordered stop ids), for the
    /// GO live-guidance engine. Operational lines only, like `plan`.
    static func planDetailed(from fromId: String, to toId: String, language: AppLanguage) -> DetailedPlan? {
        guard fromId != toId else { return nil }
        let stations = allStations()
        let byId = Dictionary(uniqueKeysWithValues: stations.map { ($0.id, $0) })
        guard byId[fromId] != nil, byId[toId] != nil else { return nil }
        let graph = buildGraph(SyrmosData.operationalLines, stations: stations)
        guard let result = shortestPath(from: fromId, to: toId, graph: graph) else { return nil }

        // Group the (stationId, edge) path into per-line legs, keeping every stop
        // id. A transfer edge (lineId == nil) ends the current leg and starts the
        // next one at the co-located board stop.
        var legs: [DetailedLeg] = []
        var curLine: String?
        var curStops: [String] = [fromId]
        func flush() {
            if let line = curLine, curStops.count >= 2 {
                legs.append(DetailedLeg(lineId: line, stationIds: curStops))
            }
        }
        for (stationId, edge) in result.path {
            if edge.lineId == nil {
                flush()
                curLine = nil
                curStops = [stationId]
            } else if edge.lineId != curLine {
                // New same-line leg. When a line change is not via a transfer edge
                // (co-located same-id junction), start the new leg from the shared
                // station so no stop is dropped.
                if curLine != nil, let junction = curStops.last {
                    flush()
                    curStops = [junction]
                }
                curLine = edge.lineId
                curStops.append(stationId)
            } else {
                curStops.append(stationId)
            }
        }
        flush()

        guard !legs.isEmpty else { return nil }
        return DetailedPlan(legs: legs, totalMinutes: result.total)
    }

    private static func compute(from fromId: String, to toId: String, lines: [TransitLine], language: AppLanguage) -> Plan? {
        if fromId == toId { return nil }
        let stations = allStations()
        let byId = Dictionary(uniqueKeysWithValues: stations.map { ($0.id, $0) })
        guard byId[fromId] != nil, byId[toId] != nil else { return nil }

        let graph = buildGraph(lines, stations: stations)
        guard let result = shortestPath(from: fromId, to: toId, graph: graph) else { return nil }
        let path = result.path

        // Merge consecutive same-line edges into legs; transfer edges split legs.
        var legs: [Leg] = []
        var curLine: String?
        var stops = 0
        var minutes = 0
        var lastName = byId[fromId].map { displayName($0, language) } ?? fromId

        func flush(endName: String) {
            if let line = curLine {
                legs.append(Leg(lineId: line, toName: endName, stops: stops, minutes: minutes))
            }
        }

        for (stationId, edge) in path {
            let stationName = byId[stationId].map { displayName($0, language) } ?? stationId
            if edge.lineId == nil {
                // Transfer: close the current leg at this interchange.
                flush(endName: stationName)
                curLine = nil
                stops = 0
                minutes = 0
                lastName = stationName
            } else if edge.lineId != curLine {
                flush(endName: lastName)
                curLine = edge.lineId
                stops = 1
                minutes = edge.weight
                lastName = stationName
            } else {
                stops += 1
                minutes += edge.weight
                lastName = stationName
            }
        }
        flush(endName: byId[toId].map { displayName($0, language) } ?? toId)

        if legs.isEmpty { return nil }
        return Plan(legs: legs, totalMinutes: result.total, transfers: max(0, legs.count - 1))
    }

    // MARK: - Graph + shortest path (shared by compute and planDetailed)

    private static func buildGraph(_ lines: [TransitLine], stations: [TransitStation]) -> [String: [Edge]] {
        var graph: [String: [Edge]] = [:]

        // Same-line edges between consecutive stations.
        for line in lines {
            let weight = travelTime(for: line.type)
            let ordered = SyrmosData.stations(for: line.id)
            for i in 0..<max(0, ordered.count - 1) {
                let a = ordered[i].id
                let b = ordered[i + 1].id
                graph[a, default: []].append(Edge(to: b, lineId: line.id, weight: weight))
                graph[b, default: []].append(Edge(to: a, lineId: line.id, weight: weight))
            }
        }

        // Transfer edges between co-located stations (same physical place,
        // different per-line ids).
        var groups: [String: [String]] = [:]
        for st in stations {
            groups[norm(st.name.isEmpty ? st.nameEl : st.name), default: []].append(st.id)
        }
        for (_, ids) in groups where ids.count > 1 {
            for i in 0..<ids.count {
                for j in (i + 1)..<ids.count {
                    graph[ids[i], default: []].append(Edge(to: ids[j], lineId: nil, weight: TRANSFER_MINUTES))
                    graph[ids[j], default: []].append(Edge(to: ids[i], lineId: nil, weight: TRANSFER_MINUTES))
                }
            }
        }
        return graph
    }

    /// Dijkstra returning the ordered (stationId, edgeUsedToReachIt) path from
    /// `fromId` to `toId` (excluding the origin) and the total weight to `toId`.
    private static func shortestPath(from fromId: String, to toId: String, graph: [String: [Edge]]) -> (path: [(String, Edge)], total: Int)? {
        var dist: [String: Int] = [fromId: 0]
        var prev: [String: (String, Edge)] = [:]
        var visited = Set<String>()
        var frontier: [(String, Int)] = [(fromId, 0)]

        while !frontier.isEmpty {
            frontier.sort { $0.1 < $1.1 }
            let (current, d) = frontier.removeFirst()
            if visited.contains(current) { continue }
            visited.insert(current)
            if current == toId { break }
            for edge in graph[current] ?? [] {
                let nd = d + edge.weight
                if nd < (dist[edge.to] ?? Int.max) {
                    dist[edge.to] = nd
                    prev[edge.to] = (current, edge)
                    frontier.append((edge.to, nd))
                }
            }
        }

        guard prev[toId] != nil else { return nil }

        var path: [(String, Edge)] = []
        var node = toId
        while node != fromId {
            guard let (p, e) = prev[node] else { break }
            path.insert((node, e), at: 0)
            node = p
        }
        return (path, dist[toId] ?? path.reduce(0) { $0 + $1.1.weight })
    }

    // MARK: - Helpers

    private static let TRANSFER_MINUTES = 3

    private static func travelTime(for type: TransitType) -> Int {
        switch type {
        case .metro: return 2
        case .tram: return 3
        case .suburban: return 4
        case .bus: return 4
        case .scenic: return 5
        }
    }

    private static func allStations() -> [TransitStation] {
        var seen = Set<String>()
        var out: [TransitStation] = []
        for line in SyrmosData.lines {
            for st in SyrmosData.stations(for: line.id) where !seen.contains(st.id) {
                seen.insert(st.id); out.append(st)
            }
        }
        return out
    }

    private static func displayName(_ st: TransitStation, _ lang: AppLanguage) -> String {
        lang == .greek && !st.nameEl.isEmpty ? st.nameEl : st.name
    }

    private static func norm(_ s: String) -> String {
        String(AthensTransitParser.fold(s).filter { $0.isLetter || $0.isNumber })
    }
}
