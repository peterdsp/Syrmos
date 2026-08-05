package com.syrmos.app.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.syrmos.app.AriadneNavBus
import com.syrmos.app.AriadneNavEvent
import com.syrmos.app.NotificationNavBus
import com.syrmos.app.NotificationNavEvent
import com.syrmos.app.screen.AlertDetailScreenRoute
import com.syrmos.app.screen.LineDetailScreenRoute
import com.syrmos.app.screen.StationDetailScreenRoute
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.app.platform.requestUserLocation
import com.syrmos.feature.home.HomeScreen
import com.syrmos.feature.home.HomeViewModel
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
        val viewModel = koinInject<HomeViewModel>()
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
            scrollToWeatherRequest = scrollToWeatherRequest,
        )
    }
}
