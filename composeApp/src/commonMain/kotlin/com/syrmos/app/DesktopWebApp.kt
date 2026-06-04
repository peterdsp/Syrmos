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
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.LiveSuburbanTrain
import com.syrmos.core.model.transit.SimulatedTrain
import com.syrmos.feature.home.HomeViewModel
import com.syrmos.feature.lines.LinesViewModel
import com.syrmos.feature.map.MapScreen
import com.syrmos.feature.map.MapStationNode
import com.syrmos.feature.map.MapViewModel
import org.koin.compose.koinInject

private enum class DesktopSection(
    val title: String,
    val icon: ImageVector,
) {
    Planner("Planner", Icons.Filled.Map),
    Schedules("Schedules", Icons.Filled.Schedule),
    Passes("Passes", Icons.Filled.CalendarMonth),
    Account("Account", Icons.Filled.AccountCircle),
}

@Composable
fun DesktopWebApp() {
    val mapViewModel = koinInject<MapViewModel>()
    val homeViewModel = koinInject<HomeViewModel>()
    val linesViewModel = koinInject<LinesViewModel>()
    val mapState by mapViewModel.uiState.collectAsState()
    val homeState by homeViewModel.uiState.collectAsState()
    val linesState by linesViewModel.uiState.collectAsState()
    val lang by LocalizationManager.language.collectAsState()
    var selectedSection by remember { mutableStateOf(DesktopSection.Planner) }
    var search by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        DesktopSidebar(
            selectedSection = selectedSection,
            onSectionSelected = { selectedSection = it },
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
                    )
                }

                item {
                    PlannerCard(
                        search = search,
                        onSearchChange = { search = it },
                        selectedStation = mapState.selectedStation,
                        selectedStationLines = mapState.selectedStationLines,
                        stations = mapState.mapStations,
                    )
                }

                item {
                    RouteComparisonCard(lines = linesState.lines)
                }

                item {
                    TimetableCard(lines = linesState.lines, lang = lang)
                }

                item {
                    ExportCard()
                }
            }
        }
    }
}

@Composable
private fun DesktopSidebar(
    selectedSection: DesktopSection,
    onSectionSelected: (DesktopSection) -> Unit,
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
                text = "Athens rail command center",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(18.dp))

            DesktopSection.entries.forEach { section ->
                SidebarItem(
                    section = section,
                    selected = selectedSection == section,
                    onClick = { onSectionSelected(section) },
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Network status",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Metro, tram and suburban data loaded for planning.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            text = section.title,
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
            text = "Athens transit planner",
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
) {
    DashboardCard(title = "Trip planning") {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            label = { Text("Search station or destination") },
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill(value = stations.size.toString(), label = "stations")
            StatPill(value = selectedStationLines.size.toString(), label = "lines here")
            StatPill(value = "90+", label = "accessible stops")
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedStation == null) {
            Text(
                text = "Select a station on the map to inspect lines, accessibility and next steps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            StationSummary(
                station = selectedStation,
                lines = selectedStationLines,
            )
        }
    }
}

@Composable
private fun StationSummary(
    station: MapStationNode,
    lines: List<Line>,
) {
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
                LineBadge(line)
            }
        }
        Text(
            text = if (station.isInterchange) "Transfer station" else "Direct station",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${station.stationIds.size} merged records",
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
) {
    DashboardCard(title = "Live trains") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (simulatedTrains.isNotEmpty()) {
                SectionLabel("Metro & Tram (${simulatedTrains.size} active)")
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
                                text = "Near ${train.currentStationName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Next: ${train.nextStationName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            if (liveTrains.isNotEmpty()) {
                SectionLabel("Suburban railway")
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
                    text = "No live trains available right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionLabel("Nearby or popular stations")
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
                            text = if (station.isInterchange) "Popular interchange" else "Popular stop",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${station.lineIds.size} lines",
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
private fun RouteComparisonCard(lines: List<Line>) {
    DashboardCard(title = "Route comparison") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RouteOption(
                title = "Fastest",
                metric = "18 min",
                detail = lines.firstOrNull()?.name ?: "Metro",
                modifier = Modifier.weight(1f),
            )
            RouteOption(
                title = "Fewest transfers",
                metric = "1 transfer",
                detail = lines.getOrNull(1)?.name ?: "Tram",
                modifier = Modifier.weight(1f),
            )
            RouteOption(
                title = "Best coverage",
                metric = "4 lines",
                detail = "Metro + tram",
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
    DashboardCard(title = "Schedule board") {
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
private fun ExportCard() {
    DashboardCard(title = "Export") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {}, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Print, contentDescription = "Print schedule")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Print schedule")
            }
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Download, contentDescription = "Download PDF")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download PDF")
            }
        }
    }
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
                text = "${line.terminalA} to ${line.terminalB}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "$minutes min",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun LineBadge(line: Line) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(line.color.toComposeColor().copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(line.color.toComposeColor())
                .width(8.dp)
                .height(8.dp),
        )
        Text(
            text = line.name,
            style = MaterialTheme.typography.labelMedium,
            color = line.color.toComposeColor(),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun Line.localizedName(lang: AppLanguage): String {
    return if (lang == AppLanguage.GREEK && nameEl.isNotBlank()) nameEl else name
}

@Suppress("unused")
private fun LineType.label(): String = when (this) {
    LineType.METRO -> "Metro"
    LineType.TRAM -> "Tram"
    LineType.SUBURBAN -> "Suburban"
}
