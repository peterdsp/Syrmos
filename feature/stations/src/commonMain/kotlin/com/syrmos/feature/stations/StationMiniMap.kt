package com.syrmos.feature.stations

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.syrmos.core.model.transit.LineColor

data class MiniMapLine(
    val name: String,
    val color: LineColor,
)

@Composable
internal expect fun StationMiniMap(
    latitude: Double,
    longitude: Double,
    stationName: String,
    connectingLines: List<MiniMapLine>,
    modifier: Modifier = Modifier,
)
