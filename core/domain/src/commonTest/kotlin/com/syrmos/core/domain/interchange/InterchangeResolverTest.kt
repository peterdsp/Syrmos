package com.syrmos.core.domain.interchange

import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineColor
import com.syrmos.core.model.transit.LineStatus
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.Station
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proximity interchange resolution, ported from iOS. A transfer is any line
 * with a stop within 150 m of the hub, resolved to THAT line's own nearest
 * station id (never the hub's stored lineIds), operational and scheduled only,
 * nearest first.
 */
class InterchangeResolverTest {

    private fun line(id: String, status: LineStatus = LineStatus.OPERATIONAL) = Line(
        id = id, name = id, nameEl = id, type = LineType.METRO, color = LineColor.BLUE,
        terminalA = "a", terminalB = "b", stationCount = 0, status = status,
    )

    private fun station(id: String, lat: Double, lng: Double, lineId: String) =
        Station(id = id, name = id, nameEl = id, latitude = lat, longitude = lng, lineIds = listOf(lineId))

    // Hub near Syntagma. 0.00025 deg lon ~= 22 m; 0.0005 deg lat ~= 55 m;
    // 0.003 deg lat ~= 333 m (all at ~38 N).
    private val hubLat = 37.9838
    private val hubLng = 23.7280

    private val lines = listOf(
        line("M1"),                              // current line -> excluded
        line("M2"),                              // 22 m -> nearest, included
        line("M3"),                              // 55 m -> included, after M2
        line("DK1", LineStatus.SUSPENDED),       // 9 m but not operational -> excluded
        line("X3"),                              // 9 m but no schedule -> excluded
        line("A9"),                              // 333 m -> too far, excluded
    )

    private val stationsByLine = mapOf(
        "M1" to listOf(station("M1_SYN", 37.9838, 23.7280, "M1")),
        "M2" to listOf(station("M2_FAR", 37.9900, 23.7400, "M2"), station("M2_SYN", 37.9838, 23.72825, "M2")),
        "M3" to listOf(station("M3_SYN", 37.9843, 23.7280, "M3")),
        "DK1" to listOf(station("DK1_SYN", 37.9838, 23.72809, "DK1")),
        "X3" to listOf(station("X3_SYN", 37.9838, 23.72791, "X3")),
        "A9" to listOf(station("A9_FAR", 37.9868, 23.7280, "A9")),
    )

    private val hasSchedule: (String) -> Boolean = { it != "X3" }

    @Test
    fun returnsOnlyOperationalScheduledNearbyLinesNearestFirst() {
        val targets = InterchangeResolver.resolve(
            hubLatitude = hubLat,
            hubLongitude = hubLng,
            currentLineId = "M1",
            lines = lines,
            stationsByLine = stationsByLine,
            hasSchedule = hasSchedule,
        )
        assertEquals(listOf("M2", "M3"), targets.map { it.line.id }, "M2 (22m) before M3 (55m); others excluded")
    }

    @Test
    fun resolvesEachLinesOwnNearestStationId() {
        val targets = InterchangeResolver.resolve(
            hubLat, hubLng, "M1", lines, stationsByLine, hasSchedule,
        )
        // Not the hub's id, not M2's far stop: each line's own nearest stop.
        assertEquals(mapOf("M2" to "M2_SYN", "M3" to "M3_SYN"), targets.associate { it.line.id to it.stationId })
    }

    @Test
    fun currentLineIsNeverATarget() {
        val targets = InterchangeResolver.resolve(
            hubLat, hubLng, currentLineId = "M2", lines = lines,
            stationsByLine = stationsByLine, hasSchedule = hasSchedule,
        )
        assertEquals(false, targets.any { it.line.id == "M2" })
    }

    @Test
    fun emptyWhenNoLineHasANearbyScheduledStop() {
        val targets = InterchangeResolver.resolve(
            hubLatitude = 40.0, hubLongitude = 22.0, // far from every stop
            currentLineId = "M1", lines = lines,
            stationsByLine = stationsByLine, hasSchedule = hasSchedule,
        )
        assertEquals(emptyList(), targets)
    }
}
