import Foundation

/// The station-complex all-directions board.
///
/// Answers one question for a whole station complex: "what leaves from here, in
/// EVERY supported direction?" Swift port of the web `SyrmosStationBoard` and
/// the Kotlin `StationComplexBoard`; all three are asserted against the same
/// language-neutral fixture in `fixtures/station-board/`, so they cannot drift
/// apart on grouping, ordering, deduplication or coverage.
///
/// Why this exists on iOS. `HomeView` already rendered a direction board, but it
/// asked `ScheduleProjector` for three departures per line of ONE map node and
/// then capped the result at four rows, so a less frequent railway destination
/// could be dropped before grouping ever ran. It also grouped by `(lineId,
/// destination)` with a fresh `UUID()` identity rendered by array offset, which
/// cannot survive a reorder. And the node itself was not the station: Athens is
/// five boarding stop ids that no client joined.
///
/// The rule here is the same as on the other two clients: enumerate every group
/// first, limit presentation last.
enum StationComplexBoard {

    // MARK: - Identity

    /// A reviewed station complex: one name, several real boarding areas.
    struct Complex: Equatable {
        let id: String
        let name: String
        let nameEl: String
        let nameSq: String
        let nameIt: String
        let areas: [Area]
        /// True when the complex was synthesised from a single station rather
        /// than read from the reviewed registry.
        var synthetic: Bool = false

        struct Area: Equatable {
            let id: String
            let name: String
            let nameEl: String
            let nameSq: String
            let nameIt: String
            let stopIds: [String]
        }

        var memberStopIds: [String] {
            var out: [String] = []
            for area in areas {
                for id in area.stopIds where !out.contains(id) { out.append(id) }
            }
            return out
        }

        func localizedName(_ language: AppLanguage) -> String {
            switch language {
            case .greek: return nameEl.isEmpty ? name : nameEl
            case .albanian: return nameSq.isEmpty ? name : nameSq
            case .italian: return nameIt.isEmpty ? name : nameIt
            default: return name
            }
        }

        func localizedAreaName(_ areaId: String, _ language: AppLanguage) -> String {
            guard let area = areas.first(where: { $0.id == areaId }) else { return "" }
            switch language {
            case .greek: return area.nameEl.isEmpty ? area.name : area.nameEl
            case .albanian: return area.nameSq.isEmpty ? area.name : area.nameSq
            case .italian: return area.nameIt.isEmpty ? area.name : area.nameIt
            default: return area.name
            }
        }
    }

    // MARK: - Coverage

    /// Coverage of one (boarding stop, service) pair. Partial coverage stays
    /// visible: a board that could not read one of its services must never look
    /// complete.
    enum Coverage: String, Equatable {
        case loaded
        case loading
        case unavailable
        case noDepartureInWindow = "no_departure_in_window"
        case notOperating = "not_operating"
    }

    struct CoverageEntry: Equatable {
        let areaId: String
        let stopId: String
        let operatorId: String
        let lineId: String
        let patternKey: String
        let destination: String
        let state: Coverage
        var reason: String? = nil
    }

    // MARK: - Input

    /// One candidate departure from one boarding stop.
    ///
    /// `absoluteMinutes` is minutes from `now` on a monotonic timeline the
    /// CALLER computed from absolute Europe/Athens timestamps. This type never
    /// re-derives a service date and never adds 24 hours to a past departure:
    /// doing that here is what produced "every yesterday train is 23 hours away".
    struct Candidate: Equatable {
        let stopId: String
        let areaId: String
        let lineId: String
        var operatorId: String = ""
        let destination: String
        var destinationId: String? = nil
        var patternKey: String = ""
        var tripId: String? = nil
        var serviceDate: String? = nil
        var time: String = ""
        let absoluteMinutes: Int
        var scheduledMinutes: Int? = nil
        var source: SourceConfidence = .scheduled
        var cancelled: Bool = false
        var status: String? = nil
        var observedAt: Date? = nil
        var trainNo: String? = nil
        var serviceType: String = ""
        /// False at a trip's own final stop. A terminal arrival is not a
        /// departure, and offering "to this very station" is the defect this
        /// flag exists to prevent.
        var boardsHere: Bool = true
    }

    // MARK: - Output

    struct Board: Equatable {
        let complex: Complex
        let generatedAt: Date
        let windowMinutes: Int
        let groups: [Group]
        /// Groups that actually have a departure inside the window.
        let timedGroupCount: Int
        /// True when a service could not be READ. A service that simply has no
        /// train tonight is complete information, not partial coverage.
        let partial: Bool
    }

