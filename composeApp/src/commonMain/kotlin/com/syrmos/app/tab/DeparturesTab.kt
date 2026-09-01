package com.syrmos.app.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.app.platform.hasAirportCalendarPermission
import com.syrmos.app.platform.loadAirportCalendarTrips
import com.syrmos.app.platform.requestAirportCalendarPermission
import com.syrmos.app.screen.StationDetailScreenRoute
import com.syrmos.feature.schedule.AirportCalendarTrip
import com.syrmos.feature.schedule.AirportHubScreen
import kotlinx.coroutines.launch

object DeparturesTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val lang by LocalizationManager.language.collectAsState()
            return TabOptions(
                index = 3u,
                title = when (lang) {
                    AppLanguage.ENGLISH -> "Airport"
                    AppLanguage.GREEK -> "Αεροδρόμιο"
                    AppLanguage.ALBANIAN -> "Aeroporti"
                    AppLanguage.ITALIAN -> "Aeroporto"
                },
                icon = null,
            )
        }

    // A per-tab Navigator (mirrors ExploreTab) so tapping an airport service can
    // push the stop's Station Detail onto the Airport tab's own back stack.
    @Composable
    override fun Content() {
        Navigator(AirportRootScreen())
    }
}

private class AirportRootScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        var calendarConnected by remember { mutableStateOf(hasAirportCalendarPermission()) }
        var calendarTrips by remember { mutableStateOf<List<AirportCalendarTrip>>(emptyList()) }

        LaunchedEffect(calendarConnected) {
            if (calendarConnected) calendarTrips = loadAirportCalendarTrips()
        }

        AirportHubScreen(
            calendarTrips = calendarTrips,
            calendarConnected = calendarConnected,
            onConnectCalendar = {
                scope.launch {
                    calendarConnected = requestAirportCalendarPermission()
                    if (calendarConnected) calendarTrips = loadAirportCalendarTrips()
                }
            },
            onOpenStation = { stationId -> navigator.push(StationDetailScreenRoute(stationId)) },
        )
    }
}
