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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.syrmos.core.designsystem.theme.MetroBlue
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
 * Bottom sheet that lets the user pick any train to track. The flow is a
 * linear four-step drill-down (Line -> Direction -> Station -> Departure)
 * because that mirrors how commuters actually think about a train they
 * want to catch: "which line", "which way", "from which station", "which
 * specific departure". Each step is a plain list so it works cleanly on
 * iOS, Android, and Web via the shared Compose runtime.
 *
 * The sheet stays open across steps so back navigation is one tap. Picking
 * a departure calls onTrack with a fully-hydrated [TrackedDeparture] and
 * dismisses the sheet.
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

    var step by remember { mutableStateOf(PickStep.LINE) }
    var selectedLine by remember { mutableStateOf<Line?>(null) }
    var selectedDirection by remember { mutableStateOf<Direction?>(null) }
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
            getNextDepartures.invoke(
                stationId = station.id,
                lineId = line.id,
                direction = dir,
                limit = 8,
            ).collectLatest { departures = it }
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
                        PickStep.LINE -> PickStep.LINE
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
                PickStep.LINE -> LineList(
                    lines = lines,
                    lang = lang,
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
                    onSelect = {
                        selectedStation = it
                        step = PickStep.DEPARTURE
                    },
                )
                PickStep.DEPARTURE -> DepartureList(
                    departures = departures,
                    lang = lang,
                    onSelect = { dep ->
                        val line = selectedLine ?: return@DepartureList
                        val station = selectedStation ?: return@DepartureList
                        val nowEpoch = Clock.System.now().epochSeconds
                        val destination = when (dep.direction) {
                            Direction.OUTBOUND -> line.terminalB
                            Direction.INBOUND -> line.terminalA
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
                                    direction = dep.direction,
                                    lang = lang,
                                ),
                            ),
                        )
                        onDismiss()
                    },
                )
            }
        }
    }
}

private enum class PickStep { LINE, DIRECTION, STATION, DEPARTURE }

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
        if (step != PickStep.LINE) {
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
                PickStep.LINE -> pickLineHeader(lang)
                PickStep.DIRECTION -> "${selectedLine?.id ?: ""} · ${pickDirectionHeader(lang)}"
                PickStep.STATION -> "${selectedLine?.id ?: ""} · ${pickStationHeader(lang)}"
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
private fun LineList(
    lines: List<Line>,
    lang: AppLanguage,
    onSelect: (Line) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(lines) { line ->
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
    onSelect: (Direction) -> Unit,
) {
    val accent = line.color.toComposeColor()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DirectionRow(
            label = "${L.TO.text(lang)} ${line.terminalB}",
            accent = accent,
            onClick = { onSelect(Direction.OUTBOUND) },
        )
        DirectionRow(
            label = "${L.TO.text(lang)} ${line.terminalA}",
            accent = accent,
            onClick = { onSelect(Direction.INBOUND) },
        )
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
                        text = dep.time,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${dep.minutesAway} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = trackVerbLabel(lang),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MetroBlue,
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

private fun pickLineHeader(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Επίλεξε γραμμή"
    AppLanguage.ALBANIAN -> "Zgjidh linjën"
    else -> "Pick a line"
}
private fun pickDirectionHeader(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Κατεύθυνση"
    AppLanguage.ALBANIAN -> "Drejtimi"
    else -> "Direction"
}
private fun pickStationHeader(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Σταθμός"
    AppLanguage.ALBANIAN -> "Stacion"
    else -> "Station"
}
private fun noDeparturesLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Δεν υπάρχουν επόμενες αναχωρήσεις."
    AppLanguage.ALBANIAN -> "S'ka nisje të radhës."
    else -> "No upcoming departures."
}
private fun trackVerbLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Παρακολούθηση"
    AppLanguage.ALBANIAN -> "Ndiq"
    else -> "Track"
}
