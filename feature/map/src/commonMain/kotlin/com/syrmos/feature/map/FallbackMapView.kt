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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.LiveSuburbanTrain
import com.syrmos.core.model.transit.SimulatedTrain
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
            .pointerInput(uiState.mapStations) {
                detectTapGestures { tapOffset ->
                    val canvasW = size.width.toFloat()
                    val canvasH = size.height.toFloat()
                    val hitRadius = 24f / scale.coerceAtLeast(0.5f)
                    val hitRadiusSq = (hitRadius * hitRadius).coerceAtLeast(400f)

                    var closestStation: MapStationNode? = null
                    var closestDistSq = Float.MAX_VALUE

                    for (station in uiState.mapStations) {
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
        val points = stations.map { s ->
            latLonToScreen(s.latitude, s.longitude, canvasWidth, canvasHeight, offsetX, offsetY, scale)
        }
        if (points.size >= 2) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                if (points.size == 2) {
                    lineTo(points[1].x, points[1].y)
                } else {
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]
                        val mx = (prev.x + curr.x) / 2f
                        val my = (prev.y + curr.y) / 2f
                        quadraticTo(prev.x, prev.y, mx, my)
                    }
                    lineTo(points.last().x, points.last().y)
                }
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }

    val dotRadius = (6f * scale).coerceIn(3f, 18f)
    val interchangeRadius = (9f * scale).coerceIn(5f, 26f)
    val selectedStation = uiState.selectedStation
    val showLabels = scale >= 1.2f

    for (station in uiState.mapStations) {
        val pos = latLonToScreen(station.latitude, station.longitude, canvasWidth, canvasHeight, offsetX, offsetY, scale)

        if (pos.x < -100 || pos.x > canvasWidth + 100 || pos.y < -100 || pos.y > canvasHeight + 100) {
            continue
        }

        val isSelected = selectedStation?.id == station.id
        if (station.isInterchange) {
            val r = if (isSelected) interchangeRadius * 1.4f else interchangeRadius
            val ringColors = station.lineIds.take(3).mapNotNull { lineId ->
                uiState.lines.find { it.id == lineId }?.color?.toComposeColor()
            }
            if (isSelected) {
                drawCircle(color = Color(0xFF0072CE).copy(alpha = 0.25f), radius = r * 2f, center = pos)
            }
            drawCircle(color = Color.White, radius = r + (2f * scale).coerceIn(1f, 4f), center = pos)
            drawCircle(
                color = Color(0xFF333333),
                radius = r + (1.5f * scale).coerceIn(0.5f, 3f),
                center = pos,
                style = Stroke(width = (2.5f * scale).coerceIn(1.2f, 6f)),
            )
            drawCircle(color = Color.White, radius = r * 0.65f, center = pos)
            if (ringColors.size > 1) {
                val segmentAngle = 360f / ringColors.size
                ringColors.forEachIndexed { index, color ->
                    drawArc(
                        color = color,
                        startAngle = -90f + index * segmentAngle,
                        sweepAngle = segmentAngle,
                        useCenter = false,
                        topLeft = Offset(pos.x - r, pos.y - r),
                        size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                        style = Stroke(width = (3f * scale).coerceIn(1.5f, 7f)),
                    )
                }
            }
        } else {
            val stationColor = station.lineIds.firstOrNull()
                ?.let { lineId -> uiState.lines.find { it.id == lineId }?.color?.toComposeColor() }
                ?: Color.Gray
            val r = if (isSelected) dotRadius * 1.4f else dotRadius
            if (isSelected) {
                drawCircle(color = stationColor.copy(alpha = 0.25f), radius = r * 2f, center = pos)
            }
            drawCircle(color = Color.White, radius = r + (1.5f * scale).coerceIn(0.5f, 3f), center = pos)
            drawCircle(color = stationColor, radius = r, center = pos)
            drawCircle(color = Color.White, radius = r * 0.4f, center = pos)
        }

        if (showLabels) {
            val labelStyle = TextStyle(
                fontSize = (9f * (scale * 0.6f).coerceIn(0.6f, 1.4f)).sp,
                color = Color(0xFF2D2D2D),
                fontWeight = if (station.isInterchange) FontWeight.SemiBold else FontWeight.Normal,
            )
            val labelOffset = if (station.isInterchange) interchangeRadius + 6f else dotRadius + 5f
            val textLayoutResult = textMeasurer.measure(station.name, labelStyle)
            val bgPadding = 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(pos.x + labelOffset - bgPadding, pos.y - textLayoutResult.size.height / 2f - bgPadding),
                size = androidx.compose.ui.geometry.Size(
                    textLayoutResult.size.width.toFloat() + bgPadding * 2,
                    textLayoutResult.size.height.toFloat() + bgPadding * 2,
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(pos.x + labelOffset, pos.y - textLayoutResult.size.height / 2f),
            )
        }
    }

    drawSimulatedTrains(
        trains = uiState.simulatedTrains,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        offsetX = offsetX,
        offsetY = offsetY,
        scale = scale,
        textMeasurer = textMeasurer,
    )

    drawLiveTrains(
        trains = uiState.liveTrains,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        offsetX = offsetX,
        offsetY = offsetY,
        scale = scale,
        textMeasurer = textMeasurer,
    )
}

