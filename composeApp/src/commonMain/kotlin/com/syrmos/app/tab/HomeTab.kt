package com.syrmos.app.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import com.syrmos.app.AriadneNavBus
import com.syrmos.app.AriadneNavEvent
import com.syrmos.app.NotificationNavBus
import com.syrmos.app.NotificationNavEvent
import com.syrmos.app.screen.AlertDetailScreenRoute
import com.syrmos.app.screen.GoJourneyScreenRoute
import com.syrmos.app.screen.LineDetailScreenRoute
import com.syrmos.app.screen.StationDetailScreenRoute
import com.syrmos.app.screen.buildGuidanceJourney
import com.syrmos.app.journey.ActiveJourneyRepository
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.app.platform.requestLocationPermission
import com.syrmos.app.platform.requestUserLocation
import com.syrmos.feature.home.HomeScreen
import com.syrmos.feature.home.HomeViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.koinInject

object HomeTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val lang by LocalizationManager.language.collectAsState()
            return TabOptions(
                index = 0u,
                title = L.HOME.text(lang),
                icon = null,
            )
        }

    @Composable
    override fun Content() {
        Navigator(HomeListScreen())
    }
}

private class HomeListScreen : cafe.adriel.voyager.core.screen.Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val tabNavigator = LocalTabNavigator.current
        val viewModel = koinInject<HomeViewModel>()
        val stationRepo = koinInject<StationRepositoryImpl>()
        val lang by LocalizationManager.language.collectAsState()
        val scope = rememberCoroutineScope()
        var scrollToWeatherRequest by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            val location = requestUserLocation()
            if (location != null) {
                viewModel.onLocationUpdate(location.latitude, location.longitude)
            }
        }

        LaunchedEffect(Unit) {
            AriadneNavBus.events.collect { event ->
                when (event) {
                    is AriadneNavEvent.Station -> navigator.push(StationDetailScreenRoute(event.stationId))
                    is AriadneNavEvent.Line -> navigator.push(LineDetailScreenRoute(event.lineId))
                }
            }
        }

        LaunchedEffect(Unit) {
            NotificationNavBus.homeEvents.collect { event ->
                when (event) {
                    is NotificationNavEvent.Alert -> navigator.push(AlertDetailScreenRoute(event.alertId))
                    is NotificationNavEvent.Station -> navigator.push(StationDetailScreenRoute(event.stationId))
                    NotificationNavEvent.Weather -> scrollToWeatherRequest += 1
                    NotificationNavEvent.Home -> Unit
                    // Cold-launch / process-death continuity: rebuild the live GO
                    // session from its persisted snapshot and reopen GO where the
                    // rider left off. No begin flow runs, so no duplicate session.
                    NotificationNavEvent.ResumeGo -> {
                        val active = ActiveJourneyRepository.active.value
                        val alreadyOnGo = navigator.items.lastOrNull() is GoJourneyScreenRoute
                        if (active != null && !alreadyOnGo) {
                            val guidance = buildGuidanceJourney(active.itinerarySnapshot, stationRepo, lang)
                            if (guidance.legs.isNotEmpty()) {
                                navigator.push(GoJourneyScreenRoute(guidance))
                            }
                        }
                    }
                }
            }
        }

        HomeScreen(
            viewModel = viewModel,
            onStationClick = { stationId ->
                navigator.push(StationDetailScreenRoute(stationId))
            },
            onLineClick = { lineId ->
                navigator.push(LineDetailScreenRoute(lineId))
            },
            onMapClick = { tabNavigator.current = MapTab },
            onEnableLocation = {
                scope.launch {
                    requestLocationPermission()
                    val location = requestUserLocation()
                    if (location != null) {
                        viewModel.onLocationUpdate(location.latitude, location.longitude)
                    }
                }
            },
            scrollToWeatherRequest = scrollToWeatherRequest,
        )
    }
}
