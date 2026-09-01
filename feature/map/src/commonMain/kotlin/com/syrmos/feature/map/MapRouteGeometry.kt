package com.syrmos.feature.map

import com.syrmos.core.common.map.LatLng
import com.syrmos.core.common.map.RouteGeometry
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.Station

/**
 * The single per-line polyline the map both draws and rides: it feeds the drawn
 * route, the vehicle-marker snapping, AND the train simulator/projector, so a
 * bus can never be drawn along its stops while its vehicle is interpolated
 * along an unrelated OSM shape (the PU1 divergence Codex caught).
 *
 * Per line:
 * - bus: its ordered stops, closed into a loop when circular (PU1). Bundled OSM
 *   bus shapes are deliberately ignored because they can be incomplete and would
 *   strand the route or fling a vehicle onto the missing segment.
 * - rail/tram with a real OSM shape: that shape (accurate curves).
 * - anything else (a missing or degenerate OSM shape): a Catmull-Rom spline
 *   through the ordered stops, so the drawn line, the snapped marker and the
 *   simulated vehicle all use the exact same points.
 *
 * Lines that resolve to fewer than two points are dropped.
 */
fun effectiveLineGeometry(
    lines: List<Line>,
    lineStations: Map<String, List<Station>>,
    osmShapes: Map<String, List<LatLng>>,
): Map<String, List<LatLng>> =
    lines.associate { line ->
        val stations = lineStations[line.id].orEmpty().map { LatLng(it.latitude, it.longitude) }
        val shape = RouteGeometry.displayShape(
            isBus = line.type == LineType.BUS,
            isLoop = RouteGeometry.isLoopTerminals(line.terminalA, line.terminalB),
            stations = stations,
            osmShape = osmShapes[line.id],
        ) ?: catmullRomSpline(stations)
        line.id to shape
    }.filterValues { it.size >= 2 }

/**
 * Catmull-Rom smoothing of an ordered polyline, used only for the rail/tram
 * fallback when no OSM track geometry is bundled. Returns the input unchanged
 * for fewer than three points (nothing to smooth).
 */
fun catmullRomSpline(points: List<LatLng>, segments: Int = 5): List<LatLng> {
    if (points.size < 3) return points
    val result = mutableListOf(points[0])
    for (i in 0 until points.size - 1) {
        val p0 = points[maxOf(i - 1, 0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[minOf(i + 2, points.size - 1)]
        for (t in 1..segments) {
            val f = t.toDouble() / (segments + 1)
            val lat = cr(p0.lat, p1.lat, p2.lat, p3.lat, f)
            val lng = cr(p0.lng, p1.lng, p2.lng, p3.lng, f)
            result.add(LatLng(lat, lng))
        }
        result.add(p2)
    }
    return result
}

private fun cr(a: Double, b: Double, c: Double, d: Double, t: Double): Double =
    0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (-a + 3 * b - 3 * c + d) * t * t * t)
