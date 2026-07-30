package com.syrmos.feature.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.designsystem.component.LineColorIndicator
import com.syrmos.core.designsystem.component.formatMinutesAway
import com.syrmos.core.designsystem.component.liquidGlassOverlay
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import kotlin.math.roundToInt
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LiveSuburbanTrain
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
                onTrainSelected = viewModel::selectTrain,
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
                .padding(end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (uiState.liveTrains.isNotEmpty()) {
                Surface(
                    onClick = { viewModel.toggleLiveTrainsSheet() },
                    modifier = Modifier.size(56.dp).liquidGlassOverlay(),
                    shape = CircleShape,
                    color = SyrmosColorTokens.suburban,
                    shadowElevation = 6.dp,
                    tonalElevation = 2.dp,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsRailway,
                            contentDescription = "Live trains",
                            tint = Color.White,
                        )
                    }
                }
            }

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

        AnimatedVisibility(
            visible = uiState.selectedTrain != null && uiState.selectedStation == null,
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

            TrainDetailCard(
                train = uiState.selectedTrain,
                line = uiState.lines.find { it.id == uiState.selectedTrain?.lineId },
                onClose = viewModel::clearTrainSelection,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, offsetY.coerceAtLeast(0f).roundToInt()) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (offsetY > dismissThreshold) {
                                    viewModel.clearTrainSelection()
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

        if (uiState.showLiveTrainsSheet) {
            LiveTrainsSheet(
                trains = uiState.liveTrains,
                lines = uiState.lines,
                onTrainSelected = viewModel::flyToTrain,
                onDismiss = viewModel::toggleLiveTrainsSheet,
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
                                        departure.minutesAway <= 2 -> SyrmosColorTokens.arrivalSoon
                                        departure.minutesAway <= 5 -> SyrmosColorTokens.arrivalModerate
                                        else -> SyrmosColorTokens.arrivalFar
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

@Composable
private fun TrainDetailCard(
    train: com.syrmos.core.model.transit.LiveSuburbanTrain?,
    line: Line?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    train ?: return
    val uriHandler = LocalUriHandler.current
    val lineColor = line?.color?.toComposeColor() ?: MaterialTheme.colorScheme.primary

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (line != null) {
                            LineBadge(line = line)
                        }
                        Text(
                            text = "Train ${train.trainNumber}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (train.serviceType.isNotBlank()) {
                            Text(
                                text = train.serviceType.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!train.locomotiveNumber.isNullOrBlank()) {
                            Text(
                                text = "Loco ${train.locomotiveNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close train details")
                }
            }

            // Route with progress
            if (train.origin != null || train.destination != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = train.origin ?: "Train ${train.trainNumber}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                val dep = train.scheduledDeparture
                                if (dep != null) {
                                    Text(
                                        text = dep,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                if (train.origin == null && train.destination == null) {
                                    Text(
                                        text = "Route not published by operator",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                            }
                            if (train.origin != null || train.destination != null) {
                                Icon(
                                    imageVector = Icons.Filled.SwapHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp).padding(horizontal = 4.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End,
                            ) {
                                val dest = train.destination
                                if (dest != null) {
                                    Text(
                                        text = dest,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                val arr = train.scheduledArrival
                                if (arr != null) {
                                    Text(
                                        text = arr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }

                        val prog = train.progress
                        if (prog != null && prog > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .background(lineColor.copy(alpha = 0.15f), RoundedCornerShape(3.dp)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = prog.coerceIn(0.0, 1.0).toFloat())
                                        .height(6.dp)
                                        .background(lineColor, RoundedCornerShape(3.dp)),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${(prog * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val distDest = train.distanceToDestination
                                if (distDest != null) {
                                    Text(
                                        text = formatDistance(distDest),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Status row
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (train.delayMinutes > 0)
                        SyrmosColorTokens.disruption.copy(alpha = 0.12f)
                    else
                        SyrmosColorTokens.live.copy(alpha = 0.12f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (train.delayMinutes > 0) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = SyrmosColorTokens.disruption,
                            )
                            Text(
                                text = "+${train.delayMinutes} min",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = SyrmosColorTokens.disruption,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(SyrmosColorTokens.live, CircleShape),
                            )
                            Text(
                                text = "On time",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = SyrmosColorTokens.live,
                            )
                        }
                    }
                }

                val nextStn = train.nextStation
                if (nextStn != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Place,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = nextStn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Telemetry grid
            val speed = train.speedKph
            val crs = train.course
            val alt = train.altitude
            if (speed != null || crs != null || alt != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        maxItemsInEachRow = 3,
                    ) {
                        if (speed != null) {
                            TelemetryCell(
                                value = "${speed.toInt()}",
                                unit = "km/h",
                                accent = if (speed > 100) SyrmosColorTokens.disruption else lineColor,
                            )
                        }
                        if (crs != null) {
                            TelemetryCell(
                                value = "${crs.toInt()}",
                                unit = headingLabel(crs),
                                accent = lineColor,
                            )
                        }
                        if (alt != null) {
                            TelemetryCell(
                                value = "${alt.toInt()}",
                                unit = "m alt",
                                accent = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val distNext = train.distanceToNextStation
                        if (distNext != null) {
                            TelemetryCell(
                                value = formatDistanceShort(distNext),
                                unit = "to next",
                                accent = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val sig = train.signalStatus
                        if (!sig.isNullOrBlank()) {
                            TelemetryCell(
                                value = sig.replaceFirstChar { c -> c.uppercase() },
                                unit = "signal",
                                accent = if (sig.lowercase() == "good") SyrmosColorTokens.live else SyrmosColorTokens.warning,
                            )
                        }
                    }
                }
            }

            // Live stream button
            val streamUrl = train.liveStreamUrl
            if (!streamUrl.isNullOrBlank()) {
                Button(
                    onClick = { uriHandler.openUri(streamUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Watch Live",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color.Red, CircleShape),
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red,
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryCell(
    value: String,
    unit: String,
    accent: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

private fun trainRouteLabel(train: LiveSuburbanTrain): String {
    val o = train.origin
    val d = train.destination
    if (o != null && d != null) return "$o → $d"
    if (o != null) return "From $o"
    if (d != null) return "To $d"
    return "Train ${train.trainNumber}"
}

private fun headingLabel(degrees: Double): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val idx = ((degrees + 22.5) % 360 / 45).toInt().coerceIn(0, dirs.lastIndex)
    return "${dirs[idx]}°"
}

private fun formatDistance(meters: Int): String {
    return if (meters >= 1000) {
        val km = meters / 1000.0
        val rounded = (km * 10).toInt() / 10.0
        "$rounded km left"
    } else {
        "$meters m left"
    }
}

private fun formatDistanceShort(meters: Int): String {
    return if (meters >= 1000) {
        val km = meters / 1000.0
        val rounded = (km * 10).toInt() / 10.0
        "$rounded km"
    } else {
        "$meters m"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveTrainsSheet(
    trains: List<com.syrmos.core.model.transit.LiveSuburbanTrain>,
    lines: List<Line>,
    onTrainSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var selectedFilter by remember { mutableStateOf<String?>(null) }

    val lineCategories = remember(trains) {
        trains.map { it.lineId }.distinct().sorted()
    }

    val filteredTrains = remember(trains, selectedFilter) {
        if (selectedFilter == null) trains
        else trains.filter { it.lineId == selectedFilter }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Live trains",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${filteredTrains.size} trains running",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.DirectionsRailway,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = SyrmosColorTokens.suburban,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterPill(
                    label = "All",
                    selected = selectedFilter == null,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { selectedFilter = null },
                )
                lineCategories.forEach { lineId ->
                    val line = lines.find { it.id == lineId }
                    FilterPill(
                        label = lineId,
                        selected = selectedFilter == lineId,
                        color = line?.color?.toComposeColor()
                            ?: MaterialTheme.colorScheme.primary,
                        onClick = { selectedFilter = lineId },
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filteredTrains, key = { it.id }) { train ->
                    val line = lines.find { it.id == train.lineId }
                    TrainListRow(
                        train = train,
                        line = line,
                        onClick = { onTrainSelected(train.id) },
                    )
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) color.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = if (selected) BorderStroke(1.5.dp, color) else null,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrainListRow(
    train: com.syrmos.core.model.transit.LiveSuburbanTrain,
    line: Line?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (line != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = line.color.toComposeColor().copy(alpha = 0.15f),
                ) {
                    Text(
                        text = train.lineId,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = line.color.toComposeColor(),
                    )
                }
                Spacer(modifier = Modifier.size(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trainRouteLabel(train),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (train.nextStation != null) {
                    Text(
                        text = "Next: ${train.nextStation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (train.delayMinutes > 0) {
                Text(
                    text = "+${train.delayMinutes}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = SyrmosColorTokens.disruption,
                )
            } else {
                Text(
                    text = "OK",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = SyrmosColorTokens.live,
                )
            }
        }
    }
}
