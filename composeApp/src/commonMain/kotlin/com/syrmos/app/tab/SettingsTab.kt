package com.syrmos.app.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.syrmos.feature.settings.SettingsScreen

object SettingsTab : Tab {
    override val options: TabOptions
        @Composable
        get() = remember {
            TabOptions(
                index = 3u,
                title = "Settings",
                icon = null,
            )
        }

    @Composable
    override fun Content() {
        SettingsScreen()
    }
}
