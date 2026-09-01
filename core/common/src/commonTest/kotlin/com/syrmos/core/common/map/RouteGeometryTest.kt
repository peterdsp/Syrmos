package com.syrmos.core.common.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteGeometryTest {

    @Test
    fun equalTerminalsAreALoop() {
        assertTrue(RouteGeometry.isLoopTerminals("Kastelokampos", "Kastelokampos"))
        assertTrue(RouteGeometry.isLoopTerminals(" Kastelokampos ", "kastelokampos"))
    }

    @Test
    fun differentOrBlankTerminalsAreNotALoop() {
        assertFalse(RouteGeometry.isLoopTerminals("Kastelokampos", "Agios Vasileios"))
        assertFalse(RouteGeometry.isLoopTerminals("", ""))
        assertFalse(RouteGeometry.isLoopTerminals("  ", "  "))
    }

    @Test
    fun loopPolylineIsClosedByAppendingTheFirstPoint() {
        val pts = listOf(LatLng(38.29, 21.79), LatLng(38.30, 21.80), LatLng(38.31, 21.78))

        val closed = RouteGeometry.closeLoop(pts, isLoop = true)

        assertEquals(pts.size + 1, closed.size, "a loop must return to its origin")
        assertEquals(pts.first(), closed.last())
    }

    @Test
    fun nonLoopPolylineIsUnchanged() {
        val pts = listOf(LatLng(38.29, 21.79), LatLng(38.30, 21.80))
        assertEquals(pts, RouteGeometry.closeLoop(pts, isLoop = false))
    }

    @Test
    fun alreadyClosedLoopIsNotDoubleClosed() {
        val first = LatLng(38.29, 21.79)
        val pts = listOf(first, LatLng(38.30, 21.80), first)
        assertEquals(pts, RouteGeometry.closeLoop(pts, isLoop = true))
    }

    @Test
    fun degeneratePolylineIsUnchanged() {
        val one = listOf(LatLng(38.29, 21.79))
        assertEquals(one, RouteGeometry.closeLoop(one, isLoop = true))
        assertEquals(emptyList(), RouteGeometry.closeLoop(emptyList(), isLoop = true))
    }

    // --- displayShape: draw geometry == marker-snap geometry ---

    @Test
    fun busDisplayShapeIgnoresTheOsmShapeAndUsesStops() {
        // PU1 regression: the bundled shape omits the western stops, so a marker
        // snapped to it lands ~1km off route. displayShape must return the loop-
        // closed stops, not the shape, so draw and snap both use the stops.
        val paKst = LatLng(38.2896, 21.771523)
        val puOae = LatLng(38.2810, 21.782000)
        val stops = listOf(paKst, LatLng(38.30, 21.79), puOae)
        val incompleteOsm = listOf(LatLng(38.29, 21.7836343), LatLng(38.29, 21.7945585))

        val shape = RouteGeometry.displayShape(
            isBus = true, isLoop = true, stations = stops, osmShape = incompleteOsm,
        )!!

        assertEquals(paKst, shape.first(), "bus geometry must start at the first real stop, not the OSM shape")
        assertEquals(paKst, shape.last(), "a loop bus must close back to its origin stop")
        assertEquals(stops.size + 1, shape.size)
        assertTrue(incompleteOsm.none { it in shape }, "the incomplete OSM shape must not leak into bus geometry")
    }

    @Test
    fun nonLoopBusDisplayShapeIsItsStraightStops() {
        val stops = listOf(LatLng(40.56, 22.96), LatLng(40.52, 22.97))
        assertEquals(
            stops,
            RouteGeometry.displayShape(isBus = true, isLoop = false, stations = stops, osmShape = null),
        )
    }

    @Test
    fun railDisplayShapePrefersTheOsmShape() {
        val stops = listOf(LatLng(37.94, 23.64), LatLng(37.99, 23.74))
        val osm = listOf(LatLng(37.94, 23.64), LatLng(37.96, 23.69), LatLng(37.99, 23.74))
        assertEquals(
            osm,
            RouteGeometry.displayShape(isBus = false, isLoop = false, stations = stops, osmShape = osm),
        )
    }

    @Test
    fun railWithoutShapeReturnsNullSoCallerCanSpline() {
        val stops = listOf(LatLng(37.94, 23.64), LatLng(37.99, 23.74))
        assertEquals(null, RouteGeometry.displayShape(isBus = false, isLoop = false, stations = stops, osmShape = null))
    }

    @Test
    fun busWithFewerThanTwoStopsHasNoDisplayShape() {
        assertEquals(
            null,
            RouteGeometry.displayShape(isBus = true, isLoop = false, stations = listOf(LatLng(1.0, 2.0)), osmShape = null),
        )
    }
}
