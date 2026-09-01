package com.syrmos.app.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.sync.AnnouncementsRepository
import com.syrmos.core.designsystem.component.AlertBannerInfo
import com.syrmos.feature.stations.StationDetailScreen
import com.syrmos.feature.stations.StationDetailViewModel
import org.koin.compose.koinInject

data class StationDetailScreenRoute(
    val stationId: String,
    // Set when reached as a transfer from another hub: scopes this screen's
    // departures to that one line, so it shows the tapped line's timetable here.
    val focusLineId: String? = null,
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = koinInject<StationDetailViewModel>()
        val announcementsRepo = koinInject<AnnouncementsRepository>()
        val feed by announcementsRepo.feed.collectAsState()
        val lineDisruptions by announcementsRepo.lineDisruptions.collectAsState()
        val lang by LocalizationManager.language.collectAsState()
        val uiState by viewModel.uiState.collectAsState()

        val stationLineIds = uiState.connectingLines.map { it.id }
        val matchingAlert = feed.announcements.firstOrNull { ann ->
            ann.isServiceAlert && ann.affectedLines.any { affected ->
                stationLineIds.any { it.equals(affected, ignoreCase = true) }
            }
        }
        val alertBanner = matchingAlert?.let { ann ->
            val detail = when (lang) {
                com.syrmos.core.common.AppLanguage.GREEK -> ann.title
                com.syrmos.core.common.AppLanguage.ALBANIAN ->
                    ann.titleSq.ifBlank { ann.titleEn.ifBlank { ann.title } }
                com.syrmos.core.common.AppLanguage.ITALIAN ->
                    ann.titleEn.ifBlank { ann.title }
                else -> ann.titleEn.ifBlank { ann.title }
            }
            AlertBannerInfo(
                headline = L.SERVICE_ALERT_AFFECTS_LINE.text(lang),
                detail = detail,
            )
        }

        viewModel.loadStation(stationId, focusLineId)
        StationDetailScreen(
            viewModel = viewModel,
            alertBanner = alertBanner,
            lineDisruptions = lineDisruptions,
            onBack = { navigator.pop() },
            onOpenTransfer = { lineId, targetStationId ->
                // Honor BOTH resolver ids: open the resolved stop scoped to the
                // tapped line, so the destination shows that line's timetable at
                // that hub, not every line's departures aggregated. Ignore a tap
                // that would push the identical route (belt-and-suspenders: the use
                // case already excludes the focused line from a scoped screen).
                if (targetStationId != stationId || lineId != focusLineId) {
                    navigator.push(StationDetailScreenRoute(targetStationId, focusLineId = lineId))
                }
            },
        )
    }
}
