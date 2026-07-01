package com.syrmos.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.DataFreshness
import com.syrmos.core.common.DepartureTracking
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.common.TrackedDeparture
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.designsystem.theme.MetroBlue
import com.syrmos.core.designsystem.theme.SuburbanPurple
import com.syrmos.core.designsystem.theme.TramOrange
import com.syrmos.core.domain.usecase.GetLastTrainUseCase
import com.syrmos.core.domain.usecase.UpcomingDeparture
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.network.STASYAnnouncement
import com.syrmos.core.network.STASYServiceStatus
import org.koin.compose.koinInject

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStationClick: (String) -> Unit = {},
    onLineClick: (String) -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val lang by LocalizationManager.language.collectAsState()
    var showAriadne by remember { mutableStateOf(false) }
    val tracked by DepartureTracking.active.collectAsState()
    var nowEpoch by remember { mutableStateOf(Clock.System.now().epochSeconds) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowEpoch = Clock.System.now().epochSeconds
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 90.dp, end = 16.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Answer-first: the lead element is one actionable line ("Next M2 to
        // Syntagma, 4 min"). Everything else (timetable tiles, alerts, lines)
        // is demoted below it so the screen reads like a companion, not a
        // schedule. The offline-alive pill rides in the hero's eyebrow row so
        // the user always knows whether the countdown is live or predicted.
        val activeTrack = tracked
        if (activeTrack != null) {
            item {
                TrackingCard(
                    tracked = activeTrack,
                    nowEpoch = nowEpoch,
                    lang = lang,
                    onStop = { DepartureTracking.stop() },
                )
            }
        }

        item {
            FreshnessPill(freshness = uiState.freshness, lang = lang)
        }

        item {
            AnswerHero(
                next = uiState.nextDeparture,
                line = uiState.nextDepartureLine,
                hasLocation = uiState.nearestStations.isNotEmpty(),
                isTracked = activeTrack != null,
                lang = lang,
                onStationClick = {
                    uiState.nearestStations.firstOrNull()?.let { onStationClick(it.stationId) }
                },
                onTrack = {
                    val next = uiState.nextDeparture
                    val station = uiState.nearestStations.firstOrNull()
                    if (next != null && station != null) {
                        DepartureTracking.track(
                            TrackedDeparture(
                                lineId = uiState.nextDepartureLine?.id ?: next.lineId,
                                stationId = station.stationId,
                                stationName = station.stationName,
                                destination = uiState.nextDepartureLine?.let {
                                    destinationName(it, next.direction, lang)
                                } ?: "",
                                scheduledTime = next.time,
                                targetEpochSeconds = nowEpoch + next.minutesAway * 60L,
                            ),
                        )
                    }
                },
            )
        }

        val lastTrain = uiState.lastTrain
        if (lastTrain != null) {
            item {
                LastTrainTeaser(
                    lastTrain = lastTrain,
                    line = uiState.lastTrainLine,
                    lang = lang,
                )
            }
        }

        val weather = uiState.weather
        if (weather != null) {
            item { WeatherCard(snapshot = weather, lang = lang) }
        }

        // Section order mirrors iOS: alerts/news + service status appear
        // immediately under the welcome subtitle so users see operational
        // state before any of the navigation tiles.
        val alerts = uiState.announcements.filter { it.isServiceAlert }
        if (alerts.isNotEmpty()) {
            item {
                AlertsSection(
                    alerts = alerts,
                    lang = lang,
                    onOpenUrl = onOpenUrl,
                )
            }
        } else if (uiState.announcements.isNotEmpty()) {
            item {
                LatestNewsSection(
                    announcement = uiState.announcements.first(),
                    lang = lang,
                    onOpenUrl = onOpenUrl,
                )
            }
        }

        val status = uiState.serviceStatus
        // Hide the pill when an alert is already represented in the
        // serviceAlert cards above — otherwise the same banner text
        // renders twice on the home screen.
        val pillRedundant = status?.isAlert == true && alerts.isNotEmpty()
        if (status != null && !pillRedundant) {
            item {
                ServiceStatusPill(status = status, lang = lang)
            }
        }

        item {
            NetworkOverview(lines = uiState.lines, lang = lang)
        }

        if (uiState.nearestStations.isNotEmpty()) {
            item {
                NearbyStationsSection(
                    stations = uiState.nearestStations,
                    lines = uiState.lines,
                    lang = lang,
                    onStationClick = onStationClick,
                )
            }
        }

        if (uiState.error != null) {
            item {
                Text(
                    text = L.COULD_NOT_REACH.text(lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (uiState.lines.isNotEmpty()) {
            item {
                SectionTitle(text = L.LINES.text(lang))
            }

            val grouped = uiState.lines.groupBy { it.type }
            listOf(LineType.METRO, LineType.TRAM, LineType.SUBURBAN).forEach { type ->
                val linesForType = grouped[type] ?: return@forEach
                items(linesForType) { line ->
                    LineCard(
                        line = line,
                        lang = lang,
                        onClick = { onLineClick(line.id) },
                    )
                }
            }
        }
    }

    com.syrmos.core.designsystem.component.CompactTabHeader(
        title = "Syrmos",
        subtitle = L.APP_SUBTITLE.text(lang),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .zIndex(1f),
    )

    // Ask Ariadne launcher. Sits above the tab bar; opens the offline
    // assistant overlay. Hidden while the overlay is open.
    if (!showAriadne) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 150.dp)
                .zIndex(2f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable { showAriadne = true }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "🧭", style = MaterialTheme.typography.titleMedium)
            Text(
                text = askAriadneLabel(lang),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }

    if (showAriadne) {
        val assistantViewModel = koinInject<com.syrmos.feature.home.assistant.AssistantViewModel>()
        Box(modifier = Modifier.fillMaxSize().zIndex(3f)) {
            com.syrmos.feature.home.assistant.AssistantScreen(
                viewModel = assistantViewModel,
                onClose = { showAriadne = false },
                onOpenStation = { showAriadne = false; onStationClick(it) },
                onOpenLine = { showAriadne = false; onLineClick(it) },
            )
        }
    }
    }
}

private fun askAriadneLabel(lang: AppLanguage): String = when (lang) {
    AppLanguage.GREEK -> "Ρώτα την Αριάδνη"
    AppLanguage.ALBANIAN -> "Pyet Ariadne"
    else -> "Ask Ariadne"
}

// MARK: Answer-first hero

@Composable
private fun AnswerHero(
    next: UpcomingDeparture?,
    line: Line?,
    hasLocation: Boolean,
    isTracked: Boolean,
    lang: AppLanguage,
    onStationClick: () -> Unit,
    onTrack: () -> Unit,
) {
    val accent = line?.color?.toComposeColor() ?: MetroBlue
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (next != null) Modifier.clickable(onClick = onStationClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = L.NEXT_TRAIN.text(lang).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (next != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LineBadge(line = line, fallbackId = next.lineId, accent = accent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${L.TO.text(lang)} ${destinationName(line, next.direction, lang)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = next.time,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = formatCountdown(next.minutesAway, lang),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isTracked) MaterialTheme.colorScheme.surfaceVariant else accent.copy(alpha = 0.14f),
                        )
                        .clickable(enabled = !isTracked, onClick = onTrack)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(text = if (isTracked) "📍" else "🔔", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = if (isTracked) trackingOnLabel(lang) else trackLabel(lang),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isTracked) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                    )
                }
            } else {
                Text(
                    text = if (!hasLocation) {
                        L.ENABLE_LOCATION_FOR_NEXT.text(lang)
                    } else {
                        L.SERVICE_OVER.text(lang)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LineBadge(line: Line?, fallbackId: String, accent: Color) {
    val label = line?.id ?: fallbackId
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun FreshnessPill(freshness: DataFreshness, lang: AppLanguage) {
    val live = freshness == DataFreshness.LIVE
    val dot = if (live) Color(0xFF2E7D32) else Color(0xFFB26A00)
    val bg = if (live) Color(0x1A2E7D32) else Color(0x1AB26A00)
    val label = if (live) {
        L.LIVE.text(lang)
    } else {
        "${L.RUNNING_OFFLINE.text(lang)} · ${L.PREDICTED_FROM_SCHEDULE.text(lang)}"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(dot),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun LastTrainTeaser(
    lastTrain: GetLastTrainUseCase.LastTrain,
    line: Line?,
    lang: AppLanguage,
) {
    val lineLabel = line?.id ?: lastTrain.lineId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "🌙", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "${L.LAST_TRAIN.text(lang)} $lineLabel · ${L.LEAVE_BY.text(lang)} ${lastTrain.time}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun destinationName(line: Line?, direction: Direction, lang: AppLanguage): String {
    line ?: return ""
    return when (direction) {
        Direction.OUTBOUND -> line.terminalB
        Direction.INBOUND -> line.terminalA
    }
}

// MARK: Track-this-departure (Tier 2 in-app surface)

@Composable
private fun TrackingCard(
    tracked: TrackedDeparture,
    nowEpoch: Long,
    lang: AppLanguage,
    onStop: () -> Unit,
) {
    val remaining = tracked.minutesRemaining(nowEpoch)
    val due = tracked.isDue(nowEpoch)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MetroBlue.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trackingHeader(lang).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MetroBlue,
                )
                Text(
                    text = "${tracked.lineId} · ${tracked.stationName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (tracked.destination.isNotBlank()) {
                    Text(
                        text = "${L.TO.text(lang)} ${tracked.destination} · ${tracked.scheduledTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (due) dueLabel(lang) else "$remaining min",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MetroBlue,
                )
                Text(
                    text = stopLabel(lang),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onStop),
                )
            }
        }
    }
}

private fun trackLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Παρακολούθηση"; AppLanguage.ALBANIAN -> "Ndiq"; else -> "Track"
}
private fun trackingOnLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Παρακολουθείται"; AppLanguage.ALBANIAN -> "Po ndiqet"; else -> "Tracking"
}
private fun trackingHeader(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Παρακολούθηση συρμού"; AppLanguage.ALBANIAN -> "Po ndiqet treni"; else -> "Tracking your train"
}
private fun stopLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Διακοπή"; AppLanguage.ALBANIAN -> "Ndalo"; else -> "Stop"
}
private fun dueLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Τώρα"; AppLanguage.ALBANIAN -> "Tani"; else -> "Due"
}

