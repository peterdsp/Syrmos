package com.syrmos.feature.lines

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import com.syrmos.core.designsystem.component.LineColorIndicator
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.domain.usecase.GetLineDetailUseCase
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineColor
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LineDetailUiState(
    val line: Line? = null,
    val stations: List<Station> = emptyList(),
    val isLoading: Boolean = true,
)

class LineDetailViewModel(
    private val getLineDetailUseCase: GetLineDetailUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(LineDetailUiState())
    val uiState: StateFlow<LineDetailUiState> = _uiState.asStateFlow()

    fun loadLine(lineId: String) {
        _uiState.update { it.copy(isLoading = true) }
        scope.launch {
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineDetailScreen(
    viewModel: LineDetailViewModel,
    onStationClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val line = uiState.line

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(line?.name ?: "") })
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (line != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                // Header card
                item {
                    LineDetailHeader(line = line, stationCount = uiState.stations.size)
                }

                // Stations section header
                item {
                    Text(
                        text = "Stations",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                    )
                }

                // Station rows
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
                    text = "$stationCount stations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
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
        // Vertical line indicator with dot
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar segment
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(12.dp)
                    .background(if (isFirst) Color.Transparent else color),
            )
            // Station dot
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
                        // Stroke ring drawn via layering: outer colored circle + inner white
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
            // Bottom bar segment
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(12.dp)
                    .background(if (isLast) Color.Transparent else color),
            )
        }

        // Station info
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

        // Interchange line dots
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

private fun lineIdToColor(lineId: String): LineColor = when {
    lineId == "M1" -> LineColor.GREEN
    lineId == "M2" -> LineColor.RED
    lineId == "M3" -> LineColor.BLUE
    lineId.startsWith("T") -> LineColor.TRAM_ORANGE
    else -> LineColor.SUBURBAN_PURPLE
}
