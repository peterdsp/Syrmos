package com.syrmos.feature.map

import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.data.repository.ScheduleRepositoryImpl
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.data.repository.TransitPatternRepositoryImpl
import com.syrmos.core.data.seed.SeedServicePattern
import com.syrmos.core.common.extensions.currentAthensDayOfWeek
import com.syrmos.core.common.extensions.currentAthensTime
import com.syrmos.core.common.extensions.parseTime
import com.syrmos.core.domain.usecase.GetNextDeparturesUseCase
import com.syrmos.core.model.schedule.DayType
import com.syrmos.core.model.schedule.Frequency
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LiveSuburbanTrain
import com.syrmos.core.model.transit.SimulatedTrain
import com.syrmos.core.model.transit.Station
import com.syrmos.core.network.RailwayGovLiveTrackerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

data class MapUiState(
    val stations: List<Station> = emptyList(),
    val mapStations: List<MapStationNode> = emptyList(),
    val lines: List<Line> = emptyList(),
    val lineStations: Map<String, List<Station>> = emptyMap(),
    val selectedStation: MapStationNode? = null,
    val selectedStationLines: List<Line> = emptyList(),
    val selectedStationDepartures: List<StationDepartureUi> = emptyList(),
    val liveTrains: List<LiveSuburbanTrain> = emptyList(),
    val simulatedTrains: List<SimulatedTrain> = emptyList(),
    val isLoading: Boolean = true,
)

class MapViewModel(
    private val stationRepository: StationRepositoryImpl,
    private val lineRepository: LineRepositoryImpl,
    private val scheduleRepository: ScheduleRepositoryImpl,
    private val getNextDepartures: GetNextDeparturesUseCase,
    private val transitPatternRepository: TransitPatternRepositoryImpl,
    private val liveTrackerService: RailwayGovLiveTrackerService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        loadMapData()
        observeLiveTrains()
        runTrainSimulation()
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

            _uiState.update {
                it.copy(
                    stations = stations,
                    mapStations = mapStations,
                    lines = lines,
                    lineStations = lineStations,
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
            )
        }

        scope.launch {
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
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedStation = null,
                selectedStationLines = emptyList(),
                selectedStationDepartures = emptyList(),
            )
        }
    }

    private fun observeLiveTrains() {
        scope.launch {
            liveTrackerService.observeSuburbanTrains(setOf("A1", "A2", "A3", "A4")).collect { trains ->
                _uiState.update { it.copy(liveTrains = trains) }
            }
        }
    }

    private fun runTrainSimulation() {
        scope.launch {
            while (isActive) {
                val state = _uiState.value
                if (state.lines.isNotEmpty() && state.lineStations.isNotEmpty()) {
                    val simulated = simulateTrains(state.lines, state.lineStations)
                    _uiState.update { it.copy(simulatedTrains = simulated) }
                }
                delay(10_000)
            }
        }
    }

    private suspend fun buildDeparturesForStation(
        station: MapStationNode,
        stationLines: List<Line>,
        lineStations: Map<String, List<Station>>,
    ): List<StationDepartureUi> {
        val departures = mutableListOf<StationDepartureUi>()

        stationLines.forEach { line ->
            val orderedStations = lineStations[line.id].orEmpty()
            val stationId = station.stationIdByLineId[line.id] ?: station.stationIds.firstOrNull() ?: return@forEach
            val servicePatterns = transitPatternRepository.getPatternsFor(line.id, stationId)

            if (servicePatterns.isNotEmpty()) {
                departures += patternDepartures(line, servicePatterns)
                return@forEach
            }

            Direction.entries.forEach { direction ->
                val liveDepartures = getNextDepartures.invoke(
                    stationId = stationId,
                    lineId = line.id,
                    direction = direction,
                    limit = 3,
                ).first()

                if (liveDepartures.isNotEmpty()) {
                    liveDepartures.forEach { departure ->
                        departures += StationDepartureUi(
                            line = line,
                            destinationLabel = directionLabel(direction, orderedStations, line),
                            time = departure.time,
                            minutesAway = departure.minutesAway,
                        )
                    }
                } else {
                    departures += fallbackDepartures(stationId, line, direction, orderedStations)
                }
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

    private fun resolveCurrentDayType(): DayType {
        return when (currentAthensDayOfWeek()) {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY -> DayType.WEEKDAY
            DayOfWeek.FRIDAY -> DayType.FRIDAY
            DayOfWeek.SATURDAY -> DayType.SATURDAY
            DayOfWeek.SUNDAY -> DayType.SUNDAY
            else -> DayType.WEEKDAY
        }
    }

    private fun Frequency.matchesCurrentTime(): Boolean {
        val (start, end) = timeRange.split("-")
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