    struct Group: Equatable, Identifiable {
        /// Stable across refreshes: built from identity, never from an array
        /// offset, because rows reorder every time a train leaves.
        let id: String
        let areaId: String
        let stopId: String
        let operatorId: String
        let lineId: String
        let serviceType: String
        let destination: String
        let destinationId: String?
        let destinationKey: String
        let patternKey: String
        let times: [Time]
        /// The soonest time that is inside the window and not cancelled.
        let next: Time?
        let moreCount: Int
        let total: Int
        let coverage: Coverage
        var coverageReason: String? = nil
        /// Sort key: the next eligible effective departure.
        let sortMinutes: Int

        /// Each time keeps its OWN source and cancellation state, so a
        /// group-wide badge can never advertise a later live ETA over a
        /// scheduled lead time.
        struct Time: Equatable {
            let absoluteMinutes: Int
            let time: String
            var tripId: String? = nil
            var trainNo: String? = nil
            var source: SourceConfidence = .scheduled
            var cancelled: Bool = false
            var status: String? = nil
            var observedAt: Date? = nil
            var beyondWindow: Bool = false
            var scheduledMinutes: Int? = nil
        }

        /// The group's own source is the soonest shown time's source.
        var source: SourceConfidence { times.first?.source ?? .unknown }
    }

    // MARK: - Folding

