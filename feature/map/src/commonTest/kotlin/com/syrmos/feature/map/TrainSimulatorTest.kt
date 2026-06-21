package com.syrmos.feature.map

import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineColor
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.Station
import com.syrmos.core.network.SyrmosLivePositionsService
import kotlinx.datetime.Clock
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Regression tests for `simulateTrains()`. Pins the contracts that broke
 * iOS train rendering in v1.0.3:
 *
 *  - Empty snapshot or empty trains list MUST return empty list, not crash.
 *  - Missing offsets for a (lineId, direction) skip just that train.
 *  - SUBURBAN lines are excluded (rendered separately as `liveTrains`).
 *  - M3_AIR remaps to M3 polyline + offsets.
 *  - Position interpolates correctly along the segment based on
 *    elapsedMinutes vs the segment's minutesFromOrigin range.
 *  - Trains outside the [0, totalTravelMinutes + 0.5] window are dropped.
 */
class TrainSimulatorTest {

    private val m3Line = Line(
        id = "M3",
        name = "Line 3",
        nameEl = "Γραμμή 3",
        type = LineType.METRO,
        color = LineColor.BLUE,
        terminalA = "Dimotiko Theatro",
        terminalB = "Doukissis Plakentias",
        stationCount = 24,
    )

    private val t6Line = Line(
        id = "T6",
        name = "Tram T6",
        nameEl = "Τραμ T6",
        type = LineType.TRAM,
        color = LineColor.TRAM_ORANGE,
        terminalA = "Syntagma",
        terminalB = "Pikrodafni",
        stationCount = 19,
    )

    private val suburbanLine = Line(
        id = "A1",
        name = "Suburban A1",
        nameEl = "Προαστιακός A1",
        type = LineType.SUBURBAN,
        color = LineColor.SUBURBAN_PURPLE,
        terminalA = "Piraeus",
        terminalB = "Kiato",
        stationCount = 30,
    )

    private val stationA = Station(
        id = "M3_A",
        name = "Station A",
        nameEl = "Α",
        latitude = 37.9800,
        longitude = 23.7300,
        lineIds = listOf("M3"),
    )
    private val stationB = Station(
        id = "M3_B",
        name = "Station B",
        nameEl = "Β",
        latitude = 37.9900,
        longitude = 23.7400,
        lineIds = listOf("M3"),
    )
    private val stationC = Station(
        id = "M3_C",
        name = "Station C",
        nameEl = "Γ",
        latitude = 38.0000,
        longitude = 23.7500,
        lineIds = listOf("M3"),
    )

    private val m3Stops = listOf(
        SyrmosLivePositionsService.OffsetStop(
            stationId = "M3_A",
            stationEn = "Station A",
            stopSequence = 1,
            minutesFromOrigin = 0,
        ),
        SyrmosLivePositionsService.OffsetStop(
            stationId = "M3_B",
            stationEn = "Station B",
            stopSequence = 2,
            minutesFromOrigin = 10,
        ),
        SyrmosLivePositionsService.OffsetStop(
            stationId = "M3_C",
            stationEn = "Station C",
            stopSequence = 3,
            minutesFromOrigin = 20,
        ),
    )

    private val m3LineStations = mapOf("M3" to listOf(stationA, stationB, stationC))

    private fun snapshotWith(
        trains: List<SyrmosLivePositionsService.LiveTrain>,
        offsetsKey: Pair<String, String> = "M3" to "outbound",
        stops: List<SyrmosLivePositionsService.OffsetStop> = m3Stops,
        generatedAtEpochSeconds: Long = Clock.System.now().epochSeconds,
    ) = LivePositionsSnapshot(
        trains = trains,
        offsets = mapOf(offsetsKey to stops),
        generatedAtEpochSeconds = generatedAtEpochSeconds,
    )

    @Test
    fun returnsEmptyWhenSnapshotIsNull() {
        val trains = simulateTrains(
            lines = listOf(m3Line),
            lineStations = m3LineStations,
            snapshot = null,
        )
        assertTrue(trains.isEmpty())
    }

    @Test
    fun returnsEmptyWhenTrainsListIsEmpty() {
        val trains = simulateTrains(
            lines = listOf(m3Line),
            lineStations = m3LineStations,
            snapshot = snapshotWith(trains = emptyList()),
        )
        assertTrue(trains.isEmpty())
    }

    @Test
    fun skipsTrainWhenOffsetsMissingForItsLineDirection() {
        val now = Clock.System.now().epochSeconds
        val train = SyrmosLivePositionsService.LiveTrain(
            lineId = "M3",
            directionKey = "inbound",
            originDepartureMinute = 0.0,
            elapsedMinutes = 5.0,
            totalTravelMinutes = 20,
            serviceType = "regular",
        )
        val trains = simulateTrains(
            lines = listOf(m3Line),
            lineStations = m3LineStations,
            snapshot = snapshotWith(
                trains = listOf(train),
                offsetsKey = "M3" to "outbound",
                generatedAtEpochSeconds = now,
            ),
        )
        assertTrue(trains.isEmpty(), "Train without matching offsets must be skipped")
    }

