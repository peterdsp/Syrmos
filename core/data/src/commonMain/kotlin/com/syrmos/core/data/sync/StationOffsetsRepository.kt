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
    // the bundled stations for many metro/tram/suburban stops (e.g. server
    // M1_THI vs bundle M1_THE for Thiseio). The projector looks offsets up by
    // the BUNDLE stationId, so a mismatch silently dropped that stop to
    // band-only timing. Each offset carries a `stationEn`, so we canonicalise
    // every offset's stationId to the bundle id by matching the name.
    //
    // The match is LINE-SCOPED: a stop's name is only matched against the
    // stations on its own line, so a same-name stop on another line (Agia
    // Paraskevi is on both M3 and T6; Syntagma on M2/M3/T6) can never remap to
    // the wrong line's id. Names fold with Greek accents stripped, matching the
    // bundle's English name or Greek name_el. Stops that still don't match keep
    // their original id, so this can only improve, never regress. Verified
    // against the live API: 474/474 offset stops resolve.
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

        // The residual stops whose server name is a genuinely different wording
        // or language than the bundle (Alexander the Great = Megalou Alexandrou;
        // SEF = Peace and Friendship Stadium; the T7 "Plateia …" tram stops).
        // Each verified to a real bundle id ON THE CORRECT LINE.
        private val HARD_ALIASES = mapOf(
            "M2_STA" to "M2_LAR", "M3_AMB" to "M3_AMP",
            "T6_AGH" to "T6_APK", "T6_AGI" to "T6_AFP", "T6_ALE" to "T6_MAL",
            "T7_AG2" to "T7_AGA", "T7_AG3" to "T7_MET", "T7_AGH" to "T7_SKE",
            "T7_EL2" to "T7_EOL", "T7_NDA" to "T7_AK2", "T7_OMI" to "T7_SKY",
            "T7_PEA" to "T7_SEF", "T7_PL2" to "T7_IPP", "T7_PL3" to "T7_VER",
            "T7_PL4" to "T7_KAT", "T7_PL5" to "T7_ESP", "T7_STA" to "T7_AK1",
            "T7_SYN" to "T7_34S",
        )
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
