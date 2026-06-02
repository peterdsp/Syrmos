package com.syrmos.feature.stations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syrmos.core.designsystem.component.DepartureCard
import com.syrmos.core.designsystem.component.LineColorIndicator
import com.syrmos.core.designsystem.component.SectionHeader
import com.syrmos.core.model.transit.LineColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDetailScreen(
    viewModel: StationDetailViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.stationName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (uiState.stationNameEl.isNotEmpty()) {
                            Text(
                                text = uiState.stationNameEl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uiState.connectingLines.isNotEmpty()) {
                item { SectionHeader(title = "Lines at this station") }
                items(uiState.connectingLines) { line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LineColorIndicator(lineColor = line.color, size = 14.dp)
                        Text(
                            text = "${line.name} (${line.terminalA} - ${line.terminalB})",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (uiState.departures.isNotEmpty()) {
                item { SectionHeader(title = "Next departures") }
                items(uiState.departures) { departure ->
                    DepartureCard(
                        lineName = departure.lineId,
                        lineColor = lineIdToColor(departure.lineId),
                        direction = departure.direction.name.lowercase()
                            .replaceFirstChar { it.uppercase() },
                        minutesAway = departure.minutesAway,
                        departureTime = departure.time,
                        modifier = Modifier.padding(horizontal = 16.dp),
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
    lineId.startsWith("P") -> LineColor.SUBURBAN_PURPLE
    else -> LineColor.BLUE
}
