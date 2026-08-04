package com.syrmos.feature.stations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.syrmos.core.designsystem.component.toComposeColor

@Composable
internal actual fun StationMiniMap(
    latitude: Double,
    longitude: Double,
    stationName: String,
    connectingLines: List<MiniMapLine>,
    modifier: Modifier,
) {
    val bgColor = Color(0xFFE8ECF0)
    val gridColor = Color(0xFFD0D4D8)
    val primaryColor = connectingLines.firstOrNull()
        ?.color?.toComposeColor() ?: Color(0xFF0072CE)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor),
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        val gridSpacing = 40f
        var x = gridSpacing
        while (x < w) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 0.5f)
            x += gridSpacing
        }
        var y = gridSpacing
        while (y < h) {
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 0.5f)
            y += gridSpacing
        }

        drawCircle(color = primaryColor.copy(alpha = 0.12f), radius = 60f, center = Offset(cx, cy))
        drawCircle(color = Color.White, radius = 16f, center = Offset(cx, cy))
        drawCircle(color = primaryColor, radius = 12f, center = Offset(cx, cy))
        drawCircle(color = Color.White, radius = 4f, center = Offset(cx, cy))
    }
}
