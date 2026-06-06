package com.syrmos.feature.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.SimulatedTrain
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private fun resolveVehicleDrawable(context: Context, train: SimulatedTrain): android.graphics.drawable.Drawable? {
    val drawableName = vehicleDrawableName(train) ?: return null
    val resId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
    if (resId == 0) return null
    return androidx.core.content.ContextCompat.getDrawable(context, resId)
}

private fun vehicleDrawableName(train: SimulatedTrain): String? {
    val isInbound = train.direction == Direction.INBOUND
    return when (train.lineId) {
        "M1" -> if (isInbound) "ic_metro_m1_left_to_piraeus" else "ic_metro_m1_right_to_kifissia"
        "M2" -> if (isInbound) "ic_metro_m2_left_to_anthoupoli" else "ic_metro_m2_right_to_elliniko"
        "M3" -> when {
            train.isAirportService -> "ic_metro_m3_right_to_airport"
            isInbound -> "ic_metro_m3_left_to_dimotiko_theatro"
            else -> "ic_metro_m3_right_to_doukissis_plakentias"
        }
        "T6" -> if (isInbound) "ic_tram_t6_left_to_syntagma" else "ic_tram_t6_right_to_pikrodafni"
        "T7" -> if (isInbound) "ic_tram_t7_left_to_akti_posidonos" else "ic_tram_t7_right_to_asklipiio_voulas"
        else -> null
    }
}

private fun resolveStationDrawable(
    context: Context,
    stationId: String,
    lineStations: Map<String, List<com.syrmos.core.model.transit.Station>>,
): android.graphics.drawable.Drawable? {
    for ((lineId, stations) in lineStations) {
        val index = stations.indexOfFirst { it.id == stationId }
        if (index < 0) continue
        val prefix = when {
            lineId.startsWith("M") -> "ic_metro_${lineId.lowercase()}"
            lineId.startsWith("T") -> "ic_tram_${lineId.lowercase()}"
            else -> "ic_train_${lineId.lowercase()}"
        }
        val num = String.format("%02d", index + 1)
        val pattern = "${prefix}_${num}_"
        val resId = findDrawableByPrefix(context, pattern)
        if (resId != 0) return androidx.core.content.ContextCompat.getDrawable(context, resId)
    }
    return null
}

private val drawableNameCache = mutableMapOf<String, Int>()

private fun findDrawableByPrefix(context: Context, prefix: String): Int {
    drawableNameCache[prefix]?.let { return it }
    val fields = try {
        Class.forName("${context.packageName}.R\$drawable").fields
    } catch (_: Exception) { return 0 }
    for (field in fields) {
        if (field.name.startsWith(prefix)) {
            val resId = field.getInt(null)
            drawableNameCache[prefix] = resId
            return resId
        }
    }
    drawableNameCache[prefix] = 0
    return 0
}

