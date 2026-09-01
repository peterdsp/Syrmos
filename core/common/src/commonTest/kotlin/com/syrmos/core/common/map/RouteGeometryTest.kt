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
}
