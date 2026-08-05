package com.syrmos.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.sync.FaresRepository
import com.syrmos.core.data.sync.AnnouncementsRepository
import com.syrmos.core.designsystem.component.LineColorIndicator
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.alerts.AlertSeverity
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.LiveSuburbanTrain
import com.syrmos.core.model.transit.SimulatedTrain
import com.syrmos.core.network.STASYAnnouncement
import com.syrmos.core.network.STASYServiceStatus
import com.syrmos.core.network.SyrmosSchedulesService.FareProduct
import com.syrmos.core.network.SyrmosSchedulesService.InfoLink
import com.syrmos.feature.home.HomeViewModel
import com.syrmos.feature.lines.LinesViewModel
import com.syrmos.feature.map.MapScreen
import com.syrmos.feature.map.MapStationNode
import com.syrmos.feature.map.MapViewModel
import androidx.compose.ui.platform.LocalUriHandler
import org.koin.compose.koinInject
import com.syrmos.app.platform.readSelectedDesktopSectionId
import com.syrmos.app.platform.writeSelectedDesktopSectionId

private enum class DesktopSection(
    val title: String,
    val titleKey: L,
    val icon: ImageVector,
) {
    Planner("Planner", L.DESKTOP_PLANNER, Icons.Filled.Map),
    Schedules("Schedules", L.DESKTOP_SCHEDULES, Icons.Filled.Schedule),
    Passes("Passes", L.DESKTOP_PASSES, Icons.Filled.CalendarMonth),
    Account("Account", L.DESKTOP_ACCOUNT, Icons.Filled.AccountCircle),
}

@Composable
fun DesktopWebApp() {
    val mapViewModel = koinInject<MapViewModel>()
    val homeViewModel = koinInject<HomeViewModel>()
    val linesViewModel = koinInject<LinesViewModel>()
    val faresRepo = koinInject<FaresRepository>()
    val mapState by mapViewModel.uiState.collectAsState()
    val homeState by homeViewModel.uiState.collectAsState()
    val linesState by linesViewModel.uiState.collectAsState()
    val fareProducts by faresRepo.products.collectAsState()
    val fareInfoLinks by faresRepo.infoLinks.collectAsState()
    val lang by LocalizationManager.language.collectAsState()
    val uriHandler = LocalUriHandler.current
    var selectedSection by remember {
        mutableStateOf(
            DesktopSection.entries.firstOrNull {
                it.name.equals(readSelectedDesktopSectionId(), ignoreCase = true)
            } ?: DesktopSection.Planner,
        )
    }
    var search by remember { mutableStateOf("") }

    LaunchedEffect(selectedSection) {
        writeSelectedDesktopSectionId(selectedSection.name.lowercase())
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        DesktopSidebar(
            selectedSection = selectedSection,
            onSectionSelected = { selectedSection = it },
            lang = lang,
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            ) {
                MapScreen(
                    viewModel = mapViewModel,
                    showTopBar = false,
                    initialScale = 0.65f,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 12.dp),
            ) {
                item {
                    DesktopHeader(lang = lang)
                }

                item {
                    OperationsCard(
                        liveTrains = mapState.liveTrains,
                        simulatedTrains = mapState.simulatedTrains,
                        nearestStations = homeState.nearestStations,
                        mapStations = mapState.mapStations,
                        lines = mapState.lines,
                        lang = lang,
                    )
                }

                if (homeState.announcements.isNotEmpty() || homeState.serviceStatus != null) {
                    item {
                        UpdatesCard(
                            status = homeState.serviceStatus,
                            announcements = homeState.announcements,
                            lang = lang,
                            onOpenUrl = uriHandler::openUri,
                        )
                    }
                }

                if (fareProducts.isNotEmpty()) {
                    item {
                        TicketsCard(
                            products = fareProducts,
                            lang = lang,
                            onBuy = { uriHandler.openUri(it) },
                        )
                    }
                }

                if (fareInfoLinks.isNotEmpty()) {
                    item {
                        InfoLinksCard(
                            links = fareInfoLinks,
                            lang = lang,
                            onOpen = { uriHandler.openUri(it) },
                        )
                    }
                }

                item {
                    HellenicTrainCard(
                        lang = lang,
                        onBuy = { uriHandler.openUri(it) },
                    )
                }

                item {
                    PlannerCard(
                        search = search,
                        onSearchChange = { search = it },
                        selectedStation = mapState.selectedStation,
                        selectedStationLines = mapState.selectedStationLines,
                        stations = mapState.mapStations,
                        lang = lang,
                    )
                }

                item {
                    RouteComparisonCard(lines = linesState.lines, lang = lang)
                }

                item {
                    TimetableCard(lines = linesState.lines, lang = lang)
                }

                item {
                    ExportCard(lang = lang)
                }
            }
        }
    }
}

