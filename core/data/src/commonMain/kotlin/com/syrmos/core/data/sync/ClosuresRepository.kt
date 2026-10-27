package com.syrmos.core.data.sync

import com.syrmos.core.common.Broadcast
import com.syrmos.core.common.ClosureEffect
import com.syrmos.core.common.ClosureMode
import com.syrmos.core.common.LocalizedText
import com.syrmos.core.common.ReplacementBus
import com.syrmos.core.common.ReplacementBusStop
import com.syrmos.core.common.StationClosure
import com.syrmos.core.common.StationClosures
import com.syrmos.core.common.loadStringPref
import com.syrmos.core.common.persistStringPref
import com.syrmos.core.data.seed.ResourceReader
import com.syrmos.core.network.SyrmosClosuresService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Offline-first station closures (3.0.2), shaped like [AnnouncementsRepository]:
 *  - hydrateFromBundleIfNeeded() loads the last live payload cached on device,
 *    else the bundled `files/seed/schedules-v2/closures.json`, so a closure still
 *    shows on a cold start with no network.
 *  - refresh() pulls `/api/closures`; on failure the StateFlow keeps its value.
 *
 * The window is evaluated on device with [StationClosures.effect], so a closure
 * that starts at 08:00 flips at 08:00 even offline. [closedStationIdsFlow] re-runs
 * the rule every 30 s for the map grey-out and the badges.
 */
class ClosuresRepository(
    private val service: SyrmosClosuresService,
    private val resourceReader: ResourceReader? = null,
) {
    private val _closures = MutableStateFlow<List<StationClosure>>(emptyList())
    val closures: StateFlow<List<StationClosure>> = _closures.asStateFlow()

    private var hydrated = false

    suspend fun hydrateFromBundleIfNeeded() {
        if (hydrated || _closures.value.isNotEmpty()) return
        hydrated = true
        val cached = runCatching { loadStringPref(CACHE_KEY, "") }.getOrDefault("")
        val fromCache = if (cached.isNotBlank()) ClosuresPayloadParser.parseClosures(cached) else null
        if (fromCache != null) {
            _closures.value = fromCache
            return
        }
        val reader = resourceReader ?: return
        val body = runCatching { reader.readText(BUNDLE_PATH) }.getOrNull() ?: return
        ClosuresPayloadParser.parseClosures(body)?.let { _closures.value = it }
    }

    /** Live refresh from /api/closures. Silent on network failure. */
    suspend fun refresh() {
        val body = service.fetchClosuresBody() ?: return
        val parsed = ClosuresPayloadParser.parseClosures(body) ?: return
        hydrated = true
        _closures.value = parsed
        runCatching { persistStringPref(CACHE_KEY, body) }
    }

    /** Test and debug seam: replace the in-memory set. */
    fun setClosures(list: List<StationClosure>) {
        _closures.value = list
    }

    fun effect(stationId: String, lineId: String?, now: Long = nowEpochSeconds()): ClosureEffect =
        StationClosures.effect(_closures.value, stationId, lineId, now)

    /** Effect for a per-line station id, deriving its line from the id. */
    fun effectForStation(stationId: String, now: Long = nowEpochSeconds()): ClosureEffect =
        effect(stationId, StationClosures.lineIdOfStation(stationId), now)

    fun closureFor(effect: ClosureEffect): StationClosure? =
        StationClosures.closureFor(_closures.value, effect)

    fun closedStationIds(now: Long = nowEpochSeconds()): Set<String> =
        StationClosures.closedStationIds(_closures.value, now)

    /** Closed station ids, re-evaluated when data changes and every 30 s. */
    fun closedStationIdsFlow(): Flow<Set<String>> =
        combine(_closures, ticker()) { list, now -> StationClosures.closedStationIds(list, now) }
            .distinctUntilChanged()

    private fun ticker(): Flow<Long> = flow {
        while (true) {
            emit(nowEpochSeconds())
            delay(30_000)
        }
    }

    companion object {
        const val BUNDLE_PATH = "files/seed/schedules-v2/closures.json"
        private const val CACHE_KEY = "closures_cache_v1"
        fun nowEpochSeconds(): Long = Clock.System.now().epochSeconds
    }
}

