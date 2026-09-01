package com.syrmos.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the live-positions / station-offsets decode
 * resilience (audit #25). The feed is decoded row by row so a single
 * malformed vehicle or offset line is skipped instead of throwing and
 * nulling the whole response, which used to drop the entire live map
 * silently back to the schedule simulator. Mirrors the iOS resilience
 * fixes for the trains feed (#22) and announcements (#24).
 */
class SyrmosLivePositionsDecodeTest {

    @Test
    fun validPayloadDecodesEveryTrain() {
        val body = """
            {
              "generatedAt": "2026-09-01T10:00:00Z",
              "lineIds": ["M3", "T6"],
              "trains": [
                {"lineId":"M3","directionKey":"outbound","originDepartureMinute":0.0,"elapsedMinutes":5.0,"totalTravelMinutes":20,"serviceType":"regular"},
                {"lineId":"T6","directionKey":"inbound","originDepartureMinute":8.0,"elapsedMinutes":3.0,"totalTravelMinutes":40,"serviceType":"regular"}
              ]
            }
        """.trimIndent()
        val result = SyrmosLivePositionsService.parseLivePositions(body)
        assertEquals("2026-09-01T10:00:00Z", result.generatedAt)
        assertEquals(listOf("M3", "T6"), result.lineIds)
        assertEquals(2, result.trains.size)
    }

    @Test
    fun oneWrongTypedTrainRowDoesNotVoidTheRest() {
        // The middle row has a string where totalTravelMinutes must be an Int.
        // It must be skipped, leaving the two good rows intact - the old
        // atomic decode returned null (empty live map) for the whole payload.
        val body = """
            {
              "trains": [
                {"lineId":"M3","directionKey":"outbound","originDepartureMinute":0.0,"elapsedMinutes":5.0,"totalTravelMinutes":20,"serviceType":"regular"},
                {"lineId":"M2","directionKey":"outbound","originDepartureMinute":0.0,"elapsedMinutes":5.0,"totalTravelMinutes":"oops","serviceType":"regular"},
                {"lineId":"T6","directionKey":"inbound","originDepartureMinute":0.0,"elapsedMinutes":5.0,"totalTravelMinutes":40,"serviceType":"regular"}
              ]
            }
        """.trimIndent()
        val result = SyrmosLivePositionsService.parseLivePositions(body)
        assertEquals(listOf("M3", "T6"), result.trains.map { it.lineId })
    }

    @Test
    fun trainRowMissingOptionalFieldStillDecodes() {
        // serviceType omitted -> default "regular", row survives.
        val body = """
            {"trains":[{"lineId":"M3","directionKey":"outbound","originDepartureMinute":0.0,"elapsedMinutes":5.0,"totalTravelMinutes":20}]}
        """.trimIndent()
        val result = SyrmosLivePositionsService.parseLivePositions(body)
        assertEquals(1, result.trains.size)
        assertEquals("regular", result.trains.first().serviceType)
    }

    @Test
    fun trainRowMissingLineOrDirectionIsDropped() {
        // A row that decodes but has no line/direction can't be placed on the
        // map, so it is filtered out (not kept as a zero-valued ghost).
        val body = """
            {"trains":[
              {"directionKey":"outbound","originDepartureMinute":0.0,"elapsedMinutes":5.0,"totalTravelMinutes":20,"serviceType":"regular"},
              {"lineId":"M3","originDepartureMinute":0.0,"elapsedMinutes":5.0,"totalTravelMinutes":20,"serviceType":"regular"},
              {"lineId":"M3","directionKey":"outbound","originDepartureMinute":0.0,"elapsedMinutes":5.0,"totalTravelMinutes":20,"serviceType":"regular"}
            ]}
        """.trimIndent()
        val result = SyrmosLivePositionsService.parseLivePositions(body)
        assertEquals(1, result.trains.size, "Only the row with both line and direction is usable")
    }

    @Test
    fun emptyTrainsPayloadDecodesToEmpty() {
        val result = SyrmosLivePositionsService.parseLivePositions("""{"trains":[]}""")
        assertTrue(result.trains.isEmpty())
    }

    @Test
    fun oneMalformedOffsetLineDoesNotVoidTheTable() {
        // The M2 line entry has a stop whose stopSequence is a non-numeric
        // string; that line is dropped but M3's offsets survive.
        val body = """
            {
              "updatedAt": "2026-09-01T10:00:00Z",
              "source": "pi",
              "lines": [
                {"lineId":"M3","direction":"outbound","stops":[{"stationId":"M3_A","stopSequence":1,"minutesFromOrigin":0}]},
                {"lineId":"M2","direction":"outbound","stops":[{"stationId":"M2_A","stopSequence":"x","minutesFromOrigin":0}]}
              ]
            }
        """.trimIndent()
        val result = SyrmosLivePositionsService.parseStationOffsets(body)
        assertEquals(listOf("M3"), result.lines.map { it.lineId })
        assertEquals("pi", result.source)
    }

    @Test
    fun offsetLineMissingIdentityIsDropped() {
        val body = """
            {"lines":[
              {"direction":"outbound","stops":[]},
              {"lineId":"M3","direction":"outbound","stops":[{"stationId":"M3_A","stopSequence":1,"minutesFromOrigin":0}]}
            ]}
        """.trimIndent()
        val result = SyrmosLivePositionsService.parseStationOffsets(body)
        assertEquals(listOf("M3"), result.lines.map { it.lineId })
    }
}
