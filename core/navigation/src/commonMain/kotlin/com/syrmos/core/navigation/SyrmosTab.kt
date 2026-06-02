package com.syrmos.core.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions

enum class TabType {
    HOME, LINES, MAP, SETTINGS,
}

interface SyrmosTab : Tab {
    val tabType: TabType
}
