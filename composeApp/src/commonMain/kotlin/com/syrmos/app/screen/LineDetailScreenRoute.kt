package com.syrmos.app.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.syrmos.feature.lines.LineDetailScreen
import com.syrmos.feature.lines.LineDetailViewModel
import org.koin.compose.koinInject

data class LineDetailScreenRoute(val lineId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = koinInject<LineDetailViewModel>()
        LaunchedEffect(lineId) {
            viewModel.loadLine(lineId)
        }
        LineDetailScreen(
            viewModel = viewModel,
            onStationClick = { stationId ->
                navigator.push(StationDetailScreenRoute(stationId))
            },
        )
    }
}
