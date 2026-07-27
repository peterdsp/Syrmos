package com.syrmos.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalUriHandler
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
import com.syrmos.core.common.TrackedRouteStop
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import com.syrmos.core.designsystem.component.SourceConfidenceChip
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.model.schedule.SourceConfidence
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import com.syrmos.core.domain.usecase.GetLastTrainUseCase
import com.syrmos.core.domain.usecase.UpcomingDeparture
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.Station
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
    var showTrackPicker by remember { mutableStateOf(false) }
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
                val trackedLine = uiState.lines.firstOrNull { it.id == activeTrack.lineId }
                val accent = trackedLine?.color?.toComposeColor() ?: SyrmosColorTokens.metroBlue
                TrackingCard(
                    tracked = activeTrack,
                    nowEpoch = nowEpoch,
                    lang = lang,
                    lineAccent = accent,
                    onStop = { DepartureTracking.stop() },
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FreshnessPill(freshness = uiState.freshness, lang = lang)
                Spacer(modifier = Modifier.weight(1f))
                TrackAnyTrainChip(
                    lang = lang,
                    onClick = { showTrackPicker = true },
                )
            }
        }

        // Answer-hero shows the "next train" for the nearest station. When
        // the user is already tracking a specific train, the countdown lives
        // in the TrackingCard above and this card duplicates it. Hide it
        // while tracking is active so there is exactly one countdown on
        // screen. If the user wants a different next train, they can stop
        // tracking or use the Lines tab.
        if (activeTrack == null) {
            item {
                AnswerHero(
                    next = uiState.nextDeparture,
                    line = uiState.nextDepartureLine,
                    upcoming = uiState.upcomingDepartures,
                    hasLocation = uiState.nearestStations.isNotEmpty(),
                    isTracked = false,
                    lang = lang,
                    onStationClick = {
                        uiState.nearestStations.firstOrNull()?.let { onStationClick(it.stationId) }
                    },
                    onTrack = {
                        val next = uiState.nextDeparture
                        val station = uiState.nearestStations.firstOrNull()
                        if (next != null && station != null) {
                            val lineId = uiState.nextDepartureLine?.id ?: next.lineId
                            val stationsOnLine = uiState.stationsByLine[lineId].orEmpty()
                            DepartureTracking.track(
                                TrackedDeparture(
                                    lineId = lineId,
                                    stationId = station.stationId,
                                    stationName = station.stationName,
                                    destination = uiState.nextDepartureLine?.let {
                                        destinationName(it, next.direction, lang)
                                    } ?: "",
                                    scheduledTime = next.time,
                                    targetEpochSeconds = nowEpoch + next.minutesAway * 60L,
                                    routeStations = computeRouteStations(
                                        stations = stationsOnLine,
                                        targetStationId = station.stationId,
                                        direction = next.direction,
                                        lang = lang,
                                    ),
                                ),
                            )
                        }
                    },
                )
            }
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
            // Emergency weather card: only when the current condition is
            // severe (heavy showers, thunderstorm, snow). Sits ABOVE the
            // regular weather card so a user opening Home on a stormy day
            // sees the safety message before the temperature.
            if (weather.current.condition.isSevere) {
                item { EmergencyWeatherCard(condition = weather.current.condition, lang = lang) }
            }
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

    if (showTrackPicker) {
        TrackPickerSheet(
            lines = uiState.lines,
            lang = lang,
            onDismiss = { showTrackPicker = false },
        )
    }
    }
}

@Composable
private fun TrackAnyTrainChip(lang: AppLanguage, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SyrmosColorTokens.metroBlue.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = "🎯", style = MaterialTheme.typography.labelMedium)
        Text(
            text = trackAnyLabel(lang),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = SyrmosColorTokens.metroBlue,
        )
    }
}

private fun trackAnyLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Παρακολούθηση συρμού"
    AppLanguage.ALBANIAN -> "Ndiq një tren"
    else -> "Track a train"
}

/**
 * Severe-weather warning card. Animated raindrops fall out of an amber
 * cloud, headline + safety copy in the active language, and the local
 * Greek emergency numbers so the user can reach help without leaving
 * the app. Kept compact so it doesn't dominate Home; hidden by
 * HomeScreen when the current condition isn't severe.
 */
