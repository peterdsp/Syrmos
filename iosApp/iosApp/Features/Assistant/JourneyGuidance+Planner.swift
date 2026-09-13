import Foundation

// Bridges the point-to-point planner to the GO live-guidance engine: turn a
// `JourneyPlanner.DetailedPlan` (per-leg ordered stop ids) into a
// `GuidanceJourney` the GO engine can guide the rider through, resolving stop
// display names in the active language. This is the seam a GO screen uses:
// plan A -> B, then hand the journey to GoGuidance/JourneyGuidance.

extension GuidanceJourney {
    /// Shared id -> display-name resolver over the bundled network, language-aware.
    /// One source of truth (interchange ids dedupe on first sight, matching
    /// JourneyPlanner.allStations()) so building and RESUMING a GO trip name stops
    /// identically. Reused by `ActiveJourneyStore`'s resume path.
    static func stationName(language: AppLanguage) -> (String) -> String {
        var byId: [String: TransitStation] = [:]
        for line in SyrmosData.lines {
            for st in SyrmosData.stations(for: line.id) where byId[st.id] == nil {
                byId[st.id] = st
            }
        }
        return { id in
            guard let st = byId[id] else { return id }
            return language == .greek && !st.nameEl.isEmpty ? st.nameEl : st.name
        }
    }

    /// Build a GO journey from the planner's detailed plan. Each leg's `towards`
    /// is its alight station name (the direction the rider is heading on that leg).
    static func from(_ plan: JourneyPlanner.DetailedPlan, language: AppLanguage) -> GuidanceJourney {
        let name = stationName(language: language)
        let legs = plan.legs.map { leg in
            let stops = leg.stationIds.map { GuidanceStop(id: $0, name: name($0)) }
            return GuidanceLeg(lineId: leg.lineId, towards: stops.last?.name ?? "", stops: stops)
        }
        return GuidanceJourney(legs: legs)
    }
}