/** Mirrors iOS Departure.minutesAwayDisplay: "Now", "5 min", "3h 21min". */
private fun formatCountdown(minutesAway: Int, lang: AppLanguage): String {
    if (minutesAway <= 1) {
        return when (lang) {
            AppLanguage.GREEK -> "Τώρα"
            AppLanguage.ALBANIAN -> "Tani"
            else -> "Now"
        }
    }
    if (minutesAway < 60) return "$minutesAway min"
    val h = minutesAway / 60
    val m = minutesAway % 60
    return if (m == 0) "${h}h" else "${h}h ${m}min"
}

@Composable
private fun ScreenHeader(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun NetworkOverview(
    lines: List<Line>,
    lang: AppLanguage,
) {
    val metroCount = lines.count { it.type == LineType.METRO }
    val tramCount = lines.count { it.type == LineType.TRAM }
    val suburbanCount = lines.count { it.type == LineType.SUBURBAN }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            value = if (metroCount > 0) metroCount.toString() else "3",
            label = L.METRO.text(lang),
            color = MetroBlue,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = if (tramCount > 0) tramCount.toString() else "2",
            label = L.TRAM.text(lang),
            color = TramOrange,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = if (suburbanCount > 0) suburbanCount.toString() else "4",
            label = L.SUBURBAN.text(lang),
            color = SuburbanPurple,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
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
private fun ServiceStatusPill(
    status: STASYServiceStatus,
    lang: AppLanguage,
) {
    val message = when (lang) {
        AppLanguage.GREEK -> status.rawMessage
        AppLanguage.ALBANIAN -> status.rawMessageSq.ifEmpty { status.rawMessageEn.ifEmpty { status.rawMessage } }
        else -> status.rawMessageEn.ifEmpty { status.rawMessage }
    }
    if (message.isBlank()) return
    val bg = if (status.isAlert) Color(0x1FFF9800) else Color(0x1A4CAF50)
    val accent = if (status.isAlert) Color(0xFFE65100) else Color(0xFF2E7D32)
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AlertsSection(
    alerts: List<STASYAnnouncement>,
    lang: AppLanguage,
    onOpenUrl: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "⚠", style = MaterialTheme.typography.titleSmall)
            SectionTitle(text = L.SERVICE_ALERTS.text(lang))
        }
        alerts.take(3).forEach { alert ->
            AlertCard(
                announcement = alert,
                isAlert = true,
                lang = lang,
                onOpenUrl = onOpenUrl,
            )
        }
    }
}

