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
    // When set (opened as a transfer from another hub), departures are scoped to
    // this one line, so the screen shows that line's timetable at this station.
    val focusLineId: String? = null,
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
    private var loadedFocusLineId: String? = null
    private var loadJob: Job? = null
    private var refreshJob: Job? = null

    fun loadStation(stationId: String, focusLineId: String? = null) {
        if (stationId == loadedStationId && focusLineId == loadedFocusLineId) return
        loadedStationId = stationId
        loadedFocusLineId = focusLineId
        // Cancel the previous load AND poller, and clear station-scoped state
        // synchronously, so an in-flight load for the old station can neither
        // write its departures/transfers under the new one nor (out of order)
        // restart its poller. Every post-suspension write below is also guarded
        // by loadedStationId as belt-and-suspenders.
        loadJob?.cancel()
        refreshJob?.cancel()
        // Reset EVERY station-derived field, not just departures: the detail
        // lookup suspends and the screen does not gate on isLoading, so keeping
        // the old name/coords/lines/isSuburban would render the previous
        // station's map, alerts and ticket action under the new route.
        _uiState.update {
            it.copy(
                stationName = "",
                stationNameEl = "",
                latitude = 0.0,
                longitude = 0.0,
                connectingLines = emptyList(),
                lineIds = emptyList(),
                isInterchange = false,
                isSuburban = false,
                departures = emptyList(),
                hasLoadedDepartures = false,
                interchangeTargets = emptyList(),
                focusLineId = focusLineId,
                isLoading = true,
            )
        }
        loadJob = scope.launch {
            val detail = getStationDetail.invoke(stationId).first()
            if (loadedStationId != stationId || loadedFocusLineId != focusLineId) return@launch
            if (detail == null) {
                // Reset loadedStationId so the same station can be retried;
                // otherwise a null detail left the screen spinning forever.
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
                    lineIds = detail.station.lineIds,
                    isInterchange = detail.station.isInterchange,
                    isSuburban = detail.station.lineIds.any { id -> id in suburbanIds },
                    isLoading = false,
                )
            }

            // Start the departures poller immediately; it must not wait on the
            // proximity resolver, which runs in parallel and is only cosmetic.
            // When opened as a transfer, scope departures to the tapped line so
            // the screen shows THAT line's timetable at this stop, not every
            // line's departures aggregated (which could crowd it out).
            val departureLineIds = focusLineId?.let { listOf(it) } ?: detail.station.lineIds
            startRefreshLoop(stationId, focusLineId, departureLineIds)

            launch {
                // Interchange is cosmetic: a resolver DB/resource failure must
                // degrade to empty targets, never crash station detail as an
                // uncaught coroutine exception.
                try {
                    val targets = getInterchangeTargets.invoke(detail.station)
                    if (loadedStationId == stationId && loadedFocusLineId == focusLineId) {
                        _uiState.update { it.copy(interchangeTargets = targets) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Leave interchangeTargets empty; the rest of the screen is fine.
                }
            }
        }
    }

    /**
     * Polls [getStationDepartures] every 15 seconds so the "5 min / 10 min"
     * countdowns tick down live while the screen is visible. Cancelling the
     * previous job avoids duplicate timers if a user navigates between
     * stations quickly.
     */
    private fun startRefreshLoop(stationId: String, focusLineId: String?, lineIds: List<String>) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (isActive) {
                try {
                    val departures = getStationDepartures.invoke(stationId, lineIds)
                    // Guard: never write departures if the user has already moved to
                    // another station OR changed the focused line (belt-and-suspenders
                    // over the cancel).
                    if (loadedStationId == stationId && loadedFocusLineId == focusLineId) {
                        _uiState.update { it.copy(departures = departures, hasLoadedDepartures = true) }
                    }
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
        loadJob?.cancel()
        loadJob = null
        refreshJob?.cancel()
        refreshJob = null
    }

    private companion object {
        private const val REFRESH_INTERVAL_MS = 15_000L
    }
}
