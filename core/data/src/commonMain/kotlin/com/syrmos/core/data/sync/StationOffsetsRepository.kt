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

    // Station-ID reconciliation. The server and bundle now share canonical IDs
    // after the M6 reconciliation, but the name-index approach is kept as a
    // safety net for any future server-side ID changes. Line-scoped matching
    // prevents cross-line collisions (Agia Paraskevi on M3 vs T6, etc.).
    private var canonicalByLine: Map<String, Map<String, String>> = emptyMap()

    @Serializable
    private data class SeedStation(
        val id: String = "",
        val name: String = "",
        @SerialName("name_el") val nameEl: String = "",
    )

    @Serializable
    private data class SeedRoute(
        @SerialName("line_id") val lineId: String = "",
        @SerialName("station_ids") val stationIds: List<String> = emptyList(),
    )

    private suspend fun ensureNameIndex() {
        if (canonicalByLine.isNotEmpty()) return
        val reader = resourceReader ?: return
        val stationsBody = runCatching { reader.readText("files/seed/stations.json") }.getOrNull() ?: return
        val routesBody = runCatching { reader.readText("files/seed/routes.json") }.getOrNull() ?: return
        val stations = runCatching { json.decodeFromString<List<SeedStation>>(stationsBody) }.getOrNull() ?: return
        val routes = runCatching { json.decodeFromString<List<SeedRoute>>(routesBody) }.getOrNull() ?: return
        val byId = stations.associateBy { it.id }
        val out = mutableMapOf<String, MutableMap<String, String>>()
        for (r in routes) {
            if (r.lineId.isBlank()) continue
            val m = out.getOrPut(r.lineId) { mutableMapOf() }
            for (sid in r.stationIds) {
                val s = byId[sid] ?: continue
                // Not putIfAbsent: that's a JVM-only Map method (compiles on
                // Android, breaks the wasmJs/web build).
                fold(s.name)?.let { if (it !in m) m[it] = sid }
                fold(s.nameEl)?.let { if (it !in m) m[it] = sid }
            }
        }
        canonicalByLine = out
    }

    /** Fold a name to a match key: lowercase, Greek accents stripped, alnum only. */
    private fun fold(n: String): String? {
        val sb = StringBuilder()
        for (c in n.lowercase()) {
            val d = GREEK_ACCENTS[c] ?: c
            if (d.isLetterOrDigit()) sb.append(d)
        }
        return sb.toString().ifBlank { null }
    }

    private fun canonicalize(
        stop: SyrmosSchedulesService.StationOffsetStop,
        lineId: String,
    ): SyrmosSchedulesService.StationOffsetStop {
        // The M3 airport branch's offsets are grouped under M3_AIR but its stops
        // are M3 stations.
        val base = lineId.removeSuffix("_AIR")
        val canonical = HARD_ALIASES[stop.stationId]
            ?: fold(stop.stationEn)?.let { canonicalByLine[base]?.get(it) }
        return if (canonical != null && canonical != stop.stationId) stop.copy(stationId = canonical) else stop
    }

    private companion object {
        /** Greek accented vowels -> plain (no NFD in KMP commonMain). */
        private val GREEK_ACCENTS = mapOf(
            'ά' to 'α', 'έ' to 'ε', 'ή' to 'η', 'ί' to 'ι', 'ϊ' to 'ι', 'ΐ' to 'ι',
            'ό' to 'ο', 'ύ' to 'υ', 'ϋ' to 'υ', 'ΰ' to 'υ', 'ώ' to 'ω',
        )

        // After the M6 station-ID reconciliation the bundle uses the same
        // canonical IDs as the server, so no hard aliases are needed.
        private val HARD_ALIASES = emptyMap<String, String>()
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
            out[key] = g.stops.map { canonicalize(it, g.lineId) }.sortedBy { it.stopSequence }
        }
        return out
    }
}
