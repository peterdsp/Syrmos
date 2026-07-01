package com.syrmos.feature.home

import com.syrmos.core.common.DataFreshness
import com.syrmos.core.common.LiveDataFreshness
import com.syrmos.core.data.sync.AnnouncementsRepository
import com.syrmos.core.data.sync.WeatherRepository
import com.syrmos.core.model.weather.WeatherSnapshot
import com.syrmos.core.domain.usecase.FindNearestStationUseCase
import com.syrmos.core.domain.usecase.GetLastTrainUseCase
import com.syrmos.core.domain.usecase.GetLinesUseCase
import com.syrmos.core.domain.usecase.GetNextDeparturesUseCase
import com.syrmos.core.domain.usecase.UpcomingDeparture
import com.syrmos.core.model.location.NearestStationResult
import com.syrmos.core.model.location.UserLocation
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LiveSuburbanTrain
import com.syrmos.core.network.STASYAnnouncement
import com.syrmos.core.network.STASYServiceStatus
import com.syrmos.core.network.RailwayGovLiveTrackerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    /** The single soonest departure from the nearest station: the answer-first hero. */
    val nextDeparture: UpcomingDeparture? = null,
    /** Resolved line for [nextDeparture], for its colour and destination terminal. */
    val nextDepartureLine: Line? = null,
    /** Tonight's final train on the nearest station's primary line. */
    val lastTrain: GetLastTrainUseCase.LastTrain? = null,
    /** Line for [lastTrain]. */
    val lastTrainLine: Line? = null,
    /** Whether arrivals are live or predicted from the bundled schedule. */
    val freshness: DataFreshness = DataFreshness.PREDICTED,
    /** Current weather for the travel-context card; null until first fetch. */
    val weather: WeatherSnapshot? = null,
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
    private val getLastTrain: GetLastTrainUseCase,
    private val getLinesUseCase: GetLinesUseCase,
    private val announcementsRepository: AnnouncementsRepository,
    private val liveTrackerService: RailwayGovLiveTrackerService,
    private val weatherRepository: WeatherRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadLines()
        observeAnnouncements()
        refreshAnnouncements()
        observeLiveTrains()
        observeFreshness()
        observeWeather()
    }

    private fun observeWeather() {
        scope.launch {
            weatherRepository.snapshot.collect { snap ->
                _uiState.update { it.copy(weather = snap) }
            }
        }
        // Athens default so Web and no-location launches still show weather.
        scope.launch { runCatching { weatherRepository.refresh() } }
    }

    /**
     * Keeps the offline-alive pill honest. The flow emits immediately whenever
     * a live fetch lands (markLive), and the 15s tick downgrades the pill back
     * to PREDICTED once the last live data ages past the freshness window. The
     * value is recomputed from the clock each tick, so a screen left open does
     * not stay falsely "live".
     */
    private fun observeFreshness() {
        scope.launch {
            LiveDataFreshness.lastLiveUpdate.collect {
                _uiState.update { state -> state.copy(freshness = LiveDataFreshness.freshnessNow()) }
            }
        }
        scope.launch {
            while (true) {
                _uiState.update { state -> state.copy(freshness = LiveDataFreshness.freshnessNow()) }
                delay(15_000)
            }
        }
    }

    private fun observeAnnouncements() {
        scope.launch {
            // Hydrate from the bundled snapshot first so a cold-launch with
            // no network still has a status pill + alert list to render.
            announcementsRepository.hydrateFromBundleIfNeeded()
            announcementsRepository.feed.collect { feed ->
                _uiState.update {
                    it.copy(
                        announcements = feed.announcements,
                        serviceStatus = feed.status,
                    )
                }
            }
        }
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
        scope.launch {
            runCatching { announcementsRepository.refresh() }
        }
    }

    fun onLocationUpdate(latitude: Double, longitude: Double) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val location = UserLocation(latitude, longitude)
            val nearest = findNearestStation.invoke(location, limit = 3).first()
            _uiState.update { it.copy(nearestStations = nearest, isLoading = false) }
            // Refresh weather for the user's actual location.
            launch { runCatching { weatherRepository.refresh(latitude, longitude, placeName = "Here") } }

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
        val next = sorted.firstOrNull()
        val nextLine = next?.let { resolveLine(it.lineId) }

        // Last-train teaser is anchored to whichever line the next departure
        // is on, so the hero answer ("Next M2 in 4 min") and the teaser
        // ("Last M2: leave by 00:14") describe the same line the user is
        // most likely about to ride. Falls back to the nearest station's
        // first line when nothing is upcoming.
        val teaserLineId = next?.lineId?.let { normalizeLineId(it) } ?: lineIds.firstOrNull()
        val lastTrain = teaserLineId?.let { getLastTrain.latestEitherDirection(stationId, it) }
        val lastTrainLine = lastTrain?.let { resolveLine(it.lineId) }

        _uiState.update {
            it.copy(
                upcomingDepartures = sorted,
                nextDeparture = next,
                nextDepartureLine = nextLine,
                lastTrain = lastTrain,
                lastTrainLine = lastTrainLine,
            )
        }
    }

    /** M3_AIR is the airport branch of Line 3; it shares M3's UI identity. */
    private fun normalizeLineId(lineId: String): String =
        if (lineId.startsWith("M3")) "M3" else lineId

    private fun resolveLine(lineId: String): Line? {
        val normalized = normalizeLineId(lineId)
        return _uiState.value.lines.firstOrNull { it.id == normalized }
    }
}
