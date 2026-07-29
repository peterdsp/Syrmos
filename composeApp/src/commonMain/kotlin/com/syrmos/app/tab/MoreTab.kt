package com.syrmos.app.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.syrmos.core.common.AriadneEngineStatus
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.app.platform.provideAriadneEngineStatus
import com.syrmos.feature.settings.SettingsScreen

object MoreTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val lang by LocalizationManager.language.collectAsState()
            return TabOptions(
                index = 4u,
                title = L.MORE_TAB.text(lang),
                icon = null,
            )
        }

    @Composable
    override fun Content() {
        var engine by remember { mutableStateOf<AriadneEngineStatus?>(null) }
        LaunchedEffect(Unit) { engine = provideAriadneEngineStatus() }
        SettingsScreen(ariadneEngine = engine)
    }
}
