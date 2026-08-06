package com.syrmos.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.StationNameTranslator
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import com.syrmos.core.designsystem.theme.tokens.SyrmosTypographyTokens
import com.syrmos.core.domain.usecase.UpcomingDeparture
import com.syrmos.core.model.alerts.AlertSeverity
import com.syrmos.core.model.location.NearestStationResult
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.weather.WeatherSnapshot
import com.syrmos.core.network.RailNewsItem
import com.syrmos.core.network.STASYAnnouncement
import com.syrmos.core.network.STASYServiceStatus
import kotlin.math.roundToInt

@Composable
internal fun PulseContextTag(text: String, color: Color) {
    Text(
        text = text.uppercase(),
        style = SyrmosTypographyTokens.contextTag,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
internal fun PulseActionChip(
    icon: String,
    label: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = if (enabled) 0.14f else 0.07f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = icon, style = MaterialTheme.typography.labelMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun pulseContextLabel(
    lang: AppLanguage,
    isTracking: Boolean,
    isDisrupted: Boolean,
    isLateNight: Boolean,
): String = when {
    isTracking -> localized(lang, "In transit", "Σε διαδρομή", "Në udhëtim", "In viaggio")
    isDisrupted -> localized(lang, "Service disruption", "Διακοπή υπηρεσίας", "Ndërprerje shërbimi", "Disservizio")
    isLateNight -> localized(lang, "Last trains tonight", "Τελευταίοι συρμοί", "Trenat e fundit sonte", "Ultimi treni stasera")
    else -> {
        val hour = com.syrmos.core.common.extensions.currentAthensTime().hour
        when (hour) {
            in 5..10 -> localized(lang, "Morning commute", "Πρωινή μετακίνηση", "Udhëtimi i mëngjesit", "Viaggio mattutino")
            in 17..20 -> localized(lang, "Evening return", "Βραδινή επιστροφή", "Kthimi i mbrëmjes", "Rientro serale")
            else -> localized(lang, "Your Ichnos status", "Η κατασταση Ichnos", "Gjendja jote Ichnos", "Il tuo stato Ichnos")
        }
    }
}

internal fun lastTrainsTonightLabel(lang: AppLanguage): String =
    localized(lang, "Last trains tonight", "Τελευταίοι συρμοί απόψε", "Trenat e fundit sonte", "Ultimi treni stasera")

internal fun disruptionPulseMessage(lang: AppLanguage): String = localized(
    lang,
    "A service update affects this line. The latest verified detail is below.",
    "Μια ενημέρωση υπηρεσίας επηρεάζει αυτή τη γραμμή. Η τελευταία επιβεβαιωμένη πληροφορία είναι παρακάτω.",
    "Një përditësim shërbimi prek këtë linjë. Detajet e fundit të verifikuara janë më poshtë.",
    "Un aggiornamento di servizio interessa questa linea. I dettagli verificati sono qui sotto.",
)

internal fun weatherPulseMessage(snapshot: WeatherSnapshot, lang: AppLanguage): String {
    val temperature = snapshot.current.temperatureC.roundToInt()
    val condition = when {
        snapshot.current.condition.isSevere -> localized(lang, "Severe weather", "Έντονα καιρικά φαινόμενα", "Mot i rëndë", "Maltempo intenso")
        snapshot.current.condition.isWet -> localized(lang, "Rain may affect outdoor platforms", "Η βροχή μπορεί να επηρεάσει τις υπαίθριες αποβάθρες", "Shiu mund të ndikojë platformat e jashtme", "La pioggia può influire sui binari all'aperto")
        else -> localized(lang, "No weather impact on service", "Χωρίς επίδραση του καιρού", "Pa ndikim të motit në shërbim", "Nessun impatto meteo sul servizio")
    }
    return "$temperature° · $condition"
}

@Composable
internal fun LivingMapStrip(
    station: NearestStationResult?,
    line: Line?,
    liveTrainCount: Int,
    lang: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = line?.color?.toComposeColor() ?: SyrmosColorTokens.brand
    val surfaceColor = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val y = size.height * 0.62f
            val start = size.width * 0.08f
            val end = size.width * 0.92f
            drawLine(accent.copy(alpha = 0.7f), Offset(start, y), Offset(end, y), strokeWidth = 8f)
            repeat(6) { index ->
                val x = start + (end - start) * index / 5f
                drawCircle(surfaceColor, 8f, Offset(x, y))
                drawCircle(accent, 8f, Offset(x, y), style = Stroke(width = 3f))
            }
            val trainX = start + (end - start) * 0.58f
            drawCircle(accent, 15f, Offset(trainX, y))
            drawCircle(Color.White, 5f, Offset(trainX, y))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localized(lang, "Living map", "Ζωντανός χάρτης", "Harta e gjallë", "Mappa viva"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                station?.let {
                    Text(
                        text = StationNameTranslator.localizeEnglish(it.stationName, lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = localized(lang, "$liveTrainCount live", "$liveTrainCount ζωντανά", "$liveTrainCount live", "$liveTrainCount live"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text("↗", color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

private data class HomeInsight(
    val title: String,
    val summary: String,
    val source: String,
    val timestamp: String,
    val url: String,
    val color: Color,
)

@Composable
internal fun InsightsStream(
    announcements: List<STASYAnnouncement>,
    news: List<RailNewsItem>,
    status: STASYServiceStatus?,
    lang: AppLanguage,
    onOpenUrl: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val items = buildList {
        status?.takeIf { it.rawMessage.isNotBlank() || it.rawMessageEn.isNotBlank() }?.let {
            add(
                HomeInsight(
                    title = localized(lang, "Network status", "Κατάσταση δικτύου", "Gjendja e rrjetit", "Stato della rete"),
                    summary = it.localizedMessage(lang),
                    source = "Syrmos",
                    timestamp = "",
                    url = "",
                    color = if (it.isAlert) SyrmosColorTokens.warning else SyrmosColorTokens.live,
                ),
            )
        }
        announcements.sortedByDescending { it.isServiceAlert }.forEach { item ->
            add(
                HomeInsight(
                    title = item.localizedTitle(lang),
                    summary = item.localizedSummary(lang),
                    source = "STASY",
                    timestamp = item.date,
                    url = item.url,
                    color = if (item.isServiceAlert) SyrmosColorTokens.warning else SyrmosColorTokens.brand,
                ),
            )
        }
        news.forEach { item ->
            add(
                HomeInsight(
                    title = item.localizedTitle(lang),
                    summary = item.localizedSummary(lang),
                    source = "Hellenic Train",
                    timestamp = item.publishedAt.take(10),
                    url = item.url,
                    color = SyrmosColorTokens.national,
                ),
            )
        }
    }
    if (items.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = localized(lang, "What matters now", "Τι έχει σημασία τώρα", "Çfarë ka rëndësi tani", "Cosa conta adesso"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            if (items.size > 2) {
                Text(
                    text = if (expanded) localized(lang, "Show less", "Λιγότερα", "Trego më pak", "Mostra meno")
                    else localized(lang, "Show all", "Όλα", "Trego të gjitha", "Mostra tutto"),
                    style = MaterialTheme.typography.labelMedium,
                    color = SyrmosColorTokens.brand,
                    modifier = Modifier.clickable { expanded = !expanded }.padding(8.dp),
                )
            }
        }
        items.take(if (expanded) 8 else 2).forEach { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (item.url.isNotBlank()) Modifier.clickable { onOpenUrl(item.url) } else Modifier),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(item.color))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (item.summary.isNotBlank()) {
                            Text(
                                item.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            listOf(item.source, item.timestamp).filter { it.isNotBlank() }.joinToString(" · "),
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
internal fun RadialNearbySection(
    stations: List<NearestStationResult>,
    lines: List<Line>,
    departures: List<UpcomingDeparture>,
    lineDisruptions: Map<String, AlertSeverity>,
    lang: AppLanguage,
    onStationClick: (String) -> Unit,
) {
    var listMode by remember { mutableStateOf(false) }
    var selectedIndex by remember(stations) { mutableStateOf(0) }
    val selected = stations.getOrNull(selectedIndex)
    val orbitColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = localized(lang, "Around you", "Γύρω σου", "Rreth teje", "Intorno a te"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (listMode) localized(lang, "Radial", "Ακτινικά", "Radiale", "Radiale")
                else localized(lang, "List", "Λίστα", "Listë", "Elenco"),
                style = MaterialTheme.typography.labelMedium,
                color = SyrmosColorTokens.brand,
                modifier = Modifier.clickable { listMode = !listMode }.padding(8.dp),
            )
        }

        if (listMode) {
            stations.take(4).forEach { station ->
                NearbyListRow(station, lines, lang) { onStationClick(station.stationId) }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    drawCircle(orbitColor, radius = size.minDimension * 0.20f, center = center, style = Stroke(2f))
                    drawCircle(orbitColor, radius = size.minDimension * 0.38f, center = center, style = Stroke(2f))
                    drawCircle(SyrmosColorTokens.brand.copy(alpha = 0.18f), radius = 22f, center = center)
                    drawCircle(SyrmosColorTokens.brand, radius = 9f, center = center)
                }
                stations.take(3).forEachIndexed { index, station ->
                    val placement = when (index) {
                        0 -> Modifier.align(Alignment.TopEnd).offset(x = (-26).dp, y = 36.dp)
                        1 -> Modifier.align(Alignment.BottomStart).offset(x = 30.dp, y = (-30).dp)
                        else -> Modifier.align(Alignment.BottomEnd).offset(x = (-52).dp, y = (-18).dp)
                    }
                    val color = station.lineIds.firstOrNull()
                        ?.let { id -> lines.firstOrNull { it.id == id }?.color?.toComposeColor() }
                        ?: SyrmosColorTokens.brand
                    Column(
                        modifier = placement
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { selectedIndex = index }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(if (selectedIndex == index) 18.dp else 14.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                        Text(
                            StationNameTranslator.localizeEnglish(station.stationName, lang),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(formatNearbyDistance(station.distanceMeters), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            selected?.let { station ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onStationClick(station.stationId) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                StationNameTranslator.localizeEnglish(station.stationName, lang),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(formatNearbyDistance(station.distanceMeters), style = MaterialTheme.typography.labelMedium)
                        }
                        departures.filter { it.lineId in station.lineIds }.take(2).forEach { departure ->
                            val disrupted = lineDisruptions[departure.lineId]
                            Text(
                                text = "${departure.lineId} · ${formatCountdown(departure.minutesAway, lang)}" +
                                    if (disrupted != null && disrupted.rank > 0) " · ⚠" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyListRow(
    station: NearestStationResult,
    lines: List<Line>,
    lang: AppLanguage,
    onClick: () -> Unit,
) {
    val color = station.lineIds.firstOrNull()
        ?.let { id -> lines.firstOrNull { it.id == id }?.color?.toComposeColor() }
        ?: SyrmosColorTokens.brand
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(
            StationNameTranslator.localizeEnglish(station.stationName, lang),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(formatNearbyDistance(station.distanceMeters), style = MaterialTheme.typography.labelSmall)
    }
}

private fun localizedValue(
    lang: AppLanguage,
    en: String,
    el: String,
    sq: String,
    it: String,
): String = when (lang) {
    AppLanguage.GREEK -> el.ifBlank { en }
    AppLanguage.ALBANIAN -> sq.ifBlank { en }
    AppLanguage.ITALIAN -> it.ifBlank { en }
    else -> en.ifBlank { el }
}

private fun localized(lang: AppLanguage, en: String, el: String, sq: String, it: String): String = when (lang) {
    AppLanguage.GREEK -> el
    AppLanguage.ALBANIAN -> sq
    AppLanguage.ITALIAN -> it
    else -> en
}

private fun formatNearbyDistance(meters: Int): String = when {
    meters < 1000 -> "$meters m"
    else -> "${meters / 100 / 10.0} km"
}
