package com.syrmos.app.screen

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.syrmos.feature.stations.StationDetailScreen
import com.syrmos.feature.stations.StationDetailViewModel
import org.koin.compose.koinInject

data class StationDetailScreenRoute(val stationId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = koinInject<StationDetailViewModel>()
        viewModel.loadStation(stationId)
        StationDetailScreen(
            viewModel = viewModel,
            onBack = { navigator.pop() },
        )
    }
}
