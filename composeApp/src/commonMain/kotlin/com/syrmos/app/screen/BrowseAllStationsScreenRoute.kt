package com.syrmos.app.screen

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.syrmos.feature.lines.BrowseAllStationsScreen

class BrowseAllStationsScreenRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        BrowseAllStationsScreen(
            onStationClick = { stationId ->
                navigator.push(StationDetailScreenRoute(stationId))
            },
            onBack = { navigator.pop() },
        )
    }
}
