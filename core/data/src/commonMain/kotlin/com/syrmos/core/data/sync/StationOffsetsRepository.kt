package com.syrmos.core.data.sync

import com.syrmos.core.data.seed.ResourceReader
import com.syrmos.core.network.SyrmosSchedulesService
import com.syrmos.core.network.SyrmosSchedulesService.StationOffsetGroup
import com.syrmos.core.network.SyrmosSchedulesService.StationOffsetsPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
        ensureNameIndex()
        _offsets.value = indexByDirection(payload.lines)
    }

    /** Network refresh. Silent on failure. */
    suspend fun refresh() {
        val payload = schedulesService.fetchStationOffsets().firstOrNull() ?: return
        ensureNameIndex()
        val indexed = indexByDirection(payload.lines)
        if (indexed.isNotEmpty()) {
            _offsets.value = indexed
        }
    }

    // Station-ID reconciliation. The Pi's /api/station-offsets (and the STASY
    // scraper that seeds it) use a different 3-letter abbreviation scheme than
    // the bundled stations for ~53 metro/tram stops (e.g. server M1_THI vs
    // bundle M1_THE for Thiseio). The projector looks offsets up by the BUNDLE
    // stationId, so a mismatch silently dropped those stops to band-only timing.
    // Each offset carries a `stationEn`, so we canonicalise every offset's
    // stationId to the bundle id by matching the name (English or Greek). Stops
    // that don't match keep their original id, so this can only improve, never
    // regress. Canonical source of truth = the bundle stations.
    private var canonicalIdByName: Map<String, String> = emptyMap()

    @Serializable
    private data class SeedStation(
        val id: String = "",
        val name: String = "",
        @SerialName("name_el") val nameEl: String = "",
    )

    private suspend fun ensureNameIndex() {
        if (canonicalIdByName.isNotEmpty()) return
        val reader = resourceReader ?: return
        val body = runCatching { reader.readText("files/seed/stations.json") }.getOrNull() ?: return
        val stations = runCatching { json.decodeFromString<List<SeedStation>>(body) }.getOrNull() ?: return
        val map = mutableMapOf<String, String>()
        for (s in stations) {
            if (s.id.isBlank()) continue
            normalizeName(s.name)?.let { map.putIfAbsent(it, s.id) }
            normalizeName(s.nameEl)?.let { map.putIfAbsent(it, s.id) }
        }
        canonicalIdByName = map
    }

    /** Fold a station name to a match key: lowercase, letters/digits only. */
    private fun normalizeName(n: String): String? {
        val cleaned = buildString {
            for (c in n.lowercase()) if (c.isLetterOrDigit()) append(c)
        }
        return cleaned.ifBlank { null }
    }

    private fun canonicalize(stop: SyrmosSchedulesService.StationOffsetStop): SyrmosSchedulesService.StationOffsetStop {
        val canonical = normalizeName(stop.stationEn)?.let { canonicalIdByName[it] }
        return if (canonical != null && canonical != stop.stationId) stop.copy(stationId = canonical) else stop
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
            out[key] = g.stops.map { canonicalize(it) }.sortedBy { it.stopSequence }
        }
        return out
    }
}