@Composable
internal actual fun PlatformMapView(
    uiState: MapUiState,
    onStationSelected: (String) -> Unit,
    modifier: Modifier,
    initialScale: Float,
) {
    val context = LocalContext.current
    var hasFittedBounds by remember { mutableStateOf(false) }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val lineOverlays = remember { mutableListOf<Polyline>() }
    val stationMarkers = remember { mutableMapOf<String, Marker>() }
    val trainMarkers = remember { mutableMapOf<String, Marker>() }
    val liveTrainMarkers = remember { mutableMapOf<String, Marker>() }

    DisposableEffect(context) {
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose { }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(12.0)
                controller.setCenter(GeoPoint(37.98, 23.73))
                zoomController.setVisibility(
                    org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT
                )
                minZoomLevel = 9.0
                maxZoomLevel = 18.0
                mapViewRef.value = this
            }
        },
    )

    val mapView = mapViewRef.value ?: return

    LaunchedEffect(uiState.lines, uiState.lineStations) {
        lineOverlays.forEach { mapView.overlays.remove(it) }
        lineOverlays.clear()

        uiState.lines.forEach { line ->
            val lineStations = uiState.lineStations[line.id].orEmpty()
            if (lineStations.size < 2) return@forEach

            val polyline = Polyline().apply {
                outlinePaint.color = line.color.toComposeColor().toArgb()
                outlinePaint.strokeWidth = if (line.type == LineType.SUBURBAN) 7f else 10f
                setPoints(lineStations.map { GeoPoint(it.latitude, it.longitude) })
            }
            lineOverlays.add(polyline)
            mapView.overlays.add(0, polyline)
        }
        mapView.invalidate()
    }

    LaunchedEffect(uiState.mapStations, uiState.selectedStation) {
        val currentIds = uiState.mapStations.map { it.id }.toSet()
        val staleIds = stationMarkers.keys - currentIds
        staleIds.forEach { id ->
            stationMarkers[id]?.let { mapView.overlays.remove(it) }
            stationMarkers.remove(id)
        }

        uiState.mapStations.forEach { station ->
            val existing = stationMarkers[station.id]
            val isSelected = uiState.selectedStation?.id == station.id
            val primaryStationId = station.stationIds.firstOrNull() ?: station.id

            val stationDrawable = resolveStationDrawable(context, primaryStationId, uiState.lineStations)
            val icon = stationDrawable ?: buildMarkerBitmap(
                color = station.lineIds.firstNotNullOfOrNull { lineId ->
                    uiState.lines.find { it.id == lineId }?.color?.toComposeColor()
                }?.toArgb() ?: 0xFF64748B.toInt(),
                interchange = station.isInterchange,
                selected = isSelected,
            )

            if (existing != null) {
                existing.position = GeoPoint(station.latitude, station.longitude)
                existing.icon = icon
            } else {
                val marker = Marker(mapView).apply {
                    position = GeoPoint(station.latitude, station.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    this.icon = icon
                    setOnMarkerClickListener { _, _ ->
                        onStationSelected(station.id)
                        true
                    }
                }
                stationMarkers[station.id] = marker
                mapView.overlays.add(marker)
            }
        }
        mapView.invalidate()
    }

    LaunchedEffect(uiState.simulatedTrains) {
        val activeIds = uiState.simulatedTrains.map { it.id }.toSet()
        val staleIds = trainMarkers.keys - activeIds
        staleIds.forEach { id ->
            trainMarkers[id]?.let { mapView.overlays.remove(it) }
            trainMarkers.remove(id)
        }

        uiState.simulatedTrains.forEach { train ->
            val existing = trainMarkers[train.id]

            if (existing != null) {
                existing.position = GeoPoint(train.latitude, train.longitude)
            } else {
                val vehicleIcon = resolveVehicleDrawable(context, train)
                val lineColor = train.lineColor.toComposeColor().toArgb()
                val marker = Marker(mapView).apply {
                    position = GeoPoint(train.latitude, train.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = vehicleIcon ?: when {
                        train.isAirportService -> buildAirportTrainBitmap()
                        train.lineType == LineType.TRAM -> buildTramTrainBitmap(lineColor, train.lineId)
                        else -> buildMetroTrainBitmap(lineColor, train.lineId)
                    }
                    title = "${train.lineName} → ${train.destinationName}"
                    snippet = "Near ${train.currentStationName}"
                }
                trainMarkers[train.id] = marker
                mapView.overlays.add(marker)
            }
        }
        mapView.invalidate()
    }

    LaunchedEffect(uiState.liveTrains) {
        val activeIds = uiState.liveTrains.map { it.id }.toSet()
        val staleIds = liveTrainMarkers.keys - activeIds
        staleIds.forEach { id ->
            liveTrainMarkers[id]?.let { mapView.overlays.remove(it) }
            liveTrainMarkers.remove(id)
        }

        uiState.liveTrains.forEach { train ->
            val existing = liveTrainMarkers[train.id]
            if (existing != null) {
                existing.position = GeoPoint(train.latitude, train.longitude)
            } else {
                val marker = Marker(mapView).apply {
                    position = GeoPoint(train.latitude, train.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = buildLiveTrainBitmap(
                        color = uiState.lines.find { it.id == train.lineId }?.color?.toComposeColor()?.toArgb()
                            ?: 0xFF0072CE.toInt(),
                    )
                }
                liveTrainMarkers[train.id] = marker
                mapView.overlays.add(marker)
            }
        }
        mapView.invalidate()
    }

    LaunchedEffect(uiState.mapStations) {
        if (!hasFittedBounds && uiState.mapStations.isNotEmpty()) {
            hasFittedBounds = true
            val points = uiState.mapStations.map { GeoPoint(it.latitude, it.longitude) }
            mapView.post {
                mapView.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(points), true, 96)
            }
        }
    }

    LaunchedEffect(uiState.selectedStation) {
        if (uiState.selectedStation != null) {
            mapView.controller.animateTo(
                GeoPoint(uiState.selectedStation.latitude, uiState.selectedStation.longitude)
            )
        }
    }
}

// Fallback bitmap builders for when PNG drawables are not found

private fun buildMarkerBitmap(color: Int, interchange: Boolean, selected: Boolean): android.graphics.drawable.Drawable {
    val size = if (interchange) 64 else 48
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    if (selected) {
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x330072CE }
        canvas.drawCircle(cx, cy, if (interchange) 24f else 20f, halo)
    }

    if (interchange) {
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
        canvas.drawCircle(cx - 6f, cy, 10f, ring)
        canvas.drawCircle(cx + 6f, cy, 10f, ring)
        canvas.drawCircle(cx, cy - 6f, 10f, ring)
        canvas.drawCircle(cx, cy, 6f, white)
    } else {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
        canvas.drawCircle(cx, cy, 10f, fill)
        canvas.drawCircle(cx, cy, 4f, white)
    }

    return BitmapDrawable(null, bitmap)
}

