package com.syrmos.core.common

import com.syrmos.core.common.map.LatLng
import com.syrmos.core.common.map.VehicleInterpolation
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VehicleInterpolationTest {

    @Test
    fun trainGlideEaseEndpoints() {
        assertEquals(0.0, VehicleInterpolation.trainGlideEase(0.0), 1e-6)
        assertEquals(1.0, VehicleInterpolation.trainGlideEase(1.0), 1e-6)
    }

    @Test
    fun trainGlideEaseIsMonotonic() {
        var prev = 0.0
        for (i in 1..100) {
            val t = i / 100.0
            val v = VehicleInterpolation.trainGlideEase(t)
            assertTrue(v >= prev - 1e-9, "easing must be monotonic at t=$t")
            prev = v
        }
    }

    @Test
    fun buildDistanceTableAndPointAtArc() {
        val poly = listOf(LatLng(37.97, 23.72), LatLng(37.98, 23.73), LatLng(37.99, 23.74))
        val table = VehicleInterpolation.buildDistanceTable(poly)
        assertEquals(3, table.size)
        assertEquals(0.0, table[0])
        assertTrue(table[1] > 0)
        assertTrue(table[2] > table[1])

        val start = VehicleInterpolation.pointAtArc(poly, table, 0.0)
        assertEquals(poly.first().lat, start.lat, 1e-9)
        assertEquals(poly.first().lng, start.lng, 1e-9)

        val end = VehicleInterpolation.pointAtArc(poly, table, table.last())
        assertEquals(poly.last().lat, end.lat, 1e-9)
        assertEquals(poly.last().lng, end.lng, 1e-9)

        val mid = VehicleInterpolation.pointAtArc(poly, table, table[1])
        assertEquals(poly[1].lat, mid.lat, 1e-6)
        assertEquals(poly[1].lng, mid.lng, 1e-6)
    }

    @Test
    fun bearingNorthIsZero() {
        val from = LatLng(37.97, 23.73)
        val to = LatLng(37.98, 23.73)
        val b = VehicleInterpolation.bearingDeg(from, to)
        assertTrue(abs(b) < 1.0 || abs(b - 360) < 1.0, "north bearing should be ~0, got $b")
    }

    @Test
    fun lowPassBearingWraps() {
        val result = VehicleInterpolation.lowPassBearing(350.0, 10.0, 0.5)
        assertTrue(result in 355.0..365.0 || result in 0.0..5.0, "should wrap around 360, got $result")
    }

    @Test
    fun haversineSanity() {
        val a = LatLng(37.97, 23.72)
        val b = LatLng(37.98, 23.73)
        val dist = VehicleInterpolation.haversineM(a, b)
        assertTrue(dist in 1000.0..2000.0, "two close points ~1.3km apart, got $dist")
    }
}
