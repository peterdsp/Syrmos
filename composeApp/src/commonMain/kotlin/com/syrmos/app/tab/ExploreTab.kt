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
import com.syrmos.app.screen.StationDetailScreenRoute
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.feature.lines.LinesScreen
import com.syrmos.feature.lines.LinesViewModel
import org.koin.compose.koinInject

object ExploreTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val lang by LocalizationManager.language.collectAsState()
            return TabOptions(
                index = 1u,
                title = L.EXPLORE.text(lang),
                icon = null,
            )
        }

    @Composable
    override fun Content() {
        Navigator(ExploreListScreen())
    }
}

private class ExploreListScreen : cafe.adriel.voyager.core.screen.Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = koinInject<LinesViewModel>()
        LinesScreen(
            viewModel = viewModel,
            onLineClick = { lineId ->
                navigator.push(LineDetailScreenRoute(lineId))
            },
            onDestinationClick = { stationId, _ ->
                navigator.push(StationDetailScreenRoute(stationId))
            },
            onBrowseAllClick = {
                // TODO: browse-all-stations screen
            },
        )
    }
}