    @Test
    fun projectsSuburbanTrainsWhenOffsetsAvailable() {
        // After 2026-06-21: suburban A1/A2/A3/A4 are projected the same way
        // as metro/tram when offsets are present, so the dots keep moving
        // offline. The railway.gov.gr live feed (when reachable) wins via
        // the MapViewModel's per-line dedupe, NOT inside simulateTrains.
        val now = Clock.System.now().epochSeconds
        val train = SyrmosLivePositionsService.LiveTrain(
            lineId = "A1",
            directionKey = "outbound",
            originDepartureMinute = 0.0,
            elapsedMinutes = 5.0,
            totalTravelMinutes = 60,
            serviceType = "regular",
        )
        val suburbanStops = listOf(
            SyrmosLivePositionsService.OffsetStop("A1_X", stopSequence = 1, minutesFromOrigin = 0),
            SyrmosLivePositionsService.OffsetStop("A1_Y", stopSequence = 2, minutesFromOrigin = 30),
        )
        val xStation = Station("A1_X", "X", "X", 37.9, 23.7, listOf("A1"))
        val yStation = Station("A1_Y", "Y", "Y", 38.0, 23.8, listOf("A1"))
        val trains = simulateTrains(
            lines = listOf(suburbanLine),
            lineStations = mapOf("A1" to listOf(xStation, yStation)),
            snapshot = snapshotWith(
                trains = listOf(train),
                offsetsKey = "A1" to "outbound",
                stops = suburbanStops,
                generatedAtEpochSeconds = now,
            ),
        )
        assertEquals(1, trains.size, "Suburban A1 train with offsets must be projected")
        assertEquals("A1", trains.first().lineId)
    }

    @Test
    fun interpolatesPositionAtMidpointOfSegment() {
        val now = Clock.System.now().epochSeconds
        // elapsedMinutes = 5 -> halfway between Station A (0min) and Station B (10min)
        val train = SyrmosLivePositionsService.LiveTrain(
            lineId = "M3",
            directionKey = "outbound",
            originDepartureMinute = 0.0,
            elapsedMinutes = 5.0,
            totalTravelMinutes = 20,
            serviceType = "regular",
        )
        val result = simulateTrains(
            lines = listOf(m3Line),
            lineStations = m3LineStations,
            snapshot = snapshotWith(trains = listOf(train), generatedAtEpochSeconds = now),
        )
        assertEquals(1, result.size, "One M3 train must yield one SimulatedTrain")
        val sim = result.first()
        val expectedLat = (stationA.latitude + stationB.latitude) / 2.0
        val expectedLon = (stationA.longitude + stationB.longitude) / 2.0
        assertTrue(abs(sim.latitude - expectedLat) < 0.01, "lat ${sim.latitude} should be near $expectedLat")
        assertTrue(abs(sim.longitude - expectedLon) < 0.01, "lon ${sim.longitude} should be near $expectedLon")
        assertEquals("Station A", sim.currentStationName)
        assertEquals("Station B", sim.nextStationName)
        assertEquals(LineType.METRO, sim.lineType)
    }

    @Test
    fun remapsM3AirToM3PolylineAndOffsets() {
        val now = Clock.System.now().epochSeconds
        val airTrain = SyrmosLivePositionsService.LiveTrain(
            lineId = "M3_AIR",
            directionKey = "outbound",
            originDepartureMinute = 0.0,
            elapsedMinutes = 5.0,
            totalTravelMinutes = 20,
            serviceType = "airport",
        )
        val result = simulateTrains(
            lines = listOf(m3Line),
            lineStations = m3LineStations,
            snapshot = snapshotWith(
                trains = listOf(airTrain),
                offsetsKey = "M3" to "outbound",
                generatedAtEpochSeconds = now,
            ),
        )
        assertEquals(1, result.size)
        val sim = result.first()
        assertTrue(sim.isAirportService, "M3_AIR train must keep isAirportService = true")
        assertEquals("M3", sim.lineId, "Display lineId must collapse to M3")
    }

    @Test
    fun dropsTrainWhoseElapsedExceedsTotalTravel() {
        val now = Clock.System.now().epochSeconds
        // elapsedMinutes (25) > totalTravelMinutes (20) + 0.5 -> dropped
        val staleTrain = SyrmosLivePositionsService.LiveTrain(
            lineId = "M3",
            directionKey = "outbound",
            originDepartureMinute = 0.0,
            elapsedMinutes = 25.0,
            totalTravelMinutes = 20,
            serviceType = "regular",
        )
        val result = simulateTrains(
            lines = listOf(m3Line),
            lineStations = m3LineStations,
            snapshot = snapshotWith(trains = listOf(staleTrain), generatedAtEpochSeconds = now),
        )
        assertTrue(result.isEmpty(), "Train past terminal must be dropped")
    }

    @Test
    fun acceptsMultipleTrainsOnSameLine() {
        val now = Clock.System.now().epochSeconds
        val trainA = SyrmosLivePositionsService.LiveTrain(
            lineId = "M3",
            directionKey = "outbound",
            originDepartureMinute = 0.0,
            elapsedMinutes = 3.0,
            totalTravelMinutes = 20,
            serviceType = "regular",
        )
        val trainB = SyrmosLivePositionsService.LiveTrain(
            lineId = "M3",
            directionKey = "outbound",
            originDepartureMinute = 8.0,
            elapsedMinutes = 12.0,
            totalTravelMinutes = 20,
            serviceType = "regular",
        )
        val result = simulateTrains(
            lines = listOf(m3Line),
            lineStations = m3LineStations,
            snapshot = snapshotWith(trains = listOf(trainA, trainB), generatedAtEpochSeconds = now),
        )
        assertEquals(2, result.size, "Both trains must appear; IDs are disambiguated by originDepartureMinute")
        assertNotNull(result.firstOrNull { it.id.contains("_0") })
        assertNotNull(result.firstOrNull { it.id.contains("_8") })
    }
}
