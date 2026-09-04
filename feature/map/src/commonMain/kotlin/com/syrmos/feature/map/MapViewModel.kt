package com.syrmos.feature.map

import com.syrmos.core.common.map.LatLng
import com.syrmos.core.data.repository.LineGeometryRepositoryImpl
import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.data.repository.ScheduleRepositoryImpl
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.data.repository.TransitPatternRepositoryImpl
import com.syrmos.core.data.seed.SeedServicePattern
import com.syrmos.core.common.extensions.currentAthensDate
import com.syrmos.core.common.extensions.currentAthensDayOfWeek
import com.syrmos.core.common.extensions.currentAthensTime
import com.syrmos.core.common.extensions.parseTime
import com.syrmos.core.domain.schedule.ServiceDayResolver
import com.syrmos.core.domain.usecase.ComputeActiveTrainsFromBandsUseCase
import com.syrmos.core.domain.usecase.GetNextDeparturesUseCase
import com.syrmos.core.model.schedule.DayType
import com.syrmos.core.model.schedule.Frequency
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LiveSuburbanTrain
import com.syrmos.core.model.transit.SimulatedTrain
import com.syrmos.core.model.transit.Station
import com.syrmos.core.model.status.LiveVehicleState
import com.syrmos.core.model.alerts.AlertSeverity
import com.syrmos.core.data.sync.AnnouncementsRepository
import com.syrmos.core.data.sync.StationOffsetsRepository
import com.syrmos.core.network.OasaAirportBusService
import com.syrmos.core.network.RailwayGovLiveTrackerService
import com.syrmos.core.network.SyrmosLivePositionsService
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

data class StationDepartureUi(
    val line: Line,
    val destinationLabel: String,
    val time: String,
    val minutesAway: Int,
)

/**
 * A real-GPS live train ready to plot, tagged with its freshness so the renderer
 * can be honest about age. Only [LiveVehicleState.LIVE] and
 * [LiveVehicleState.STALE] markers are ever produced; an EXPIRED position is
 * dropped upstream (and its line handed back to the schedule projector), so a
 * dead / offline feed never leaves a frozen "live" dot on the map. [ageSeconds]
 * is `null` when the position carried no usable timestamp.
 */
data class LiveTrainMarker(
    val train: LiveSuburbanTrain,
    val state: LiveVehicleState,
    val ageSeconds: Long?,
)

data class MapUiState(
    val stations: List<Station> = emptyList(),
    val mapStations: List<MapStationNode> = emptyList(),
    val lines: List<Line> = emptyList(),
    val lineStations: Map<String, List<Station>> = emptyMap(),
    // The one polyline per line used to draw the route, snap vehicle markers and
    // interpolate simulated/projected vehicles, so all three always agree. See
    // effectiveLineGeometry (buses ride their stops, not incomplete OSM shapes).
    val effectiveGeometry: Map<String, List<LatLng>> = emptyMap(),
    val selectedStation: MapStationNode? = null,
    val selectedStationLines: List<Line> = emptyList(),
    val selectedStationDepartures: List<StationDepartureUi> = emptyList(),
    val liveTrains: List<LiveSuburbanTrain> = emptyList(),
    // The real-GPS trains actually plotted, tagged with per-vehicle freshness and
    // with EXPIRED positions already dropped. Recomputed every simulation tick so
    // a marker ages LIVE -> STALE -> gone against wall-clock even when the feed
    // stops emitting (offline). The renderer must use THIS, not [liveTrains], so
    // an aged position is never drawn as a plain live dot.
    val visibleLiveTrains: List<LiveTrainMarker> = emptyList(),
    val simulatedTrains: List<SimulatedTrain> = emptyList(),
    // Live airport express-bus positions (X93/95/96/97) from the OASA telematics
    // feed. Plotted on the map beside the trains; blanked by the vehicles toggle.
    val busVehicles: List<AirportBusVehicle> = emptyList(),
    val selectedTrain: LiveSuburbanTrain? = null,
    val selectedSimulatedTrain: SimulatedTrain? = null,
    val showTrains: Boolean = true,
    val showLiveTrainsSheet: Boolean = false,
    val isLoading: Boolean = true,
    val locateUserRequest: Long = 0L,
    val stationDisruptions: Map<String, AlertSeverity> = emptyMap(),
)

