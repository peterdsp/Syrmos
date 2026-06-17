package com.syrmos.feature.schedule

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.sync.ScheduleSyncRepository
import com.syrmos.core.network.SyrmosSchedulesService
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

/// Airport-focused departures. Replaces the previous full-timetables
/// browser. Today's next-departures for the M3 airport branch
/// (M3_AIR) and the A1 Suburban Piraeus <-> Airport line, split by
/// direction into "To Airport" and "From Airport". No line picker,
/// no day picker, no search box: a single decision-free screen for
/// someone trying to catch a plane.

private data class AirportDeparture(
    val time: String,
    val timeMinutes: Int,
    val lineId: String,
    val isOutbound: Boolean,
    val isPast: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen() {
    val sync = koinInject<ScheduleSyncRepository>()
    val bundles by sync.lineBundles.collectAsState()
    val lang by LocalizationManager.language.collectAsState()

    val zone = remember { TimeZone.of("Europe/Athens") }
    val now: LocalDateTime = remember(bundles) {
        Clock.System.now().toLocalDateTime(zone)
    }

    val m3AirDepartures = remember(bundles, now) {
        projectAirport(bundles["M3_AIR"], now)
    }
    val a1Departures = remember(bundles, now) {
        projectAirport(bundles["A1"], now)
    }

    val toAirport = (m3AirDepartures + a1Departures)
        .filter { it.isOutbound && !it.isPast }
        .sortedBy { it.timeMinutes }
        .take(8)
    val fromAirport = (m3AirDepartures + a1Departures)
        .filter { !it.isOutbound && !it.isPast }
        .sortedBy { it.timeMinutes }
        .take(8)

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(when (lang) {
                    AppLanguage.GREEK -> "Αεροδρόμιο"
                    AppLanguage.ALBANIAN -> "Aeroporti"
                    else -> "Airport"
                })
            })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionHeader(
                    title = when (lang) {
                        AppLanguage.GREEK -> "Προς Αεροδρόμιο"
                        AppLanguage.ALBANIAN -> "Drejt Aeroportit"
                        else -> "To Airport"
                    },
                    accent = Color(0xFF0083C9),
                )
            }
            if (toAirport.isEmpty()) {
                item { EmptyRow(lang) }
            } else {
                items(toAirport) { d -> DepartureRow(d, now) }
            }

            item { Spacer(Modifier.height(16.dp)) }

            item {
                SectionHeader(
                    title = when (lang) {
                        AppLanguage.GREEK -> "Από Αεροδρόμιο"
                        AppLanguage.ALBANIAN -> "Nga Aeroporti"
                        else -> "From Airport"
                    },
                    accent = Color(0xFFE08A00),
                )
            }
            if (fromAirport.isEmpty()) {
                item { EmptyRow(lang) }
            } else {
                items(fromAirport) { d -> DepartureRow(d, now) }
            }

            item {
                Text(
                    text = when (lang) {
                        AppLanguage.GREEK -> "Επόμενα δρομολόγια για/από το Αεροδρόμιο (Μετρό Γρ. 3 και Προαστιακός Α1)."
                        AppLanguage.ALBANIAN -> "Nisjet e ardhshme drejt/nga Aeroporti (Metro Linja 3 dhe Treni periferik A1)."
                        else -> "Next departures to and from Athens Airport (Metro Line 3 and Suburban A1)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Flight,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptyRow(lang: AppLanguage) {
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

@Composable
private fun DepartureRow(d: AirportDeparture, now: LocalDateTime) {
    val nowMinutes = now.time.hour * 60 + now.time.minute
    val minsAway = (d.timeMinutes - nowMinutes).coerceAtLeast(0)
    val color = colorForLine(d.lineId)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lineDisplayName(d.lineId),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = d.time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (minsAway <= 1) "now" else "$minsAway min",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    minsAway <= 2 -> Color(0xFF2E7D32)
                    minsAway <= 5 -> Color(0xFFE65100)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

private fun lineDisplayName(lineId: String): String = when (lineId) {
    "M3_AIR", "M3" -> "Metro Line 3"
    "A1" -> "Suburban A1"
    else -> lineId
}

private fun colorForLine(lineId: String): Color = when (lineId) {
    "M1" -> Color(0xFF00843D)
    "M2" -> Color(0xFFE61E2A)
    "M3", "M3_AIR" -> Color(0xFF0083C9)
    "T6", "T7" -> Color(0xFFF39800)
    else -> Color(0xFFEE2625)
}

private fun projectAirport(
    bundle: SyrmosSchedulesService.LineSchedule?,
    now: LocalDateTime,
): List<AirportDeparture> {
    if (bundle == null) return emptyList()
    val today = now.date
    val dayType = resolveDayType(today)
    val rule = bundle.rules.firstOrNull { it.dayType == dayType } ?: return emptyList()
    val openM = rule.openTime.toMinutesOfDay() ?: 0
    val closeM = rule.closeTime.toMinutesOfDay() ?: (24 * 60)
    val effClose = if (closeM <= openM) closeM + 24 * 60 else closeM
    val nowMinutes = now.time.hour * 60 + now.time.minute

    val out = mutableListOf<AirportDeparture>()
    bundle.bands
        .filter { it.dayType == dayType }
        .filter { it.label.contains("airport", ignoreCase = true) || bundle.lineId == "M3_AIR" }
        .sortedBy { it.timeStart.toMinutesOfDay() ?: 0 }
        .forEach { band ->
            val rawStart = band.timeStart.toMinutesOfDay() ?: return@forEach
            val rawEnd = band.timeEnd.toMinutesOfDay() ?: return@forEach
            if (band.headwayMinutes <= 0) return@forEach
            val isOutbound = band.direction?.equals("outbound", ignoreCase = true)
                ?: true  // A1 is bidirectional service; default to outbound when unspecified
            var slot = rawStart.toDouble()
            val end = rawEnd.toDouble()
            while (slot <= end) {
                val slotMin = kotlin.math.round(slot).toInt()
                if (rule.is247 || (slotMin in openM..effClose)) {
                    val display = ((slotMin % (24 * 60)) + 24 * 60) % (24 * 60)
                    val hh = (display / 60).toString().padStart(2, '0')
                    val mm = (display % 60).toString().padStart(2, '0')
                    out += AirportDeparture(
                        time = "$hh:$mm",
                        timeMinutes = slotMin,
                        lineId = bundle.lineId,
                        isOutbound = isOutbound,
                        isPast = slotMin < nowMinutes,
                    )
                }
                slot += band.headwayMinutes
            }
        }
    return out
}

private fun resolveDayType(date: LocalDate): String {
    val mmdd = buildString {
        append(date.monthNumber.toString().padStart(2, '0'))
        append('-')
        append(date.dayOfMonth.toString().padStart(2, '0'))
    }
    val holiday = mapOf(
        "01-01" to "sun", "05-01" to "sun", "10-28" to "sun",
        "12-25" to "sun", "12-26" to "sun",
        "08-15" to "aug_15", "12-24" to "dec_24_31", "12-31" to "dec_24_31",
        "01-02" to "sat", "01-06" to "sat", "11-17" to "sat",
    )[mmdd]
    if (holiday != null) return holiday
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
