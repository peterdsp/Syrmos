package com.syrmos.core.data.sync

import com.syrmos.core.data.seed.ResourceReader
import com.syrmos.core.network.SyrmosSchedulesService
import com.syrmos.core.network.SyrmosSchedulesService.StationOffsetGroup
import com.syrmos.core.network.SyrmosSchedulesService.StationOffsetsPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json

/**
 * Per-station minutes-from-origin offsets for M1, M2, M3, T6 and T7. Sourced
 * from STASY's HTML timetable pages and exposed by the Pi as
 * `/api/station-offsets`. Combined with `ScheduleSyncRepository`'s
 * band-projected origin departures, this lets the projector emit exact
 * HH:MM at every metro and tram stop instead of just the terminal.
 *
 * Behaviour mirrors `ScheduleSyncRepository`:
 * - In-memory `StateFlow<Map<String, List<StationOffsetStop>>>` keyed by
 *   `"<lineId>|<direction>"` for O(1) lookup in the projector hot path.
 * - Cold start: hydrate from bundled `files/seed/schedules-v2/station-offsets.json`.
 * - Live refresh: GET `/api/station-offsets`, swap the in-memory map.
 * - All failures silent; the projector falls back to band-only when the map
 *   is empty for that (line, direction).
 */
class StationOffsetsRepository(
    private val schedulesService: SyrmosSchedulesService,
    private val resourceReader: ResourceReader? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** key: "lineId|direction" (e.g. "M2|outbound") -> ordered stops. */
    private val _offsets = MutableStateFlow<Map<String, List<SyrmosSchedulesService.StationOffsetStop>>>(emptyMap())
    val offsets: StateFlow<Map<String, List<SyrmosSchedulesService.StationOffsetStop>>> = _offsets.asStateFlow()

    suspend fun hydrateFromBundleIfNeeded() {
        if (_offsets.value.isNotEmpty()) return
        val reader = resourceReader ?: return
        val body = runCatching {
            reader.readText("files/seed/schedules-v2/station-offsets.json")
        }.getOrNull() ?: return
        if (body.isBlank() || body == "{}") return
        val payload = runCatching {
            json.decodeFromString<StationOffsetsPayload>(body)
        }.getOrNull() ?: return
        _offsets.value = indexByDirection(payload.lines)
    }

    /** Network refresh. Silent on failure. */
    suspend fun refresh() {
        val payload = schedulesService.fetchStationOffsets().firstOrNull() ?: return
        val indexed = indexByDirection(payload.lines)
        if (indexed.isNotEmpty()) {
            _offsets.value = indexed
        }
    }

    /**
     * Lookup helper: returns the (stopSequence, minutesFromOrigin) for the
     * given (line, direction, stationId) or null when not present.
     */
    fun offsetFor(lineId: String, direction: String, stationId: String): SyrmosSchedulesService.StationOffsetStop? {
        if (stationId.isBlank()) return null
        return _offsets.value["$lineId|$direction"]?.firstOrNull { it.stationId == stationId }
    }

    /** All stops in order for the (line, direction) group, or empty. */
    fun stopsFor(lineId: String, direction: String): List<SyrmosSchedulesService.StationOffsetStop> {
        return _offsets.value["$lineId|$direction"].orEmpty()
    }

    private fun indexByDirection(
        groups: List<StationOffsetGroup>,
    ): Map<String, List<SyrmosSchedulesService.StationOffsetStop>> {
        if (groups.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, List<SyrmosSchedulesService.StationOffsetStop>>()
        for (g in groups) {
            val key = "${g.lineId}|${g.direction}"
            out[key] = g.stops.sortedBy { it.stopSequence }
        }
        return out
    }
}
