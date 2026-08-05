package com.syrmos.feature.lines

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.sync.AnnouncementsRepository
import com.syrmos.core.designsystem.animation.staggeredEntrance
import com.syrmos.core.designsystem.component.CompactTabHeader
import com.syrmos.core.designsystem.component.LineColorIndicator
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.Region
import com.syrmos.core.model.alerts.AlertSeverity
import org.koin.compose.koinInject

private data class Destination(
    val nameKey: L,
    val hookKey: L,
    val emoji: String,
    val connections: List<String>,
    val stationId: String,
    val lineId: String,
)

private val CURATED_DESTINATIONS = listOf(
    Destination(L.DEST_AIRPORT, L.DEST_AIRPORT_HOOK, "✈️", listOf("M3", "A1", "A2"), stationId = "A1_AIR", lineId = "A1"),
    Destination(L.DEST_PIRAEUS, L.DEST_PIRAEUS_HOOK, "⛴️", listOf("M1", "M3", "A1"), stationId = "M1_PIR", lineId = "M1"),
    Destination(L.DEST_MONASTIRAKI, L.DEST_MONASTIRAKI_HOOK, "🏛️", listOf("M1", "M3"), stationId = "M1_MON", lineId = "M1"),
    Destination(L.DEST_KIFISIA, L.DEST_KIFISIA_HOOK, "🌳", listOf("M1"), stationId = "M1_KIF", lineId = "M1"),
    Destination(L.DEST_THESSALONIKI, L.DEST_THESSALONIKI_HOOK, "🌆", listOf("IC", "TM1"), stationId = "GR_THE", lineId = "IC1"),
    Destination(L.DEST_METEORA, L.DEST_METEORA_HOOK, "⛰️", listOf("IC"), stationId = "KB_KAL", lineId = "KB1"),
    Destination(L.DEST_PATRAS, L.DEST_PATRAS_HOOK, "🌉", listOf("PS1"), stationId = "PA_AND", lineId = "PS1"),
    Destination(L.DEST_DIAKOPTO, L.DEST_DIAKOPTO_HOOK, "🚂", listOf("DK1"), stationId = "KI_DIA", lineId = "DK1"),
)

