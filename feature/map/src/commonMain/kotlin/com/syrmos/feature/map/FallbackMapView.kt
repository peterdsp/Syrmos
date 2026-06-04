package com.syrmos.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.sp
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.model.transit.Station
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

private const val CENTER_LAT = 37.98
private const val CENTER_LON = 23.73

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

@Composable
internal fun FallbackPlatformMapView(
    uiState: MapUiState,
    onStationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialScale: Float = 1f,
) {
    var scale by remember { mutableFloatStateOf(initialScale) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
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
                            station.latitude,
                            station.longitude,
                            canvasW,
                            canvasH,
                            offsetX,
                            offsetY,
                            scale,
                        )
                        val dx = tapOffset.x - pos.x
                        val dy = tapOffset.y - pos.y
                        val distSq = dx * dx + dy * dy
                        if (distSq < hitRadiusSq && distSq < closestDistSq) {
                            closestDistSq = distSq
                            closestStation = station
                        }
                    }

                    closestStation?.let { onStationSelected(it.id) }
                }
            },
    ) {
        drawFallbackMap(
            uiState = uiState,
            canvasWidth = size.width,
            canvasHeight = size.height,
            offsetX = offsetX,
            offsetY = offsetY,
            scale = scale,
            textMeasurer = textMeasurer,
        )
    }
}

private fun DrawScope.drawFallbackMap(
    uiState: MapUiState,
    canvasWidth: Float,
    canvasHeight: Float,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    textMeasurer: TextMeasurer,
) {
    for (line in uiState.lines) {
        val stations = uiState.lineStations[line.id] ?: continue
        val lineColor = line.color.toComposeColor()
        val strokeWidth = (4f * scale).coerceIn(1.5f, 12f)

        for (i in 0 until stations.size - 1) {
            val from = stations[i]
            val to = stations[i + 1]
            val fromPos = latLonToScreen(from.latitude, from.longitude, canvasWidth, canvasHeight, offsetX, offsetY, scale)
            val toPos = latLonToScreen(to.latitude, to.longitude, canvasWidth, canvasHeight, offsetX, offsetY, scale)
            drawLine(
                color = lineColor,
                start = fromPos,
                end = toPos,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }

    val dotRadius = (5f * scale).coerceIn(2.5f, 16f)
    val interchangeRadius = (8f * scale).coerceIn(4f, 24f)
    val selectedStation = uiState.selectedStation
    val showLabels = scale >= 1.2f

    for (station in uiState.stations) {
        val pos = latLonToScreen(station.latitude, station.longitude, canvasWidth, canvasHeight, offsetX, offsetY, scale)

        if (pos.x < -100 || pos.x > canvasWidth + 100 || pos.y < -100 || pos.y > canvasHeight + 100) {
            continue
        }

        val isSelected = selectedStation?.id == station.id
        if (station.isInterchange) {
            val r = if (isSelected) interchangeRadius * 1.5f else interchangeRadius
            drawCircle(color = Color.White, radius = r, center = pos)
            drawCircle(color = Color.Black, radius = r, center = pos, style = Stroke(width = (2f * scale).coerceIn(1f, 5f)))
            if (isSelected) {
                drawCircle(color = Color(0xFF0072CE).copy(alpha = 0.3f), radius = r * 1.6f, center = pos)
            }
        } else {
            val stationColor = station.lineIds.firstOrNull()
                ?.let { lineId -> uiState.lines.find { it.id == lineId }?.color?.toComposeColor() }
                ?: Color.Gray
            val r = if (isSelected) dotRadius * 1.6f else dotRadius
            if (isSelected) {
                drawCircle(color = stationColor.copy(alpha = 0.3f), radius = r * 1.8f, center = pos)
            }
            drawCircle(color = stationColor, radius = r, center = pos)
            drawCircle(color = Color.White, radius = r * 0.35f, center = pos)
        }

        if (showLabels) {
            val labelStyle = TextStyle(
                fontSize = (9f * (scale * 0.6f).coerceIn(0.6f, 1.4f)).sp,
                color = Color.DarkGray,
                fontWeight = if (station.isInterchange) FontWeight.SemiBold else FontWeight.Normal,
            )
            val labelOffset = if (station.isInterchange) interchangeRadius + 4f else dotRadius + 3f
            val textLayoutResult = textMeasurer.measure(station.name, labelStyle)
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(pos.x + labelOffset, pos.y - textLayoutResult.size.height / 2f),
            )
        }
    }
}
