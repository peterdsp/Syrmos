package com.syrmos.app.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.syrmos.feature.map.MapScreen

object MapTab : Tab {
    override val options: TabOptions
        @Composable
        get() = remember {
            TabOptions(
                index = 2u,
                title = "Map",
                icon = null,
            )
        }

    @Composable
    override fun Content() {
        MapScreen()
    }
}