private fun DrawScope.drawSimulatedTrains(
    trains: List<SimulatedTrain>,
    canvasWidth: Float,
    canvasHeight: Float,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    textMeasurer: TextMeasurer,
) {
    for (train in trains) {
        val pos = latLonToScreen(train.latitude, train.longitude, canvasWidth, canvasHeight, offsetX, offsetY, scale)
        if (pos.x < -100 || pos.x > canvasWidth + 100 || pos.y < -100 || pos.y > canvasHeight + 100) continue

        val lineColor = train.lineColor.toComposeColor()

        if (train.isAirportService) {
            drawAirportTrain(pos, lineColor, scale)
        } else when (train.lineType) {
            LineType.METRO -> drawMetroTrain(pos, lineColor, scale)
            LineType.TRAM -> drawTramTrain(pos, lineColor, scale)
            else -> drawMetroTrain(pos, lineColor, scale)
        }

        val badgeResult = textMeasurer.measure(
            train.lineId,
            TextStyle(
                fontSize = (6f * (scale * 0.6f).coerceIn(0.5f, 1.2f)).sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            ),
        )
        val badgeW = badgeResult.size.width + 6f
        val badgeH = badgeResult.size.height + 2f
        val trainR = when {
            train.isAirportService -> (8f * scale).coerceIn(4f, 18f)
            train.lineType == LineType.TRAM -> (5f * scale).coerceIn(3f, 12f)
            else -> (6.5f * scale).coerceIn(3.5f, 15f)
        }
        drawRoundRect(
            color = lineColor,
            topLeft = Offset(pos.x - badgeW / 2, pos.y - trainR - badgeH - 2f),
            size = androidx.compose.ui.geometry.Size(badgeW, badgeH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(badgeH / 2, badgeH / 2),
        )
        drawText(
            textLayoutResult = badgeResult,
            topLeft = Offset(pos.x - badgeResult.size.width / 2f, pos.y - trainR - badgeH - 1f),
        )

        if (scale >= 1.4f) {
            val label = "${train.lineName} → ${train.destinationName}"
            val textLayoutResult = textMeasurer.measure(
                label,
                TextStyle(
                    fontSize = (7f * (scale * 0.55f).coerceIn(0.5f, 1.1f)).sp,
                    color = lineColor,
                    fontWeight = FontWeight.Bold,
                ),
            )
            val bgPadding = 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.92f),
                topLeft = Offset(
                    pos.x + trainR + 3f - bgPadding,
                    pos.y - textLayoutResult.size.height / 2f - bgPadding,
                ),
                size = androidx.compose.ui.geometry.Size(
                    textLayoutResult.size.width.toFloat() + bgPadding * 2,
                    textLayoutResult.size.height.toFloat() + bgPadding * 2,
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(pos.x + trainR + 3f, pos.y - textLayoutResult.size.height / 2f),
            )
        }
    }
}