private fun buildMetroTrainBitmap(color: Int, lineLabel: String = ""): android.graphics.drawable.Drawable {
    val width = 56
    val height = 64
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = width / 2f

    if (lineLabel.isNotEmpty()) {
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFFFFFFFF.toInt()
            textSize = 10f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawRoundRect(RectF(cx - 14f, 0f, cx + 14f, 14f), 7f, 7f, badgePaint)
        canvas.drawText(lineLabel, cx, 11f, textPaint)
    }

    val cy = if (lineLabel.isNotEmpty()) 38f else 32f
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    canvas.drawRoundRect(RectF(cx - 13f, cy - 11f, cx + 13f, cy + 11f), 6f, 6f, white)
    canvas.drawRoundRect(RectF(cx - 11f, cy - 9f, cx + 11f, cy + 9f), 5f, 5f, fill)
    canvas.drawRoundRect(RectF(cx - 6f, cy - 2f, cx + 6f, cy), 1f, 1f, white)
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xB3FFFFFF.toInt() }
    canvas.drawCircle(cx - 4f, cy + 4f, 1.5f, dot)
    canvas.drawCircle(cx + 4f, cy + 4f, 1.5f, dot)
    return BitmapDrawable(null, bitmap)
}

private fun buildTramTrainBitmap(color: Int, lineLabel: String = ""): android.graphics.drawable.Drawable {
    val width = 56
    val height = 56
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = width / 2f

    if (lineLabel.isNotEmpty()) {
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFFFFFFFF.toInt()
            textSize = 10f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawRoundRect(RectF(cx - 14f, 0f, cx + 14f, 14f), 7f, 7f, badgePaint)
        canvas.drawText(lineLabel, cx, 11f, textPaint)
    }

    val cy = if (lineLabel.isNotEmpty()) 34f else 28f
    val w = 16f
    val h = 10f
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    canvas.drawRoundRect(RectF(cx - w / 2 - 2, cy - h / 2 - 2, cx + w / 2 + 2, cy + h / 2 + 2), h * 0.4f, h * 0.4f, white)
    canvas.drawRoundRect(RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), h * 0.35f, h * 0.35f, fill)
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xB3FFFFFF.toInt() }
    canvas.drawCircle(cx - 3f, cy, 1.5f, dot)
    canvas.drawCircle(cx + 3f, cy, 1.5f, dot)
    return BitmapDrawable(null, bitmap)
}

private fun buildAirportTrainBitmap(): android.graphics.drawable.Drawable {
    val width = 56
    val height = 64
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = width / 2f
    val blue = 0xFF0057B8.toInt()

    val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blue }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 9f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    canvas.drawRoundRect(RectF(cx - 11f, 0f, cx + 11f, 14f), 7f, 7f, badgePaint)
    canvas.drawText("M3", cx, 11f, textPaint)

    val cy = 38f
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blue }

    canvas.drawRoundRect(RectF(cx - 14f, cy - 12f, cx + 14f, cy + 12f), 6f, 6f, white)
    canvas.drawRoundRect(RectF(cx - 12f, cy - 10f, cx + 12f, cy + 10f), 5f, 5f, fill)
    val s = 5f
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawLine(cx - s, cy + s * 0.2f, cx + s, cy + s * 0.2f, linePaint)
    canvas.drawLine(cx, cy - s * 0.6f, cx, cy + s * 0.6f, Paint(linePaint).apply { strokeWidth = 1.5f })
    canvas.drawLine(cx - s * 0.6f, cy - s * 0.3f, cx + s * 0.6f, cy - s * 0.3f, Paint(linePaint).apply { strokeWidth = 1.5f })
    return BitmapDrawable(null, bitmap)
}

private fun buildLiveTrainBitmap(color: Int): android.graphics.drawable.Drawable {
    val size = 40
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x330072CE }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }

    canvas.drawCircle(cx, cy, 15f, halo)
    canvas.drawCircle(cx, cy, 9f, fill)
    canvas.drawCircle(cx, cy, 3f, white)
    return BitmapDrawable(null, bitmap)
}
