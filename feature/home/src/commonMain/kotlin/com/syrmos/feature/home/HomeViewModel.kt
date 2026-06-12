package com.syrmos.feature.home

import com.syrmos.core.domain.usecase.FindNearestStationUseCase
import com.syrmos.core.domain.usecase.GetLinesUseCase
import com.syrmos.core.domain.usecase.GetNextDeparturesUseCase
import com.syrmos.core.domain.usecase.UpcomingDeparture
import com.syrmos.core.model.location.NearestStationResult
import com.syrmos.core.model.location.UserLocation
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LiveSuburbanTrain
import com.syrmos.core.network.STASYAnnouncement
import com.syrmos.core.network.STASYAnnouncementService
import com.syrmos.core.network.STASYServiceStatus
import com.syrmos.core.network.RailwayGovLiveTrackerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val nearestStations: List<NearestStationResult> = emptyList(),
    val upcomingDepartures: List<UpcomingDeparture> = emptyList(),
    val liveTrains: List<LiveSuburbanTrain> = emptyList(),
    val selectedStationId: String? = null,
    val lines: List<Line> = emptyList(),
    val announcements: List<STASYAnnouncement> = emptyList(),
    val serviceStatus: STASYServiceStatus? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(
    private val findNearestStation: FindNearestStationUseCase,
    private val getNextDepartures: GetNextDeparturesUseCase,
    private val getLinesUseCase: GetLinesUseCase,
    private val stasyService: STASYAnnouncementService,
    private val liveTrackerService: RailwayGovLiveTrackerService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadLines()
        loadAnnouncements()
        observeLiveTrains()
    }

    private fun loadLines() {
        scope.launch {
            getLinesUseCase.getAllLines()
                .catch { /* ignore */ }
                .collect { lines ->
                    _uiState.update { it.copy(lines = lines) }
                }
        }
    }

    private fun loadAnnouncements() {
        scope.launch {
            stasyService.fetchFeed()
                .catch { /* ignore */ }
                .collect { feed ->
                    _uiState.update {
                        it.copy(
                            announcements = feed.announcements,
                            serviceStatus = feed.status,
                        )
                    }
                }
        }
    }

    private fun observeLiveTrains() {
        scope.launch {
            liveTrackerService.observeSuburbanTrains(setOf("A1", "A2", "A3", "A4"))
                .catch { /* ignore */ }
                .collect { trains ->
                    _uiState.update { it.copy(liveTrains = trains) }
                }
        }
    }

    fun refreshAnnouncements() {
        loadAnnouncements()
    }

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
