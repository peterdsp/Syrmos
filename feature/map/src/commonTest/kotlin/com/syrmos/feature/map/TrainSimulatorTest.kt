package com.syrmos.feature.map

import com.syrmos.core.common.map.LatLng
import com.syrmos.core.common.map.VehicleInterpolation
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineColor
import com.syrmos.core.model.transit.LineStatus
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.Station
import com.syrmos.core.network.SyrmosLivePositionsService
import com.syrmos.core.network.SyrmosSchedulesService
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
        val xStation = Station(id = "A1_X", name = "X", nameEl = "X", latitude = 37.9, longitude = 23.7, lineIds = listOf("A1"))
        val yStation = Station(id = "A1_Y", name = "Y", nameEl = "Y", latitude = 38.0, longitude = 23.8, lineIds = listOf("A1"))
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

    // --- status honouring -------------------------------------------------
    //
    // A line that is built but not open (Thessaloniki Line 2 until the Kalamaria
    // extension opens) still renders on the map, greyed, because the track is
    // real. It must never carry a moving train, because the service does not
    // exist. Everything else about the train is valid here: the ONLY reason it
    // must be dropped is the line's status.

    private val underConstructionLine = m3Line.copy(
        id = "TM2",
        status = LineStatus.UNDER_CONSTRUCTION,
    )

    @Test
    fun dropsTrainOnUnderConstructionLine() {
        val now = Clock.System.now().epochSeconds
        val train = SyrmosLivePositionsService.LiveTrain(
            lineId = "TM2",
            directionKey = "outbound",
            originDepartureMinute = 0.0,
            elapsedMinutes = 5.0,
            totalTravelMinutes = 20,
            serviceType = "regular",
        )
        val result = simulateTrains(
            lines = listOf(underConstructionLine),
            lineStations = mapOf("TM2" to m3LineStations.getValue("M3")),
            snapshot = snapshotWith(
                trains = listOf(train),
                offsetsKey = "TM2" to "outbound",
                generatedAtEpochSeconds = now,
            ),
        )
        assertTrue(
            result.isEmpty(),
            "An under-construction line must never carry a train, even when the " +
                "feed reports one and its offsets resolve",
        )
    }

    @Test
    fun stillRunsTrainsOnOperationalLines() {
        // The status filter must not be over-eager: the same train on an
        // operational line still simulates.
        val now = Clock.System.now().epochSeconds
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
        assertEquals(1, result.size, "An operational line must still yield trains")
    }

    // --- geometry wiring --------------------------------------------------
    //
    // When per-line track geometry is supplied, a simulated train must ride the
    // real polyline instead of the straight chord between its two stations (the
    // chord cuts across bays and put coastal trains in the sea). The arc math
    // itself is pinned in VehicleInterpolationTest; THIS test proves the
    // `geometry` argument is actually threaded through simulateTrains().

    @Test
    fun placesTrainOnLineGeometryNotChordWhenGeometryProvided() {
        val now = Clock.System.now().epochSeconds
        // elapsedMinutes 5 -> halfway between Station A (0min) and Station B (10min).
        val train = SyrmosLivePositionsService.LiveTrain(
            lineId = "M3",
            directionKey = "outbound",
            originDepartureMinute = 0.0,
            elapsedMinutes = 5.0,
            totalTravelMinutes = 20,
            serviceType = "regular",
        )
        // L-shaped M3 track: east along lat 37.98 to a corner, then north to B.
        // The straight chord A->B would cut the corner; arc-following rides it.
        val m3Polyline = listOf(
            LatLng(37.98, 23.73),   // Station A (polyline start)
            LatLng(37.98, 23.75),   // corner
            LatLng(37.99, 23.74),   // Station B (polyline end)
        )
        val snapshot = snapshotWith(trains = listOf(train), generatedAtEpochSeconds = now)

        val chord = simulateTrains(listOf(m3Line), m3LineStations, snapshot).first()
        val onTrack = simulateTrains(
            listOf(m3Line),
            m3LineStations,
            snapshot,
            geometry = mapOf("M3" to m3Polyline),
        ).first()

        // 1) Geometry must MOVE the train off the chord — this is the wiring proof.
        val delta = VehicleInterpolation.haversineM(
            LatLng(chord.latitude, chord.longitude),
            LatLng(onTrack.latitude, onTrack.longitude),
        )
        assertTrue(delta > 200.0, "geometry must shift the train off the chord, got ${delta}m")

        // 2) The geometry position must sit ON the polyline, not float beside it.
        val table = VehicleInterpolation.buildDistanceTable(m3Polyline)
        val nearest = VehicleInterpolation.pointAtArc(
            m3Polyline,
            table,
            VehicleInterpolation.stationArc(m3Polyline, table, LatLng(onTrack.latitude, onTrack.longitude)),
        )
        val offTrack = VehicleInterpolation.haversineM(
            LatLng(onTrack.latitude, onTrack.longitude),
            nearest,
        )
        assertTrue(offTrack < 50.0, "geometry train must lie on the track, off by ${offTrack}m")
    }

    // --- validDates (date-scoped seasonal trips) --------------------------
    //
    // A trip may carry a comma-separated list of ISO dates it actually runs on
    // (seasonal services like the Pelion railway PL1). projectScheduledTrains
    // must hide its vehicle on any other date, matching the offline
    // getNextDepartures SQL filter and the iOS ScheduleProjector. An
    // unrestricted trip (null/blank validDates) always runs, and an unknown
    // todayIso ("") disables the filter so a missing date never blanks the map.

    private val pelionLine = Line(
        id = "PL1",
        name = "Pelion Railway",
        nameEl = "Τρενάκι Πηλίου",
        type = LineType.SUBURBAN,
        color = LineColor.SUBURBAN_PURPLE,
        terminalA = "Ano Lechonia",
        terminalB = "Milies",
        stationCount = 2,
    )
    private val pelionStationA = Station(
        id = "PL1_A", name = "Ano Lechonia", nameEl = "Άνω Λεχώνια",
        latitude = 39.33, longitude = 23.03, lineIds = listOf("PL1"),
    )
    private val pelionStationB = Station(
        id = "PL1_B", name = "Milies", nameEl = "Μηλιές",
        latitude = 39.33, longitude = 23.15, lineIds = listOf("PL1"),
    )
    private val pelionStations = mapOf("PL1" to listOf(pelionStationA, pelionStationB))

    // dayType is left empty so the trip runs on every weekday, isolating the
    // validDates branch as the only thing that can drop it.
    private fun pelionBundle(validDates: String?) = mapOf(
        "PL1" to SyrmosSchedulesService.LineSchedule(
            lineId = "PL1",
            trips = listOf(
                SyrmosSchedulesService.TripEntry(
                    trainNo = "1",
                    dayType = "",
                    direction = "outbound",
                    validDates = validDates,
                    stops = listOf(
                        SyrmosSchedulesService.TripStop("PL1_A", 1, "10:00"),
                        SyrmosSchedulesService.TripStop("PL1_B", 2, "10:20"),
                    ),
                ),
            ),
        ),
    )

    // 10:10 Athens, squarely inside the 10:00 -> 10:20 trip window.
    private val pelionNowMinutes = 10.0 * 60 + 10

    @Test
    fun projectsDatedTripOnItsValidDate() {
        val result = projectScheduledTrains(
            lines = listOf(pelionLine),
            lineStations = pelionStations,
            bundles = pelionBundle(validDates = "2026-09-01"),
            today = "",
            nowMinutes = pelionNowMinutes,
            todayIso = "2026-09-01",
        )
        assertEquals(1, result.size, "Dated trip must project on a date listed in validDates")
        assertEquals("PL1", result.first().lineId)
    }

    @Test
    fun hidesDatedTripOffItsValidDate() {
        val result = projectScheduledTrains(
            lines = listOf(pelionLine),
            lineStations = pelionStations,
            bundles = pelionBundle(validDates = "2026-09-01,2026-09-08"),
            today = "",
            nowMinutes = pelionNowMinutes,
            todayIso = "2026-09-02",
        )
        assertTrue(result.isEmpty(), "Dated trip must be hidden on a date absent from validDates")
    }

    @Test
    fun datedTripMatchesWholeTokenNotSubstring() {
        // "2026-09-01" must not match by being a substring of a longer token.
        // Comma-boundary matching (mirrored in the SQL) rejects it.
        val result = projectScheduledTrains(
            lines = listOf(pelionLine),
            lineStations = pelionStations,
            bundles = pelionBundle(validDates = "2026-09-011,2026-09-08"),
            today = "",
            nowMinutes = pelionNowMinutes,
            todayIso = "2026-09-01",
        )
        assertTrue(result.isEmpty(), "todayIso must match a whole date token, never a substring")
    }

    @Test
    fun unrestrictedTripRunsOnAnyDate() {
        val result = projectScheduledTrains(
            lines = listOf(pelionLine),
            lineStations = pelionStations,
            bundles = pelionBundle(validDates = null),
            today = "",
            nowMinutes = pelionNowMinutes,
            todayIso = "2026-09-02",
        )
        assertEquals(1, result.size, "A trip with no validDates must run on every matching day")
    }

    @Test
    fun unknownTodayIsoDoesNotHideDatedTrip() {
        // todayIso is empty when the date is unknown; the filter must be a no-op
        // then so we never blank the map for a missing date.
        val result = projectScheduledTrains(
            lines = listOf(pelionLine),
            lineStations = pelionStations,
            bundles = pelionBundle(validDates = "2026-09-01"),
            today = "",
            nowMinutes = pelionNowMinutes,
            todayIso = "",
        )
        assertEquals(1, result.size, "Empty todayIso must disable the validDates filter")
    }
}