/**
 * Decoder for the 3.0.2 closures and broadcasts payloads. Lenient on purpose: an
 * unknown mode or a row missing its id is skipped instead of failing the whole
 * payload, and a malformed body returns null so callers keep their prior value.
 */
object ClosuresPayloadParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    fun parseClosures(body: String): List<StationClosure>? {
        if (body.isBlank()) return null
        val payload = runCatching { json.decodeFromString<ClosuresPayload>(body) }.getOrNull() ?: return null
        return payload.closures.mapNotNull { it.toModel() }
    }

    fun parseBroadcasts(body: String): List<Broadcast>? {
        if (body.isBlank()) return null
        val payload = runCatching { json.decodeFromString<BroadcastsPayload>(body) }.getOrNull() ?: return null
        return payload.broadcasts.mapNotNull { it.toModel() }
    }

    @Serializable
    private data class Text(val en: String? = null, val el: String? = null, val sq: String? = null, val it: String? = null) {
        fun toModel() = LocalizedText(en = en, el = el, sq = sq, it = it)
    }

    @Serializable
    private data class BusStop(val name: Text = Text(), val lat: Double? = null, val lon: Double? = null)

    @Serializable
    private data class Bus(
        val label: Text = Text(),
        val fromStop: Text? = null,
        val toStop: Text? = null,
        val headwayMinutes: Int? = null,
        val stops: List<BusStop> = emptyList(),
    )

    @Serializable
    private data class ClosuresPayload(val schemaVersion: Int = 1, val closures: List<ClosureDto> = emptyList())

    @Serializable
    private data class ClosureDto(
        val id: String = "",
        val stationIds: List<String> = emptyList(),
        val lineIds: List<String> = emptyList(),
        val mode: String = "",
        val startsAtEpochSeconds: Long? = null,
        val endsAtEpochSeconds: Long? = null,
        val status: String = "scheduled",
        val reason: Text? = null,
        val replacementBus: Bus? = null,
        val source: String = "admin",
    ) {
        fun toModel(): StationClosure? {
            val m = ClosureMode.fromRaw(mode) ?: return null
            val start = startsAtEpochSeconds ?: return null
            if (id.isBlank() || stationIds.isEmpty()) return null
            return StationClosure(
                id = id,
                stationIds = stationIds,
                lineIds = lineIds,
                mode = m,
                startsAtEpochSeconds = start,
                endsAtEpochSeconds = endsAtEpochSeconds,
                status = status,
                reason = reason?.toModel(),
                replacementBus = replacementBus?.let { b ->
                    ReplacementBus(
                        label = b.label.toModel(),
                        fromStop = b.fromStop?.toModel(),
                        toStop = b.toStop?.toModel(),
                        headwayMinutes = b.headwayMinutes,
                        stops = b.stops.map { ReplacementBusStop(it.name.toModel(), it.lat, it.lon) },
                    )
                },
                source = source,
            )
        }
    }

    @Serializable
    private data class BroadcastsPayload(val schemaVersion: Int = 1, val broadcasts: List<BroadcastDto> = emptyList())

    @Serializable
    private data class BroadcastDto(
        val id: String = "",
        val title: Text = Text(),
        val body: Text = Text(),
        val audience: String = "all",
        val sentAtEpochSeconds: Long? = null,
        val expiresAtEpochSeconds: Long? = null,
        val deepLink: String? = null,
        val closureId: String? = null,
    ) {
        fun toModel(): Broadcast? {
            if (id.isBlank()) return null
            return Broadcast(
                id = id,
                title = title.toModel(),
                body = body.toModel(),
                audience = audience,
                sentAtEpochSeconds = sentAtEpochSeconds ?: return null,
                expiresAtEpochSeconds = expiresAtEpochSeconds,
                deepLink = deepLink,
                closureId = closureId,
            )
        }
    }
}
