package com.syrmos.feature.stations

import com.syrmos.core.domain.usecase.GetNextDeparturesUseCase
import com.syrmos.core.domain.usecase.GetStationDetailUseCase
import com.syrmos.core.domain.usecase.GetStationDeparturesUseCase
import com.syrmos.core.domain.usecase.UpcomingDeparture
import com.syrmos.core.model.transit.Line
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

data class StationDetailUiState(
    val stationName: String = "",
    val stationNameEl: String = "",
    val connectingLines: List<Line> = emptyList(),
    val departures: List<UpcomingDeparture> = emptyList(),
    val isLoading: Boolean = true,
)

class StationDetailViewModel(
    private val getStationDetail: GetStationDetailUseCase,
    private val getStationDepartures: GetStationDeparturesUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(StationDetailUiState())
    val uiState: StateFlow<StationDetailUiState> = _uiState.asStateFlow()

    private var loadedStationId: String? = null
    private var refreshJob: Job? = null

    fun loadStation(stationId: String) {
        if (stationId == loadedStationId) return
        loadedStationId = stationId
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val detail = getStationDetail.invoke(stationId).first() ?: return@launch

            _uiState.update {
                it.copy(
                    stationName = detail.station.name,
                    stationNameEl = detail.station.nameEl,
                    connectingLines = detail.connectingLines,
                    isLoading = false,
                )
            }

            startRefreshLoop(stationId, detail.station.lineIds)
        }
    }

    /**
     * Polls [getStationDepartures] every 15 seconds so the "5 min / 10 min"
     * countdowns tick down live while the screen is visible. Cancelling the
     * previous job avoids duplicate timers if a user navigates between
     * stations quickly.
     */
    private fun startRefreshLoop(stationId: String, lineIds: List<String>) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (isActive) {
                val departures = getStationDepartures.invoke(stationId, lineIds)
                _uiState.update { it.copy(departures = departures) }
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun dispose() {
        refreshJob?.cancel()
        refreshJob = null
    }

    private companion object {
        private const val REFRESH_INTERVAL_MS = 15_000L
    }
}
