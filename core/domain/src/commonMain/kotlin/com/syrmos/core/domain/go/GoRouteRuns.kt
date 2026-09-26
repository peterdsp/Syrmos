package com.syrmos.core.domain.go

/** A placeable coordinate for a stop (WGS84 degrees). */
data class GoRoutePoint(val lat: Double, val lon: Double)

/** One drawable run of a journey: a leg's line id and its stop coordinates in ride order. */
data class GoRouteRun(val lineId: String, val points: List<GoRoutePoint>)

/**
 * The route map's projection of a [GuidanceJourney], the Kotlin twin of iOS
 * `GoRouteProjection.legRuns`: one coordinate run per leg in ride order so each
 * leg draws in its real line colour and an interchange reads as a colour change.
 * A leg with fewer than two placeable stops draws nothing rather than a stray
 * point. Pure; the caller supplies the coordinate lookup.
 */
object GoRouteRuns {
    fun legRuns(journey: GuidanceJourney, coordinate: (String) -> GoRoutePoint?): List<GoRouteRun> =
        journey.legs.mapNotNull { leg ->
            val points = leg.stops.mapNotNull { coordinate(it.id) }
            if (points.size < 2) null else GoRouteRun(leg.lineId, points)
        }

    /** The rider's current stop on the map, or null when it cannot be placed. */
    fun currentPoint(
        journey: GuidanceJourney,
        position: GuidancePosition,
        coordinate: (String) -> GoRoutePoint?,
    ): GoRoutePoint? =
        journey.legs.getOrNull(position.legIndex)?.stops?.getOrNull(position.stopIndex)?.let { coordinate(it.id) }
}