@Composable
private fun NearbyStationsSection(
    stations: List<com.syrmos.core.model.location.NearestStationResult>,
    lines: List<Line>,
    lang: AppLanguage,
    onStationClick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "📍", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.width(8.dp))
            SectionTitle(text = when (lang) {
                AppLanguage.GREEK -> "Κοντά μου"
                AppLanguage.ALBANIAN -> "Pranë meje"
                else -> "Near me"
            })
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
        stations.take(4).forEach { station ->
            val stationLines = station.lineIds.mapNotNull { lineId -> lines.firstOrNull { it.id == lineId } }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStationClick(station.stationId) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = station.stationName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = formatDistance(station.distanceMeters),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        stationLines.take(3).forEach { line ->
                            Text(
                                text = line.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = line.color.toComposeColor(),
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun LatestNewsSection(
    announcement: STASYAnnouncement,
    lang: AppLanguage,
    onOpenUrl: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "i", style = MaterialTheme.typography.titleMedium)
            SectionTitle(text = L.LATEST_FROM_STASY.text(lang))
        }
        AlertCard(
            announcement = announcement,
            isAlert = false,
            lang = lang,
            onOpenUrl = onOpenUrl,
        )
    }
}

@Composable
private fun AlertCard(
    announcement: STASYAnnouncement,
    isAlert: Boolean,
    lang: AppLanguage,
    onOpenUrl: (String) -> Unit,
) {
    val hasUrl = announcement.url.isNotBlank()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (hasUrl) Modifier.clickable { onOpenUrl(announcement.url) } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (isAlert) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.surface,
        ),
        border = if (isAlert) BorderStroke(1.dp, Color(0x33E87722)) else null,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = when (lang) {
                        AppLanguage.GREEK -> announcement.title
                        AppLanguage.ALBANIAN -> announcement.titleSq.ifEmpty { announcement.titleEn.ifEmpty { announcement.title } }
                        else -> announcement.titleEn.ifEmpty { announcement.title }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (announcement.date.isNotBlank()) {
                    Text(
                        text = announcement.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (hasUrl) {
                Text(
                    text = "↗",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun LineCard(
    line: Line,
    lang: AppLanguage,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(line.color.toComposeColor()),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.localizedName(lang),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${line.terminalA} - ${line.terminalB}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = line.stationCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "›",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            )
        }
    }
}

private fun Line.localizedName(lang: AppLanguage): String {
    return if (lang == AppLanguage.GREEK && nameEl.isNotBlank()) nameEl else name
}

private fun formatDistance(meters: Int): String = when {
    meters < 1000 -> "$meters m away"
    else -> {
        val tenths = meters / 100
        "${tenths / 10}.${tenths % 10} km away"
    }
}
