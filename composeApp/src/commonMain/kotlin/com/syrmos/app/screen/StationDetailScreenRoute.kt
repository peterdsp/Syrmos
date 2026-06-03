package com.syrmos.app.screen

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import com.syrmos.feature.stations.StationDetailScreen
import com.syrmos.feature.stations.StationDetailViewModel
import org.koin.compose.koinInject

data class StationDetailScreenRoute(val stationId: String) : Screen {
    @Composable
    override fun Content() {
        val viewModel = koinInject<StationDetailViewModel>()
        viewModel.loadStation(stationId)
        StationDetailScreen(viewModel = viewModel)
    }
}
