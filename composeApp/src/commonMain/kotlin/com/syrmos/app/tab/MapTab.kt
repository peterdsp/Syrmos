package com.syrmos.app.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.syrmos.app.platform.requestUserLocation
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.feature.map.MapScreen
import com.syrmos.feature.map.MapViewModel
import org.koin.compose.koinInject

object MapTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val lang by LocalizationManager.language.collectAsState()
            return TabOptions(
                index = 2u,
                title = L.MAP.text(lang),
                icon = null,
            )
        }

    @Composable
    override fun Content() {
        val viewModel = koinInject<MapViewModel>()
        // Auto-center on the user when the Map tab appears AND permission
        // is already granted. requestUserLocation() returns null when the
        // user has denied or the platform can't get a fix, which keeps the
        // map on the Athens fallback baked into the underlying map view.
        LaunchedEffect(Unit) {
            val loc = requestUserLocation()
            if (loc != null) {
                viewModel.requestLocateUser()
            }
        }
        MapScreen(viewModel = viewModel)
    }
}
