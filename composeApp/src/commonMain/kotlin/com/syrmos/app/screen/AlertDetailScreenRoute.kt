package com.syrmos.app.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.sync.AnnouncementsRepository
import com.syrmos.feature.home.AlertDetailScreen
import org.koin.compose.koinInject

data class AlertDetailScreenRoute(val alertId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = koinInject<AnnouncementsRepository>()
        val feed by repository.feed.collectAsState()
        val language by LocalizationManager.language.collectAsState()

        LaunchedEffect(alertId) {
            repository.hydrateFromBundleIfNeeded()
            if (feed.announcements.none { it.id == alertId }) {
                runCatching { repository.refresh() }
            }
        }

        AlertDetailScreen(
            alert = feed.announcements.firstOrNull { it.id == alertId },
            language = language,
            onBack = navigator::pop,
        )
    }
}
