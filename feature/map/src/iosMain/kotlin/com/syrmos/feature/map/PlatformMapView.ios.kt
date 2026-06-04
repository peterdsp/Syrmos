package com.syrmos.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun PlatformMapView(
    uiState: MapUiState,
    onStationSelected: (String) -> Unit,
    modifier: Modifier,
    initialScale: Float,
) {
    FallbackPlatformMapView(
        uiState = uiState,
        onStationSelected = onStationSelected,
        modifier = modifier,
        initialScale = initialScale,
    )
}
