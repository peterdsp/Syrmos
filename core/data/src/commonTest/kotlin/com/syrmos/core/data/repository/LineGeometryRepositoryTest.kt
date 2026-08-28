package com.syrmos.core.data.repository

import com.syrmos.core.common.map.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [LineGeometryRepositoryImpl.parseShapes] — the pure parse step that
 * turns the bundled `schedules-v2/shapes.json` into per-line (lat,lng) polylines
 * fed to the on-device train simulator.
 */
class LineGeometryRepositoryTest {

    @Test
    fun parsesRealShapesJsonFormatIntoPolylines() {
        // Mirrors the actual seed shape: extra keys (osmRelationId/from/to/points)
        // are present and must be ignored; only `coordinates` matters on device.
        val json = """
            {"version":1,"source":"OSM (ODbL)",
             "shapes":{
               "M1":{"osmRelationId":445858,"from":"Πειραιάς","to":"Κηφισιά","points":3,
                     "coordinates":[[38.073395,23.80822],[38.072895,23.808264],[38.072661,23.80825]]},
               "T6":{"coordinates":[[37.90,23.70],[37.91,23.71]]}
             }}
        """.trimIndent()

        val geom = LineGeometryRepositoryImpl.parseShapes(json)

        assertEquals(setOf("M1", "T6"), geom.keys)
        assertEquals(3, geom.getValue("M1").size)
        assertEquals(LatLng(38.073395, 23.80822), geom.getValue("M1").first())
        assertEquals(LatLng(38.072661, 23.80825), geom.getValue("M1").last())
        assertEquals(LatLng(37.91, 23.71), geom.getValue("T6").last())
    }

    @Test
    fun dropsMalformedCoordinatePairsButKeepsValidOnes() {
        // [37.95] has <2 numbers -> dropped; [38.0,23.8,999] keeps the first two.
        val json = """{"shapes":{"M2":{"coordinates":[[37.9,23.7],[37.95],[38.0,23.8,999]]}}}"""

        val geom = LineGeometryRepositoryImpl.parseShapes(json)

        assertEquals(2, geom.getValue("M2").size)
        assertEquals(LatLng(37.9, 23.7), geom.getValue("M2")[0])
        assertEquals(LatLng(38.0, 23.8), geom.getValue("M2")[1])
    }

    @Test
    fun omitsLinesWhosePolylineEndsUpEmpty() {
        val json = """{"shapes":{"EMPTY":{"coordinates":[]},"OK":{"coordinates":[[1.0,2.0]]}}}"""

        val geom = LineGeometryRepositoryImpl.parseShapes(json)

        assertEquals(setOf("OK"), geom.keys, "a line with no usable points must be omitted")
    }

    @Test
    fun ignoresUnknownTopLevelKeysAndEmptyShapes() {
        val geom = LineGeometryRepositoryImpl.parseShapes(
            """{"version":2,"source":"x","shapes":{}}""",
        )
        assertTrue(geom.isEmpty())
    }
}
