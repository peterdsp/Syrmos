package com.syrmos.app.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import androidx.compose.foundation.layout.fillMaxSize
import com.syrmos.core.designsystem.layout.rememberContentWorkspace
import com.syrmos.core.common.layout.WorkspaceTask
import com.syrmos.core.common.layout.WorkspaceArrangement
import com.syrmos.core.common.layout.PaneRole
import com.syrmos.app.screen.LineDetailPane
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.syrmos.app.screen.LineDetailScreenRoute
import com.syrmos.app.screen.BrowseAllStationsScreenRoute
import com.syrmos.app.screen.PlanScreenRoute
import com.syrmos.app.screen.StationDetailScreenRoute
import com.syrmos.app.platform.requestLocationPermission
import com.syrmos.app.platform.requestUserLocation
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.feature.lines.LinesScreen
import com.syrmos.feature.lines.LinesViewModel
import org.koin.compose.koinInject
import kotlinx.coroutines.flow.first

object ExploreTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val lang by LocalizationManager.language.collectAsState()
            return TabOptions(
                index = 1u,
                title = L.EXPLORE.text(lang),
                icon = null,
            )
        }

    @Composable
    override fun Content() {
        Navigator(ExploreListScreen())
    }
}

private class ExploreListScreen : cafe.adriel.voyager.core.screen.Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = koinInject<LinesViewModel>()
        val stationRepository = koinInject<StationRepositoryImpl>()
        val lang by LocalizationManager.language.collectAsState()

        suspend fun resolveNearestOrigin(requestPermission: Boolean): String? {
            if (requestPermission) requestLocationPermission()
            val location = requestUserLocation() ?: return null
            val nearest = stationRepository.findNearestStations(location, limit = 1).first().firstOrNull()
                ?: return null
            val station = stationRepository.getStationById(nearest.stationId).first()
            return when (lang) {
                AppLanguage.GREEK -> station?.nameEl ?: nearest.stationName
                AppLanguage.ALBANIAN -> station?.nameSq ?: station?.name ?: nearest.stationName
                else -> station?.name ?: nearest.stationName
            }
        }

        // Foldables and tablets (six-posture prompt, section 6, Explore): the list
        // is the task pane and the selected line's detail the companion, decided
        // by the shared policy; a phone keeps the list with push navigation.
        var selectedLineId by rememberSaveable { mutableStateOf<String?>(null) }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val ws = rememberContentWorkspace(
                task = WorkspaceTask.EXPLORE,
                width = maxWidth.value.toInt(),
                height = maxHeight.value.toInt(),
            )
            val paired = ws.arrangement != WorkspaceArrangement.SINGLE
            val list: @Composable () -> Unit = {
                LinesScreen(
                    viewModel = viewModel,
                    onLineClick = { lineId ->
                        if (paired) selectedLineId = lineId else navigator.push(LineDetailScreenRoute(lineId))
                    },
                    onDestinationClick = { stationId, _ ->
                        navigator.push(StationDetailScreenRoute(stationId))
                    },
                    onBrowseAllClick = {
                        navigator.push(BrowseAllStationsScreenRoute())
                    },
                    onDetectOrigin = { resolveNearestOrigin(requestPermission = false) },
                    onRequestLocationOrigin = { resolveNearestOrigin(requestPermission = true) },
                )
            }
            val companion: @Composable () -> Unit = {
                val lineId = selectedLineId
                if (lineId != null) {
                    LineDetailPane(
                        lineId = lineId,
                        onStationClick = { navigator.push(StationDetailScreenRoute(it)) },
                        onBack = { selectedLineId = null },
                    )
                } else {
                    ExploreCompanionPlaceholder(lang)
                }
            }
            when (ws.arrangement) {
                WorkspaceArrangement.SIDE_BY_SIDE -> {
                    val taskW = ws.pane(PaneRole.TASK)?.rect?.right ?: (maxWidth.value.toInt() / 2)
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.width(taskW.dp).fillMaxHeight()) { list() }
                        VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        Box(Modifier.weight(1f).fillMaxHeight()) { companion() }
                    }
                }
                WorkspaceArrangement.STACKED -> {
                    val companionH = ws.pane(PaneRole.COMPANION)?.rect?.bottom ?: (maxHeight.value.toInt() * 45 / 100)
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().height(companionH.dp)) { companion() }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        Box(Modifier.weight(1f).fillMaxWidth()) { list() }
                    }
                }
                WorkspaceArrangement.SINGLE -> list()
            }
            // Entry into the 3.0 Plan flow. Interim launch point until Journeys
            // becomes a primary destination; keeps the flow reachable and testable.
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { navigator.push(PlanScreenRoute()) },
                modifier = androidx.compose.ui.Modifier
                    // Sits ABOVE the Ariadne launcher pill (which owns bottom=96dp,
                    // end=16dp) so the two never overlap in the bottom-right corner.
                    .align(androidx.compose.ui.Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 168.dp),
            ) {
                androidx.compose.material3.Text(
                    when (lang) {
                        AppLanguage.GREEK -> "Σχεδίασε διαδρομή"
                        AppLanguage.ALBANIAN -> "Planifiko udhëtim"
                        AppLanguage.ITALIAN -> "Pianifica un viaggio"
                        else -> "Plan a journey"
                    },
                )
            }
        }
    }
}

/** Calm invitation in the Explore companion before a line is chosen. */
@Composable
private fun ExploreCompanionPlaceholder(lang: AppLanguage) {
    fun t(en: String, el: String, sq: String, it: String) = when (lang) {
        AppLanguage.GREEK -> el; AppLanguage.ALBANIAN -> sq; AppLanguage.ITALIAN -> it; else -> en
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                t("Choose a line.", "Διάλεξε γραμμή.", "Zgjidh një linjë.", "Scegli una linea."),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            )
            Text(
                t("Its stations, live trains and alerts appear here.",
                  "Οι σταθμοί, οι ζωντανοί συρμοί και οι ειδοποιήσεις της εμφανίζονται εδώ.",
                  "Stacionet, trenat live dhe njoftimet e saj shfaqen këtu.",
                  "Le sue stazioni, i treni in tempo reale e gli avvisi compaiono qui."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
