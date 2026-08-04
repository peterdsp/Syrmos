package com.syrmos.feature.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.data.sync.ScheduleSyncRepository
import com.syrmos.core.data.sync.StationOffsetsRepository
import com.syrmos.core.designsystem.component.SourceConfidenceChip
import com.syrmos.core.model.schedule.SourceConfidence
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineColor
import com.syrmos.core.model.transit.Station
import com.syrmos.core.network.SyrmosSchedulesService
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

/**
 * Universal departures screen: pick any line, any station, see the next
 * trains in both directions with per-station passage times. Replaces the
 * old airport-only screen while keeping the same glass-card layout, 7-day
 * pill row, and expand/collapse interaction.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen() {
    val sync = koinInject<ScheduleSyncRepository>()
    val offsetsRepo = koinInject<StationOffsetsRepository>()
    val lineRepo = koinInject<LineRepositoryImpl>()
    val stationRepo = koinInject<StationRepositoryImpl>()
    val bundles by sync.lineBundles.collectAsState()
    offsetsRepo.offsets.collectAsState()
    val lang by LocalizationManager.language.collectAsState()
    val scope = rememberCoroutineScope()

    var allLines by remember { mutableStateOf<List<Line>>(emptyList()) }
    var stationsOnLine by remember { mutableStateOf<List<Station>>(emptyList()) }

    LaunchedEffect(Unit) {
        scope.launch { sync.hydrateFromBundleIfNeeded() }
        scope.launch { offsetsRepo.hydrateFromBundleIfNeeded() }
        val lines = lineRepo.getAllLines().firstOrNull().orEmpty()
        allLines = lines.filter { it.isOperational }
    }

    var dayOffset by remember { mutableStateOf(0) }
    var selectedLineId by remember { mutableStateOf("M3") }
    var selectedStationId by remember { mutableStateOf("") }

    LaunchedEffect(allLines) {
        if (allLines.isNotEmpty() && allLines.none { it.id == selectedLineId }) {
            selectedLineId = allLines.first().id
        }
    }

    LaunchedEffect(selectedLineId) {
        if (selectedLineId.isNotEmpty()) {
            val stations = stationRepo.getStationsOnLine(selectedLineId).firstOrNull().orEmpty()
            stationsOnLine = stations
            if (stations.isNotEmpty() && stations.none { it.id == selectedStationId }) {
                selectedStationId = stations.first().id
            }
        }
    }

    val selectedLine = allLines.firstOrNull { it.id == selectedLineId }
    val zone = remember { TimeZone.of("Europe/Athens") }
    val now: LocalDateTime = remember(dayOffset, bundles) {
        Clock.System.now().toLocalDateTime(zone)
    }

    val departures = remember(bundles, dayOffset, selectedStationId, selectedLineId, now) {
        if (selectedStationId.isEmpty() || selectedLineId.isEmpty()) emptyList()
        else DepartureProjection.compute(
            bundles = bundles,
            offsets = offsetsRepo,
            stationId = selectedStationId,
            lineId = selectedLineId,
            dayOffset = dayOffset,
            now = now,
        )
    }
    val outbound = departures.filter { it.isOutbound }
    val inbound = departures.filter { !it.isOutbound }
    val isToday = dayOffset == 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 76.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DayPickerRow(selected = dayOffset, onSelect = { dayOffset = it }, lang = lang)
            LinePickerCard(
                selectedLine = selectedLine,
                allLines = allLines,
                lang = lang,
                onSelect = { newLine ->
                    selectedLineId = newLine.id
                },
            )
            if (stationsOnLine.isNotEmpty()) {
                StationPickerCard(
                    stations = stationsOnLine,
                    selectedStationId = selectedStationId,
                    lang = lang,
                    onSelect = { selectedStationId = it },
                )
            }
            DepartureSection(
                kind = DirectionKind.OUTBOUND,
                departures = outbound,
                isToday = isToday,
                nowMinutes = now.time.hour * 60 + now.time.minute,
                lang = lang,
                destinationLabel = selectedLine?.terminalB ?: "",
                accent = lineColorFor(selectedLineId, selectedLine),
            )
            DepartureSection(
                kind = DirectionKind.INBOUND,
                departures = inbound,
                isToday = isToday,
                nowMinutes = now.time.hour * 60 + now.time.minute,
                lang = lang,
                destinationLabel = selectedLine?.terminalA ?: "",
                accent = lineColorFor(selectedLineId, selectedLine),
            )
        }

        com.syrmos.core.designsystem.component.CompactTabHeader(
            title = L.DEPARTURES.text(lang),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f),
        )
    }
}

// MARK: - Day picker

@Composable
private fun DayPickerRow(selected: Int, onSelect: (Int) -> Unit, lang: AppLanguage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (offset in 0..6) {
            val isSelected = offset == selected
            val tint = if (isSelected) Color(0xFF0083C9)
            else MaterialTheme.colorScheme.surface
            val fg = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            Surface(
                shape = CircleShape,
                color = tint,
                contentColor = fg,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .clickable { onSelect(offset) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = dayLabel(offset, lang),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = dayNumber(offset).toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = fg,
                        )
                    }
                }
            }
        }
    }
}

private fun dayLabel(offset: Int, lang: AppLanguage): String {
    if (offset == 0) return when (lang) {
        AppLanguage.GREEK -> "ΣΗΜ"
        AppLanguage.ALBANIAN -> "SOT"
        else -> "TODAY"
    }
    val date = todayPlus(offset)
    val key = date.dayOfWeek
    return when (lang) {
        AppLanguage.GREEK -> when (key) {
            DayOfWeek.MONDAY -> "ΔΕΥ"; DayOfWeek.TUESDAY -> "ΤΡΙ"; DayOfWeek.WEDNESDAY -> "ΤΕΤ"
            DayOfWeek.THURSDAY -> "ΠΕΜ"; DayOfWeek.FRIDAY -> "ΠΑΡ"; DayOfWeek.SATURDAY -> "ΣΑΒ"
            DayOfWeek.SUNDAY -> "ΚΥΡ"; else -> ""
        }
        AppLanguage.ALBANIAN -> when (key) {
            DayOfWeek.MONDAY -> "HEN"; DayOfWeek.TUESDAY -> "MAR"; DayOfWeek.WEDNESDAY -> "MER"
            DayOfWeek.THURSDAY -> "ENJ"; DayOfWeek.FRIDAY -> "PRE"; DayOfWeek.SATURDAY -> "SHT"
            DayOfWeek.SUNDAY -> "DIE"; else -> ""
        }
        else -> when (key) {
            DayOfWeek.MONDAY -> "MON"; DayOfWeek.TUESDAY -> "TUE"; DayOfWeek.WEDNESDAY -> "WED"
            DayOfWeek.THURSDAY -> "THU"; DayOfWeek.FRIDAY -> "FRI"; DayOfWeek.SATURDAY -> "SAT"
            DayOfWeek.SUNDAY -> "SUN"; else -> ""
        }
    }
}

private fun dayNumber(offset: Int): Int = todayPlus(offset).dayOfMonth

private fun todayPlus(offset: Int): LocalDate {
    var d = Clock.System.now().toLocalDateTime(TimeZone.of("Europe/Athens")).date
    repeat(offset) { d = d.nextDay() }
    return d
}

private fun LocalDate.nextDay(): LocalDate {
    val dim = daysInMonth(year, monthNumber)
    return if (dayOfMonth < dim) LocalDate(year, monthNumber, dayOfMonth + 1)
    else if (monthNumber < 12) LocalDate(year, monthNumber + 1, 1)
    else LocalDate(year + 1, 1, 1)
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}

// MARK: - Line + Station picker cards

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinePickerCard(
    selectedLine: Line?,
    allLines: List<Line>,
    lang: AppLanguage,
    onSelect: (Line) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val tint = lineColorFor(selectedLine?.id ?: "", selectedLine)
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Train, contentDescription = null,
                        tint = tint, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (lang) {
                        AppLanguage.GREEK -> "ΓΡΑΜΜΗ"
                        AppLanguage.ALBANIAN -> "LINJA"
                        else -> "LINE"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = lineDisplayLabel(selectedLine, lang),
                        onValueChange = {},
                        readOnly = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        for (line in allLines) {
                            DropdownMenuItem(
                                text = { Text(lineDisplayLabel(line, lang)) },
                                onClick = { onSelect(line); expanded = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationPickerCard(
    stations: List<Station>,
    selectedStationId: String,
    lang: AppLanguage,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = stations.firstOrNull { it.id == selectedStationId }
    val displayName = stationDisplayName(current, lang)
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (lang) {
                        AppLanguage.GREEK -> "ΣΤΑΘΜΟΣ"
                        AppLanguage.ALBANIAN -> "STACIONI"
                        else -> "STATION"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = {},
                        readOnly = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        for (s in stations) {
                            DropdownMenuItem(
                                text = { Text(stationDisplayName(s, lang)) },
                                onClick = { onSelect(s.id); expanded = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(modifier = Modifier.padding(16.dp)) { content() }
    }
}

// MARK: - Section

private enum class DirectionKind { OUTBOUND, INBOUND }

@Composable
private fun DepartureSection(
    kind: DirectionKind,
    departures: List<ProjectedDeparture>,
    isToday: Boolean,
    nowMinutes: Int,
    lang: AppLanguage,
    destinationLabel: String,
    accent: Color,
) {
    val past = if (isToday) departures.filter { it.timeMinutes < nowMinutes } else emptyList()
    val upcoming = if (isToday) departures.filter { it.timeMinutes >= nowMinutes } else departures
    val featured = upcoming.firstOrNull()
    var mode by remember(kind) { mutableStateOf(ExpandMode.FEATURED) }

    GlassCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val icon = if (kind == DirectionKind.OUTBOUND) Icons.Filled.ArrowForward else Icons.Filled.ArrowBack
                        Icon(icon, contentDescription = null, tint = accent,
                            modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = directionTitle(kind, destinationLabel, lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (upcoming.size > 1) {
                        Text(
                            text = upcomingSubtitle(upcoming.size, lang),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (featured != null) {
                FeaturedRow(featured, isToday, accent, lang)
            } else {
                Text(
                    text = when (lang) {
                        AppLanguage.GREEK -> "Δεν υπάρχουν διαθέσιμα δρομολόγια."
                        AppLanguage.ALBANIAN -> "Nuk ka nisje te disponueshme."
                        else -> "No departures available."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            val showPast = mode == ExpandMode.SHOW_PAST && past.isNotEmpty()
            val showAll = mode == ExpandMode.SHOW_ALL && upcoming.size > 1
            AnimatedVisibility(
                visible = showPast || showAll,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    val expanded = when (mode) {
                        ExpandMode.SHOW_PAST -> past.reversed()
                        ExpandMode.SHOW_ALL -> upcoming.drop(1)
                        else -> emptyList()
                    }
                    expanded.forEach { d ->
                        ExpandedRow(d, isToday, accent, lang)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isToday && past.isNotEmpty()) {
                    GlassPill(
                        label = when (lang) {
                            AppLanguage.GREEK -> "Προηγούμενα"
                            AppLanguage.ALBANIAN -> "Me pare"
                            else -> "Earlier"
                        },
                        icon = Icons.Filled.History,
                        isActive = mode == ExpandMode.SHOW_PAST,
                        accent = accent,
                        onClick = {
                            mode = if (mode == ExpandMode.SHOW_PAST) ExpandMode.FEATURED else ExpandMode.SHOW_PAST
                        },
                    )
                }
                if (upcoming.size > 1) {
                    GlassPill(
                        label = when (lang) {
                            AppLanguage.GREEK -> "Ολα τα επόμενα"
                            AppLanguage.ALBANIAN -> "Te gjitha"
                            else -> "All upcoming"
                        },
                        icon = if (mode == ExpandMode.SHOW_ALL) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                        isActive = mode == ExpandMode.SHOW_ALL,
                        accent = accent,
                        onClick = {
                            mode = if (mode == ExpandMode.SHOW_ALL) ExpandMode.FEATURED else ExpandMode.SHOW_ALL
                        },
                    )
                }
            }
        }
    }
}

private enum class ExpandMode { FEATURED, SHOW_PAST, SHOW_ALL }

@Composable
private fun FeaturedRow(d: ProjectedDeparture, isToday: Boolean, accent: Color, lang: AppLanguage) {
    val nowMin = Clock.System.now().toLocalDateTime(TimeZone.of("Europe/Athens"))
        .let { it.time.hour * 60 + it.time.minute }
    val minsAway = (d.timeMinutes - nowMin).coerceAtLeast(0)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "${d.lineId} towards ${d.destinationLabel}, $minsAway minutes, at ${d.time}"
        },
    ) {
        Surface(
            shape = CircleShape,
            color = accent.copy(alpha = 0.2f),
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Train, contentDescription = null, tint = accent,
                    modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = d.lineId,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = directionLine(d.destinationLabel, lang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
            SourceConfidenceChip(
                confidence = d.sourceConfidence,
                label = scheduleSourceLabel(d.sourceConfidence, lang),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (isToday) {
                Text(
                    text = if (minsAway <= 1) nowLabel(lang) else com.syrmos.core.designsystem.component.formatMinutesAway(minsAway),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
                Text(
                    text = d.time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = d.time,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
            }
        }
    }
}

@Composable
private fun ExpandedRow(d: ProjectedDeparture, isToday: Boolean, accent: Color, lang: AppLanguage) {
    val nowMin = Clock.System.now().toLocalDateTime(TimeZone.of("Europe/Athens"))
        .let { it.time.hour * 60 + it.time.minute }
    val minsAway = (d.timeMinutes - nowMin).coerceAtLeast(0)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = d.lineId,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = directionLine(d.destinationLabel, lang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
            SourceConfidenceChip(
                confidence = d.sourceConfidence,
                label = scheduleSourceLabel(d.sourceConfidence, lang),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isToday && minsAway > 0) {
                Text(
                    text = com.syrmos.core.designsystem.component.formatMinutesAway(minsAway),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = d.time,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun GlassPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val bg = if (isActive) accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val fg = if (isActive) Color.White else accent
    Surface(
        shape = CircleShape,
        color = bg,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) Color.Transparent else accent.copy(alpha = 0.25f),
        ),
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.Medium)
        }
    }
}

// MARK: - Helpers

private fun lineDisplayLabel(line: Line?, lang: AppLanguage): String {
    if (line == null) return ""
    return if (lang == AppLanguage.GREEK && line.nameEl.isNotBlank()) line.nameEl else line.name
}

private fun stationDisplayName(station: Station?, lang: AppLanguage): String {
    if (station == null) return ""
    return when (lang) {
        AppLanguage.GREEK -> station.nameEl.ifBlank { station.name }
        AppLanguage.ALBANIAN -> station.nameSq?.ifBlank { null } ?: station.name
        else -> station.name
    }
}

private fun lineColorFor(lineId: String, line: Line?): Color {
    return when (lineId) {
        "M1" -> Color(0xFF00843D)
        "M2" -> Color(0xFFE61E2A)
        "M3", "M3_AIR" -> Color(0xFF0083C9)
        "T6", "T7" -> Color(0xFFF39800)
        else -> line?.color?.toComposeColorOrNull() ?: Color(0xFFEE2625)
    }
}

private fun LineColor.toComposeColorOrNull(): Color? {
    val hex = this.hex
    if (hex.length < 7) return null
    val rgb = hex.removePrefix("#")
    val parsed = rgb.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or parsed)
}

// MARK: - i18n strings

private fun directionTitle(kind: DirectionKind, dest: String, lang: AppLanguage): String {
    val prefix = when (kind to lang) {
        DirectionKind.OUTBOUND to AppLanguage.GREEK -> "Προς"
        DirectionKind.OUTBOUND to AppLanguage.ALBANIAN -> "Drejt"
        DirectionKind.OUTBOUND to AppLanguage.ENGLISH -> "Towards"
        DirectionKind.INBOUND to AppLanguage.GREEK -> "Προς"
        DirectionKind.INBOUND to AppLanguage.ALBANIAN -> "Drejt"
        DirectionKind.INBOUND to AppLanguage.ENGLISH -> "Towards"
        else -> "Towards"
    }
    return "$prefix $dest"
}

private fun upcomingSubtitle(n: Int, lang: AppLanguage): String = when (lang) {
    AppLanguage.GREEK -> "$n επόμενα δρομολόγια"
    AppLanguage.ALBANIAN -> "$n nisje te radhes"
    else -> "$n upcoming departures"
}

private fun directionLine(dest: String, lang: AppLanguage): String = when (lang) {
    AppLanguage.GREEK -> "προς $dest"
    AppLanguage.ALBANIAN -> "drejt $dest"
    else -> "towards $dest"
}

private fun nowLabel(lang: AppLanguage): String = when (lang) {
    AppLanguage.GREEK -> "Τωρα"
    AppLanguage.ALBANIAN -> "Tani"
    else -> "Now"
}

// MARK: - Projection

internal data class ProjectedDeparture(
    val time: String,
    val timeMinutes: Int,
    val lineId: String,
    val isOutbound: Boolean,
    val destinationLabel: String,
    val sourceConfidence: SourceConfidence = SourceConfidence.SCHEDULED,
)

internal object DepartureProjection {
    fun compute(
        bundles: Map<String, SyrmosSchedulesService.LineSchedule>,
        offsets: StationOffsetsRepository,
        stationId: String,
        lineId: String,
        dayOffset: Int,
        now: LocalDateTime,
    ): List<ProjectedDeparture> {
        val results = mutableListOf<ProjectedDeparture>()
        val targetDate = run {
            var d = now.date
            repeat(dayOffset) { d = d.nextDay() }
            d
        }
        val dt = resolveDayType(targetDate)
        val nowMinutes = if (dayOffset == 0) now.time.hour * 60 + now.time.minute else 0

        val lineIds = if (lineId == "M3") listOf("M3", "M3_AIR") else listOf(lineId)

        for (lid in lineIds) {
            val bundle = bundles[lid] ?: continue

            if (bundle.trips.isNotEmpty()) {
                emitFromTrips(
                    bundle = bundle,
                    dt = dt,
                    nowMinutes = nowMinutes,
                    stationId = stationId,
                    lineIdOut = lineId,
                    out = results,
                )
            }

            emitFromBands(
                bundle = bundle,
                dt = dt,
                nowMinutes = nowMinutes,
                stationId = stationId,
                offsets = offsets,
                lineIdOut = lineId,
                out = results,
            )
        }

        return results.sortedBy { it.timeMinutes }
    }

    private fun emitFromBands(
        bundle: SyrmosSchedulesService.LineSchedule,
        dt: String,
        nowMinutes: Int,
        stationId: String,
        offsets: StationOffsetsRepository,
        lineIdOut: String,
        out: MutableList<ProjectedDeparture>,
    ) {
        val outOffset = stationOffset(offsets, bundle.lineId, "outbound", stationId)
        val inOffset = stationOffset(offsets, bundle.lineId, "inbound", stationId)

        for (band in bundle.bands) {
            if (band.dayType != dt) continue
            val rawStart = band.timeStart.toMinutesOfDay() ?: continue
            val rawEnd = band.timeEnd.toMinutesOfDay() ?: continue
            if (band.headwayMinutes <= 0) continue
            val end = rawEnd + (if (rawEnd < rawStart) 24 * 60 else 0)
            val dir = (band.direction ?: "both").lowercase()
            data class DirEntry(val label: String, val offset: Int, val isOutbound: Boolean)
            val directions: List<DirEntry> = when (dir) {
                "outbound" -> listOf(DirEntry(band.label.ifBlank { "outbound" }, outOffset, true))
                "inbound" -> listOf(DirEntry(band.label.ifBlank { "inbound" }, inOffset, false))
                else -> listOf(
                    DirEntry("outbound", outOffset, true),
                    DirEntry("inbound", inOffset, false),
                )
            }
            for ((label, stationOffset, isOutbound) in directions) {
                var slot = rawStart.toDouble()
                while (slot <= end) {
                    val timeMin = slot.toInt() + stationOffset
                    if (timeMin >= nowMinutes) {
                        val display = ((timeMin % (24 * 60)) + 24 * 60) % (24 * 60)
                        val time = "${(display / 60).toString().padStart(2, '0')}:${(display % 60).toString().padStart(2, '0')}"
                        val override = bundle.lastTrains.firstOrNull { entry ->
                            entry.dayType == dt && entry.direction == (if (isOutbound) "outbound" else "inbound") &&
                                entry.fromStationId == stationId &&
                                ((entry.time.toMinutesOfDay() ?: -1) - timeMin).let { delta -> delta in -1..1 }
                        }
                        val destLabel = override?.endStationId ?: label
                        out.add(ProjectedDeparture(
                            time = time,
                            timeMinutes = timeMin,
                            lineId = lineIdOut,
                            isOutbound = isOutbound,
                            destinationLabel = destLabel,
                        ))
                    }
                    slot += band.headwayMinutes
                }
            }
        }
    }

    private fun emitFromTrips(
        bundle: SyrmosSchedulesService.LineSchedule,
        dt: String,
        nowMinutes: Int,
        stationId: String,
        lineIdOut: String,
        out: MutableList<ProjectedDeparture>,
    ) {
        for (trip in bundle.trips) {
            if (trip.dayType != dt) continue
            val stop = trip.stops.firstOrNull { it.stationId == stationId } ?: continue
            val depMin = stop.departureTime.toMinutesOfDay() ?: continue
            if (depMin < nowMinutes) continue
            val display = ((depMin % (24 * 60)) + 24 * 60) % (24 * 60)
            val time = "${(display / 60).toString().padStart(2, '0')}:${(display % 60).toString().padStart(2, '0')}"
            val isOut = trip.direction.lowercase() != "inbound"
            val dest = trip.stops.lastOrNull()?.stationId ?: trip.serviceLabel
            out.add(ProjectedDeparture(
                time = time,
                timeMinutes = depMin,
                lineId = lineIdOut,
                isOutbound = isOut,
                destinationLabel = dest,
                sourceConfidence = SourceConfidence.SCHEDULED,
            ))
        }
    }

    private fun stationOffset(
        offsets: StationOffsetsRepository,
        lineId: String,
        direction: String,
        stationId: String,
    ): Int {
        val primary = offsets.offsetFor(lineId, direction, stationId)?.minutesFromOrigin ?: 0
        if (primary > 0) return primary
        if (lineId == "M3_AIR") {
            return offsets.offsetFor("M3", direction, stationId)?.minutesFromOrigin ?: 0
        }
        return 0
    }
}

private fun resolveDayType(date: LocalDate): String {
    val mmdd = "${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
    mapOf(
        "01-01" to "sun", "05-01" to "sun", "10-28" to "sun",
        "12-25" to "sun", "12-26" to "sun",
        "08-15" to "aug_15", "12-24" to "dec_24_31", "12-31" to "dec_24_31",
        "01-02" to "sat", "01-06" to "sat", "11-17" to "sat",
    )[mmdd]?.let { return it }
    return when (date.dayOfWeek) {
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY -> "mon_thu"
        DayOfWeek.FRIDAY -> "fri"
        DayOfWeek.SATURDAY -> "sat"
        DayOfWeek.SUNDAY -> "sun"
        else -> "mon_thu"
    }
}

private fun String.toMinutesOfDay(): Int? {
    val parts = split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

private fun scheduleSourceLabel(sc: SourceConfidence, lang: AppLanguage): String = when (sc) {
    SourceConfidence.LIVE -> L.LIVE.text(lang)
    SourceConfidence.SCHEDULED -> L.SOURCE_SCHEDULED.text(lang)
    SourceConfidence.ESTIMATED -> L.SOURCE_ESTIMATED.text(lang)
    SourceConfidence.OFFLINE -> L.SOURCE_OFFLINE.text(lang)
    SourceConfidence.OPERATOR_LINK -> L.SOURCE_OPERATOR.text(lang)
    SourceConfidence.UNKNOWN -> ""
}
