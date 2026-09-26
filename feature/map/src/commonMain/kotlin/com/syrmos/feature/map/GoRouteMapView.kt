package com.syrmos.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.map.LatLng
import com.syrmos.core.domain.go.GoCameraAction
import com.syrmos.core.domain.go.GoCameraIntent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** One leg of the journey on the GO route map: its line, colour and stop coordinates in ride order. */
data class GoRouteMapLeg(val lineId: String, val color: Color, val points: List<LatLng>)

/**
 * The GO journey's route map (the Android peer of iOS `GoRouteMapView`): every
 * leg in its real line colour, the rider's current stop as a haloed dot, and a
 * camera with explicit intent (shared `GoCamera` reducer): the route is fitted
 * on first layout, a one-shot [command] runs when [commandTick] changes (Fit
 * route / Follow), the current stop is followed only under FOLLOW, and a manual
 * pan (reported through [onUserPan]) is respected until the rider asks again. Android draws it on the platform map with tiles and attribution; the
 * other targets draw the same route on a canvas so the route never disappears
 * when geography is unavailable.
 */
@Composable
expect fun GoRouteMapView(
    legs: List<GoRouteMapLeg>,
    current: LatLng?,
    accent: Color,
    intent: GoCameraIntent,
    command: GoCameraAction,
    commandTick: Int,
    onUserPan: () -> Unit,
    modifier: Modifier = Modifier,
)

/**
 * The tile-less fallback: the legs and the current stop, fitted to the canvas
 * with a margin, on the muted surface. Used by the non-Android targets and
 * meant to read as a clear schematic, not a blank surface with attribution.
 */
@Composable
internal fun GoRouteFallbackMap(
    legs: List<GoRouteMapLeg>,
    current: LatLng?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val halo = MaterialTheme.colorScheme.surface
    Canvas(modifier.background(surface)) {
        val all = legs.flatMap { it.points } + listOfNotNull(current)
        // A quiet grid so the fallback reads as a map surface.
        val step = 32.dp.toPx()
        var x = 0f
        while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f); y += step }
        if (all.size < 2) return@Canvas

        val minLat = all.minOf { it.lat }; val maxLat = all.maxOf { it.lat }
        val minLng = all.minOf { it.lng }; val maxLng = all.maxOf { it.lng }
        val midLat = (minLat + maxLat) / 2.0
        val kx = cos(midLat * PI / 180.0)
        val spanX = max((maxLng - minLng) * kx, 1e-4)
        val spanY = max(maxLat - minLat, 1e-4)
        val margin = 24.dp.toPx()
        val scale = min((size.width - 2 * margin) / spanX, (size.height - 2 * margin) / spanY).toFloat()
        val drawnW = (spanX * scale).toFloat(); val drawnH = (spanY * scale).toFloat()
        val ox = (size.width - drawnW) / 2f; val oy = (size.height - drawnH) / 2f
        fun project(p: LatLng) = Offset(
            ox + ((p.lng - minLng) * kx * scale).toFloat(),
            oy + ((maxLat - p.lat) * scale).toFloat(),
        )

        val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        legs.forEach { leg ->
            if (leg.points.size < 2) return@forEach
            val path = Path()
            leg.points.forEachIndexed { i, p -> val o = project(p); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
            drawPath(path, leg.color, style = stroke)
            leg.points.forEach { p -> drawCircle(halo, 3.dp.toPx(), project(p)); drawCircle(leg.color, 1.5.dp.toPx(), project(p)) }
        }
        current?.let { c ->
            val o = project(c)
            drawCircle(accent.copy(alpha = 0.25f), 14.dp.toPx(), o)
            drawCircle(halo, 8.dp.toPx(), o)
            drawCircle(accent, 5.5.dp.toPx(), o)
        }
    }
}
