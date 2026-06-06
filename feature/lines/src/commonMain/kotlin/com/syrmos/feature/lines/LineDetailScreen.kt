package com.syrmos.feature.lines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.designsystem.component.LineColorIndicator
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.domain.usecase.GetLineDetailUseCase
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineColor
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.LiveSuburbanTrain
import com.syrmos.core.model.transit.Station
import com.syrmos.core.network.RailwayGovLiveTrackerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

data class LineDetailUiState(
    val line: Line? = null,
    val stations: List<Station> = emptyList(),
    val liveTrains: List<LiveSuburbanTrain> = emptyList(),
    val isLoading: Boolean = true,
    val isLiveTrackerLoading: Boolean = false,
)

class LineDetailViewModel(
    private val getLineDetailUseCase: GetLineDetailUseCase,
    private val liveTrackerService: RailwayGovLiveTrackerService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(LineDetailUiState())
    private var lineDetailJob: Job? = null
    private var liveTrackerJob: Job? = null

    val uiState: StateFlow<LineDetailUiState> = _uiState.asStateFlow()

    fun loadLine(lineId: String) {
        lineDetailJob?.cancel()
        liveTrackerJob?.cancel()

        _uiState.update {
            it.copy(
                isLoading = true,
                isLiveTrackerLoading = lineId.startsWith("A"),
                liveTrains = emptyList(),
            )
        }

        lineDetailJob = scope.launch {
            getLineDetailUseCase.invoke(lineId).collect { detail ->
                _uiState.update {
                    it.copy(
                        line = detail?.line,
                        stations = detail?.stations ?: emptyList(),
                        isLoading = false,
                    )
                }
            }
        }

        if (lineId.startsWith("A")) {
            liveTrackerJob = scope.launch {
                liveTrackerService.observeSuburbanTrains(lineId).collect { trains ->
                    _uiState.update {
                        it.copy(
                            liveTrains = trains,
                            isLiveTrackerLoading = false,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineDetailScreen(
    viewModel: LineDetailViewModel,
    onStationClick: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val line = uiState.line
    val lang by LocalizationManager.language.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(line?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (line != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    LineDetailHeader(
                        line = line,
                        stationCount = uiState.stations.size,
                        liveTrainCount = uiState.liveTrains.size,
                        lang = lang,
                    )
                }

                if (line.type == LineType.SUBURBAN) {
                    item {
                        SectionHeader(
                            title = L.LIVE_TRACKER.text(lang),
                            subtitle = buildLiveTrackerSubtitle(
                                trainCount = uiState.liveTrains.size,
                                isLoading = uiState.isLiveTrackerLoading,
                                lang = lang,
                            ),
                        )
                    }

                    when {
                        uiState.isLiveTrackerLoading && uiState.liveTrains.isEmpty() -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(72.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }

                        uiState.liveTrains.isEmpty() -> {
                            item {
                                Text(
                                    text = L.NO_LIVE_TRAINS.text(lang),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }

                        else -> {
                            items(uiState.liveTrains) { liveTrain ->
                                LiveTrainCard(
                                    train = liveTrain,
                                    lineColor = line.color,
                                    lang = lang,
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = L.STATIONS.text(lang),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                    )
                }

                itemsIndexed(uiState.stations) { index, station ->
                    StationRow(
                        station = station,
                        lineColor = line.color,
                        lineId = line.id,
                        isFirst = index == 0,
                        isLast = index == uiState.stations.size - 1,
                        onClick = { onStationClick(station.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LineDetailHeader(
    line: Line,
    stationCount: Int,
    liveTrainCount: Int,
    lang: AppLanguage,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineColorIndicator(lineColor = line.color, size = 16.dp)
            Column {
                Text(
                    text = "${line.terminalA} - ${line.terminalB}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (line.type == LineType.SUBURBAN && liveTrainCount > 0) {
                        "$stationCount stations, $liveTrainCount ${L.ACTIVE_TRAINS.text(lang)}"
                    } else {
                        "$stationCount stations"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String?,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LiveTrainCard(
    train: LiveSuburbanTrain,
    lineColor: LineColor,
    lang: AppLanguage,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineColorIndicator(lineColor = lineColor, size = 16.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = train.trainNumber,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = buildRouteLabel(train),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Text(
                    text = buildDelayLabel(train.delayMinutes, lang),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (train.delayMinutes > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }

            if (train.progress != null) {
                val progress = train.progress
                LinearProgressIndicator(
                    progress = { progress?.toFloat()?.coerceIn(0f, 1f) ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = lineColor.toComposeColor(),
                    trackColor = lineColor.toComposeColor().copy(alpha = 0.16f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LiveTrainMetric(
                    label = L.NEXT_STOP.text(lang),
                    value = train.nextStation ?: "GPS only",
                    modifier = Modifier.weight(1f),
                )
                LiveTrainMetric(
                    label = L.SPEED.text(lang),
                    value = buildSpeedLabel(train.speedKph),
                    modifier = Modifier.weight(1f),
                )
                LiveTrainMetric(
                    label = L.UPDATED.text(lang),
                    value = train.updatedAt.toAthensClockTime(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LiveTrainMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StationRow(
    station: Station,
    lineColor: LineColor,
    lineId: String,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = lineColor.toComposeColor()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(12.dp)
                    .background(if (isFirst) Color.Transparent else color),
            )
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (station.isInterchange) Color.White else color),
                contentAlignment = Alignment.Center,
            ) {
                if (station.isInterchange) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(color),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(12.dp)
                    .background(if (isLast) Color.Transparent else color),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = station.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = station.nameEl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (station.isInterchange) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                station.lineIds
                    .filter { it != lineId }
                    .forEach { otherLineId ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(lineIdToColor(otherLineId).toComposeColor()),
                        )
                    }
            }
        }
    }
}

private fun buildLiveTrackerSubtitle(
    trainCount: Int,
    isLoading: Boolean,
    lang: AppLanguage,
): String? {
    return when {
        trainCount > 0 -> "$trainCount ${L.ACTIVE_TRAINS.text(lang)}"
        isLoading -> null
        else -> null
    }
}

private fun buildRouteLabel(train: LiveSuburbanTrain): String {
    val origin = train.origin.orEmpty()
    val destination = train.destination.orEmpty()

    return when {
        origin.isNotBlank() && destination.isNotBlank() -> "$origin - $destination"
        destination.isNotBlank() -> destination
        origin.isNotBlank() -> origin
        else -> train.lineId
    }
}

private fun buildDelayLabel(
    delayMinutes: Int,
    lang: AppLanguage,
): String {
    return if (delayMinutes <= 0) {
        L.ON_TIME.text(lang)
    } else {
        "${L.DELAYED.text(lang)} ${delayMinutes}m"
    }
}

private fun buildSpeedLabel(speedKph: Double?): String {
    if (speedKph == null) return "--"
    return "${speedKph.roundToInt()} km/h"
}

private fun String.toAthensClockTime(): String {
    return runCatching {
        Instant.parse(this)
            .toLocalDateTime(TimeZone.of("Europe/Athens"))
            .let { localDateTime ->
                val hour = localDateTime.hour.toString().padStart(2, '0')
                val minute = localDateTime.minute.toString().padStart(2, '0')
                "$hour:$minute"
            }
    }.getOrDefault("--:--")
}

private fun lineIdToColor(lineId: String): LineColor = when {
    lineId == "M1" -> LineColor.GREEN
    lineId == "M2" -> LineColor.RED
    lineId == "M3" -> LineColor.BLUE
    lineId.startsWith("T") -> LineColor.TRAM_ORANGE
    else -> LineColor.SUBURBAN_PURPLE
}
