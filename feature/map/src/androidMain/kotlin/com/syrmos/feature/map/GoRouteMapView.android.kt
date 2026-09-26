package com.syrmos.feature.map

import android.graphics.drawable.GradientDrawable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.syrmos.core.common.map.LatLng
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * osmdroid rendering of the GO route: one [Polyline] per leg in its line colour,
 * a haloed marker on the current stop, attribution kept visible, and a camera
 * that fits the route only when [fitTick] changes so manual exploration survives
 * recomposition and reflow. One map instance per GO screen.
 */
@Composable
actual fun GoRouteMapView(
    legs: List<GoRouteMapLeg>,
    current: LatLng?,
    accent: Color,
    fitTick: Int,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val legOverlays = remember { mutableListOf<Polyline>() }
    var currentMarker by remember { mutableStateOf<Marker?>(null) }
    var lastFit by remember { mutableStateOf(Int.MIN_VALUE) }

    DisposableEffect(context) {
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose { }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(tileSourceFor(dark))
                setMultiTouchControls(true)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                minZoomLevel = 9.0
                maxZoomLevel = 18.0
                controller.setZoom(12.0)
                controller.setCenter(GeoPoint(37.98, 23.73))
                overlays.add(CopyrightOverlay(ctx))
            }
        },
        update = { mv ->
            val desired = tileSourceFor(dark)
            if (mv.tileProvider.tileSource != desired) mv.setTileSource(desired)

            legOverlays.forEach { mv.overlays.remove(it) }
            legOverlays.clear()
            legs.forEach { leg ->
                if (leg.points.size < 2) return@forEach
                val polyline = Polyline().apply {
                    outlinePaint.color = leg.color.toArgb()
                    outlinePaint.strokeWidth = 16f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                    setPoints(leg.points.map { GeoPoint(it.lat, it.lng) })
                    setOnClickListener { _, _, _ -> false }
                }
                legOverlays.add(polyline)
                mv.overlays.add(0, polyline)
            }

            currentMarker?.let { mv.overlays.remove(it) }
            currentMarker = current?.let { c ->
                Marker(mv).apply {
                    position = GeoPoint(c.lat, c.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(accent.toArgb())
                        setStroke(8, android.graphics.Color.WHITE)
                        setSize(44, 44)
                    }
                    setInfoWindow(null)
                    setOnMarkerClickListener { _, _ -> true }
                }.also { mv.overlays.add(it) }
            }

            if (lastFit != fitTick) {
                lastFit = fitTick
                val all = legs.flatMap { it.points }.map { GeoPoint(it.lat, it.lng) }
                if (all.size >= 2) {
                    val box = BoundingBox.fromGeoPoints(all).increaseByScale(1.3f)
                    val fit = { mv.zoomToBoundingBox(box, false, 24) }
                    if (mv.width == 0 || mv.height == 0) {
                        mv.addOnFirstLayoutListener { _, _, _, _, _ -> fit() }
                    } else {
                        mv.post { fit() }
                    }
                }
            }
            mv.invalidate()
        },
    )
}
