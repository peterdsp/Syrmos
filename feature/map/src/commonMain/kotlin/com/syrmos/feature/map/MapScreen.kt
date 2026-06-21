package com.syrmos.feature.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.designsystem.component.LineColorIndicator
import com.syrmos.core.designsystem.component.formatMinutesAway
import com.syrmos.core.designsystem.component.liquidGlassOverlay
import com.syrmos.core.designsystem.theme.ArrivalFar
import com.syrmos.core.designsystem.theme.ArrivalModerate
import com.syrmos.core.designsystem.theme.ArrivalSoon
import kotlin.math.roundToInt
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.model.transit.Line
import org.koin.compose.koinInject

@Composable
fun MapScreen(
    viewModel: MapViewModel = koinInject(),
    showTopBar: Boolean = true,
    initialScale: Float = 1f,
) {
    val uiState by viewModel.uiState.collectAsState()
    val lang by LocalizationManager.language.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            PlatformMapView(
                uiState = if (uiState.showTrains) uiState else uiState.copy(
                    simulatedTrains = emptyList(),
                    liveTrains = emptyList(),
                ),
                onStationSelected = viewModel::selectStation,
                modifier = Modifier.fillMaxSize(),
                initialScale = initialScale,
            )
        }

        if (showTopBar) {
            com.syrmos.core.designsystem.component.CompactTabHeader(
                title = L.MAP.text(lang),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .zIndex(1f),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Surface(
                onClick = { viewModel.toggleTrainVisibility() },
                modifier = Modifier.size(56.dp).liquidGlassOverlay(),
                shape = CircleShape,
                color = if (uiState.showTrains)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shadowElevation = 6.dp,
                tonalElevation = 2.dp,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Train,
                        contentDescription = "Toggle trains",
                        tint = if (uiState.showTrains)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                onClick = { viewModel.requestLocateUser() },
                modifier = Modifier.size(56.dp).liquidGlassOverlay(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
                tonalElevation = 2.dp,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = "Locate me",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.selectedStation != null,
            enter = slideInVertically(
                animationSpec = tween(350),
                initialOffsetY = { it },
            ),
            exit = slideOutVertically(
                animationSpec = tween(300),
                targetOffsetY = { it },
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(2f),
        ) {
            val density = LocalDensity.current
            var offsetY by remember { mutableStateOf(0f) }
            val dismissThreshold = with(density) { 100.dp.toPx() }

            StationSheetCard(
                uiState = uiState,
                onClose = viewModel::clearSelection,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, offsetY.coerceAtLeast(0f).roundToInt()) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (offsetY > dismissThreshold) {
                                    viewModel.clearSelection()
                                }
                                offsetY = 0f
                            },
                            onDragCancel = { offsetY = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                if (dragAmount > 0 || offsetY > 0) {
                                    change.consume()
                                    offsetY += dragAmount
                                }
                            },
                        )
                    }
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 100.dp),
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
                val departures = uiState.selectedStationDepartures.take(4)
                Column {
                    Text(
                        text = "NEXT DEPARTURES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    departures.forEachIndexed { index, departure ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LineColorIndicator(lineColor = departure.line.color, size = 10.dp)
                                Column {
                                    Text(
                                        text = departure.line.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "to ${departure.destinationLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatMinutesAway(departure.minutesAway),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when {
                                        departure.minutesAway <= 2 -> ArrivalSoon
                                        departure.minutesAway <= 5 -> ArrivalModerate
                                        else -> ArrivalFar
                                    },
                                )
                                Text(
                                    text = departure.time,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (index < departures.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    uriHandler.openUri(
                        "https://www.google.com/maps/dir/?api=1&destination=${station.latitude},${station.longitude}&travelmode=transit"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
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
