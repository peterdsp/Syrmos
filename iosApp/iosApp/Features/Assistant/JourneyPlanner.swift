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

    private struct Edge {
        let to: String
        let lineId: String?   // nil = transfer between co-located stations
        let weight: Int
    }

    /// Fastest route across the whole network.
    static func plan(from fromId: String, to toId: String, language: AppLanguage) -> Plan? {
        compute(from: fromId, to: toId, lines: SyrmosData.lines, language: language)
    }

    /// Metro-only alternative (M1/M2/M3), the sheltered option Ariadne can offer
    /// when the fastest route uses the tram or a surface line and the weather
    /// makes staying underground worth a few extra minutes. Nil when there's no
    /// all-metro path between the two stations. Mirrors KMP `metroOnly`.
    static func metroOnly(from fromId: String, to toId: String, language: AppLanguage) -> Plan? {
        compute(from: fromId, to: toId, lines: SyrmosData.lines.filter { $0.type == .metro }, language: language)
    }

    private static func compute(from fromId: String, to toId: String, lines: [TransitLine], language: AppLanguage) -> Plan? {
        if fromId == toId { return nil }
        let stations = allStations()
        let byId = Dictionary(uniqueKeysWithValues: stations.map { ($0.id, $0) })
        guard byId[fromId] != nil, byId[toId] != nil else { return nil }

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

        // Dijkstra.
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

        // Reconstruct (stationId, edge) path from origin to destination.
        var path: [(String, Edge)] = []
        var node = toId
        while node != fromId {
            guard let (p, e) = prev[node] else { break }
            path.insert((node, e), at: 0)
            node = p
        }

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
        let total = dist[toId] ?? legs.reduce(0) { $0 + $1.minutes }
        return Plan(legs: legs, totalMinutes: total, transfers: max(0, legs.count - 1))
    }

    // MARK: - Helpers

    private static let TRANSFER_MINUTES = 3

    private static func travelTime(for type: TransitType) -> Int {
        switch type {
        case .metro: return 2
        case .tram: return 3
        case .suburban: return 4
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
