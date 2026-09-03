import Foundation

// Bridges the point-to-point planner to the GO live-guidance engine: turn a
// `JourneyPlanner.DetailedPlan` (per-leg ordered stop ids) into a
// `GuidanceJourney` the GO engine can guide the rider through, resolving stop
// display names in the active language. This is the seam a GO screen uses:
// plan A -> B, then hand the journey to GoGuidance/JourneyGuidance.

extension GuidanceJourney {
    /// Build a GO journey from the planner's detailed plan. Each leg's `towards`
    /// is its alight station name (the direction the rider is heading on that leg).
    static func from(_ plan: JourneyPlanner.DetailedPlan, language: AppLanguage) -> GuidanceJourney {
        // id -> station, gathered from every line's stop list (interchange ids
        // dedupe on first sight, matching JourneyPlanner.allStations()).
        var byId: [String: TransitStation] = [:]
        for line in SyrmosData.lines {
            for st in SyrmosData.stations(for: line.id) where byId[st.id] == nil {
                byId[st.id] = st
            }
        }
        func name(_ id: String) -> String {
            guard let st = byId[id] else { return id }
            return language == .greek && !st.nameEl.isEmpty ? st.nameEl : st.name
        }
        let legs = plan.legs.map { leg in
            let stops = leg.stationIds.map { GuidanceStop(id: $0, name: name($0)) }
            return GuidanceLeg(lineId: leg.lineId, towards: stops.last?.name ?? "", stops: stops)
        }
        return GuidanceJourney(legs: legs)
    }
}