class MapViewModel(
    private val stationRepository: StationRepositoryImpl,
    private val lineRepository: LineRepositoryImpl,
    private val scheduleRepository: ScheduleRepositoryImpl,
    private val getNextDepartures: GetNextDeparturesUseCase,
    private val transitPatternRepository: TransitPatternRepositoryImpl,
    private val liveTrackerService: RailwayGovLiveTrackerService,
    private val livePositionsService: SyrmosLivePositionsService,
    private val airportBusService: OasaAirportBusService,
    private val stationOffsetsRepo: StationOffsetsRepository,
    private val scheduleSyncRepository: com.syrmos.core.data.sync.ScheduleSyncRepository,
    private val computeActiveTrains: ComputeActiveTrainsFromBandsUseCase,
    private val announcementsRepository: AnnouncementsRepository,
    private val lineGeometryRepository: LineGeometryRepositoryImpl,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private var livePositionsSnapshot: LivePositionsSnapshot? = null
    private var departureRefreshJob: Job? = null

    init {
        loadMapData()
        observeLiveTrains()
        pollLivePositions()
        pollAirportBuses()
        runTrainSimulation()
        observeStationDisruptions()
    }

    private fun observeStationDisruptions() {
        scope.launch {
            announcementsRepository.stationDisruptions.collect { disruptions ->
                _uiState.update { it.copy(stationDisruptions = disruptions) }
            }
        }
    }

    private fun loadMapData() {
        scope.launch {
            val lines = lineRepository.getAllLines().first()
            val stations = stationRepository.getAllStations().first()
            val mapStations = MapStationNode.fromStations(stations)

            val lineStations = mutableMapOf<String, List<Station>>()
            for (line in lines) {
                val ordered = stationRepository.getStationsOnLine(line.id).first()
                lineStations[line.id] = ordered
            }

            // One geometry per line, computed once, shared by the renderer and the
            // train simulator/projector so a bus is never drawn along its stops
            // while its vehicle rides an unrelated OSM shape.
            val effectiveGeometry = effectiveLineGeometry(
                lines = lines,
                lineStations = lineStations,
                osmShapes = lineGeometryRepository.getLineGeometry(),
            )

            _uiState.update {
                it.copy(
                    stations = stations,
                    mapStations = mapStations,
                    lines = lines,
                    lineStations = lineStations,
                    effectiveGeometry = effectiveGeometry,
                    isLoading = false,
                )
            }
        }
    }

    fun selectStation(stationId: String) {
        val state = _uiState.value
        val station = state.mapStations.find { it.id == stationId } ?: return
        val stationLines = state.lines.filter { it.id in station.lineIds }
        _uiState.update {
            it.copy(
                selectedStation = station,
                selectedStationLines = stationLines,
                selectedStationDepartures = emptyList(),
                selectedTrain = null,
                selectedSimulatedTrain = null,
            )
        }

        departureRefreshJob?.cancel()
        departureRefreshJob = scope.launch {
            while (isActive) {
                val departures = buildDeparturesForStation(
                    station = station,
                    stationLines = stationLines,
                    lineStations = state.lineStations,
                )
                _uiState.update { current ->
                    if (current.selectedStation?.id != stationId) current else current.copy(
                        selectedStationDepartures = departures,
                    )
                }
                delay(30_000)
            }
        }
    }

    fun requestLocateUser() {
        _uiState.update { it.copy(locateUserRequest = it.locateUserRequest + 1) }
    }

    fun toggleTrainVisibility() {
        _uiState.update { it.copy(showTrains = !it.showTrains) }
    }

    fun clearSelection() {
        departureRefreshJob?.cancel()
        departureRefreshJob = null
        _uiState.update {
            it.copy(
                selectedStation = null,
                selectedStationLines = emptyList(),
                selectedStationDepartures = emptyList(),
            )
        }
    }

    fun selectTrain(trainId: String) {
        val state = _uiState.value
        val liveTrain = state.liveTrains.find { it.id == trainId }
        val simulatedTrain = state.simulatedTrains.find { it.id == trainId }
        if (liveTrain == null && simulatedTrain == null) return
        clearSelection()
        _uiState.update {
            it.copy(
                selectedTrain = liveTrain,
                selectedSimulatedTrain = simulatedTrain,
            )
        }
    }

    fun clearTrainSelection() {
        _uiState.update {
            it.copy(
                selectedTrain = null,
                selectedSimulatedTrain = null,
            )
        }
    }

    fun toggleLiveTrainsSheet() {
        _uiState.update { it.copy(showLiveTrainsSheet = !it.showLiveTrainsSheet) }
    }

    fun flyToTrain(trainId: String) {
        selectTrain(trainId)
        _uiState.update { it.copy(showLiveTrainsSheet = false) }
    }

    private fun observeLiveTrains() {
        scope.launch {
            liveTrackerService.observeSuburbanTrains().collect { trains ->
                _uiState.update { state ->
                    val refreshedSelection = state.selectedTrain?.let { sel ->
                        trains.find { it.id == sel.id }
                    }
                    state.copy(
                        liveTrains = trains,
                        selectedTrain = refreshedSelection ?: state.selectedTrain,
                    )
                }
            }
        }
    }

    private fun runTrainSimulation() {
        scope.launch {
            while (isActive) {
                val state = _uiState.value
                // The exact polyline the route is drawn as, so a vehicle is placed
                // ALONG what the user sees (buses on their stops, not an incomplete
                // OSM bus shape). Empty until loadMapData populates it; the guard
                // below waits for lineStations, which is set in the same update.
                val geometry = state.effectiveGeometry
                if (state.lines.isNotEmpty() && state.lineStations.isNotEmpty()) {
                    val closedIds = state.stationDisruptions
                        .filterValues { it == AlertSeverity.CLOSURE }
                        .keys
                    val simulated = simulateTrains(
                        state.lines,
                        state.lineStations,
                        livePositionsSnapshot,
                        closedStationIds = closedIds,
                        geometry = geometry,
                    )
                    // Classify each real-GPS train by the age of its own position.
                    // A LIVE or STALE (recent-but-aged) train is plotted; an
                    // EXPIRED one is dropped so a dead/offline feed cannot leave a
                    // frozen dot pretending to be live. Recomputed every tick so a
                    // marker crosses LIVE -> STALE -> gone against wall-clock with
                    // no new data. Suburban A1-A4 dedupe: a STILL-TRACKED (LIVE or
                    // STALE) live train hides its line's projected dot; an EXPIRED
                    // ghost is NOT covered, so the projector fills back in once the
                    // feed goes stale - the offline fallback the frozen ghost used
                    // to block.
                    val classification = classifyLiveTrains(state.liveTrains, Clock.System.now())
                    val liveMarkers = classification.markers
                    val coveredByLive = classification.coveredLineIds
                    val filtered = simulated.filter { it.lineId !in coveredByLive }
                    // National rail + rail-replacement buses have no live feed or
                    // offsets, so project them from the bundled timetables.
                    val now = currentAthensTime()
                    val projected = projectScheduledTrains(
                        lines = state.lines,
                        lineStations = state.lineStations,
                        bundles = scheduleSyncRepository.lineBundles.value,
                        today = scheduleDayTypeString(),
                        nowMinutes = now.hour * 60 + now.minute + now.second / 60.0,
                        geometry = geometry,
                        // Athens ISO date so a date-scoped seasonal vehicle is
                        // projected only on the dates it runs.
                        todayIso = currentAthensDate().toString(),
                    ).filter { it.lineId !in coveredByLive }
                    val visibleTrains = filtered + projected
                    _uiState.update { current ->
                        current.copy(
                            simulatedTrains = visibleTrains,
                            visibleLiveTrains = liveMarkers,
                            selectedSimulatedTrain = current.selectedSimulatedTrain?.let { selected ->
                                visibleTrains.find { it.id == selected.id }
                            },
                        )
                    }
                }
                delay(1_000)
            }
        }
    }

    private fun pollAirportBuses() {
        scope.launch {
            while (isActive) {
                val vehicles = AirportBusVehicles.parse(airportBusService.fetchAirportBuses())
                _uiState.update { it.copy(busVehicles = vehicles) }
                delay(15_000)
            }
        }
    }

    private fun pollLivePositions() {
        scope.launch {
            val targetLines = listOf("M1", "M2", "M3", "M3_AIR", "T6", "T7", "A1", "A2", "A3", "A4")
            val offsetsResponse = livePositionsService.fetchStationOffsets()
            var offsetsMap = offsetsResponse?.lines
                ?.associate { (it.lineId to it.direction) to it.stops.sortedBy { s -> s.stopSequence } }
                .orEmpty()
            if (offsetsMap.isEmpty()) {
                offsetsMap = bundledOffsets()
            }
            while (isActive) {
                if (offsetsMap.isEmpty()) {
                    offsetsMap = bundledOffsets()
                }
                val active = livePositionsService.fetchActiveTrains(targetLines)
                if (active != null && active.trains.isNotEmpty()) {
                    val generatedAtEpoch = runCatching {
                        Instant.parse(active.generatedAt).epochSeconds
                    }.getOrElse {
                        kotlinx.datetime.Clock.System.now().epochSeconds
                    }
                    livePositionsSnapshot = LivePositionsSnapshot(
                        trains = active.trains,
                        offsets = offsetsMap,
                        generatedAtEpochSeconds = generatedAtEpoch,
                    )
                } else {
                    // Offline or empty live feed: project the metro/tram trains
                    // running right now from the bundled frequency bands so the
                    // map keeps showing moving dots. generatedAt = Athens now so
                    // TrainSimulator's origin-epoch recovery advances each dot by
                    // wall clock. When service is closed the projection is empty,
                    // which correctly clears the dots.
                    livePositionsSnapshot = LivePositionsSnapshot(
                        trains = computeActiveTrains.invoke(),
                        offsets = offsetsMap,
                        generatedAtEpochSeconds = kotlinx.datetime.Clock.System.now().epochSeconds,
                    )
                }
                delay(15_000)
            }
        }
    }

    private fun bundledOffsets(): Map<Pair<String, String>, List<SyrmosLivePositionsService.OffsetStop>> =
        convertBundledOffsets(stationOffsetsRepo.offsets.value)

    private suspend fun buildDeparturesForStation(
        station: MapStationNode,
        stationLines: List<Line>,
        lineStations: Map<String, List<Station>>,
    ): List<StationDepartureUi> {
        val departures = mutableListOf<StationDepartureUi>()

        stationLines.forEach { line ->
            val orderedStations = lineStations[line.id].orEmpty()
            val stationId = station.stationIdByLineId[line.id] ?: station.stationIds.firstOrNull() ?: return@forEach

            var hasBandResults = false
            Direction.entries.forEach { direction ->
                val liveDepartures = getNextDepartures.invoke(
                    stationId = stationId,
                    lineId = line.id,
                    direction = direction,
                    limit = 3,
                ).first()

                if (liveDepartures.isNotEmpty()) {
                    hasBandResults = true
                    liveDepartures.forEach { departure ->
                        departures += StationDepartureUi(
                            line = line,
                            destinationLabel = directionLabel(direction, orderedStations, line),
                            time = departure.time,
                            minutesAway = departure.minutesAway,
                        )
                    }
                }
            }
            if (hasBandResults) return@forEach

            val servicePatterns = transitPatternRepository.getPatternsFor(line.id, stationId)
            if (servicePatterns.isNotEmpty()) {
                departures += patternDepartures(line, servicePatterns)
                return@forEach
            }

            Direction.entries.forEach { direction ->
                departures += fallbackDepartures(stationId, line, direction, orderedStations)
            }
        }

        return departures
            .distinctBy { "${it.line.id}-${it.destinationLabel}-${it.time}" }
            .sortedBy { it.minutesAway }
            .take(8)
    }

    private fun patternDepartures(
        line: Line,
        patterns: List<SeedServicePattern>,
    ): List<StationDepartureUi> {
        val now = currentAthensTime()
        return patterns.flatMap { pattern ->
            (1..4).map { multiplier ->
                val minutesAway = pattern.frequencyMinutes * multiplier
                StationDepartureUi(
                    line = line,
                    destinationLabel = pattern.direction,
                    time = addMinutes(now.hour, now.minute, minutesAway),
                    minutesAway = minutesAway,
                )
            }
        }
    }

    private suspend fun fallbackDepartures(
        stationId: String,
        line: Line,
        direction: Direction,
        orderedStations: List<Station>,
    ): List<StationDepartureUi> {
        if (stationId !in orderedStations.map { it.id }) return emptyList()

        val dayType = resolveCurrentDayType()
        val frequencies = scheduleRepository.getFrequencies(line.id, dayType).first()
        val activeFrequency = frequencies.firstOrNull { it.matchesCurrentTime() } ?: frequencies.firstOrNull()
            ?: return emptyList()

        val now = currentAthensTime()
        return (1..2).map { multiplier ->
            val minutesAway = activeFrequency.frequencyMinutes * multiplier
            val departureTime = addMinutes(now.hour, now.minute, minutesAway)
            StationDepartureUi(
                line = line,
                destinationLabel = directionLabel(direction, orderedStations, line),
                time = departureTime,
                minutesAway = minutesAway,
            )
        }
    }

    private fun directionLabel(
        direction: Direction,
        orderedStations: List<Station>,
        line: Line,
    ): String {
        val fallback = when (direction) {
            Direction.OUTBOUND -> line.terminalB
            Direction.INBOUND -> line.terminalA
        }
        if (orderedStations.isEmpty()) return fallback
        return when (direction) {
            Direction.OUTBOUND -> orderedStations.lastOrNull()?.name ?: fallback
            Direction.INBOUND -> orderedStations.firstOrNull()?.name ?: fallback
        }
    }

    // Seed-DB fallback day type. Routes through the shared resolver so a public
    // holiday (e.g. Aug 15 on a weekday) queries the Sunday/Saturday service it
    // actually runs instead of weekday service.
    private fun resolveCurrentDayType(): DayType =
        ServiceDayResolver.baseDayType(currentAthensDate())

    /// The schedule day-type string the bundled national/bus trips are keyed by
    /// ("mon_thu" / "fri" / "sat" / "sun"), matching the web projector.
    private fun scheduleDayTypeString(): String = when (currentAthensDayOfWeek()) {
        DayOfWeek.FRIDAY -> "fri"
        DayOfWeek.SATURDAY -> "sat"
        DayOfWeek.SUNDAY -> "sun"
        else -> "mon_thu"
    }

    private fun Frequency.matchesCurrentTime(): Boolean {
        // Guard the split: a malformed timeRange with no "-" would throw on the
        // destructuring (before parseTime even runs). Treat it as no match.
        val parts = timeRange.split("-")
        if (parts.size != 2) return false
        val start = parts[0]
        val end = parts[1]
        val nowMinutes = currentAthensTime().hour * 60 + currentAthensTime().minute
        val startMinutes = parseTime(start).hour * 60 + parseTime(start).minute
        val endTime = parseTime(end)
        var endMinutes = endTime.hour * 60 + endTime.minute
        if (end.startsWith("00:") || endMinutes < startMinutes) {
            endMinutes += 24 * 60
        }
        val normalizedNow = if (nowMinutes < startMinutes && endMinutes > 24 * 60) nowMinutes + 24 * 60 else nowMinutes
        return normalizedNow in startMinutes..endMinutes
    }

    private fun addMinutes(hour: Int, minute: Int, delta: Int): String {
        val total = (hour * 60 + minute + delta) % (24 * 60)
        val nextHour = total / 60
        val nextMinute = total % 60
        return "${nextHour.toString().padStart(2, '0')}:${nextMinute.toString().padStart(2, '0')}"
    }
}

internal fun convertBundledOffsets(
    repoData: Map<String, List<com.syrmos.core.network.SyrmosSchedulesService.StationOffsetStop>>,
): Map<Pair<String, String>, List<SyrmosLivePositionsService.OffsetStop>> {
    if (repoData.isEmpty()) return emptyMap()
    return repoData.entries.associate { (key, stops) ->
        val parts = key.split("|", limit = 2)
        val lineId = parts[0]
        val direction = parts.getOrElse(1) { "outbound" }
        (lineId to direction) to stops.map {
            SyrmosLivePositionsService.OffsetStop(
                stationId = it.stationId,
                stationEn = it.stationEn,
                stopSequence = it.stopSequence,
                minutesFromOrigin = it.minutesFromOrigin,
            )
        }
    }
}