    /// Fold case and accents but keep the letters, so "Ελληνικό" and "Ελληνικο"
    /// fold together while "Kifissia" and "Kifisias" never do.
    static func fold(_ s: String) -> String {
        s.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: nil)
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .split(whereSeparator: { $0 == " " || $0 == "\t" || $0 == "\n" })
            .joined(separator: " ")
    }

    private static func rank(_ s: SourceConfidence) -> Int {
        switch s {
        case .live: return 4
        case .scheduled: return 3
        case .estimated: return 2
        case .offline, .operatorLink: return 1
        case .unknown: return 0
        }
    }

    // MARK: - Deduplication

    /// Identity of one physical departure, used for deduplication ONLY.
    ///
    /// A provider-qualified trip id plus service date plus boarding stop is the
    /// strong form. Without a trip id we use a documented conservative composite
    /// that includes the EXACT absolute minute, so two real trains leaving in
    /// the same minute stay distinct. Never a rounded countdown, never a
    /// destination alone.
    static func identity(_ d: Candidate) -> String {
        if let trip = d.tripId, !trip.isEmpty {
            return "trip:\(d.operatorId):\(trip)@\(d.serviceDate ?? "")#\(d.stopId)"
        }
        return "composite:\(d.stopId)|\(d.lineId)|\(fold(d.destination))|\(d.absoluteMinutes)"
    }

    private static func merge(_ a: Candidate, _ b: Candidate) -> Candidate {
        var primary = rank(b.source) > rank(a.source) ? b : a
        let other = primary == a ? b : a
        primary.cancelled = a.cancelled || b.cancelled
        primary.status = primary.status ?? other.status
        primary.scheduledMinutes = a.scheduledMinutes ?? b.scheduledMinutes
        primary.observedAt = primary.observedAt ?? other.observedAt
        primary.tripId = a.tripId ?? b.tripId
        primary.trainNo = primary.trainNo ?? other.trainNo
        return primary
    }

    /// Collapse records that provably describe the same departure, preserving
    /// input order for the survivors.
    static func dedupe(_ list: [Candidate]) -> [Candidate] {
        var byId: [String: Candidate] = [:]
        var order: [String] = []
        for d in list {
            let id = identity(d)
            guard let existing = byId[id] else {
                byId[id] = d
                order.append(id)
                continue
            }
            // Two records that BOTH carry a trip id and disagree are two trains.
            if let a = existing.tripId, let b = d.tripId, a != b {
                let alt = id + "#" + b
                if byId[alt] == nil {
                    byId[alt] = d
                    order.append(alt)
                }
                continue
            }
            byId[id] = merge(existing, d)
        }
        return order.compactMap { byId[$0] }
    }

    // MARK: - Grouping

    static func groupId(areaId: String, operatorId: String, lineId: String,
                        patternKey: String, destination: String) -> String {
        [areaId, operatorId, lineId, patternKey, fold(destination)].joined(separator: "|")
    }

    // MARK: - The board

    /// Build the board. Pure: no clock, no network, no view state.
    static func build(
        complex: Complex,
        candidates: [Candidate],
        coverage: [CoverageEntry] = [],
        windowMinutes: Int = 12 * 60,
        maxTimesPerGroup: Int = 3,
        generatedAt: Date = Date(timeIntervalSince1970: 0)
    ) -> Board {
        // 1. Only real boardable departures become actionable rows.
        let raw = candidates.filter(\.boardsHere)

        // 2. Reconcile per service/trip, NOT per station. One published
        //    railway response can no longer suppress both metro directions,
        //    because nothing here picks a single winning source for a station.
        let merged = dedupe(raw)

        // 3. Enumerate groups BEFORE any limit is applied.
        var order: [String] = []
        var members: [String: [Candidate]] = [:]
        var seed: [String: Candidate] = [:]
        for d in merged {
            let id = groupId(areaId: d.areaId, operatorId: d.operatorId, lineId: d.lineId,
                             patternKey: d.patternKey, destination: d.destination)
            if members[id] == nil {
                order.append(id)
                members[id] = []
                seed[id] = d
            }
            members[id]?.append(d)
        }

        var rows: [Group] = []
        for id in order {
            guard let first = seed[id] else { continue }
            let all = (members[id] ?? []).sorted {
                $0.absoluteMinutes != $1.absoluteMinutes
                    ? $0.absoluteMinutes < $1.absoluteMinutes
                    : ($0.tripId ?? "") < ($1.tripId ?? "")
            }
            let within = all.filter { $0.absoluteMinutes >= 0 && $0.absoluteMinutes <= windowMinutes }
            let beyond = all.filter { $0.absoluteMinutes > windowMinutes }
            // A cancellation is status, never the recommended next departure.
            let eligible = within.filter { !$0.cancelled }
            let pool = within.isEmpty ? Array(beyond.prefix(1)) : within
            let shown = maxTimesPerGroup > 0 ? Array(pool.prefix(maxTimesPerGroup)) : pool
            let times = shown.map { d in
                Group.Time(
                    absoluteMinutes: d.absoluteMinutes,
                    time: d.time,
                    tripId: d.tripId,
                    trainNo: d.trainNo,
                    source: d.source,
                    cancelled: d.cancelled,
                    status: d.status,
                    observedAt: d.observedAt,
                    beyondWindow: d.absoluteMinutes > windowMinutes,
                    scheduledMinutes: d.scheduledMinutes
                )
            }
            let next = eligible.first.map { d in
                Group.Time(absoluteMinutes: d.absoluteMinutes, time: d.time, tripId: d.tripId,
                           trainNo: d.trainNo, source: d.source)
            }
            rows.append(Group(
                id: id,
                areaId: first.areaId,
                stopId: first.stopId,
                operatorId: first.operatorId,
                lineId: first.lineId,
                serviceType: all.first(where: { !$0.serviceType.isEmpty })?.serviceType ?? "",
                destination: first.destination,
                destinationId: first.destinationId,
                destinationKey: fold(first.destination),
                patternKey: first.patternKey,
                times: times,
                next: next,
                moreCount: max(0, within.count - shown.count),
                total: within.count,
                coverage: within.isEmpty ? .noDepartureInWindow : .loaded,
                sortMinutes: next?.absoluteMinutes ?? within.first?.absoluteMinutes
                    ?? beyond.first?.absoluteMinutes ?? Int.max
            ))
        }

        // 4. A service that produced no row still has to be visible, or a
        //    partly loaded board looks complete.
        let timedIds = Set(rows.filter { $0.total > 0 }.map(\.id))
        var statusRows: [Group] = []
        for entry in coverage where entry.state != .loaded {
            let id = groupId(areaId: entry.areaId, operatorId: entry.operatorId,
                             lineId: entry.lineId, patternKey: entry.patternKey,
                             destination: entry.destination)
            if timedIds.contains(id) { continue }
            if rows.contains(where: { $0.id == id }) { continue }
            statusRows.append(Group(
                id: id,
                areaId: entry.areaId,
                stopId: entry.stopId,
                operatorId: entry.operatorId,
                lineId: entry.lineId,
                serviceType: "",
                destination: entry.destination,
                destinationId: nil,
                destinationKey: fold(entry.destination),
                patternKey: entry.patternKey,
                times: [],
                next: nil,
                moreCount: 0,
                total: 0,
                coverage: entry.state,
                coverageReason: entry.reason,
                sortMinutes: Int.max
            ))
        }

        // 5. Sort by the next eligible departure with a stable tie break, so a
        //    frequent metro service cannot crowd a less frequent railway
        //    destination off the board: every group keeps exactly one row.
        let timed = rows.filter { $0.total > 0 }
            .sorted { $0.sortMinutes != $1.sortMinutes ? $0.sortMinutes < $1.sortMinutes : $0.id < $1.id }
        let quiet = (rows.filter { $0.total == 0 } + statusRows).sorted { $0.id < $1.id }

        return Board(
            complex: complex,
            generatedAt: generatedAt,
            windowMinutes: windowMinutes,
            groups: timed + quiet,
            timedGroupCount: timed.count,
            partial: coverage.contains { $0.state == .loading || $0.state == .unavailable }
        )
    }
}
