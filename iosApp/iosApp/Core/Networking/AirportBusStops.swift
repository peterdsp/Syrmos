import Foundation

/// Ordering and naming for the stop list on an airport bus.
///
/// Mirrors `AirportBusStops` in core/common case for case; see that file for
/// the full reasoning. In short: the unified payload gives one `stops` array
/// per LINE, and an OASA line runs several route codes (X97 publishes 5373 and
/// 5375 towards the airport, 5374 and 5376 back, 78 stops in total) whose
/// sequences each restart at 1. Sorting the whole array by `sequence` showed
/// the terminal twice and an order that ran Elliniko, then the airport, then
/// back into town. A bus runs exactly one code, so that code is the answer.
enum AirportBusStops {

    /// The itinerary of the run a vehicle is on: only that vehicle's route
    /// code, in sequence order, with consecutive repeats collapsed. Falls back
    /// to the direction, then to every stop, so an older payload degrades
    /// instead of rendering an empty sheet.
    static func itinerary(
        _ stops: [AirportBusService.UnifiedStop],
        routeCode: Int?,
        direction: String? = nil
    ) -> [AirportBusService.UnifiedStop] {
        let scoped: [AirportBusService.UnifiedStop]
        if let routeCode, stops.contains(where: { $0.route_code == routeCode }) {
            scoped = stops.filter { $0.route_code == routeCode }
        } else if let direction, !direction.isEmpty, stops.contains(where: { $0.direction == direction }) {
            scoped = stops.filter { $0.direction == direction }
        } else {
            scoped = stops
        }
        let ordered = scoped.sorted { ($0.sequence ?? .max) < ($1.sequence ?? .max) }
        return collapseConsecutive(ordered)
    }

    /// Drop a stop that repeats the one immediately before it. A circular run
    /// legitimately starts and ends at the same terminal, so only ADJACENT
    /// repeats collapse and the terminal still appears at both ends.
    static func collapseConsecutive(
        _ stops: [AirportBusService.UnifiedStop]
    ) -> [AirportBusService.UnifiedStop] {
        var out: [AirportBusService.UnifiedStop] = []
        for s in stops where out.last?.stop_id != s.stop_id {
            out.append(s)
        }
        return out
    }

    /// Greek readers get `stop_name_el` ("ΤΡΟΧΑΙΑ"); everyone else gets the
    /// Latin `stop_name` ("TROXAIA"), which is at least readable to them.
    static func displayName(_ stop: AirportBusService.UnifiedStop, language: AppLanguage) -> String {
        if language == .greek, let el = stop.stop_name_el, !el.isEmpty { return el }
        return stop.stop_name.isEmpty ? (stop.stop_name_el ?? "") : stop.stop_name
    }
}
