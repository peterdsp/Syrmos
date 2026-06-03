package com.syrmos.feature.map

import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapUiState(
    val stations: List<Station> = emptyList(),
    val lines: List<Line> = emptyList(),
    val lineStations: Map<String, List<Station>> = emptyMap(),
    val selectedStation: Station? = null,
    val selectedStationLines: List<Line> = emptyList(),
    val isLoading: Boolean = true,
)

class MapViewModel(
    private val stationRepository: StationRepositoryImpl,
    private val lineRepository: LineRepositoryImpl,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        loadMapData()
    }

    private fun loadMapData() {
        scope.launch {
            val lines = lineRepository.getAllLines().first()
            val stations = stationRepository.getAllStations().first()

            val lineStations = mutableMapOf<String, List<Station>>()
            for (line in lines) {
                val ordered = stationRepository.getStationsOnLine(line.id).first()
                lineStations[line.id] = ordered
            }

            _uiState.update {
                it.copy(
                    stations = stations,
                    lines = lines,
                    lineStations = lineStations,
                    isLoading = false,
                )
            }
        }
    }

    fun selectStation(stationId: String) {
        val state = _uiState.value
        val station = state.stations.find { it.id == stationId } ?: return
        val stationLines = state.lines.filter { it.id in station.lineIds }
        _uiState.update {
            it.copy(
                selectedStation = station,
                selectedStationLines = stationLines,
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedStation = null,
                selectedStationLines = emptyList(),
            )
        }
    }
}