private fun DrawScope.drawMetroTrain(pos: Offset, color: Color, scale: Float) {
    val r = (6.5f * scale).coerceIn(3.5f, 15f)
    val stroke = (2f * scale).coerceIn(1f, 4f)
    drawCircle(color = color.copy(alpha = 0.15f), radius = r * 2f, center = pos)
    drawCircle(color = Color.White, radius = r + stroke / 2, center = pos)
    drawCircle(color = color, radius = r, center = pos)
    val s = r * 0.38f
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(pos.x - s, pos.y - s * 1.3f),
        size = androidx.compose.ui.geometry.Size(s * 2, s * 2.6f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.4f, s * 0.4f),
    )
}

private fun DrawScope.drawTramTrain(pos: Offset, color: Color, scale: Float) {
    val w = (9f * scale).coerceIn(4.5f, 20f)
    val h = (5f * scale).coerceIn(3f, 12f)
    val stroke = (1.5f * scale).coerceIn(0.8f, 3f)
    drawCircle(color = color.copy(alpha = 0.12f), radius = w * 1.3f, center = pos)
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(pos.x - w / 2 - stroke, pos.y - h / 2 - stroke),
        size = androidx.compose.ui.geometry.Size(w + stroke * 2, h + stroke * 2),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.4f, h * 0.4f),
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(pos.x - w / 2, pos.y - h / 2),
        size = androidx.compose.ui.geometry.Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.35f, h * 0.35f),
    )
    drawLine(
        color = Color.White,
        start = Offset(pos.x - w * 0.22f, pos.y),
        end = Offset(pos.x + w * 0.22f, pos.y),
        strokeWidth = stroke * 0.8f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawAirportTrain(pos: Offset, color: Color, scale: Float) {
    val r = (8f * scale).coerceIn(4f, 18f)
    val stroke = (2.5f * scale).coerceIn(1.2f, 5f)
    drawCircle(color = Color(0xFF0072CE).copy(alpha = 0.15f), radius = r * 2.2f, center = pos)
    drawCircle(color = Color.White, radius = r + stroke / 2, center = pos)
    drawCircle(color = Color(0xFF0072CE), radius = r, center = pos)
    val s = r * 0.45f
    drawLine(
        color = Color.White,
        start = Offset(pos.x - s, pos.y + s * 0.2f),
        end = Offset(pos.x + s, pos.y + s * 0.2f),
        strokeWidth = stroke * 0.7f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Color.White,
        start = Offset(pos.x, pos.y - s * 0.6f),
        end = Offset(pos.x, pos.y + s * 0.6f),
        strokeWidth = stroke * 0.5f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Color.White,
        start = Offset(pos.x - s * 0.6f, pos.y - s * 0.3f),
        end = Offset(pos.x + s * 0.6f, pos.y - s * 0.3f),
        strokeWidth = stroke * 0.5f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawLiveTrains(
    trains: List<LiveSuburbanTrain>,
    canvasWidth: Float,
    canvasHeight: Float,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    textMeasurer: TextMeasurer,
) {
    val trainRadius = (6f * scale).coerceIn(3f, 14f)
    for (train in trains) {
        val pos = latLonToScreen(train.latitude, train.longitude, canvasWidth, canvasHeight, offsetX, offsetY, scale)
        if (pos.x < -100 || pos.x > canvasWidth + 100 || pos.y < -100 || pos.y > canvasHeight + 100) continue

        val lineColor = when (train.lineId) {
            "A1", "A2", "A3", "A4" -> Color(0xFF6D4C41)
            else -> Color(0xFF0072CE)
        }
        drawCircle(color = lineColor.copy(alpha = 0.22f), radius = trainRadius * 1.9f, center = pos)
        drawCircle(color = lineColor, radius = trainRadius, center = pos)
        drawCircle(color = Color.White, radius = trainRadius * 0.35f, center = pos)

        if (scale >= 1.2f) {
            val textLayoutResult = textMeasurer.measure(
                train.trainNumber,
                TextStyle(
                    fontSize = (8f * (scale * 0.6f).coerceIn(0.6f, 1.2f)).sp,
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(pos.x + trainRadius + 4f, pos.y - textLayoutResult.size.height / 2f),
            )
        }
    }
}
