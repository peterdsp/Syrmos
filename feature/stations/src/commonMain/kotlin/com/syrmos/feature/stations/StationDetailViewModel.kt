package com.syrmos.feature.stations

import com.syrmos.core.domain.interchange.InterchangeTarget
import com.syrmos.core.domain.usecase.GetInterchangeTargetsUseCase
import com.syrmos.core.domain.usecase.GetNextDeparturesUseCase
import com.syrmos.core.domain.usecase.GetStationDetailUseCase
import com.syrmos.core.domain.usecase.GetStationDeparturesUseCase
import com.syrmos.core.domain.usecase.UpcomingDeparture
import com.syrmos.core.model.transit.Line
import kotlinx.coroutines.CancellationException
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
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val connectingLines: List<Line> = emptyList(),
    // Actionable transfers: the operational, scheduled lines serving this hub by
    // proximity, each with the station id to open on that line (nearest first).
    val interchangeTargets: List<InterchangeTarget> = emptyList(),
    val lineIds: List<String> = emptyList(),
    val isInterchange: Boolean = false,
    val isSuburban: Boolean = false,
    val departures: List<UpcomingDeparture> = emptyList(),
    val isLoading: Boolean = true,
    val hasLoadedDepartures: Boolean = false,
)

class StationDetailViewModel(
    private val getStationDetail: GetStationDetailUseCase,
    private val getStationDepartures: GetStationDeparturesUseCase,
    private val getInterchangeTargets: GetInterchangeTargetsUseCase,
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
            val detail = getStationDetail.invoke(stationId).first()
            if (detail == null) {
                // Clear the spinner and reset loadedStationId so the same station
                // can be retried; otherwise a null detail (unknown id, data gap)
                // left the screen spinning forever with the guard at loadStation's
                // top blocking any re-attempt.
                _uiState.update { it.copy(isLoading = false) }
                loadedStationId = null
                return@launch
            }

            val suburbanIds = setOf("A1", "A2", "A3", "A4")
            _uiState.update {
                it.copy(
                    stationName = detail.station.name,
                    stationNameEl = detail.station.nameEl,
                    latitude = detail.station.latitude,
                    longitude = detail.station.longitude,
                    connectingLines = detail.connectingLines,
                    // Cleared here, then filled by the proximity resolver below, so
                    // a previous station's transfers never flash on the new screen.
                    interchangeTargets = emptyList(),
                    lineIds = detail.station.lineIds,
                    isInterchange = detail.station.isInterchange,
                    isSuburban = detail.station.lineIds.any { id -> id in suburbanIds },
                    isLoading = false,
                )
            }

            // Proximity transfers are static for the session, so resolve once per
            // load (not in the 15s departures loop). Guard against a race where the
            // user has already navigated to another station.
            val targets = getInterchangeTargets.invoke(detail.station)
            if (loadedStationId == stationId) {
                _uiState.update { it.copy(interchangeTargets = targets) }
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
                try {
                    val departures = getStationDepartures.invoke(stationId, lineIds)
                    _uiState.update { it.copy(departures = departures, hasLoadedDepartures = true) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Transient failure on one tick: keep the last departures and
                    // retry next interval rather than killing the loop permanently
                    // (which froze the countdowns and could crash on Android).
                }
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
