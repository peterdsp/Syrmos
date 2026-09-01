package com.syrmos.feature.home

import com.syrmos.core.common.DataFreshness
import com.syrmos.core.common.LiveDataFreshness
import com.syrmos.core.data.sync.AnnouncementsRepository
import com.syrmos.core.data.sync.RailNewsRepository
import com.syrmos.core.data.sync.WeatherRepository
import com.syrmos.core.model.weather.WeatherSnapshot
import com.syrmos.core.domain.usecase.FindNearestStationUseCase
import com.syrmos.core.domain.usecase.GetLastTrainUseCase
import com.syrmos.core.domain.usecase.GetLineDetailUseCase
import com.syrmos.core.domain.usecase.GetLinesUseCase
import com.syrmos.core.domain.usecase.GetNextDeparturesUseCase
import com.syrmos.core.domain.usecase.UpcomingDeparture
import com.syrmos.core.model.location.NearestStationResult
import com.syrmos.core.model.location.UserLocation
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LiveSuburbanTrain
import com.syrmos.core.model.transit.Station
import com.syrmos.core.network.RailNewsItem
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
    /**
     * Ordered station list per line id, loaded once at boot from
     * [GetLineDetailUseCase]. Feeds the tracking card's station strip so
     * callers can populate [com.syrmos.core.common.TrackedDeparture.routeStations]
     * without another round-trip to the repository at track time.
     */
    val stationsByLine: Map<String, List<Station>> = emptyMap(),
    val announcements: List<STASYAnnouncement> = emptyList(),
    val railNews: List<RailNewsItem> = emptyList(),
    val serviceStatus: STASYServiceStatus? = null,
    val isLoading: Boolean = false,
    /** True while a user-initiated pull-to-refresh is in flight (drives the spinner). */
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(
    private val findNearestStation: FindNearestStationUseCase,
    private val getNextDepartures: GetNextDeparturesUseCase,
    private val getLastTrain: GetLastTrainUseCase,
    private val getLinesUseCase: GetLinesUseCase,
    private val getLineDetail: GetLineDetailUseCase,
    private val announcementsRepository: AnnouncementsRepository,
    private val liveTrackerService: RailwayGovLiveTrackerService,
    private val weatherRepository: WeatherRepository,
    private val railNews: RailNewsRepository,
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
        observeRailNews()
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

    private fun observeFreshness() {
        scope.launch {
            LiveDataFreshness.lastLiveUpdate.collect {
                _uiState.update { state -> state.copy(freshness = LiveDataFreshness.freshnessNow()) }
            }
        }
        scope.launch {
            while (true) {
                val freshness = LiveDataFreshness.freshnessNow()
                _uiState.update { state -> state.copy(freshness = freshness) }
                if (freshness == DataFreshness.PREDICTED) {
                    triggerConnectivityProbe()
                }
                delay(60_000)
            }
        }
        scope.launch {
            LiveDataFreshness.retryRequested.collect { epoch ->
                if (epoch > 0) triggerConnectivityProbe()
            }
        }
    }

    private fun triggerConnectivityProbe() {
        scope.launch {
            runCatching { announcementsRepository.refresh() }
        }
    }

    private fun observeRailNews() {
        scope.launch {
            railNews.fetchNews()
                .catch { /* ignore */ }
                .collect { news ->
                    _uiState.update { it.copy(railNews = news) }
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
            // Operational only: this list feeds the Lines section and the track
            // picker, both of which are things the user acts on. A line that is
            // built but not open belongs on the map, greyed, not in a picker.
            getLinesUseCase.getOperationalLines()
                .catch { /* ignore */ }
                .collect { lines ->
                    _uiState.update { it.copy(lines = lines) }
                    // Warm the per-line station cache so the tracking card's
                    // station strip has data ready the moment the user taps
                    // Track. Small fixed data set (9 lines), fire-and-forget.
                    lines.forEach { line -> loadStationsForLine(line.id) }
                }
        }
    }

    private fun loadStationsForLine(lineId: String) {
        scope.launch {
            getLineDetail.invoke(lineId)
                .catch { /* ignore */ }
                .collect { detail ->
                    val stations = detail?.stations.orEmpty()
                    if (stations.isEmpty()) return@collect
                    _uiState.update { state ->
                        state.copy(stationsByLine = state.stationsByLine + (lineId to stations))
                    }
                }
        }
    }

    private fun observeLiveTrains() {
        scope.launch {
            liveTrackerService.observeSuburbanTrains()
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

    /**
     * User-initiated pull-to-refresh (iOS parity with Home's `.refreshable`).
     * Re-pulls announcements and nudges live-data freshness so an offline->online
     * transition recovers immediately instead of waiting for the 60s poll. Toggles
     * [HomeUiState.isRefreshing] so the pull spinner shows for the duration.
     */
    fun refresh() {
        scope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            runCatching { announcementsRepository.refresh() }
            LiveDataFreshness.requestRetry()
            _uiState.update { it.copy(isRefreshing = false) }
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
        //
        // Only render the teaser when service is currently running (next
        // departure within an hour). Between 02:00 and the first morning
        // train the projector's 12-hour look-ahead would otherwise pick
        // tomorrow's late-afternoon last train ("leave by 14:15") and
        // dress it up as "tonight", which reads as broken to the user.
        val nextIsSoon = (next?.minutesAway ?: Int.MAX_VALUE) <= 60
        val teaserLineId = if (nextIsSoon) {
            next?.lineId?.let { normalizeLineId(it) } ?: lineIds.firstOrNull()
        } else null
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
