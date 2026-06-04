package com.syrmos.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.syrmos.core.designsystem.component.toComposeColor
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
internal actual fun PlatformMapView(
    uiState: MapUiState,
    onStationSelected: (String) -> Unit,
    modifier: Modifier,
    initialScale: Float,
) {
    val context = LocalContext.current
    var hasFittedBounds by remember { mutableStateOf(false) }

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
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
                minZoomLevel = 9.0
                maxZoomLevel = 18.0
            }
        },
        update = { mapView ->
            mapView.overlays.clear()

            uiState.lines.forEach { line ->
                val lineStations = uiState.lineStations[line.id].orEmpty()
                if (lineStations.size < 2) return@forEach

                val polyline = Polyline().apply {
                    outlinePaint.color = line.color.toComposeColor().toArgb()
                    outlinePaint.strokeWidth = if (line.id.startsWith("P")) 7f else 10f
                    setPoints(lineStations.map { GeoPoint(it.latitude, it.longitude) })
                }
                mapView.overlays.add(polyline)
            }

            uiState.mapStations.forEach { station ->
                val marker = Marker(mapView).apply {
                    position = GeoPoint(station.latitude, station.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = buildMarkerBitmap(
                        color = station.lineIds.firstNotNullOfOrNull { lineId ->
                            uiState.lines.find { it.id == lineId }?.color?.toComposeColor()
                        }?.toArgb() ?: 0xFF64748B.toInt(),
                        interchange = station.isInterchange,
                        selected = uiState.selectedStation?.id == station.id,
                    )
                    setOnMarkerClickListener { _, _ ->
                        onStationSelected(station.id)
                        true
                    }
                }
                mapView.overlays.add(marker)
            }

            uiState.liveTrains.forEach { train ->
                val marker = Marker(mapView).apply {
                    position = GeoPoint(train.latitude, train.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = buildTrainMarkerBitmap(
                        color = uiState.lines.find { it.id == train.lineId }?.color?.toComposeColor()?.toArgb()
                            ?: 0xFF0072CE.toInt(),
                    )
                }
                mapView.overlays.add(marker)
            }

            if (!hasFittedBounds && uiState.mapStations.isNotEmpty()) {
                hasFittedBounds = true
                val points = uiState.mapStations.map { GeoPoint(it.latitude, it.longitude) }
                mapView.post {
                    mapView.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(points), true, 96)
                }
            } else if (uiState.selectedStation != null) {
                mapView.controller.animateTo(
                    GeoPoint(uiState.selectedStation.latitude, uiState.selectedStation.longitude)
                )
            }

            mapView.invalidate()
        },
    )
}

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

    return android.graphics.drawable.BitmapDrawable(null, bitmap)
}

private fun buildTrainMarkerBitmap(color: Int): android.graphics.drawable.Drawable {
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
    return android.graphics.drawable.BitmapDrawable(null, bitmap)
}
