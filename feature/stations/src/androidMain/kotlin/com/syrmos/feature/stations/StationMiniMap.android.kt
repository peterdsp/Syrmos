package com.syrmos.feature.stations

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.syrmos.core.designsystem.component.toComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private fun cartoTileSource(name: String, style: String) = XYTileSource(
    name, 0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/$style/",
        "https://b.basemaps.cartocdn.com/$style/",
        "https://c.basemaps.cartocdn.com/$style/",
        "https://d.basemaps.cartocdn.com/$style/",
    ),
    "OpenStreetMap, CARTO",
)

private val CARTO_LIGHT = cartoTileSource("CartoLight", "light_nolabels")
private val CARTO_DARK = cartoTileSource("CartoDark", "dark_nolabels")

@Composable
internal actual fun StationMiniMap(
    latitude: Double,
    longitude: Double,
    stationName: String,
    connectingLines: List<MiniMapLine>,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val primaryColor = connectingLines.firstOrNull()
        ?.color?.toComposeColor()?.toArgb()
        ?: 0xFF0072CE.toInt()

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(if (isDark) CARTO_DARK else CARTO_LIGHT)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            controller.setZoom(15.5)
            controller.setCenter(GeoPoint(latitude, longitude))
        }
    }

    DisposableEffect(Unit) {
        onDispose { mapView.onDetach() }
    }

    AndroidView(
        factory = {
            val pin = createStationPin(primaryColor)
            val marker = Marker(mapView).apply {
                position = GeoPoint(latitude, longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = stationName
                icon = android.graphics.drawable.BitmapDrawable(context.resources, pin)
            }
            mapView.overlays.add(marker)
            mapView.invalidate()
            mapView
        },
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
    )
}

private fun createStationPin(color: Int): Bitmap {
    val size = 48
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val cy = size / 2f
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(cx, cy, 18f, paint)
    paint.color = color
    canvas.drawCircle(cx, cy, 13f, paint)
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(cx, cy, 5f, paint)
    return bmp
}
