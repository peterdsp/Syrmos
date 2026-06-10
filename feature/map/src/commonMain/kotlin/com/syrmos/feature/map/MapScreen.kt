package com.syrmos.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syrmos.core.designsystem.component.DepartureCard
import com.syrmos.core.designsystem.component.LineColorIndicator
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.model.transit.Line
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = koinInject(),
    showTopBar: Boolean = true,
    initialScale: Float = 1f,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Transit map",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                PlatformMapView(
                    uiState = uiState,
                    onStationSelected = viewModel::selectStation,
                    modifier = Modifier.fillMaxSize(),
                    initialScale = initialScale,
                )
            }

            FloatingActionButton(
                onClick = { viewModel.requestLocateUser() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = "Locate me",
                )
            }

            uiState.selectedStation?.let {
                StationSheetCard(
                    uiState = uiState,
                    onClose = viewModel::clearSelection,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun StationSheetCard(
    uiState: MapUiState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val station = uiState.selectedStation ?: return
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = station.displayName(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (station.nameEl.isNotBlank() && station.nameEl != station.name) {
                        Text(
                            text = station.nameEl,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close station details")
                }
            }

            if (uiState.selectedStationLines.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.selectedStationLines.forEach { line ->
                        LineBadge(line = line)
                    }
                }
            }

            // Compact pill chips for the few useful facts. Hide noisy/internal
            // info ("merged records", "Zone 1" which is the default everywhere,
            // "Lines: N" which is redundant with the badges above).
            val chips = buildList {
                if (station.isInterchange) {
                    add("interchange" to "Interchange")
                }
                if (station.accessibility) {
                    add("accessibility" to "Accessible")
                }
                if (station.zone > 1) {
                    add("zone" to "Zone ${station.zone}")
                }
            }
            if (chips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    chips.forEach { (icon, label) ->
                        FactChip(iconKey = icon, label = label)
                    }
                }
            }

            if (uiState.selectedStationDepartures.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Next departures",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    uiState.selectedStationDepartures.forEach { departure ->
                        DepartureCard(
                            lineName = departure.line.name,
                            lineColor = departure.line.color,
                            direction = departure.destinationLabel,
                            minutesAway = departure.minutesAway,
                            departureTime = departure.time,
                        )
                    }
                }
            }

            TextButton(
                onClick = {
                    uriHandler.openUri(
                        "https://www.google.com/maps/dir/?api=1&destination=${station.latitude},${station.longitude}&travelmode=transit"
                    )
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(
                    imageVector = Icons.Filled.NearMe,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Get directions")
            }
        }
    }
}

@Composable
private fun LineBadge(line: Line) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = line.color.toComposeColor().copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LineColorIndicator(lineColor = line.color, size = 10.dp)
            Text(
                text = line.name,
                style = MaterialTheme.typography.labelLarge,
                color = line.color.toComposeColor(),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Compact pill-shaped chip used to display the handful of station facts that
 * actually matter to a traveller (accessible, interchange, off-zone). Hidden
 * for default values to keep the sheet uncluttered.
 */
@Composable
private fun FactChip(
    iconKey: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val icon = when (iconKey) {
        "interchange" -> Icons.Filled.SwapHoriz
        "accessibility" -> Icons.Filled.Accessible
        "zone" -> Icons.Filled.Place
        else -> Icons.Filled.Info
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
