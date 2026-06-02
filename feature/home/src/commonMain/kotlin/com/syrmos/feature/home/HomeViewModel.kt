package com.syrmos.feature.home

import com.syrmos.core.domain.usecase.FindNearestStationUseCase
import com.syrmos.core.domain.usecase.GetNextDeparturesUseCase
import com.syrmos.core.domain.usecase.UpcomingDeparture
import com.syrmos.core.model.location.NearestStationResult
import com.syrmos.core.model.location.UserLocation
import com.syrmos.core.model.transit.Direction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val nearestStations: List<NearestStationResult> = emptyList(),
    val upcomingDepartures: List<UpcomingDeparture> = emptyList(),
    val selectedStationId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(
    private val findNearestStation: FindNearestStationUseCase,
    private val getNextDepartures: GetNextDeparturesUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun onLocationUpdate(latitude: Double, longitude: Double) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val location = UserLocation(latitude, longitude)
            val nearest = findNearestStation.invoke(location, limit = 3).first()
            _uiState.update { it.copy(nearestStations = nearest, isLoading = false) }

            if (nearest.isNotEmpty()) {
                loadDeparturesForStation(nearest.first().stationId, nearest.first().lineIds)
            }
        }
    }

    fun onStationSelected(stationId: String, lineIds: List<String>) {
        scope.launch {
            _uiState.update { it.copy(selectedStationId = stationId) }
            loadDeparturesForStation(stationId, lineIds)
        }
    }

    private suspend fun loadDeparturesForStation(stationId: String, lineIds: List<String>) {
        val allDepartures = mutableListOf<UpcomingDeparture>()
        lineIds.forEach { lineId ->
            Direction.entries.forEach { direction ->
                val departures = getNextDepartures.invoke(
                    stationId = stationId,
                    lineId = lineId,
                    direction = direction,
                    limit = 2,
                ).first()
                allDepartures.addAll(departures)
            }
        }
        val sorted = allDepartures.sortedBy { it.minutesAway }.take(6)
        _uiState.update { it.copy(upcomingDepartures = sorted) }
    }
}
