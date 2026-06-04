package com.syrmos.app.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.syrmos.app.screen.LineDetailScreenRoute
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.feature.home.HomeScreen
import com.syrmos.feature.home.HomeViewModel
import com.syrmos.feature.map.MapViewModel
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
        val mapViewModel = koinInject<MapViewModel>()
        val mapState by mapViewModel.uiState.collectAsState()
        HomeScreen(
            viewModel = viewModel,
            simulatedTrains = mapState.simulatedTrains,
            onLineClick = { lineId ->
                navigator.push(LineDetailScreenRoute(lineId))
            },
        )
    }
}
