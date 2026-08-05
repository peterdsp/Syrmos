package com.syrmos.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.DepartureTracking
import com.syrmos.core.common.L
import com.syrmos.core.common.TrackedDeparture
import com.syrmos.core.common.TrackedRouteStop
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import com.syrmos.core.domain.usecase.GetLineDetailUseCase
import com.syrmos.core.domain.usecase.GetNextDeparturesUseCase
import com.syrmos.core.domain.usecase.UpcomingDeparture
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.Clock
import org.koin.compose.koinInject

/**
 * Bottom sheet that lets the user pick any train to track. Two modes:
 * 1. Track a specific train: Line -> Direction -> Station -> Departure
 * 2. Track all trains at a station: Line -> Direction -> Station (station mode)
 * Metro lines are grayed out in specific-train mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackPickerSheet(
    lines: List<Line>,
    lang: AppLanguage,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val getLineDetail = koinInject<GetLineDetailUseCase>()
    val getNextDepartures = koinInject<GetNextDeparturesUseCase>()

    var step by remember { mutableStateOf(PickStep.CHOICE) }
    var trackMode by remember { mutableStateOf<TrackMode?>(null) }
    var selectedLine by remember { mutableStateOf<Line?>(null) }
    var selectedDirection by remember { mutableStateOf<TrackDir?>(null) }
    var selectedStation by remember { mutableStateOf<Station?>(null) }
    var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var departures by remember { mutableStateOf<List<UpcomingDeparture>>(emptyList()) }

    LaunchedEffect(selectedLine) {
        val line = selectedLine ?: return@LaunchedEffect
        getLineDetail.invoke(line.id).collectLatest { detail ->
            stations = detail?.stations.orEmpty()
        }
    }
    LaunchedEffect(selectedStation, selectedDirection, selectedLine) {
        val line = selectedLine
        val dir = selectedDirection
        val station = selectedStation
        if (line != null && dir != null && station != null) {
            val queryDir = if (dir == TrackDir.INBOUND) Direction.INBOUND else Direction.OUTBOUND
            getNextDepartures.invoke(
                stationId = station.id,
                lineId = line.id,
                direction = queryDir,
                limit = 8,
            ).collectLatest { list ->
                departures = list.filter { d ->
                    val isAirport = d.lineId == "M3_AIR" || d.serviceType == "airport"
                    when (dir) {
                        TrackDir.AIRPORT -> isAirport
                        TrackDir.OUTBOUND -> !isAirport
                        TrackDir.INBOUND -> true
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(min = 320.dp, max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PickHeader(
                step = step,
                selectedLine = selectedLine,
                selectedStation = selectedStation,
                lang = lang,
                onBack = {
                    step = when (step) {
                        PickStep.CHOICE -> PickStep.CHOICE
                        PickStep.LINE -> {
                            trackMode = null
                            PickStep.CHOICE
                        }
                        PickStep.DIRECTION -> {
                            selectedLine = null
                            PickStep.LINE
                        }
                        PickStep.STATION -> {
                            selectedDirection = null
                            PickStep.DIRECTION
                        }
                        PickStep.DEPARTURE -> {
                            selectedStation = null
                            PickStep.STATION
                        }
                    }
                },
            )

            when (step) {
                PickStep.CHOICE -> ChoiceList(
                    lang = lang,
                    onSelect = { mode ->
                        trackMode = mode
                        step = PickStep.LINE
                    },
                )
                PickStep.LINE -> LineList(
                    lines = lines,
                    lang = lang,
                    trackMode = trackMode,
                    onSelect = {
                        selectedLine = it
                        step = PickStep.DIRECTION
                    },
                )
                PickStep.DIRECTION -> DirectionList(
                    line = selectedLine ?: return@Column,
                    lang = lang,
                    onSelect = {
                        selectedDirection = it
                        step = PickStep.STATION
                    },
                )
                PickStep.STATION -> StationList(
                    stations = stations,
                    lang = lang,
                    onSelect = { station ->
                        selectedStation = station
                        if (trackMode == TrackMode.STATION_ALL) {
                            val line = selectedLine ?: return@StationList
                            val dir = selectedDirection ?: return@StationList
                            val nowEpoch = Clock.System.now().epochSeconds
                            val routeDir = if (dir == TrackDir.INBOUND) Direction.INBOUND else Direction.OUTBOUND
                            val dirKey = when (dir) {
                                TrackDir.OUTBOUND -> "outbound"
                                TrackDir.INBOUND -> "inbound"
                                TrackDir.AIRPORT -> "airport"
                            }
                            val stationName = if (lang == AppLanguage.GREEK) station.nameEl else station.name
                            val lineIds = station.lineIds.ifEmpty { listOf(line.id) }
                            val route = computeRouteStations(
                                stations = stations,
                                targetStationId = station.id,
                                direction = routeDir,
                                lang = lang,
                            )
                            DepartureTracking.track(
                                TrackedDeparture(
                                    lineId = line.id,
                                    stationId = station.id,
                                    stationName = stationName,
                                    destination = when (dir) {
                                        TrackDir.OUTBOUND -> line.terminalB
                                        TrackDir.INBOUND -> line.terminalA
                                        TrackDir.AIRPORT -> airportLabel(lang)
                                    },
                                    scheduledTime = "",
                                    targetEpochSeconds = nowEpoch + 300L,
                                    routeStations = route,
                                    directionKey = dirKey,
                                    isStationMode = true,
                                    stationLineIds = lineIds,
                                ),
                            )
                            onDismiss()
                        } else {
                            step = PickStep.DEPARTURE
                        }
                    },
                )
                PickStep.DEPARTURE -> DepartureList(
                    departures = departures,
                    lang = lang,
                    onSelect = { dep ->
                        val line = selectedLine ?: return@DepartureList
                        val station = selectedStation ?: return@DepartureList
                        val nowEpoch = Clock.System.now().epochSeconds
                        val pickDir = selectedDirection ?: TrackDir.OUTBOUND
                        val destination = when (pickDir) {
                            TrackDir.OUTBOUND -> line.terminalB
                            TrackDir.INBOUND -> line.terminalA
                            TrackDir.AIRPORT -> airportLabel(lang)
                        }
                        val routeDir = if (pickDir == TrackDir.INBOUND) Direction.INBOUND else Direction.OUTBOUND
                        val dirKey = when (pickDir) {
                            TrackDir.OUTBOUND -> "outbound"
                            TrackDir.INBOUND -> "inbound"
                            TrackDir.AIRPORT -> "airport"
                        }
                        DepartureTracking.track(
                            TrackedDeparture(
                                lineId = line.id,
                                stationId = station.id,
                                stationName = if (lang == AppLanguage.GREEK) station.nameEl else station.name,
                                destination = destination,
                                scheduledTime = dep.time,
                                targetEpochSeconds = nowEpoch + dep.minutesAway * 60L,
                                routeStations = computeRouteStations(
                                    stations = stations,
                                    targetStationId = station.id,
                                    direction = routeDir,
                                    lang = lang,
                                ),
                                directionKey = dirKey,
                            ),
                        )
                        onDismiss()
                    },
                )
            }
        }
    }
}

private enum class PickStep { CHOICE, LINE, DIRECTION, STATION, DEPARTURE }
private enum class TrackMode { SPECIFIC_TRAIN, STATION_ALL }

private enum class TrackDir { OUTBOUND, INBOUND, AIRPORT }

private val frequentLineIds = setOf("M1", "M2", "M3", "T6", "T7")

@Composable
private fun PickHeader(
    step: PickStep,
    selectedLine: Line?,
    selectedStation: Station?,
    lang: AppLanguage,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step != PickStep.CHOICE) {
            Text(
                text = "←",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = when (step) {
                PickStep.CHOICE -> trackHeader(lang)
                PickStep.LINE -> pickLineHeader(lang)
                PickStep.DIRECTION -> "${selectedLine?.id ?: ""} - ${pickDirectionHeader(lang)}"
                PickStep.STATION -> "${selectedLine?.id ?: ""} - ${pickStationHeader(lang)}"
                PickStep.DEPARTURE -> "${selectedStation?.let { if (lang == AppLanguage.GREEK) it.nameEl else it.name } ?: ""}"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChoiceList(
    lang: AppLanguage,
    onSelect: (TrackMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onSelect(TrackMode.SPECIFIC_TRAIN) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Train,
                contentDescription = null,
                tint = SyrmosColorTokens.metroBlue,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = specificTrainTitle(lang),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = specificTrainSubtitle(lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onSelect(TrackMode.STATION_ALL) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = SyrmosColorTokens.suburban,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stationAllTitle(lang),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stationAllSubtitle(lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LineList(
    lines: List<Line>,
    lang: AppLanguage,
    trackMode: TrackMode?,
    onSelect: (Line) -> Unit,
) {
    val trackableLines = lines.filter { it.id !in frequentLineIds }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(trackableLines) { line ->
            val accent = line.color.toComposeColor()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(line) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LineBadgeSmall(id = line.id, accent = accent)
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text(
                        text = if (lang == AppLanguage.GREEK && line.nameEl.isNotBlank()) line.nameEl else line.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${line.terminalA} - ${line.terminalB}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectionList(
    line: Line,
    lang: AppLanguage,
    onSelect: (TrackDir) -> Unit,
) {
    val accent = line.color.toComposeColor()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DirectionRow(
            label = "${L.TO.text(lang)} ${line.terminalB}",
            accent = accent,
            onClick = { onSelect(TrackDir.OUTBOUND) },
        )
        DirectionRow(
            label = "${L.TO.text(lang)} ${line.terminalA}",
            accent = accent,
            onClick = { onSelect(TrackDir.INBOUND) },
        )
        if (line.id == "M3") {
            DirectionRow(
                label = "${L.TO.text(lang)} ${airportLabel(lang)}",
                accent = accent,
                onClick = { onSelect(TrackDir.AIRPORT) },
            )
        }
    }
}

@Composable
private fun DirectionRow(label: String, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(accent),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StationList(
    stations: List<Station>,
    lang: AppLanguage,
    onSelect: (Station) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(stations) { station ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(station) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (lang == AppLanguage.GREEK && station.nameEl.isNotBlank()) station.nameEl else station.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (station.isInterchange) {
                    Text(
                        text = "⇄",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DepartureList(
    departures: List<UpcomingDeparture>,
    lang: AppLanguage,
    onSelect: (UpcomingDeparture) -> Unit,
) {
    if (departures.isEmpty()) {
        Text(
            text = noDeparturesLabel(lang),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(departures) { dep ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(dep) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (dep.trainNo != null) "${dep.time}  #${dep.trainNo}" else dep.time,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatCountdown(dep.minutesAway, lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = trackVerbLabel(lang),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SyrmosColorTokens.metroBlue,
                )
            }
        }
    }
}

@Composable
private fun LineBadgeSmall(id: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = id,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

private fun trackHeader(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Παρακολούθηση"
    AppLanguage.ALBANIAN -> "Ndiq"
    AppLanguage.ITALIAN -> "Traccia"
    else -> "Track"
}
private fun pickLineHeader(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Επίλεξε γραμμή"
    AppLanguage.ALBANIAN -> "Zgjidh linjen"
    AppLanguage.ITALIAN -> "Scegli una linea"
    else -> "Pick a line"
}
private fun pickDirectionHeader(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Κατεύθυνση"
    AppLanguage.ALBANIAN -> "Drejtimi"
    AppLanguage.ITALIAN -> "Direzione"
    else -> "Direction"
}
private fun pickStationHeader(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Σταθμός"
    AppLanguage.ALBANIAN -> "Stacion"
    AppLanguage.ITALIAN -> "Stazione"
    else -> "Station"
}
private fun noDeparturesLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Δεν υπάρχουν επόμενες αναχωρήσεις."
    AppLanguage.ALBANIAN -> "S'ka nisje te radhes."
    AppLanguage.ITALIAN -> "Nessuna partenza in programma."
    else -> "No upcoming departures."
}
private fun trackVerbLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Παρακολούθηση"
    AppLanguage.ALBANIAN -> "Ndiq"
    AppLanguage.ITALIAN -> "Traccia"
    else -> "Track"
}
private fun airportLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Αεροδρόμιο"
    AppLanguage.ALBANIAN -> "Aeroporti"
    AppLanguage.ITALIAN -> "Aeroporto"
    else -> "Airport"
}
private fun specificTrainTitle(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Συγκεκριμένο δρομολόγιο"
    AppLanguage.ALBANIAN -> "Nje tren specifik"
    AppLanguage.ITALIAN -> "Un treno specifico"
    else -> "A specific train"
}
private fun specificTrainSubtitle(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Επιλέξτε γραμμή, σταθμό και δρομολόγιο"
    AppLanguage.ALBANIAN -> "Zgjidhni linjen, stacionin dhe nisjen"
    AppLanguage.ITALIAN -> "Scegli linea, stazione e partenza"
    else -> "Pick a line, station and departure"
}
private fun stationAllTitle(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Ολα τα δρομολόγια σε σταθμό"
    AppLanguage.ALBANIAN -> "Te gjitha trenet ne stacion"
    AppLanguage.ITALIAN -> "Tutti i treni in una stazione"
    else -> "All trains at a station"
}
private fun stationAllSubtitle(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Παρακολουθήστε συνεχώς τα δρομολόγια"
    AppLanguage.ALBANIAN -> "Ndiqni vazhdimisht trenet"
    AppLanguage.ITALIAN -> "Monitora le partenze in continuo"
    else -> "Continuously track departures"
}
private fun metroFrequentNote(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Το μετρό έρχεται συχνά, δεν χρειάζεται παρακολούθηση"
    AppLanguage.ALBANIAN -> "Metroja vjen shpesh, nuk ka nevoje per ndjekje"
    else -> "Metro runs frequently, no need to track"
}
