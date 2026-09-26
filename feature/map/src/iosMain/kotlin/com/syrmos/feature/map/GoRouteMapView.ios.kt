package com.syrmos.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.syrmos.core.common.map.LatLng

@Composable
actual fun GoRouteMapView(
    legs: List<GoRouteMapLeg>,
    current: LatLng?,
    accent: Color,
    fitTick: Int,
    modifier: Modifier,
) {
    GoRouteFallbackMap(legs = legs, current = current, accent = accent, modifier = modifier)
}