@Composable
private fun DesktopSidebar(
    selectedSection: DesktopSection,
    onSectionSelected: (DesktopSection) -> Unit,
    lang: AppLanguage,
) {
    Surface(
        modifier = Modifier
            .width(232.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Syrmos",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = L.DESKTOP_SUBTITLE.text(lang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(18.dp))

            DesktopSection.entries.forEach { section ->
                SidebarItem(
                    section = section,
                    selected = selectedSection == section,
                    onClick = { onSectionSelected(section) },
                    lang = lang,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            LanguageSwitcher()

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = L.NETWORK_STATUS.text(lang),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = L.NETWORK_STATUS_BODY.text(lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/// Three-pill language selector for the desktop/web sidebar. The mobile
/// surface reaches the picker through Settings → Language, but the
/// desktop layout (≥900dp) renders DesktopWebApp() which skips the tab
/// navigator entirely. Without this row, web users have no way to
/// switch from English to Greek or Albanian.
@Composable
private fun LanguageSwitcher() {
    val current by LocalizationManager.language.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = L.LANGUAGE.text(current),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppLanguage.entries.forEach { lang ->
                val selected = lang == current
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { LocalizationManager.setLanguage(lang) },
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = lang.code.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(
    section: DesktopSection,
    selected: Boolean,
    onClick: () -> Unit,
    lang: AppLanguage,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = section.title,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = section.titleKey.text(lang),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DesktopHeader(lang: AppLanguage) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = L.DESKTOP_HEADER.text(lang),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = L.APP_SUBTITLE.text(lang),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlannerCard(
    search: String,
    onSearchChange: (String) -> Unit,
    selectedStation: MapStationNode?,
    selectedStationLines: List<Line>,
    stations: List<MapStationNode>,
    lang: AppLanguage,
) {
    DashboardCard(title = L.TRIP_PLANNING.text(lang)) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            label = { Text(L.SEARCH_STATION.text(lang)) },
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill(value = stations.size.toString(), label = L.STATIONS_LOWER.text(lang))
            StatPill(value = selectedStationLines.size.toString(), label = L.LINES_HERE.text(lang))
            StatPill(value = "90+", label = L.ACCESSIBLE_STOPS.text(lang))
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedStation == null) {
            Text(
                text = L.SELECT_STATION_HINT.text(lang),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            StationSummary(
                station = selectedStation,
                lines = selectedStationLines,
                lang = lang,
            )
        }
    }
}

@Composable
private fun StationSummary(
    station: MapStationNode,
    lines: List<Line>,
    lang: AppLanguage,
) {
    val announcementsRepository = koinInject<AnnouncementsRepository>()
    val lineDisruptions by announcementsRepository.lineDisruptions.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = station.displayName(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (station.nameEl.isNotBlank() && station.nameEl != station.name) {
            Text(
                text = station.nameEl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            lines.forEach { line ->
                LineBadge(line, lang, lineDisruptions[line.id])
            }
        }
        Text(
            text = if (station.isInterchange) L.TRANSFER_STATION.text(lang) else L.DIRECT_STATION.text(lang),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${station.stationIds.size} ${L.MERGED_RECORDS.text(lang)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OperationsCard(
    liveTrains: List<LiveSuburbanTrain>,
    simulatedTrains: List<SimulatedTrain>,
    nearestStations: List<com.syrmos.core.model.location.NearestStationResult>,
    mapStations: List<MapStationNode>,
    lines: List<Line>,
    lang: AppLanguage,
) {
    DashboardCard(title = L.LIVE_TRAINS.text(lang)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (simulatedTrains.isNotEmpty()) {
                SectionLabel("${L.METRO.text(lang)} & ${L.TRAM.text(lang)} (${simulatedTrains.size} ${L.ACTIVE_TRAINS.text(lang)})")
                val displayTrains = simulatedTrains
                    .groupBy { "${it.lineId}_${it.direction}" }
                    .flatMap { (_, group) -> group.take(1) }
                    .take(8)
                displayTrains.forEach { train ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = train.lineName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = train.lineColor.toComposeColor(),
                            )
                            Text(
                                text = train.destinationName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${L.NEAR.text(lang)} ${train.currentStationName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${L.NEXT_SHORT.text(lang)}: ${train.nextStationName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            if (liveTrains.isNotEmpty()) {
                SectionLabel(L.SUBURBAN_RAILWAY.text(lang))
                liveTrains.take(3).forEach { train ->
                    val line = lines.firstOrNull { it.id == train.lineId }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = line?.name ?: train.lineId,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = train.destination.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = train.trainNumber,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (simulatedTrains.isEmpty() && liveTrains.isEmpty()) {
                Text(
                    text = L.NO_LIVE_TRAINS_NOW.text(lang),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionLabel(L.NEARBY_POPULAR.text(lang))
            val stationRows = if (nearestStations.isNotEmpty()) {
                nearestStations.mapNotNull { result ->
                    mapStations.firstOrNull { node -> node.stationIds.contains(result.stationId) }
                }
            } else {
                mapStations
                    .sortedWith(compareByDescending<MapStationNode> { it.lineIds.size }.thenByDescending { it.isInterchange })
                    .take(5)
            }

            stationRows.take(5).forEach { station ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = station.displayName(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (station.isInterchange) L.POPULAR_INTERCHANGE.text(lang) else L.POPULAR_STOP.text(lang),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${station.lineIds.size} ${L.LINES_LOWER.text(lang)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun RouteComparisonCard(lines: List<Line>, lang: AppLanguage) {
    DashboardCard(title = L.ROUTE_COMPARISON.text(lang)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RouteOption(
                title = L.FASTEST.text(lang),
                metric = "18 min",
                detail = lines.firstOrNull()?.name ?: L.METRO.text(lang),
                modifier = Modifier.weight(1f),
            )
            RouteOption(
                title = L.FEWEST_TRANSFERS.text(lang),
                metric = L.ONE_TRANSFER.text(lang),
                detail = lines.getOrNull(1)?.name ?: L.TRAM.text(lang),
                modifier = Modifier.weight(1f),
            )
            RouteOption(
                title = L.BEST_COVERAGE.text(lang),
                metric = "4 ${L.LINES_LOWER.text(lang)}",
                detail = "${L.METRO.text(lang)} + ${L.TRAM.text(lang)}",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TimetableCard(
    lines: List<Line>,
    lang: AppLanguage,
) {
    DashboardCard(title = L.SCHEDULE_BOARD.text(lang)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lines.take(6).forEachIndexed { index, line ->
                ScheduleRow(
                    line = line,
                    minutes = listOf(2, 5, 8, 12, 18, 24).getOrElse(index) { 12 },
                    lang = lang,
                )
                if (index < minOf(lines.size, 6) - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                }
            }
        }
    }
}

@Composable
private fun ExportCard(lang: AppLanguage) {
    DashboardCard(title = L.EXPORT.text(lang)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {}, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Print, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(L.PRINT_SCHEDULE.text(lang))
            }
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(L.DOWNLOAD_PDF.text(lang))
            }
        }
    }
}

@Composable
private fun UpdatesCard(
    status: STASYServiceStatus?,
    announcements: List<STASYAnnouncement>,
    lang: AppLanguage,
    onOpenUrl: (String) -> Unit,
) {
    DashboardCard(title = L.LATEST_FROM_STASY.text(lang)) {
        if (status != null) {
            val message = if (lang == AppLanguage.GREEK) status.rawMessage else status.rawMessageEn
            if (message.isNotBlank()) {
                val bg = if (status.isAlert) SyrmosColorTokens.warning.copy(alpha = 0.12f) else SyrmosColorTokens.live.copy(alpha = 0.1f)
                val accent = if (status.isAlert) SyrmosColorTokens.arrivalModerate else SyrmosColorTokens.arrivalSoon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (status.isAlert) "⚠" else "✓",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        announcements.take(4).forEach { ann ->
            val title = if (lang == AppLanguage.GREEK) ann.title else ann.titleEn
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { if (ann.url.isNotBlank()) onOpenUrl(ann.url) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (ann.isServiceAlert) "⚠" else "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ann.isServiceAlert) SyrmosColorTokens.arrivalModerate else MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (ann.date.isNotBlank()) {
                        Text(
                            text = ann.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketsCard(
    products: List<FareProduct>,
    lang: AppLanguage,
    onBuy: (String) -> Unit,
) {
    DashboardCard(title = when (lang) {
        AppLanguage.GREEK -> "Εισιτήρια OASA"
        AppLanguage.ALBANIAN -> "Biletat e OASA"
        AppLanguage.ITALIAN -> "Biglietti OASA"
        else -> "OASA tickets"
    }) {
        val featured = products.take(6)
        featured.forEach { product ->
            val title = if (lang == AppLanguage.GREEK && product.titleEl.isNotBlank()) product.titleEl else product.titleEn
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val price = product.fullPriceEur
                if (price != null) {
                    Text(
                        text = formatEurDesktop(price),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = { onBuy("https://www.athenacard.gr/") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(when (lang) {
                AppLanguage.GREEK -> "Αγορά μέσω Athena Card ↗"
                AppLanguage.ALBANIAN -> "Bli përmes Athena Card ↗"
                AppLanguage.ITALIAN -> "Acquista su Athena Card ↗"
                else -> "Buy on Athena Card ↗"
            })
        }
        Text(
            text = when (lang) {
                AppLanguage.GREEK -> "Τιμές από επίσημες πηγές OASA. Η αγορά γίνεται απευθείας στο athenacard.gr."
                AppLanguage.ALBANIAN -> "Çmimet nga burimet zyrtare të OASA. Blerja bëhet drejtpërdrejt në athenacard.gr."
                AppLanguage.ITALIAN -> "Prezzi dalle fonti ufficiali OASA. L'acquisto avviene direttamente su athenacard.gr."
                else -> "Prices from official OASA sources. Purchase happens directly on athenacard.gr."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoLinksCard(
    links: List<InfoLink>,
    lang: AppLanguage,
    onOpen: (String) -> Unit,
) {
    DashboardCard(title = when (lang) {
        AppLanguage.GREEK -> "Χρήσιμες πληροφορίες"
        AppLanguage.ALBANIAN -> "Informacione të dobishme"
        AppLanguage.ITALIAN -> "Informazioni utili"
        else -> "Useful information"
    }) {
        links.forEach { link ->
            val target = if (lang == AppLanguage.GREEK) link.urlEl.ifEmpty { link.urlEn } else link.urlEn
            val title = if (lang == AppLanguage.GREEK) link.titleEl else link.titleEn
            val summary = if (lang == AppLanguage.GREEK) link.summaryEl else link.summaryEn
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = link.operatorId.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summary.isNotEmpty()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                link.bullets.forEach { bullet ->
                    val text = if (lang == AppLanguage.GREEK) bullet.el else bullet.en
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(text = text, style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(
                    onClick = { onOpen(target) },
                ) {
                    Text(
                        text = when (lang) {
                            AppLanguage.GREEK -> "Επιβεβαίωση στο "
                            AppLanguage.ALBANIAN -> "Verifiko në "
                            AppLanguage.ITALIAN -> "Verifica su "
                            else -> "Verify on "
                        } + link.operatorId.uppercase() + "  ↗",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun HellenicTrainCard(
    lang: AppLanguage,
    onBuy: (String) -> Unit,
) {
    DashboardCard(title = if (lang == AppLanguage.GREEK) "Hellenic Train" else "Hellenic Train") {
        Text(
            text = when (lang) {
                AppLanguage.GREEK -> "Εισιτήρια Προαστιακού (A1–A4), InterCity και υπεραστικών αμαξοστοιχιών απευθείας από την Hellenic Train."
                AppLanguage.ALBANIAN -> "Bileta për Trenat periferik (A1–A4), InterCity dhe ndërqytetës direkt nga Hellenic Train."
                AppLanguage.ITALIAN -> "Biglietti per treni suburbani (A1-A4), InterCity e interurbani direttamente da Hellenic Train."
                else -> "Tickets for Suburban (A1–A4), InterCity and intercity trains directly from Hellenic Train."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onBuy("https://tickets.hellenictrain.gr/") },
                modifier = Modifier.weight(1f),
            ) {
                Text(when (lang) {
                    AppLanguage.GREEK -> "Αγορά εισιτηρίου ↗"
                    AppLanguage.ALBANIAN -> "Bli biletë ↗"
                    AppLanguage.ITALIAN -> "Acquista biglietto ↗"
                    else -> "Buy ticket ↗"
                })
            }
            OutlinedButton(
                onClick = { onBuy("https://www.hellenictrain.gr/") },
                modifier = Modifier.weight(1f),
            ) {
                Text(when (lang) {
                    AppLanguage.GREEK -> "Πληροφορίες ↗"
                    AppLanguage.ALBANIAN -> "Informacione ↗"
                    AppLanguage.ITALIAN -> "Informazioni ↗"
                    else -> "Info ↗"
                })
            }
        }
    }
}

private fun formatEurDesktop(value: Double): String {
    val cents = kotlin.math.round(value * 100.0).toLong()
    val euros = cents / 100
    val rem = (cents % 100).toString().padStart(2, '0')
    return "€$euros.$rem"
}

@Composable
private fun DashboardCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun StatPill(
    value: String,
    label: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RouteOption(
    title: String,
    metric: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = metric,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ScheduleRow(
    line: Line,
    minutes: Int,
    lang: AppLanguage,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(line.color.toComposeColor()),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.localizedName(lang),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${line.terminalA} ${L.TO.text(lang)} ${line.terminalB}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = com.syrmos.core.designsystem.component.formatMinutesAway(minutes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun LineBadge(
    line: Line,
    lang: AppLanguage = AppLanguage.ENGLISH,
    disruptionSeverity: AlertSeverity? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(line.color.toComposeColor().copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LineColorIndicator(
            lineColor = line.color,
            size = 8.dp,
            disruptionSeverity = disruptionSeverity,
        )
        Text(
            text = line.localizedName(lang),
            style = MaterialTheme.typography.labelMedium,
            color = line.color.toComposeColor(),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun Line.localizedName(lang: AppLanguage): String {
    if (lang == AppLanguage.GREEK && nameEl.isNotBlank()) return nameEl
    if (lang == AppLanguage.ITALIAN) return name
        .replace("Line ", "Linea ")
        .replace("Suburban ", "Suburbano ")
    if (lang == AppLanguage.ALBANIAN) return name
        .replace("Line ", "Linja ")
        .replace("Suburban ", "Periferik ")
    return name
}

@Suppress("unused")
private fun LineType.label(): String = when (this) {
    LineType.METRO -> "Metro"
    LineType.TRAM -> "Tram"
    LineType.SUBURBAN -> "Suburban"
    LineType.BUS -> "Bus"
    LineType.SCENIC -> "Scenic"
}
