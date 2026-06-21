package com.syrmos.feature.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Flight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.sync.ScheduleSyncRepository
import com.syrmos.core.data.sync.StationOffsetsRepository
import com.syrmos.core.network.SyrmosSchedulesService
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

/**
 * Airport-focused departures screen, ported to feature parity with iOS:
 *   - 7-day pill row at the top to pick today/+1/.../+6
 *   - Line picker card (Metro M3 / Suburban A1 / Suburban A2)
 *   - Station picker card filtered to the chosen line's stops
 *   - Auto-snaps to the user's nearest airport-serving stop on first appear
 *   - Two glass cards "To Airport" / "From Airport" with one featured row
 *     plus Earlier / All-upcoming expand pills
 *   - Per-station passage times via StationOffsetsRepository
 *   - Last-train destinations honour STASY's `lastTrains` short-turn data
 *
 * Self-contained: keeps projection + view-model state in this file so the
 * KMP shared module doesn't grow a dedicated DI binding for what's
 * fundamentally one screen.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen() {
    val sync = koinInject<ScheduleSyncRepository>()
    val offsetsRepo = koinInject<StationOffsetsRepository>()
    val bundles by sync.lineBundles.collectAsState()
    offsetsRepo.offsets.collectAsState() // observe so we recompose when offsets land
    val lang by LocalizationManager.language.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch { sync.hydrateFromBundleIfNeeded() }
        scope.launch { offsetsRepo.hydrateFromBundleIfNeeded() }
    }

    var dayOffset by remember { mutableStateOf(0) }
    var selectedLineId by remember { mutableStateOf("M3") }
    var selectedStationId by remember { mutableStateOf(defaultStationIdFor("M3")) }

    val zone = remember { TimeZone.of("Europe/Athens") }
    val now: LocalDateTime = remember(dayOffset, bundles) {
        Clock.System.now().toLocalDateTime(zone)
    }

    val departures = remember(bundles, dayOffset, selectedStationId, now) {
        AirportProjection.compute(
            bundles = bundles,
            offsets = offsetsRepo,
            stationId = selectedStationId,
            dayOffset = dayOffset,
            now = now,
        )
    }
    val toAirport = departures.filter { it.isAirportBound }
    val fromAirport = departures.filter { !it.isAirportBound }
    val isToday = dayOffset == 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 76.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DayPickerRow(selected = dayOffset, onSelect = { dayOffset = it }, lang = lang)
            LinePickerCard(
                selectedLineId = selectedLineId,
                lang = lang,
                onSelect = { newLine ->
                    selectedLineId = newLine
                    selectedStationId = defaultStationIdFor(newLine)
                },
            )
            StationPickerCard(
                lineId = selectedLineId,
                selectedStationId = selectedStationId,
                lang = lang,
                onSelect = { selectedStationId = it },
            )
            AirportSection(
                kind = AirportSectionKind.TO,
                departures = toAirport,
                isToday = isToday,
                nowMinutes = now.time.hour * 60 + now.time.minute,
                lang = lang,
            )
            AirportSection(
                kind = AirportSectionKind.FROM,
                departures = fromAirport,
                isToday = isToday,
                nowMinutes = now.time.hour * 60 + now.time.minute,
                lang = lang,
            )
        }

        com.syrmos.core.designsystem.component.CompactTabHeader(
            title = when (lang) {
                AppLanguage.GREEK -> "Αεροδρόμιο"
                AppLanguage.ALBANIAN -> "Aeroporti"
                else -> "Airport"
            },
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
            DayOfWeek.MONDAY -> "HËN"; DayOfWeek.TUESDAY -> "MAR"; DayOfWeek.WEDNESDAY -> "MËR"
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
    selectedLineId: String,
    lang: AppLanguage,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = lineGroupLabel(selectedLineId, lang)
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = lineColor(selectedLineId).copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Train, contentDescription = null,
                        tint = lineColor(selectedLineId), modifier = Modifier.size(20.dp))
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
                        value = current,
                        onValueChange = {},
                        readOnly = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        for (line in AIRPORT_LINES) {
                            DropdownMenuItem(
                                text = { Text(lineGroupLabel(line, lang)) },
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
    lineId: String,
    selectedStationId: String,
    lang: AppLanguage,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val stations = stationsForLine(lineId)
    val current = stations.firstOrNull { it.id == selectedStationId }?.displayName(lang) ?: selectedStationId
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
                        value = current,
                        onValueChange = {},
                        readOnly = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        for (s in stations) {
                            DropdownMenuItem(
                                text = { Text(s.displayName(lang)) },
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

private enum class AirportSectionKind { TO, FROM }

@Composable
private fun AirportSection(
    kind: AirportSectionKind,
    departures: List<AirportDeparture>,
    isToday: Boolean,
    nowMinutes: Int,
    lang: AppLanguage,
) {
    val past = if (isToday) departures.filter { it.timeMinutes < nowMinutes } else emptyList()
    val upcoming = if (isToday) departures.filter { it.timeMinutes >= nowMinutes } else departures
    val featured = upcoming.firstOrNull()
    var mode by remember(kind) { mutableStateOf(ExpandMode.FEATURED) }
    val accent = when (kind) {
        AirportSectionKind.TO -> Color(0xFF0083C9)
        AirportSectionKind.FROM -> Color(0xFFE08A00)
    }

    GlassCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Flight, contentDescription = null, tint = accent,
                            modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sectionTitle(kind, lang),
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
                        AppLanguage.ALBANIAN -> "Nuk ka nisje të disponueshme."
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
                            AppLanguage.ALBANIAN -> "Më parë"
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
                            AppLanguage.GREEK -> "Όλα τα επόμενα"
                            AppLanguage.ALBANIAN -> "Të gjitha"
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
private fun FeaturedRow(d: AirportDeparture, isToday: Boolean, accent: Color, lang: AppLanguage) {
    val nowMin = Clock.System.now().toLocalDateTime(TimeZone.of("Europe/Athens"))
        .let { it.time.hour * 60 + it.time.minute }
    val minsAway = (d.timeMinutes - nowMin).coerceAtLeast(0)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = lineColor(d.lineId).copy(alpha = 0.2f),
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Train, contentDescription = null, tint = lineColor(d.lineId),
                    modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = lineDisplayName(d.lineId),
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
private fun ExpandedRow(d: AirportDeparture, isToday: Boolean, accent: Color, lang: AppLanguage) {
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
                text = lineDisplayName(d.lineId),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = directionLine(d.destinationLabel, lang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
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

// MARK: - i18n strings

private fun sectionTitle(kind: AirportSectionKind, lang: AppLanguage): String = when (kind to lang) {
    AirportSectionKind.TO to AppLanguage.GREEK -> "Προς Αεροδρόμιο"
    AirportSectionKind.TO to AppLanguage.ALBANIAN -> "Drejt Aeroportit"
    AirportSectionKind.TO to AppLanguage.ENGLISH -> "To Airport"
    AirportSectionKind.FROM to AppLanguage.GREEK -> "Από Αεροδρόμιο"
    AirportSectionKind.FROM to AppLanguage.ALBANIAN -> "Nga Aeroporti"
    AirportSectionKind.FROM to AppLanguage.ENGLISH -> "From Airport"
    else -> if (kind == AirportSectionKind.TO) "To Airport" else "From Airport"
}

private fun upcomingSubtitle(n: Int, lang: AppLanguage): String = when (lang) {
    AppLanguage.GREEK -> "$n επόμενα δρομολόγια"
    AppLanguage.ALBANIAN -> "$n nisje të radhës"
    else -> "$n upcoming departures"
}

private fun directionLine(dest: String, lang: AppLanguage): String = when (lang) {
    AppLanguage.GREEK -> "προς $dest"
    AppLanguage.ALBANIAN -> "drejt $dest"
    else -> "towards $dest"
}

private fun nowLabel(lang: AppLanguage): String = when (lang) {
    AppLanguage.GREEK -> "Τώρα"
    AppLanguage.ALBANIAN -> "Tani"
    else -> "Now"
}

private fun lineDisplayName(lineId: String): String = when (lineId) {
    "M3", "M3_AIR" -> "Line 3"
    "A1" -> "Suburban A1"
    "A2" -> "Suburban A2"
    else -> lineId
}

private fun lineColor(lineId: String): Color = when (lineId) {
    "M1" -> Color(0xFF00843D)
    "M2" -> Color(0xFFE61E2A)
    "M3", "M3_AIR" -> Color(0xFF0083C9)
    "T6", "T7" -> Color(0xFFF39800)
    else -> Color(0xFFEE2625) // suburban red
}

private val AIRPORT_LINES = listOf("M3", "A1", "A2")

private fun lineGroupLabel(lineId: String, lang: AppLanguage): String = when (lineId to lang) {
    "M3" to AppLanguage.GREEK -> "Μετρό Γραμμή 3"
    "M3" to AppLanguage.ALBANIAN -> "Metroja Linja 3"
    "M3" to AppLanguage.ENGLISH -> "Metro Line 3"
    "A1" to AppLanguage.GREEK -> "Προαστιακός A1 (Πειραιάς - Αεροδρόμιο)"
    "A1" to AppLanguage.ALBANIAN -> "Treni periferik A1 (Pireu - Aeroporti)"
    "A1" to AppLanguage.ENGLISH -> "Suburban A1 (Piraeus - Airport)"
    "A2" to AppLanguage.GREEK -> "Προαστιακός A2 (Άνω Λιόσια - Αεροδρόμιο)"
    "A2" to AppLanguage.ALBANIAN -> "Treni periferik A2 (Ano Liosia - Aeroporti)"
    "A2" to AppLanguage.ENGLISH -> "Suburban A2 (Ano Liosia - Airport)"
    else -> lineId
}

// MARK: - Station catalogue (per-line, in service order)

private data class AirportStation(
    val id: String,
    val name: String,
    val nameEl: String,
) {
    fun displayName(lang: AppLanguage): String = if (lang == AppLanguage.GREEK) nameEl else name
}

private fun stationsForLine(lineId: String): List<AirportStation> = when (lineId) {
    "M3" -> M3_STATIONS
    "A1" -> A1_STATIONS
    "A2" -> A2_STATIONS
    else -> emptyList()
}

private fun defaultStationIdFor(lineId: String): String = stationsForLine(lineId).firstOrNull()?.id ?: ""

private val M3_STATIONS = listOf(
    AirportStation("M3_DIM", "Dimotiko Theatro", "Δημοτικό Θέατρο"),
    AirportStation("M3_AVA", "Agia Varvara", "Αγία Βαρβάρα"),
    AirportStation("M3_KOR", "Korydallos", "Κορυδαλλός"),
    AirportStation("M3_NIK", "Nikaia", "Νίκαια"),
    AirportStation("M3_MAN", "Maniatika", "Μανιάτικα"),
    AirportStation("M3_PIR", "Piraeus", "Πειραιάς"),
    AirportStation("M3_KER", "Kerameikos", "Κεραμεικός"),
    AirportStation("M3_MON", "Monastiraki", "Μοναστηράκι"),
    AirportStation("M3_SYN", "Syntagma", "Σύνταγμα"),
    AirportStation("M3_EVA", "Evangelismos", "Ευαγγελισμός"),
    AirportStation("M3_MEG", "Megaro Mousikis", "Μέγαρο Μουσικής"),
    AirportStation("M3_AMB", "Ambelokipoi", "Αμπελόκηποι"),
    AirportStation("M3_PAN", "Panormou", "Πανόρμου"),
    AirportStation("M3_KAT", "Katehaki", "Κατεχάκη"),
    AirportStation("M3_ETH", "Ethniki Amyna", "Εθνική Άμυνα"),
    AirportStation("M3_HOL", "Holargos", "Χολαργός"),
    AirportStation("M3_NOM", "Nomismatokopio", "Νομισματοκοπείο"),
    AirportStation("M3_APA", "Agia Paraskevi", "Αγία Παρασκευή"),
    AirportStation("M3_HAL", "Halandri", "Χαλάνδρι"),
    AirportStation("M3_DPL", "Doukissis Plakentias", "Δουκίσσης Πλακεντίας"),
    AirportStation("M3_PAL", "Pallini", "Παλλήνη"),
    AirportStation("M3_PEK", "Peania-Kantza", "Παιανία-Κάντζα"),
    AirportStation("M3_KRP", "Koropi", "Κορωπί"),
    AirportStation("M3_AER", "Airport", "Αεροδρόμιο"),
)

private val A1_STATIONS = listOf(
    AirportStation("A1_PIR", "Piraeus", "Πειραιάς"),
    AirportStation("A1_LEF", "Lefka", "Λεύκα"),
    AirportStation("A1_REN", "Rentis", "Ρέντης"),
    AirportStation("A1_TAV", "Tavros", "Ταύρος"),
    AirportStation("A1_ROU", "Rouf", "Ρουφ"),
    AirportStation("A1_ATH", "Athens", "Αθήνα"),
    AirportStation("A1_AAN", "Ag. Anargyroi", "Άγιοι Ανάργυροι"),
    AirportStation("A1_PYR", "Pyrgos Vasilissis", "Πύργος Βασιλίσσης"),
    AirportStation("A1_KAC", "Kato Acharnai", "Κάτω Αχαρναί"),
    AirportStation("A1_MET", "Metamorfosi", "Μεταμόρφωση"),
    AirportStation("A1_IRK", "Irakleio", "Ηράκλειο"),
    AirportStation("A1_NER", "Neratziotissa", "Νερατζιώτισσα"),
    AirportStation("A1_KIF", "Kifisias", "Κηφισίας"),
    AirportStation("A1_PEN", "Pentelis", "Πεντέλης"),
    AirportStation("A1_DPL", "Douk. Plakentias", "Δουκίσσης Πλακεντίας"),
    AirportStation("A1_PAL", "Pallini", "Παλλήνη"),
    AirportStation("A1_PEK", "Peania-Kantza", "Παιανία-Κάντζα"),
    AirportStation("A1_KRP", "Koropi", "Κορωπί"),
    AirportStation("A1_AER", "Airport", "Αεροδρόμιο"),
)

private val A2_STATIONS = listOf(
    AirportStation("A2_ANL", "Ano Liosia", "Άνω Λιόσια"),
    AirportStation("A2_ACH", "Acharnai Center", "Σιδ. Κέντρο Αχαρνών"),
    AirportStation("A2_MET", "Metamorfosi", "Μεταμόρφωση"),
    AirportStation("A2_IRK", "Irakleio", "Ηράκλειο"),
    AirportStation("A2_NER", "Neratziotissa", "Νερατζιώτισσα"),
    AirportStation("A2_KIF", "Kifisias", "Κηφισίας"),
    AirportStation("A2_PEN", "Pentelis", "Πεντέλης"),
    AirportStation("A2_DPL", "Douk. Plakentias", "Δουκίσσης Πλακεντίας"),
    AirportStation("A2_PAL", "Pallini", "Παλλήνη"),
    AirportStation("A2_PEK", "Peania-Kantza", "Παιανία-Κάντζα"),
    AirportStation("A2_KRP", "Koropi", "Κορωπί"),
    AirportStation("A2_AER", "Airport", "Αεροδρόμιο"),
)

// MARK: - Projection

internal data class AirportDeparture(
    val time: String,
    val timeMinutes: Int,
    val lineId: String,
    val isAirportBound: Boolean,
    val destinationLabel: String,
)

internal object AirportProjection {
    fun compute(
        bundles: Map<String, SyrmosSchedulesService.LineSchedule>,
        offsets: StationOffsetsRepository,
        stationId: String,
        dayOffset: Int,
        now: LocalDateTime,
    ): List<AirportDeparture> {
        val results = mutableListOf<AirportDeparture>()
        val targetDate = run {
            var d = now.date
            repeat(dayOffset) { d = d.nextDay() }
            d
        }
        val dt = resolveDayType(targetDate)
        val nowMinutes = if (dayOffset == 0) now.time.hour * 60 + now.time.minute else 0
        val isM3 = stationId.startsWith("M3")
        val isA1 = stationId.startsWith("A1")
        val isA2 = stationId.startsWith("A2")

        if (isM3) {
            bundles["M3_AIR"]?.let { bundle ->
                emitAirportSlots(
                    bundle = bundle,
                    dt = dt,
                    nowMinutes = nowMinutes,
                    stationId = stationId,
                    offsets = offsets,
                    outboundLabel = "Airport",
                    inboundLabel = "Dimotiko Theatro",
                    lineIdOut = "M3",
                    out = results,
                )
            }
        }
        if (isA1) {
            bundles["A1"]?.let { bundle ->
                emitAirportSlots(
                    bundle = bundle,
                    dt = dt,
                    nowMinutes = nowMinutes,
                    stationId = stationId,
                    offsets = offsets,
                    outboundLabel = "Airport",
                    inboundLabel = "Piraeus",
                    lineIdOut = "A1",
                    out = results,
                )
            }
        }
        if (isA2) {
            bundles["A2"]?.let { bundle ->
                emitAirportSlots(
                    bundle = bundle,
                    dt = dt,
                    nowMinutes = nowMinutes,
                    stationId = stationId,
                    offsets = offsets,
                    outboundLabel = "Airport",
                    inboundLabel = "Ano Liosia",
                    lineIdOut = "A2",
                    out = results,
                )
            }
        }

        return results.sortedBy { it.timeMinutes }
    }

    private fun emitAirportSlots(
        bundle: SyrmosSchedulesService.LineSchedule,
        dt: String,
        nowMinutes: Int,
        stationId: String,
        offsets: StationOffsetsRepository,
        outboundLabel: String,
        inboundLabel: String,
        lineIdOut: String,
        out: MutableList<AirportDeparture>,
    ) {
        val outOffset = airportStationOffset(offsets, bundle.lineId, "outbound", stationId)
        val inOffset = airportStationOffset(offsets, bundle.lineId, "inbound", stationId)

        for (band in bundle.bands) {
            if (band.dayType != dt) continue
            val rawStart = band.timeStart.toMinutesOfDay() ?: continue
            val rawEnd = band.timeEnd.toMinutesOfDay() ?: continue
            if (band.headwayMinutes <= 0) continue
            val end = rawEnd + (if (rawEnd < rawStart) 24 * 60 else 0)
            val dir = (band.direction ?: "both").lowercase()
            val directions: List<Pair<String, Int>> = when (dir) {
                "outbound" -> listOf(outboundLabel to outOffset)
                "inbound" -> listOf(inboundLabel to inOffset)
                else -> listOf(outboundLabel to outOffset, inboundLabel to inOffset)
            }
            for ((rawLabel, stationOffset) in directions) {
                var slot = rawStart.toDouble()
                while (slot <= end) {
                    val timeMin = slot.toInt() + stationOffset
                    if (timeMin >= nowMinutes) {
                        val display = ((timeMin % (24 * 60)) + 24 * 60) % (24 * 60)
                        val time = "${(display / 60).toString().padStart(2, '0')}:${(display % 60).toString().padStart(2, '0')}"
                        val override = bundle.lastTrains.firstOrNull { entry ->
                            entry.dayType == dt && entry.direction == directionKey(rawLabel, outboundLabel) &&
                                entry.fromStationId == stationId &&
                                ((entry.time.toMinutesOfDay() ?: -1) - timeMin).let { delta -> delta in -1..1 }
                        }
                        val label = override?.let { humanStationName(it.endStationId) ?: it.endStationId } ?: rawLabel
                        out.add(AirportDeparture(
                            time = time,
                            timeMinutes = timeMin,
                            lineId = lineIdOut,
                            isAirportBound = rawLabel == outboundLabel,
                            destinationLabel = label,
                        ))
                    }
                    slot += band.headwayMinutes
                }
            }
        }
    }

    private fun airportStationOffset(
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

    private fun directionKey(label: String, outboundLabel: String): String =
        if (label == outboundLabel) "outbound" else "inbound"

    private fun humanStationName(stationId: String): String? {
        val s = (M3_STATIONS + A1_STATIONS + A2_STATIONS).firstOrNull { it.id == stationId } ?: return null
        return s.name
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
