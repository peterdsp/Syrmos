package com.syrmos.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.syrmos.core.data.sync.ScheduleSyncRepository
import com.syrmos.core.network.SyrmosSchedulesService
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

private data class TimetableEntry(
    val time: String,
    val timeMinutes: Int,
    val lineId: String,
    val direction: String,
    val isPast: Boolean,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen() {
    val sync = koinInject<ScheduleSyncRepository>()
    val bundles by sync.lineBundles.collectAsState()

    val lineIds = listOf("M1", "M2", "M3", "T6", "T7", "A1", "A2", "A3", "A4")
    var selectedLineId by remember { mutableStateOf("M3") }
    var selectedDayOffset by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }

    val zone = remember { TimeZone.of("Europe/Athens") }
    val nowLocal: LocalDateTime = remember(selectedDayOffset) {
        Clock.System.now().toLocalDateTime(zone)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Timetables") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LinePickerRow(
                lineIds = lineIds,
                selected = selectedLineId,
                onSelect = { selectedLineId = it },
            )
            Spacer(Modifier.height(8.dp))
            DayPickerRow(
                offset = selectedDayOffset,
                onSelect = { selectedDayOffset = it },
                today = nowLocal.date,
            )
            Spacer(Modifier.height(8.dp))
            SearchField(value = search, onChange = { search = it })

            val entries = projectDay(
                bundle = bundles[selectedLineId],
                selectedDayOffset = selectedDayOffset,
                now = nowLocal,
                lineId = selectedLineId,
            )
            val filtered = if (search.isBlank()) entries
            else entries.filter {
                it.direction.contains(search, ignoreCase = true)
                    || it.lineId.contains(search, ignoreCase = true)
            }

            HorizontalDivider()
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No departures available for this selection.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(filtered) { entry -> EntryRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun LinePickerRow(
    lineIds: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(lineIds) { lineId ->
            val isSelected = lineId == selected
            val color = colorForLine(lineId)
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) color.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onSelect(lineId) },
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(1.dp, color)
                } else null,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = lineId,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayPickerRow(offset: Int, onSelect: (Int) -> Unit, today: LocalDate) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(7) { o ->
            val day = today.plus(o)
            val dow = day.dayOfWeek.shortName()
            val isSelected = offset == o
            Surface(
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(54.dp)
                    .clickable { onSelect(o) },
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                } else null,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(dow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${day.dayOfMonth}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        placeholder = { Text("Search destination (Airport, Syntagma...)") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear")
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Search,
        ),
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
    )
}

@Composable
private fun EntryRow(entry: TimetableEntry) {
    val color = colorForLine(entry.lineId)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (entry.isPast) 0.3f else 1f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Line ${entry.lineId.removeSuffix("_AIR")}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = entry.direction.ifBlank { "—" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = entry.time,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (entry.isPast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), thickness = 0.5.dp)
}

private fun colorForLine(lineId: String): Color = when (lineId) {
    "M1" -> Color(0xFF00843D)
    "M2" -> Color(0xFFE61E2A)
    "M3", "M3_AIR" -> Color(0xFF0083C9)
    "T6", "T7" -> Color(0xFFF39800)
    else -> Color(0xFFEE2625)  // suburban red
}

private fun DayOfWeek.shortName(): String = when (this) {
    DayOfWeek.MONDAY -> "MON"; DayOfWeek.TUESDAY -> "TUE"; DayOfWeek.WEDNESDAY -> "WED"
    DayOfWeek.THURSDAY -> "THU"; DayOfWeek.FRIDAY -> "FRI"
    DayOfWeek.SATURDAY -> "SAT"; DayOfWeek.SUNDAY -> "SUN"
    else -> ""
}

private fun projectDay(
    bundle: SyrmosSchedulesService.LineSchedule?,
    selectedDayOffset: Int,
    now: LocalDateTime,
    lineId: String,
): List<TimetableEntry> {
    if (bundle == null) return emptyList()
    val targetDate = now.date.plus(selectedDayOffset)
    val mmdd = buildString {
        append(targetDate.monthNumber.toString().padStart(2, '0'))
        append('-')
        append(targetDate.dayOfMonth.toString().padStart(2, '0'))
    }
    val holiday = mapOf(
        "01-01" to "sun", "05-01" to "sun", "10-28" to "sun",
        "12-25" to "sun", "12-26" to "sun",
        "08-15" to "aug_15", "12-24" to "dec_24_31", "12-31" to "dec_24_31",
        "01-02" to "sat", "01-06" to "sat", "11-17" to "sat",
    )[mmdd]
    val dayType = holiday ?: when (targetDate.dayOfWeek) {
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY -> "mon_thu"
        DayOfWeek.FRIDAY -> "fri"
        DayOfWeek.SATURDAY -> "sat"
        DayOfWeek.SUNDAY -> "sun"
        else -> "mon_thu"
    }
    val rule = bundle.rules.firstOrNull { it.dayType == dayType } ?: return emptyList()
    val openM = rule.openTime.toMinutesOfDay() ?: 0
    val closeM = rule.closeTime.toMinutesOfDay() ?: (24 * 60)
    val effClose = if (closeM <= openM) closeM + 24 * 60 else closeM
    val nowMinutes = now.time.hour * 60 + now.time.minute

    val out = mutableListOf<TimetableEntry>()
    val direction = bundle.lineId.let { lid ->
        if (lid == "M3_AIR") "Airport"
        else when (lid) {
            "M1" -> "Kifissia <-> Piraeus"
            "M2" -> "Anthoupoli <-> Elliniko"
            "M3" -> "Dimotiko Theatro <-> Plakentias"
            "T6" -> "Syntagma <-> Pikrodafni"
            "T7" -> "Akti Poseidonos <-> Asklipiio Voulas"
            "A1" -> "Piraeus <-> Airport"
            "A2" -> "Ano Liosia <-> Airport"
            "A3" -> "Athens <-> Chalcis"
            "A4" -> "Piraeus <-> Kiato"
            else -> ""
        }
    }

    bundle.bands.filter { it.dayType == dayType }
        .sortedBy { it.timeStart.toMinutesOfDay() ?: 0 }
        .forEach { band ->
            val rawStart = band.timeStart.toMinutesOfDay() ?: return@forEach
            val rawEnd = band.timeEnd.toMinutesOfDay() ?: return@forEach
            if (band.headwayMinutes <= 0) return@forEach
            var slot = rawStart.toDouble()
            val end = rawEnd.toDouble()
            while (slot <= end) {
                val slotMin = kotlin.math.round(slot).toInt()
                if (rule.is247 || (slotMin in openM..effClose)) {
                    val display = ((slotMin % (24 * 60)) + 24 * 60) % (24 * 60)
                    val hh = (display / 60).toString().padStart(2, '0')
                    val mm = (display % 60).toString().padStart(2, '0')
                    out += TimetableEntry(
                        time = "$hh:$mm",
                        timeMinutes = slotMin,
                        lineId = lineId,
                        direction = direction,
                        isPast = selectedDayOffset == 0 && slotMin < nowMinutes,
                        label = band.label,
                    )
                }
                slot += band.headwayMinutes
            }
        }
    return out
}

private fun String.toMinutesOfDay(): Int? {
    val parts = split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

private fun LocalDate.plus(days: Int): LocalDate {
    var date = this
    repeat(kotlin.math.abs(days)) {
        date = if (days >= 0) date.nextDay() else date.prevDay()
    }
    return date
}

private fun LocalDate.nextDay(): LocalDate {
    val dim = daysInMonth(year, monthNumber)
    return if (dayOfMonth < dim) LocalDate(year, monthNumber, dayOfMonth + 1)
    else if (monthNumber < 12) LocalDate(year, monthNumber + 1, 1)
    else LocalDate(year + 1, 1, 1)
}

private fun LocalDate.prevDay(): LocalDate {
    return if (dayOfMonth > 1) LocalDate(year, monthNumber, dayOfMonth - 1)
    else if (monthNumber > 1) LocalDate(year, monthNumber - 1, daysInMonth(year, monthNumber - 1))
    else LocalDate(year - 1, 12, 31)
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}

