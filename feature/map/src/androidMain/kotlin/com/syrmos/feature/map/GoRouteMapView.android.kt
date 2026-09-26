package com.syrmos.feature.map

import android.graphics.drawable.GradientDrawable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.syrmos.core.common.map.LatLng
import com.syrmos.core.domain.go.GoCamera
import com.syrmos.core.domain.go.GoCameraAction
import com.syrmos.core.domain.go.GoCameraEvent
import com.syrmos.core.domain.go.GoCameraIntent
import androidx.compose.runtime.rememberUpdatedState
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
    intent: GoCameraIntent,
    command: GoCameraAction,
    commandTick: Int,
    onUserPan: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val legOverlays = remember { mutableListOf<Polyline>() }
    var currentMarker by remember { mutableStateOf<Marker?>(null) }
    // Camera bookkeeping outside Compose state: writing state from `update`
    // would recompose in a loop, and the map is the only reader.
    // The camera survives an activity recreation (fold, rotation): centre and
    // zoom are saved as the rider moves, restored in the factory, and a restored
    // camera skips the first fit so a manual view is not thrown away by reflow.
    val camLat = rememberSaveable { mutableStateOf(Double.NaN) }
    val camLng = rememberSaveable { mutableStateOf(Double.NaN) }
    val camZoom = rememberSaveable { mutableStateOf(Double.NaN) }
    class CameraMemo { var firstFitDone = false; var lastCommandTick = Int.MIN_VALUE; var lastCurrent: LatLng? = null }
    val memo = remember { CameraMemo().also { it.firstFitDone = !camZoom.value.isNaN() } }
    val panCallback by rememberUpdatedState(onUserPan)

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
                if (camZoom.value.isNaN()) {
                    controller.setZoom(12.0)
                    controller.setCenter(GeoPoint(37.98, 23.73))
                } else {
                    controller.setZoom(camZoom.value)
                    controller.setCenter(GeoPoint(camLat.value, camLng.value))
                }
                overlays.add(CopyrightOverlay(ctx))
                addMapListener(object : MapListener {
                    private fun remember(source: org.osmdroid.api.IMapView?) {
                        source ?: return
                        camLat.value = source.mapCenter.latitude
                        camLng.value = source.mapCenter.longitude
                        camZoom.value = source.zoomLevelDouble
                    }
                    override fun onScroll(event: ScrollEvent?): Boolean { remember(event?.source); return false }
                    override fun onZoom(event: ZoomEvent?): Boolean { remember(event?.source); return false }
                })
                // A finger moving on the map is the rider exploring: report it so
                // the intent turns manual. Programmatic moves never fire this.
                setOnTouchListener { _, ev ->
                    if (ev.actionMasked == android.view.MotionEvent.ACTION_MOVE) panCallback()
                    false
                }
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

            // Camera with explicit intent: fit the whole route once on first
            // layout, run a one-shot command when its tick changes, otherwise
            // move only when the current stop really changed and the intent is
            // FOLLOW. An identical recomposition never moves the camera.
            val allPoints = legs.flatMap { it.points }.map { GeoPoint(it.lat, it.lng) }
            val fitRoute = {
                if (allPoints.size >= 2) {
                    val box = BoundingBox.fromGeoPoints(allPoints).increaseByScale(1.3f)
                    val fit = { mv.zoomToBoundingBox(box, true, 24) }
                    if (mv.width == 0 || mv.height == 0) mv.addOnFirstLayoutListener { _, _, _, _, _ -> fit() } else mv.post { fit() }
                }
            }
            val action = when {
                !memo.firstFitDone && allPoints.size >= 2 -> { memo.firstFitDone = true; GoCameraAction.FIT_ROUTE }
                memo.lastCommandTick != commandTick -> command
                memo.lastCurrent != current -> GoCamera.reduce(intent, GoCameraEvent.CURRENT_STOP_CHANGED).action
                else -> GoCameraAction.NONE
            }
            memo.lastCommandTick = commandTick
            memo.lastCurrent = current
            when (action) {
                GoCameraAction.FIT_ROUTE -> fitRoute()
                GoCameraAction.CENTER_CURRENT ->
                    if (current != null) mv.controller.animateTo(GeoPoint(current.lat, current.lng)) else fitRoute()
                GoCameraAction.NONE -> Unit
            }
            mv.invalidate()
        },
    )
}
