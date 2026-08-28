package com.syrmos.core.common.map

import kotlin.math.*

data class LatLng(val lat: Double, val lng: Double)

object VehicleInterpolation {

    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val DEG_TO_RAD = PI / 180.0
    private const val RAD_TO_DEG = 180.0 / PI

    private fun toRadians(deg: Double) = deg * DEG_TO_RAD
    private fun toDegrees(rad: Double) = rad * RAD_TO_DEG

    fun trainGlideEase(t: Double): Double {
        val clamped = t.coerceIn(0.0, 1.0)
        return cubicBezier(0.16, 1.0, 0.30, 1.0, clamped)
    }

    fun cubicBezier(x1: Double, y1: Double, x2: Double, y2: Double, t: Double): Double {
        val cx = 3.0 * x1
        val bx = 3.0 * (x2 - x1) - cx
        val ax = 1.0 - cx - bx
        val cy = 3.0 * y1
        val by = 3.0 * (y2 - y1) - cy
        val ay = 1.0 - cy - by
        var guessT = t
        for (i in 0 until 8) {
            val currentX = ((ax * guessT + bx) * guessT + cx) * guessT
            val currentSlope = (3.0 * ax * guessT + 2.0 * bx) * guessT + cx
            if (currentSlope == 0.0) break
            guessT -= (currentX - t) / currentSlope
        }
        return ((ay * guessT + by) * guessT + cy) * guessT
    }

    fun buildDistanceTable(polyline: List<LatLng>): DoubleArray {
        if (polyline.isEmpty()) return doubleArrayOf()
        val table = DoubleArray(polyline.size)
        table[0] = 0.0
        for (i in 1 until polyline.size) {
            table[i] = table[i - 1] + haversineM(polyline[i - 1], polyline[i])
        }
        return table
    }

    fun pointAtArc(polyline: List<LatLng>, table: DoubleArray, arc: Double): LatLng {
        if (polyline.isEmpty()) return LatLng(0.0, 0.0)
        if (arc <= 0.0) return polyline.first()
        val total = table.last()
        if (arc >= total) return polyline.last()
        var lo = 0
        var hi = table.size - 1
        while (lo < hi - 1) {
            val mid = (lo + hi) / 2
            if (table[mid] <= arc) lo = mid else hi = mid
        }
        val segLen = table[hi] - table[lo]
        val frac = if (segLen > 0.0) (arc - table[lo]) / segLen else 0.0
        return lerp(polyline[lo], polyline[hi], frac)
    }

    /**
     * Arc-length (metres along [polyline]) of the point NEAREST to [station],
     * found by projecting the station onto each segment rather than snapping to
     * the nearest vertex. Sparse vertices on a winding coast made the vertex
     * snap map a station to a far vertex and push interpolated trains offshore;
     * segment projection keeps the arc on the real track. [table] must be
     * buildDistanceTable(polyline). Treated as locally planar (accurate at city
     * scale).
     */
    fun stationArc(polyline: List<LatLng>, table: DoubleArray, station: LatLng): Double {
        if (polyline.size < 2) return 0.0
        var bestArc = 0.0
        var bestDist = Double.MAX_VALUE
        for (i in 0 until polyline.size - 1) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val dy = b.lat - a.lat
            val dx = b.lng - a.lng
            val len2 = dy * dy + dx * dx
            val t = if (len2 > 0.0) {
                (((station.lat - a.lat) * dy + (station.lng - a.lng) * dx) / len2).coerceIn(0.0, 1.0)
            } else 0.0
            val d = haversineM(LatLng(a.lat + dy * t, a.lng + dx * t), station)
            if (d < bestDist) {
                bestDist = d
                bestArc = table[i] + (table[i + 1] - table[i]) * t
            }
        }
        return bestArc
    }

    /**
     * Position [fraction] of the way from [fromStation] to [toStation] ALONG the
     * line [polyline], so a vehicle rides the real track instead of a chord that
     * would cut across a bay. Falls back to the straight chord when there is no
     * usable geometry or the arc mapping overshoots both stations (mirrors the
     * web map's trainPosition guard).
     */
    fun positionBetween(
        polyline: List<LatLng>,
        table: DoubleArray,
        fromStation: LatLng,
        toStation: LatLng,
        fraction: Double,
    ): LatLng {
        val chord = lerp(fromStation, toStation, fraction)
        if (polyline.size < 2) return chord
        val arcFrom = stationArc(polyline, table, fromStation)
        val arcTo = stationArc(polyline, table, toStation)
        val p = pointAtArc(polyline, table, arcFrom + (arcTo - arcFrom) * fraction)
        val segLen = haversineM(fromStation, toStation)
        return if (haversineM(p, fromStation) <= segLen + 600.0 &&
            haversineM(p, toStation) <= segLen + 600.0
        ) p else chord
    }

    fun bearingDeg(from: LatLng, to: LatLng): Double {
        val dLng = toRadians(to.lng - from.lng)
        val lat1 = toRadians(from.lat)
        val lat2 = toRadians(to.lat)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun lowPassBearing(current: Double, target: Double, alpha: Double): Double {
        var diff = target - current
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360
        return (current + alpha * diff + 360.0) % 360.0
    }

    fun haversineM(a: LatLng, b: LatLng): Double {
        val dLat = toRadians(b.lat - a.lat)
        val dLng = toRadians(b.lng - a.lng)
        val lat1 = toRadians(a.lat)
        val lat2 = toRadians(b.lat)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return 2.0 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    private fun lerp(a: LatLng, b: LatLng, t: Double): LatLng {
        return LatLng(
            lat = a.lat + (b.lat - a.lat) * t,
            lng = a.lng + (b.lng - a.lng) * t,
        )
    }

    const val BEARING_ALPHA = 0.15
    const val STATION_PAUSE_MS = 300L
}
