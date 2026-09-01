package com.syrmos.feature.map

import com.syrmos.core.common.map.LatLng
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineColor
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.Station
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * effectiveLineGeometry is the single polyline the map draws, snaps markers to,
 * AND projects vehicles along. These tests pin the per-type resolution and, in
 * particular, that a bus rides its stops rather than an incomplete OSM shape:
 * the PU1 divergence where a projected vehicle rode the discarded shape and
 * landed a kilometre off its drawn route.
 */
class MapRouteGeometryTest {

    private fun line(
        id: String,
        type: LineType,
        terminalA: String = "A",
        terminalB: String = "B",
    ) = Line(
        id = id, name = id, nameEl = id, type = type, color = LineColor.SUBURBAN_PURPLE,
        terminalA = terminalA, terminalB = terminalB, stationCount = 0,
    )

    private fun station(id: String, lat: Double, lng: Double, lineId: String) =
        Station(id = id, name = id, nameEl = id, latitude = lat, longitude = lng, lineIds = listOf(lineId))

    @Test
    fun busRidesItsStopsNotTheIncompleteOsmShape() {
        val pu1 = line("PU1", LineType.BUS, terminalA = "Kastelokampos", terminalB = "Kastelokampos")
        val stops = listOf(
            station("PA_KST", 38.291426, 21.771523, "PU1"),
            station("PU_UNR", 38.288, 21.777, "PU1"),
            station("PU_OAE", 38.2810, 21.782000, "PU1"),
        )
        // The real bundled PU1 shape omits the western stops.
        val osm = mapOf("PU1" to listOf(LatLng(38.2877553, 21.7836374), LatLng(38.29, 21.7945585)))

        val geom = effectiveLineGeometry(listOf(pu1), mapOf("PU1" to stops), osm).getValue("PU1")

        assertEquals(LatLng(38.291426, 21.771523), geom.first(), "a bus must start at its first real stop")
        assertEquals(geom.first(), geom.last(), "a loop bus must return to its origin")
        assertEquals(stops.size + 1, geom.size)
        assertFalse(osm.getValue("PU1").any { it in geom }, "the incomplete OSM shape must not leak into a bus route")
    }

    @Test
    fun nonLoopBusIsItsStraightStops() {
        val vl1 = line("VL1", LineType.BUS, terminalA = "Volos", terminalB = "Larisa")
        val stops = listOf(station("VL_A", 39.36, 22.93, "VL1"), station("VL_B", 39.63, 22.41, "VL1"))

        val geom = effectiveLineGeometry(listOf(vl1), mapOf("VL1" to stops), emptyMap()).getValue("VL1")

        assertEquals(listOf(LatLng(39.36, 22.93), LatLng(39.63, 22.41)), geom)
    }

    @Test
    fun railWithAnOsmShapeKeepsThatShape() {
        val m3 = line("M3", LineType.METRO)
        val stops = listOf(station("M3_A", 37.94, 23.64, "M3"), station("M3_B", 37.99, 23.74, "M3"))
        val osm = mapOf("M3" to listOf(LatLng(37.94, 23.64), LatLng(37.96, 23.69), LatLng(37.99, 23.74)))

        val geom = effectiveLineGeometry(listOf(m3), mapOf("M3" to stops), osm).getValue("M3")

        assertEquals(osm.getValue("M3"), geom, "rail must follow the real OSM track")
    }

    @Test
    fun railWithoutAShapeIsSplinedThroughStops() {
        val a1 = line("A1", LineType.SUBURBAN)
        val stops = listOf(
            station("A1_A", 37.90, 23.60, "A1"),
            station("A1_B", 37.95, 23.70, "A1"),
            station("A1_C", 38.00, 23.80, "A1"),
        )

        val geom = effectiveLineGeometry(listOf(a1), mapOf("A1" to stops), emptyMap()).getValue("A1")

        assertTrue(geom.size > stops.size, "a shapeless rail line is smoothed with extra spline points")
        assertEquals(LatLng(37.90, 23.60), geom.first(), "the spline must start at the first stop")
        assertEquals(LatLng(38.00, 23.80), geom.last(), "the spline must end at the last stop")
    }

    @Test
    fun aLineWithFewerThanTwoUsablePointsIsDropped() {
        val stub = line("STUB", LineType.BUS)
        val stops = mapOf("STUB" to listOf(station("ONLY", 37.9, 23.7, "STUB")))

        assertFalse("STUB" in effectiveLineGeometry(listOf(stub), stops, emptyMap()))
    }

    @Test
    fun catmullRomLeavesFewerThanThreePointsUntouched() {
        val two = listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0))
        assertEquals(two, catmullRomSpline(two))
    }

    @Test
    fun catmullRomKeepsEndpointsAndDensifies() {
        val pts = listOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0), LatLng(2.0, 0.0))
        val spline = catmullRomSpline(pts, segments = 5)
        assertEquals(pts.first(), spline.first())
        assertEquals(pts.last(), spline.last())
        assertTrue(spline.size > pts.size)
    }

    @Test
    fun snapPullsAnOffRouteFixOntoThePolyline() {
        // A live GPS fix beside a straight west-east leg snaps onto the leg.
        val leg = listOf(LatLng(37.0, 23.0), LatLng(37.0, 23.10))
        val snapped = snapToPolyline(37.02, 23.05, leg)
        assertEquals(37.0, snapped.lat, 1e-9, "snapped point sits on the leg's latitude")
        assertTrue(snapped.lng in 23.0..23.10, "snapped point stays within the leg")
    }

    @Test
    fun snapReturnsInputWhenPolylineTooShort() {
        assertEquals(LatLng(37.02, 23.05), snapToPolyline(37.02, 23.05, listOf(LatLng(37.0, 23.0))))
        assertEquals(LatLng(37.02, 23.05), snapToPolyline(37.02, 23.05, emptyList()))
    }
}
