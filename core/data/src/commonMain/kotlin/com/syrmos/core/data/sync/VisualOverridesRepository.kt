package com.syrmos.core.data.sync

import com.syrmos.core.network.SyrmosVisualOverridesService
import com.syrmos.core.network.SyrmosVisualOverridesService.IconsPayload
import com.syrmos.core.network.SyrmosVisualOverridesService.LineDisplay
import com.syrmos.core.network.SyrmosVisualOverridesService.LineDisplayPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull

/**
 * Holds the latest admin-set icon URLs and line-drawing parameters in memory
 * so the Compose map can read them synchronously and re-render when an admin
 * edit lands.
 *
 * Refresh is called on cold start; failures are silent and the StateFlows
 * remain at their last known value (or empty on first launch).
 */
class VisualOverridesRepository(
    private val service: SyrmosVisualOverridesService,
) {
    private val _stationIconUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val stationIconUrls: StateFlow<Map<String, String>> = _stationIconUrls.asStateFlow()

    private val _lineDisplay = MutableStateFlow<Map<String, LineDisplay>>(emptyMap())
    val lineDisplay: StateFlow<Map<String, LineDisplay>> = _lineDisplay.asStateFlow()

    suspend fun refresh() {
        runCatching { service.fetchIcons().firstOrNull() }
            .getOrNull()
            ?.let { apply(it) }
        runCatching { service.fetchLineDisplay().firstOrNull() }
            .getOrNull()
            ?.let { apply(it) }
    }

    private fun apply(payload: IconsPayload) {
        // Interchanges override per-line icons for shared stations (Syntagma, etc.)
        val merged = HashMap<String, String>(payload.stations.size + payload.interchanges.size)
        merged.putAll(payload.stations)
        merged.putAll(payload.interchanges)
        _stationIconUrls.value = merged
    }

    private fun apply(payload: LineDisplayPayload) {
        _lineDisplay.value = payload.lines.associateBy { it.lineId }
    }
}
