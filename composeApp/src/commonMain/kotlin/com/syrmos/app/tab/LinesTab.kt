package com.syrmos.app.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.syrmos.feature.lines.LinesScreen
import com.syrmos.feature.lines.LinesViewModel
import org.koin.compose.koinInject

object LinesTab : Tab {
    override val options: TabOptions
        @Composable
        get() = remember {
            TabOptions(
                index = 1u,
                title = "Lines",
                icon = null,
            )
        }

    @Composable
    override fun Content() {
        val viewModel = koinInject<LinesViewModel>()
        LinesScreen(viewModel = viewModel)
    }
}
