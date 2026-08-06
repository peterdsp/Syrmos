package com.syrmos.app.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.app.platform.hasAirportCalendarPermission
import com.syrmos.app.platform.loadAirportCalendarTrips
import com.syrmos.app.platform.requestAirportCalendarPermission
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

    @Composable
    override fun Content() {
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
        )
    }
}
