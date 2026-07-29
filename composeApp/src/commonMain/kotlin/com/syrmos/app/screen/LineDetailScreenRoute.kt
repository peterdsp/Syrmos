package com.syrmos.app.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.sync.AnnouncementsRepository
import com.syrmos.core.designsystem.component.AlertBannerInfo
import com.syrmos.feature.lines.LineDetailScreen
import com.syrmos.feature.lines.LineDetailViewModel
import org.koin.compose.koinInject

data class LineDetailScreenRoute(val lineId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = koinInject<LineDetailViewModel>()
        val announcementsRepo = koinInject<AnnouncementsRepository>()
        val feed by announcementsRepo.feed.collectAsState()
        val lang by LocalizationManager.language.collectAsState()

        val matchingAlert = feed.announcements.firstOrNull { ann ->
            ann.isServiceAlert && ann.affectedLines.any { it.equals(lineId, ignoreCase = true) }
        }
        val alertBanner = matchingAlert?.let { ann ->
            val detail = when (lang) {
                com.syrmos.core.common.AppLanguage.GREEK -> ann.title
                com.syrmos.core.common.AppLanguage.ALBANIAN ->
                    ann.titleSq.ifBlank { ann.titleEn.ifBlank { ann.title } }
                else -> ann.titleEn.ifBlank { ann.title }
            }
            AlertBannerInfo(
                headline = L.SERVICE_ALERT_AFFECTS_LINE.text(lang),
                detail = detail,
            )
        }

        LaunchedEffect(lineId) {
            viewModel.loadLine(lineId)
        }
        LineDetailScreen(
            viewModel = viewModel,
            alertBanner = alertBanner,
            onStationClick = { stationId ->
                navigator.push(StationDetailScreenRoute(stationId))
            },
            onBack = { navigator.pop() },
        )
    }
}
