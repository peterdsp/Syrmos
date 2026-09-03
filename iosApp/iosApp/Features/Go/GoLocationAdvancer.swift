import Foundation

// Pure core of LIVE GO: given the rider's GPS fix and the journey's stop
// coordinates, decide which stop they have reached, so the guidance position can
// advance on its own (no manual tapping). Kept free of CoreLocation so it is
// fully unit-testable; the session layer feeds it a plain lat/lon.
//
// Rules:
// - Only ever move FORWARD from the current position. GPS jitter must never rewind
//   the rider's guidance to an earlier stop.
// - Place the rider at the forward stop nearest their fix, but only within
//   `thresholdMeters`; between stops (far from any) the position holds, so the
//   guidance does not flicker.
enum GoLocationAdvancer {
    struct Coord: Equatable { let lat: Double; let lon: Double }

    static func advancedPosition(
        journey: GuidanceJourney,
        current: GuidancePosition,
        coords: [String: Coord],
        lat: Double,
        lon: Double,
        thresholdMeters: Double = 350
    ) -> GuidancePosition {
        var best = current
        var bestDist = Double.greatestFiniteMagnitude
        var cursor: GuidancePosition? = current
        while let pos = cursor {
            if let stop = stop(journey, pos), let c = coords[stop.id] {
                let d = haversine(c.lat, c.lon, lat, lon)
                if d <= thresholdMeters && d < bestDist {
                    bestDist = d
                    best = pos
                }
            }
            cursor = next(journey, pos)
        }
        return best
    }

    // MARK: Journey walk (forward only)

    private static func stop(_ j: GuidanceJourney, _ p: GuidancePosition) -> GuidanceStop? {
        guard j.legs.indices.contains(p.legIndex),
              j.legs[p.legIndex].stops.indices.contains(p.stopIndex) else { return nil }
        return j.legs[p.legIndex].stops[p.stopIndex]
    }

    private static func next(_ j: GuidanceJourney, _ p: GuidancePosition) -> GuidancePosition? {
        guard j.legs.indices.contains(p.legIndex) else { return nil }
        let leg = j.legs[p.legIndex]
        if p.stopIndex < leg.stops.count - 1 {
            return GuidancePosition(legIndex: p.legIndex, stopIndex: p.stopIndex + 1)
        }
        if p.legIndex < j.legs.count - 1 {
            return GuidancePosition(legIndex: p.legIndex + 1, stopIndex: 0)
        }
        return nil
    }

    // MARK: Distance

    /// Great-circle distance in metres.
    static func haversine(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
        let r = 6_371_000.0
        let dLat = (lat2 - lat1) * .pi / 180
        let dLon = (lon2 - lon1) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2)
            + cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(min(1, sqrt(a)))
    }
}