@Composable
fun LinesScreen(
    viewModel: LinesViewModel,
    onLineClick: (String) -> Unit = {},
    onDestinationClick: (stationId: String, lineId: String) -> Unit = { _, _ -> },
    onBrowseAllClick: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val lang by LocalizationManager.language.collectAsState()
    val announcementsRepository = koinInject<AnnouncementsRepository>()
    val lineDisruptions by announcementsRepository.lineDisruptions.collectAsState()
    var reportContext by remember { mutableStateOf<RailPulseReportContext?>(null) }
    var railPulseDestination by remember { mutableStateOf<RailPulseDestination?>(null) }

    val filteredDestinations = CURATED_DESTINATIONS.filter { destination ->
        if (uiState.searchQuery.isBlank()) return@filter true
        val query = uiState.searchQuery.lowercase()
        destination.nameKey.text(lang).lowercase().contains(query) ||
            destination.hookKey.text(lang).lowercase().contains(query) ||
            destination.stationId.lowercase().contains(query) ||
            destination.lineId.lowercase().contains(query) ||
            destination.connections.any { it.lowercase().contains(query) }
    }

    when (railPulseDestination) {
        RailPulseDestination.STATION -> RailPulseStationScreen(
            lang = lang,
            onBack = { railPulseDestination = null },
            onReport = { reportContext = it },
        )
        RailPulseDestination.TRAIN -> RailPulseTrainScreen(
            lang = lang,
            onBack = { railPulseDestination = null },
            onReport = { reportContext = it },
        )
        RailPulseDestination.CONTRIBUTION -> RailPulseContributionScreen(
            lang = lang,
            onBack = { railPulseDestination = null },
        )
        null -> Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 76.dp, end = 16.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChanged,
                    lang = lang,
                )
            }

            item {
                SegmentedControl(
                    selected = uiState.segment,
                    onSelected = viewModel::onSegmentChanged,
                    lang = lang,
                )
            }

            when (uiState.segment) {
                ExploreSegment.DESTINATIONS -> {
                    if (uiState.searchQuery.isBlank()) {
                        item {
                            ExploreRailPulseContent(
                                lang = lang,
                                onReport = { reportContext = it },
                                onOpenStation = { railPulseDestination = RailPulseDestination.STATION },
                                onOpenTrain = { railPulseDestination = RailPulseDestination.TRAIN },
                            )
                        }
                        item {
                            Text(
                                text = pulseText(lang, "Explore farther", "Εξερευνησε πιο μακρια", "Eksploro me larg", "Esplora piu lontano"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = pulseText(lang, "Search results", "Αποτελεσματα αναζητησης", "Rezultatet e kerkimit", "Risultati di ricerca"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }

                    if (filteredDestinations.isEmpty()) {
                        item {
                            Text(
                                text = pulseText(lang, "No destinations found", "Δεν βρεθηκαν προορισμοι", "Nuk u gjeten destinacione", "Nessuna destinazione trovata"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
                            )
                        }
                    }

                    itemsIndexed(filteredDestinations) { index, dest ->
                        DestinationCard(
                            destination = dest,
                            lang = lang,
                            onClick = { onDestinationClick(dest.stationId, dest.lineId) },
                            modifier = Modifier.staggeredEntrance(index),
                        )
                    }

                    item {
                        BrowseAllStationsRow(
                            lang = lang,
                            onClick = onBrowseAllClick,
                            modifier = Modifier.staggeredEntrance(filteredDestinations.size),
                        )
                    }
                }
                ExploreSegment.YOUR_NETWORK -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RegionFilterRow(
                                selectedRegion = uiState.selectedRegion,
                                onRegionSelected = viewModel::onRegionSelected,
                                lang = lang,
                            )
                            TypeFilterRow(
                                selectedType = uiState.selectedType,
                                onTypeSelected = viewModel::onTypeSelected,
                                lang = lang,
                            )
                        }
                    }

                    val filtered = uiState.lines.filter { line ->
                        val matchesRegion = uiState.selectedRegion == null || line.region == uiState.selectedRegion
                        val matchesType = uiState.selectedType == null || line.type == uiState.selectedType
                        val matchesSearch = uiState.searchQuery.isBlank() || run {
                            val q = uiState.searchQuery.lowercase()
                            line.name.lowercase().contains(q) ||
                                line.nameEl.lowercase().contains(q) ||
                                line.terminalA.lowercase().contains(q) ||
                                line.terminalB.lowercase().contains(q) ||
                                line.id.lowercase().contains(q)
                        }
                        matchesRegion && matchesType && matchesSearch
                    }

                    val grouped = filtered.groupBy { it.type }
                    val orderedTypes = listOf(LineType.METRO, LineType.TRAM, LineType.SUBURBAN, LineType.BUS, LineType.SCENIC)

                    if (filtered.isEmpty() && !uiState.isLoading) {
                        item {
                            Text(
                                text = when (lang) {
                                    AppLanguage.GREEK -> "Δεν βρεθηκαν γραμμες"
                                    AppLanguage.ALBANIAN -> "Nuk u gjeten linja"
                                    AppLanguage.ITALIAN -> "Nessuna linea trovata"
                                    else -> "No lines found"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
                            )
                        }
                    }

                    orderedTypes.forEach { type ->
                        val linesForType = grouped[type] ?: return@forEach

                        item {
                            Text(
                                text = type.localizedName(lang).uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }

                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                                tonalElevation = 1.dp,
                                shadowElevation = 2.dp,
                            ) {
                                Column {
                                    linesForType.forEachIndexed { index, line ->
                                        LineRow(
                                            line = line,
                                            lang = lang,
                                            disruptionSeverity = lineDisruptions[line.id],
                                            onClick = { onLineClick(line.id) },
                                        )
                                        if (index < linesForType.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 40.dp),
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        CompactTabHeader(
            title = L.EXPLORE.text(lang),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f),
        )

        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 28.dp).zIndex(2f),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shadowElevation = 3.dp,
        ) {
            IconButton(onClick = { railPulseDestination = RailPulseDestination.CONTRIBUTION }) {
                Icon(Icons.Filled.Person, contentDescription = pulseText(lang, "Local contribution", "Τοπικη συνεισφορα", "Kontributi lokal", "Contributo locale"))
            }
        }
    }
    }

    reportContext?.let { context ->
        RailPulseQuickReportSheet(
            context = context,
            lang = lang,
            onDismiss = { reportContext = null },
        )
    }
}

@Composable
private fun SegmentedControl(
    selected: ExploreSegment,
    onSelected: (ExploreSegment) -> Unit,
    lang: AppLanguage,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
        ) {
            ExploreSegment.entries.forEach { segment ->
                val isSelected = segment == selected
                val label = when (segment) {
                    ExploreSegment.DESTINATIONS -> pulseText(lang, "Discover", "Ανακαλυψε", "Zbulo", "Scopri")
                    ExploreSegment.YOUR_NETWORK -> pulseText(lang, "Network", "Δικτυο", "Rrjeti", "Rete")
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelected(segment) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
                    tonalElevation = if (isSelected) 2.dp else 0.dp,
                    shadowElevation = if (isSelected) 1.dp else 0.dp,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun lineColorForId(lineId: String): androidx.compose.ui.graphics.Color {
    val normalized = if (lineId.startsWith("M3")) "M3" else lineId
    return when (normalized) {
        "M1" -> SyrmosColorTokens.metroGreen
        "M2" -> SyrmosColorTokens.metroRed
        "M3" -> SyrmosColorTokens.metroBlue
        "T6", "T7" -> SyrmosColorTokens.tram
        else -> SyrmosColorTokens.suburban
    }
}

private fun linePillLabel(lineId: String): String =
    if (lineId.startsWith("M3")) "M3" else lineId

@Composable
private fun DestinationCard(
    destination: Destination,
    lang: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = lineColorForId(destination.connections.first())

    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(80.dp)
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(primaryColor),
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(primaryColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = destination.emoji,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = destination.nameKey.text(lang),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = destination.hookKey.text(lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        destination.connections.forEach { lineId ->
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = lineColorForId(lineId),
                            ) {
                                Text(
                                    text = linePillLabel(lineId),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Composable
private fun BrowseAllStationsRow(
    lang: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "📍",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = L.BROWSE_ALL_STATIONS.text(lang),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = SyrmosColorTokens.brand,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    lang: AppLanguage,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )

            Box(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (query.isEmpty()) {
                    Text(
                        text = when (lang) {
                            AppLanguage.GREEK -> "Προορισμος, σταθμος, γραμμη η τρενο..."
                            AppLanguage.ALBANIAN -> "Destinacion, stacion, linje ose tren..."
                            AppLanguage.ITALIAN -> "Destinazione, stazione, linea o treno..."
                            else -> "Destination, station, line or train..."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = when (lang) {
                            AppLanguage.GREEK -> "Καθαρισμός"
                            AppLanguage.ALBANIAN -> "Pastro"
                            AppLanguage.ITALIAN -> "Cancella"
                            else -> "Clear"
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RegionFilterRow(
    selectedRegion: Region?,
    onRegionSelected: (Region?) -> Unit,
    lang: AppLanguage,
) {
    val regions = listOf(
        null to when (lang) {
            AppLanguage.GREEK -> "Ολα"
            AppLanguage.ALBANIAN -> "Te gjitha"
            AppLanguage.ITALIAN -> "Tutte"
            else -> "All"
        },
        Region.ATHENS to when (lang) {
            AppLanguage.GREEK -> "Αθηνα"
            AppLanguage.ALBANIAN -> "Athine"
            AppLanguage.ITALIAN -> "Atene"
            else -> "Athens"
        },
        Region.THESSALONIKI to when (lang) {
            AppLanguage.GREEK -> "Θεσσαλονικη"
            AppLanguage.ALBANIAN -> "Selanik"
            AppLanguage.ITALIAN -> "Salonicco"
            else -> "Thessaloniki"
        },
        Region.PATRAS to when (lang) {
            AppLanguage.GREEK -> "Πατρα"
            AppLanguage.ALBANIAN -> "Patra"
            AppLanguage.ITALIAN -> "Patrasso"
            else -> "Patras"
        },
        Region.NATIONAL to when (lang) {
            AppLanguage.GREEK -> "Υπεραστικα"
            AppLanguage.ALBANIAN -> "Nderqytetese"
            AppLanguage.ITALIAN -> "Intercity"
            else -> "Intercity"
        },
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        regions.forEach { (region, label) ->
            FilterChip(
                selected = selectedRegion == region,
                onClick = { onRegionSelected(region) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun TypeFilterRow(
    selectedType: LineType?,
    onTypeSelected: (LineType?) -> Unit,
    lang: AppLanguage,
) {
    val types = listOf(
        null to when (lang) {
            AppLanguage.GREEK -> "Ολα"
            AppLanguage.ALBANIAN -> "Te gjitha"
            AppLanguage.ITALIAN -> "Tutti"
            else -> "All"
        },
        LineType.METRO to "Metro",
        LineType.TRAM to when (lang) {
            AppLanguage.GREEK -> "Τραμ"
            AppLanguage.ALBANIAN -> "Tramvaj"
            AppLanguage.ITALIAN -> "Tram"
            else -> "Tram"
        },
        LineType.SUBURBAN to when (lang) {
            AppLanguage.GREEK -> "Προαστιακος"
            AppLanguage.ALBANIAN -> "Periferike"
            AppLanguage.ITALIAN -> "Suburbano"
            else -> "Suburban"
        },
        LineType.BUS to when (lang) {
            AppLanguage.GREEK -> "Λεωφορεια"
            AppLanguage.ALBANIAN -> "Autobuse"
            AppLanguage.ITALIAN -> "Autobus"
            else -> "Bus"
        },
        LineType.SCENIC to when (lang) {
            AppLanguage.GREEK -> "Οδοντωτος"
            AppLanguage.ALBANIAN -> "Malore"
            AppLanguage.ITALIAN -> "Panoramico"
            else -> "Scenic"
        },
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        types.forEach { (type, label) ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onTypeSelected(type) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun LineRow(
    line: Line,
    lang: AppLanguage,
    disruptionSeverity: AlertSeverity? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineColorIndicator(
            lineColor = line.color,
            size = 12.dp,
            disruptionSeverity = disruptionSeverity,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.localizedName(lang),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${line.terminalA} - ${line.terminalB}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = when (lang) {
                AppLanguage.GREEK -> "${line.stationCount} σταθμοι"
                AppLanguage.ALBANIAN -> "${line.stationCount} stacione"
                AppLanguage.ITALIAN -> "${line.stationCount} stazioni"
                else -> "${line.stationCount} stations"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun LineType.localizedName(lang: AppLanguage): String {
    return when (this) {
        LineType.METRO -> when (lang) {
            AppLanguage.GREEK -> "Μετρο"
            AppLanguage.ALBANIAN -> "Metro"
            AppLanguage.ITALIAN -> "Metro"
            else -> "Metro"
        }
        LineType.TRAM -> when (lang) {
            AppLanguage.GREEK -> "Τραμ"
            AppLanguage.ALBANIAN -> "Tramvaj"
            AppLanguage.ITALIAN -> "Tram"
            else -> "Tram"
        }
        LineType.SUBURBAN -> when (lang) {
            AppLanguage.GREEK -> "Προαστιακος Σιδηροδρομος"
            AppLanguage.ALBANIAN -> "Hekurudha periferike"
            AppLanguage.ITALIAN -> "Ferrovia suburbana"
            else -> "Suburban Railway"
        }
        LineType.BUS -> when (lang) {
            AppLanguage.GREEK -> "Λεωφορειο (αντικατασταση)"
            AppLanguage.ALBANIAN -> "Autobus (zevendesim)"
            AppLanguage.ITALIAN -> "Autobus (sostituzione ferroviaria)"
            else -> "Bus (rail replacement)"
        }
        LineType.SCENIC -> when (lang) {
            AppLanguage.GREEK -> "Οδοντωτος Σιδηροδρομος"
            AppLanguage.ALBANIAN -> "Hekurudha malore"
            AppLanguage.ITALIAN -> "Ferrovia panoramica"
            else -> "Scenic Railway"
        }
    }
}
