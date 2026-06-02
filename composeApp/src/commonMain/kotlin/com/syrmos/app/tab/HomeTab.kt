package com.syrmos.app.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.syrmos.feature.home.HomeScreen
import com.syrmos.feature.home.HomeViewModel
import org.koin.compose.koinInject

object HomeTab : Tab {
    override val options: TabOptions
        @Composable
        get() = remember {
            TabOptions(
                index = 0u,
                title = "Home",
                icon = null,
            )
        }

    @Composable
    override fun Content() {
        val viewModel = koinInject<HomeViewModel>()
        HomeScreen(viewModel = viewModel)
    }
}
