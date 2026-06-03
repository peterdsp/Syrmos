package com.syrmos.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.Station
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

// Athens center coordinates
private const val CENTER_LAT = 37.98
private const val CENTER_LON = 23.73

// Mercator projection helper
private fun latToMercatorY(lat: Double): Double {
    val latRad = lat * PI / 180.0
    return ln(tan(PI / 4.0 + latRad / 2.0))
}

private fun latLonToScreen(
    lat: Double,
    lon: Double,
    canvasWidth: Float,
    canvasHeight: Float,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
): Offset {
    val baseFactor = minOf(canvasWidth, canvasHeight) * 2.5f

    val x = ((lon - CENTER_LON).toFloat() * baseFactor * scale) + canvasWidth / 2f + offsetX
    val centerMercY = latToMercatorY(CENTER_LAT)
    val mercY = latToMercatorY(lat)
    val y = ((centerMercY - mercY) * baseFactor * scale * (180.0 / PI)).toFloat() +
        canvasHeight / 2f + offsetY

    return Offset(x, y)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val lang by LocalizationManager.language.collectAsState()

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val textMeasurer = rememberTextMeasurer()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(L.MAP.text(lang)) })
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.isLoading) {
                Text(
                    text = "Loading map...",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Map Canvas with gesture handling
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.3f, 8f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                        .pointerInput(uiState.stations) {
                            detectTapGestures { tapOffset ->
                                val canvasW = size.width.toFloat()
                                val canvasH = size.height.toFloat()
                                val hitRadius = 24f / scale.coerceAtLeast(0.5f)
                                val hitRadiusSq = (hitRadius * hitRadius).coerceAtLeast(400f)

                                var closestStation: Station? = null
                                var closestDistSq = Float.MAX_VALUE

                                for (station in uiState.stations) {
                                    val pos = latLonToScreen(
                                        station.latitude, station.longitude,
                                        canvasW, canvasH,
                                        offsetX, offsetY, scale,
                                    )
                                    val dx = tapOffset.x - pos.x
                                    val dy = tapOffset.y - pos.y
                                    val distSq = dx * dx + dy * dy
                                    if (distSq < hitRadiusSq && distSq < closestDistSq) {
                                        closestDistSq = distSq
                                        closestStation = station
                                    }
                                }

                                if (closestStation != null) {
                                    viewModel.selectStation(closestStation.id)
                                }
                            }
                        },
                ) {
                    drawMap(
                        uiState = uiState,
                        canvasWidth = size.width,
                        canvasHeight = size.height,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        scale = scale,
                        textMeasurer = textMeasurer,
                    )
                }

                // Zoom controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            scale = (scale * 1.4f).coerceAtMost(8f)
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            scale = (scale / 1.4f).coerceAtLeast(0.3f)
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SmallFloatingActionButton(
                        onClick = {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text("⌂", fontSize = 16.sp) // house/home symbol
                    }
                }
            }
        }

        // Bottom sheet for selected station
        uiState.selectedStation?.let { station ->
            val stationLines = uiState.selectedStationLines

            ModalBottomSheet(
                onDismissRequest = {
                    viewModel.clearSelection()
                },
                sheetState = sheetState,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                ) {
                    // Station name
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (station.nameEl.isNotEmpty() && station.nameEl != station.name) {
                        Text(
                            text = station.nameEl,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Line badges
                    Text(
                        text = if (lang == com.syrmos.core.common.AppLanguage.GREEK) {
                            "Γραμμές στον σταθμό"
                        } else {
                            "Lines at this station"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        stationLines.forEach { line ->
                            LineBadge(line = line)
                        }
                    }

                    if (station.isInterchange) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (lang == com.syrmos.core.common.AppLanguage.GREEK) {
                                "Σταθμός ανταπόκρισης"
                            } else {
                                "Interchange station"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LineBadge(line: Line) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(line.color.toComposeColor().copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(line.color.toComposeColor()),
        )
        Text(
            text = line.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = line.color.toComposeColor(),
        )
    }
}

private fun DrawScope.drawMap(
    uiState: MapUiState,
    canvasWidth: Float,
    canvasHeight: Float,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    textMeasurer: TextMeasurer,
) {
    // Draw line connections
    for (line in uiState.lines) {
        val stations = uiState.lineStations[line.id] ?: continue
        val lineColor = line.color.toComposeColor()
        val strokeWidth = (4f * scale).coerceIn(1.5f, 12f)

        for (i in 0 until stations.size - 1) {
            val from = stations[i]
            val to = stations[i + 1]
            val fromPos = latLonToScreen(
                from.latitude, from.longitude,
                canvasWidth, canvasHeight,
                offsetX, offsetY, scale,
            )
            val toPos = latLonToScreen(
                to.latitude, to.longitude,
                canvasWidth, canvasHeight,
                offsetX, offsetY, scale,
            )
            drawLine(
                color = lineColor,
                start = fromPos,
                end = toPos,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }

    // Draw station dots
    val dotRadius = (5f * scale).coerceIn(2.5f, 16f)
    val interchangeRadius = (8f * scale).coerceIn(4f, 24f)
    val selectedStation = uiState.selectedStation
    val showLabels = scale >= 0.7f

    for (station in uiState.stations) {
        val pos = latLonToScreen(
            station.latitude, station.longitude,
            canvasWidth, canvasHeight,
            offsetX, offsetY, scale,
        )

        // Skip stations way off screen
        if (pos.x < -100 || pos.x > canvasWidth + 100 ||
            pos.y < -100 || pos.y > canvasHeight + 100
        ) {
            continue
        }

        val isSelected = selectedStation?.id == station.id

        if (station.isInterchange) {
            // Interchange: larger circle with white fill and black outline
            val r = if (isSelected) interchangeRadius * 1.5f else interchangeRadius
            drawCircle(
                color = Color.White,
                radius = r,
                center = pos,
            )
            drawCircle(
                color = Color.Black,
                radius = r,
                center = pos,
                style = Stroke(width = (2f * scale).coerceIn(1f, 5f)),
            )
            if (isSelected) {
                drawCircle(
                    color = Color(0xFF0072CE).copy(alpha = 0.3f),
                    radius = r * 1.6f,
                    center = pos,
                )
            }
        } else {
            // Regular station: solid colored dot
            val stationColor = station.lineIds.firstOrNull()?.let { lineId ->
                uiState.lines.find { it.id == lineId }?.color?.toComposeColor()
            } ?: Color.Gray

            val r = if (isSelected) dotRadius * 1.6f else dotRadius
            if (isSelected) {
                drawCircle(
                    color = stationColor.copy(alpha = 0.3f),
                    radius = r * 1.8f,
                    center = pos,
                )
            }
            drawCircle(
                color = stationColor,
                radius = r,
                center = pos,
            )
            // White inner dot for visibility
            drawCircle(
                color = Color.White,
                radius = r * 0.35f,
                center = pos,
            )
        }

        // Draw station labels when zoomed in enough
        if (showLabels && scale >= 1.2f) {
            val labelStyle = TextStyle(
                fontSize = (9f * (scale * 0.6f).coerceIn(0.6f, 1.4f)).sp,
                color = Color.DarkGray,
                fontWeight = if (station.isInterchange) FontWeight.SemiBold else FontWeight.Normal,
            )
            val labelOffset = if (station.isInterchange) interchangeRadius + 4f else dotRadius + 3f
            val textLayoutResult = textMeasurer.measure(station.name, labelStyle)
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(
                    pos.x + labelOffset,
                    pos.y - textLayoutResult.size.height / 2f,
                ),
            )
        }
    }
}
