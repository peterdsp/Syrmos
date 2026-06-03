package com.syrmos.app.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.feature.lines.LinesScreen
import com.syrmos.feature.lines.LinesViewModel
import org.koin.compose.koinInject

object LinesTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val lang by LocalizationManager.language.collectAsState()
            return TabOptions(
                index = 1u,
                title = L.LINES.text(lang),
                icon = null,
            )
        }

    @Composable
    override fun Content() {
        val viewModel = koinInject<LinesViewModel>()
        LinesScreen(viewModel = viewModel)
    }
}
