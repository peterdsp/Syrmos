package com.syrmos.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import com.syrmos.core.designsystem.component.SectionHeader
import com.syrmos.core.designsystem.component.StationListItem
import com.syrmos.core.model.transit.LineColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStationClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Syrmos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Live Athens rail times",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (uiState.nearestStations.isNotEmpty()) {
                item { SectionHeader(title = "Nearby Stations") }
                items(uiState.nearestStations) { nearest ->
                    StationListItem(
                        stationName = nearest.stationName,
                        lineColors = nearest.lineIds.mapNotNull { lineIdToColor(it) },
                        isInterchange = nearest.lineIds.size > 1,
                        distanceText = formatDistance(nearest.distanceMeters),
                        onClick = { onStationClick(nearest.stationId) },
                    )
                }
            }

            if (uiState.upcomingDepartures.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(title = "Next Departures")
                }
                items(uiState.upcomingDepartures) { departure ->
                    DepartureCard(
                        lineName = departure.lineId,
                        lineColor = lineIdToColor(departure.lineId) ?: LineColor.BLUE,
                        direction = departure.direction.name.lowercase()
                            .replaceFirstChar { it.uppercase() },
                        minutesAway = departure.minutesAway,
                        departureTime = departure.time,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            if (uiState.nearestStations.isEmpty() && uiState.upcomingDepartures.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Text(
                            text = "Select a station to see departures",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Browse Lines tab or enable GPS to find nearby stations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun lineIdToColor(lineId: String): LineColor? = when {
    lineId == "M1" -> LineColor.GREEN
    lineId == "M2" -> LineColor.RED
    lineId == "M3" -> LineColor.BLUE
    lineId.startsWith("T") -> LineColor.TRAM_ORANGE
    lineId.startsWith("P") -> LineColor.SUBURBAN_PURPLE
    else -> null
}

private fun formatDistance(meters: Int): String = when {
    meters < 1000 -> "${meters}m"
    else -> "${"%.1f".format(meters / 1000.0)}km"
}
