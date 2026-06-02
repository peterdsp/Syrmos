package com.syrmos.feature.stations

import com.syrmos.core.domain.usecase.GetNextDeparturesUseCase
import com.syrmos.core.domain.usecase.GetStationDetailUseCase
import com.syrmos.core.domain.usecase.UpcomingDeparture
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Line
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StationDetailUiState(
    val stationName: String = "",
    val stationNameEl: String = "",
    val connectingLines: List<Line> = emptyList(),
    val departures: List<UpcomingDeparture> = emptyList(),
    val isLoading: Boolean = true,
)

class StationDetailViewModel(
    private val stationId: String,
    private val getStationDetail: GetStationDetailUseCase,
    private val getNextDepartures: GetNextDeparturesUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(StationDetailUiState())
    val uiState: StateFlow<StationDetailUiState> = _uiState.asStateFlow()

    init {
        loadStation()
    }

    private fun loadStation() {
        scope.launch {
            val detail = getStationDetail.invoke(stationId).first() ?: return@launch

            _uiState.update {
                it.copy(
                    stationName = detail.station.name,
                    stationNameEl = detail.station.nameEl,
                    connectingLines = detail.connectingLines,
                    isLoading = false,
                )
            }

            val allDepartures = mutableListOf<UpcomingDeparture>()
            detail.station.lineIds.forEach { lineId ->
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
            _uiState.update {
                it.copy(departures = allDepartures.sortedBy { d -> d.minutesAway }.take(8))
            }
        }
    }
}
