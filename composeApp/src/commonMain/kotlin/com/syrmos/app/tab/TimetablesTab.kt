package com.syrmos.app.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.syrmos.core.common.LocalizationManager
import com.syrmos.feature.schedule.ScheduleScreen

object TimetablesTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val lang by LocalizationManager.language.collectAsState()
            return TabOptions(
                index = 3u,
                title = if (lang.code == "el") "Δρομολόγια" else "Timetables",
                icon = null,
            )
        }

    @Composable
    override fun Content() {
        ScheduleScreen()
    }
}
