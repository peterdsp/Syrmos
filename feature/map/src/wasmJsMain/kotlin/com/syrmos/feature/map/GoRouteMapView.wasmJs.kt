package com.syrmos.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.syrmos.core.common.map.LatLng
import com.syrmos.core.domain.go.GoCameraAction
import com.syrmos.core.domain.go.GoCameraIntent

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
    GoRouteFallbackMap(legs = legs, current = current, accent = accent, modifier = modifier)
}