@Composable
private fun EmergencyWeatherCard(
    condition: com.syrmos.core.model.weather.WeatherCondition,
    lang: AppLanguage,
) {
    val infinite = rememberInfiniteTransition(label = "rainDrops")
    val dropOffset by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rainDropsOffset",
    )
    val dropAlpha by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rainDropsAlpha",
    )

    // Title, raindrop, and border accent. Brighter in dark mode so the thin
    // strokes and headline don't muddy against the dark card. The bg flips to
    // a deep amber-brown in dark mode so the onSurface text stays legible
    // instead of washing out light-on-cream. Badge fill stays deep orange
    // (see EmergencyNumberRow) because its white glyphs need a dark chip.
    val isDark = isSystemInDarkTheme()
    val amber = if (isDark) Color(0xFFFF9E42) else Color(0xFFE65100)
    val bg = if (isDark) Color(0xFF291705) else Color(0xFFFFF3E0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, amber.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Animated cloud + falling drop. Drop resets every 900ms so
                // the eye reads "rain falling" without a whole particle rig.
                Box(
                    modifier = Modifier.width(44.dp).height(48.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = "☁️",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 22.dp + dropOffset.dp)
                            .width(4.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(amber.copy(alpha = dropAlpha)),
                    )
                }
                Column {
                    Text(
                        text = emergencyTitle(condition, lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = amber,
                    )
                    Text(
                        text = emergencySubtitle(condition, lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                text = emergencyBody(lang),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = emergencyNumbersHeader(lang),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EmergencyNumberRow(label = "112", sub = emergencyLabel112(lang))
                EmergencyNumberRow(label = "199", sub = emergencyLabelFire(lang))
                EmergencyNumberRow(label = "11185", sub = emergencyLabelOASA(lang))
            }
            Text(
                text = tapHintLabel(lang),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmergencyNumberRow(label: String, sub: String) {
    // Tap-to-call: LocalUriHandler.openUri("tel:112") routes to the
    // dialer on Android and to the system phone handler on Web. On
    // wasmJs the browser prompts a dial-out, which still helps the
    // user reach the number even when no native dialer exists.
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { runCatching { uriHandler.openUri("tel:$label") } }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFE65100))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "☎", style = MaterialTheme.typography.labelMedium, color = Color.White)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Text(
            text = sub,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun tapHintLabel(lang: AppLanguage): String = when (lang) {
    AppLanguage.GREEK -> "Πατήστε έναν αριθμό για κλήση."
    AppLanguage.ALBANIAN -> "Prek një numër për të thirrur."
    else -> "Tap a number to call."
}

private fun emergencyTitle(condition: com.syrmos.core.model.weather.WeatherCondition, lang: AppLanguage): String {
    val storm = condition == com.syrmos.core.model.weather.WeatherCondition.THUNDERSTORM
    return when (lang) {
        AppLanguage.GREEK -> if (storm) "Καταιγίδα σε εξέλιξη" else "Έντονη κακοκαιρία"
        AppLanguage.ALBANIAN -> if (storm) "Stuhi në zhvillim" else "Mot i keq"
        else -> if (storm) "Storm in progress" else "Severe weather"
    }
}
private fun emergencySubtitle(condition: com.syrmos.core.model.weather.WeatherCondition, lang: AppLanguage): String = when (lang) {
    AppLanguage.GREEK -> "Πρόσεχε στη μετακίνηση."
    AppLanguage.ALBANIAN -> "Ki kujdes gjatë udhëtimit."
    else -> "Take care on your journey."
}
private fun emergencyBody(lang: AppLanguage): String = when (lang) {
    AppLanguage.GREEK -> "Οι υπόγειες γραμμές μετρό είναι η πιο ασφαλής επιλογή. Το τραμ και ο προαστιακός μπορεί να έχουν καθυστερήσεις. Αν χρειαστείς άμεση βοήθεια, κάλεσε:"
    AppLanguage.ALBANIAN -> "Metroja nëntokësore është zgjidhja më e sigurt. Tramvaji dhe treni periferik mund të kenë vonesa. Nëse ke nevojë për ndihmë të menjëhershme, telefono:"
    else -> "Underground metro lines are the safest option. Tram and Suburban services may run late. If you need immediate help, call:"
}
private fun emergencyNumbersHeader(lang: AppLanguage): String = when (lang) {
    AppLanguage.GREEK -> "ΤΗΛΕΦΩΝΑ ΕΚΤΑΚΤΗΣ ΑΝΑΓΚΗΣ"
    AppLanguage.ALBANIAN -> "NUMRAT E EMERGJENCËS"
    else -> "EMERGENCY NUMBERS"
}
private fun emergencyLabel112(lang: AppLanguage): String = when (lang) {
    AppLanguage.GREEK -> "Ευρωπαϊκή γραμμή έκτακτης ανάγκης"
    AppLanguage.ALBANIAN -> "Numri europian i emergjencës"
    else -> "European emergency line"
}
private fun emergencyLabelFire(lang: AppLanguage): String = when (lang) {
    AppLanguage.GREEK -> "Πυροσβεστική"
    AppLanguage.ALBANIAN -> "Zjarrfikësit"
    else -> "Fire service"
}
private fun emergencyLabelOASA(lang: AppLanguage): String = when (lang) {
    AppLanguage.GREEK -> "Πληροφορίες OASA"
    AppLanguage.ALBANIAN -> "Informacione OASA"
    else -> "OASA transit info"
}


// MARK: Answer-first hero

@Composable
private fun AnswerHero(
    next: UpcomingDeparture?,
    line: Line?,
    upcoming: List<UpcomingDeparture> = emptyList(),
    hasLocation: Boolean,
    isTracked: Boolean,
    lang: AppLanguage,
    onStationClick: () -> Unit,
    onTrack: () -> Unit,
) {
    val accent = line?.color?.toComposeColor() ?: SyrmosColorTokens.metroBlue
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
                        sourceConfidenceLabel(next.sourceConfidence, lang)?.let { chipLabel ->
                            Spacer(modifier = Modifier.height(4.dp))
                            SourceConfidenceChip(confidence = next.sourceConfidence, label = chipLabel)
                        }
                    }
                    Text(
                        text = formatCountdown(next.minutesAway, lang),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                }
                // "then 13, 23 min": the next couple of departures after the
                // featured one, so the hero answers "and after that?" at a glance
                // (matches the web hero).
                val thenTimes = upcoming.drop(1).take(2)
                    .filter { it.minutesAway > next.minutesAway }
                    .map { formatCountdown(it.minutesAway, lang) }
                if (thenTimes.isNotEmpty()) {
                    Text(
                        text = "${thenWord(lang)} ${thenTimes.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
//
// Uber-style tracking card. One card, one countdown, one big Stop button.
// Layout:
//   [ ● LIVE                     Arriving <Station> ]
//   [                                        1 min  ]
//   [ ──────────────────────  63%  progress bar ]
//   [ M3 · to Doukissis Plakentias · 13:12          ]
//   [ [        ◼  Stop tracking                  ]  ]
// The progress bar fills as the countdown ticks down, using the moment the
// user tapped Track as the "0%" anchor. No persistence needed: if the card
// leaves the composition we simply restart the anchor, which matches user
// intuition ("I opened it just now, so the bar is fresh").

@Composable
private fun TrackingCard(
    tracked: TrackedDeparture,
    nowEpoch: Long,
    lang: AppLanguage,
    lineAccent: Color,
    onStop: () -> Unit,
) {
    val remaining = tracked.minutesRemaining(nowEpoch)
    val due = tracked.isDue(nowEpoch)
    val startedAt = remember(tracked.targetEpochSeconds) { nowEpoch }
    val totalSecs = (tracked.targetEpochSeconds - startedAt).coerceAtLeast(1L)
    val elapsedSecs = (nowEpoch - startedAt).coerceAtLeast(0L)
    val rawProgress = (elapsedSecs.toFloat() / totalSecs.toFloat()).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "trackProgress",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = lineAccent.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LivePulseDot(color = lineAccent)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = L.LIVE.text(lang).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = lineAccent,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = arrivingLabel(lang, tracked.stationName),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = if (due) dueLabel(lang) else "$remaining min",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = lineAccent,
            )

            if (tracked.routeStations.size >= 2) {
                StationStrip(
                    stops = tracked.routeStations,
                    progress = progress,
                    accent = lineAccent,
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = lineAccent,
                    trackColor = lineAccent.copy(alpha = 0.20f),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(lineAccent)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = tracked.lineId,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                if (tracked.destination.isNotBlank()) {
                    Text(
                        text = "${L.TO.text(lang)} ${tracked.destination}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = tracked.scheduledTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onStop)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "◼",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stopTrackingLabel(lang),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Horizontal strip of station dots with a train marker interpolating from
 * the first dot to the last as [progress] goes 0 -> 1. The last stop is the
 * tracked station and is always highlighted. Dots the train has already
 * "passed" (index proportional to progress) get the full accent colour;
 * upcoming dots dim to a 30% wash so the eye reads left-to-right movement
 * even between minute boundaries.
 */
@Composable
private fun StationStrip(
    stops: List<TrackedRouteStop>,
    progress: Float,
    accent: Color,
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val lastIndex = stops.lastIndex
    val trainIndex = safeProgress * lastIndex

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            // Connector line behind the dots.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent.copy(alpha = 0.20f)),
            )
            // Filled portion of the connector up to the train.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(safeProgress)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            // Station dots evenly distributed across the strip.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                stops.forEachIndexed { index, _ ->
                    val passed = index <= trainIndex
                    val isTarget = index == lastIndex
                    val dotSize = if (isTarget) 14.dp else 10.dp
                    val fill = if (passed) accent else accent.copy(alpha = 0.30f)
                    Box(
                        modifier = Modifier
                            .width(dotSize)
                            .height(dotSize)
                            .clip(CircleShape)
                            .background(fill),
                    )
                }
            }
            // Train marker positioned by interpolating between dots.
            TrainMarker(
                accent = accent,
                progress = safeProgress,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            stops.forEachIndexed { index, stop ->
                if (index == 0 || index == lastIndex) {
                    Text(
                        text = stop.stationName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (index == lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (index == lastIndex) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
            }
        }
    }
}

@Composable
private fun TrainMarker(accent: Color, progress: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        // Reserve leading whitespace = progress * (fullWidth - markerWidth).
        // Simplest is a Spacer sized by weight() using a Row split.
        if (progress > 0f) {
            Spacer(modifier = Modifier.weight(progress.coerceAtLeast(0.001f)))
        }
        Text(
            text = "🚆",
            style = MaterialTheme.typography.titleSmall,
        )
        if (progress < 1f) {
            Spacer(modifier = Modifier.weight((1f - progress).coerceAtLeast(0.001f)))
        }
    }
}

/**
 * Slice up to [maxStops] stations approaching the target station in the
 * direction of travel. Result is ordered target-last so the strip reads
 * left-to-right, with earlier stops on the left and the tracked station
 * on the right.
 */
internal fun computeRouteStations(
    stations: List<Station>,
    targetStationId: String,
    direction: Direction,
    lang: AppLanguage,
    maxStops: Int = 6,
): List<TrackedRouteStop> {
    if (stations.isEmpty() || maxStops < 2) return emptyList()
    val targetIndex = stations.indexOfFirst { it.id == targetStationId }
    if (targetIndex < 0) return emptyList()

    fun label(s: Station): String =
        if (lang == AppLanguage.GREEK && s.nameEl.isNotBlank()) s.nameEl else s.name

    return when (direction) {
        Direction.OUTBOUND -> {
            val start = (targetIndex - (maxStops - 1)).coerceAtLeast(0)
            (start..targetIndex).map { i ->
                TrackedRouteStop(stations[i].id, label(stations[i]))
            }
        }
        Direction.INBOUND -> {
            val end = (targetIndex + (maxStops - 1)).coerceAtMost(stations.size - 1)
            (end downTo targetIndex).map { i ->
                TrackedRouteStop(stations[i].id, label(stations[i]))
            }
        }
    }
}

@Composable
private fun LivePulseDot(color: Color) {
    val infinite = rememberInfiniteTransition(label = "livePulse")
    val alpha by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "livePulseAlpha",
    )
    Box(
        modifier = Modifier
            .width(10.dp)
            .height(10.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}

/** Localised chip label for the hero's source-confidence, or null to hide it. */
private fun sourceConfidenceLabel(sc: SourceConfidence, lang: AppLanguage): String? = when (sc) {
    SourceConfidence.LIVE -> L.LIVE.text(lang)
    SourceConfidence.SCHEDULED -> L.SOURCE_SCHEDULED.text(lang)
    SourceConfidence.ESTIMATED -> L.SOURCE_ESTIMATED.text(lang)
    SourceConfidence.OFFLINE -> L.SOURCE_OFFLINE.text(lang)
    else -> null
}

/** "then" lead-in for the hero's follow-on departures list. */
private fun thenWord(lang: AppLanguage): String = when (lang) {
    AppLanguage.GREEK -> "μετά"
    AppLanguage.ALBANIAN -> "pastaj"
    else -> "then"
}

private fun trackLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Παρακολούθηση"; AppLanguage.ALBANIAN -> "Ndiq"; else -> "Track"
}
private fun trackingOnLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Παρακολουθείται"; AppLanguage.ALBANIAN -> "Po ndiqet"; else -> "Tracking"
}
private fun stopTrackingLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Διακοπή παρακολούθησης"
    AppLanguage.ALBANIAN -> "Ndalo ndjekjen"
    else -> "Stop tracking"
}
private fun arrivingLabel(lang: AppLanguage, station: String) = when (lang) {
    AppLanguage.GREEK -> "Φτάνει $station"
    AppLanguage.ALBANIAN -> "Po arrin $station"
    else -> "Arriving $station"
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
            color = SyrmosColorTokens.metroBlue,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = if (tramCount > 0) tramCount.toString() else "2",
            label = L.TRAM.text(lang),
            color = SyrmosColorTokens.tram,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = if (suburbanCount > 0) suburbanCount.toString() else "4",
            label = L.SUBURBAN.text(lang),
            color = SyrmosColorTokens.suburban,
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
    // Warm advisory tint for alerts. Flips to a dark warm fill in dark mode so
    // the onSurface text stays legible instead of washing out on light cream.
    val alertBg = if (isSystemInDarkTheme()) Color(0xFF2A2016) else Color(0xFFFFF3E0)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (hasUrl) Modifier.clickable { onOpenUrl(announcement.url) } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (isAlert) alertBg else MaterialTheme.colorScheme.surface,
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
