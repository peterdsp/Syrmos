package com.syrmos.core.data.sync

import com.syrmos.core.data.seed.ResourceReader
import com.syrmos.core.network.SyrmosSchedulesService
import com.syrmos.core.network.SyrmosSchedulesService.LineSchedule
import com.syrmos.core.network.SyrmosSchedulesService.Manifest
import com.syrmos.core.network.SyrmosSchedulesService.ManifestResult
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json

/**
 * Cold-start sync for line schedule rules + frequency bands.
 *
 * Behaviour:
 * - Calls `/api/schedules/manifest` with the last seen ETag.
 * - On 304: nothing to do, state remains current.
 * - On 200: pulls each line bundle whose hash changed, caches in memory.
 * - On any failure: silent; the app keeps whatever it has (bundled seed at worst).
 *
 * In-memory only for now — a follow-up wires platform-specific persistent
 * caches (DataStore on Android, file in Documents on iOS, IndexedDB on Web)
 * once the domain layer consumes this shape directly.
 */
@OptIn(ExperimentalTime::class)
class ScheduleSyncRepository(
    private val schedulesService: SyrmosSchedulesService,
    private val resourceReader: ResourceReader? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Hydrate the in-memory cache from the bundled snapshot (`files/seed/schedules-v2/`).
     * Generated at build time by `scripts/snapshot-api-to-seed.py`. Safe to call multiple
     * times — only loads if the cache is currently empty so a live refresh isn't clobbered.
     */
    suspend fun hydrateFromBundleIfNeeded() {
        if (_lineBundles.value.isNotEmpty()) return
        val reader = resourceReader ?: return
        // Metro/tram/suburban + Thessaloniki + national rail + rail-replacement
        // buses. The national/bus bundles carry per-trip timetables that the map's
        // schedule projector interpolates into moving vehicles.
        val knownLineIds = listOf(
            "M1", "M2", "M3", "M3_AIR", "T6", "T7", "A1", "A2", "A3", "A4",
            "TM1", "TM2", "TP1", "TP2", "TP3", "TP4",
            "IC1", "RG1", "AL1", "KO1", "PL1", "DK1",
            "PS1", "PS2", "PSB", "PU1", "PU2",
            "KB1", "VL1", "DX1", "KP1", "TL1",
        )
        val out = mutableMapOf<String, LineSchedule>()
        for (lid in knownLineIds) {
            val body = runCatching {
                reader.readText("files/seed/schedules-v2/$lid.json")
            }.getOrNull() ?: continue
            if (body.isBlank() || body == "{}") continue
            val parsed = runCatching {
                json.decodeFromString<LineSchedule>(body)
            }.getOrNull() ?: continue
            out[lid] = parsed
        }
        if (out.isNotEmpty()) {
            _lineBundles.value = out
        }
        runCatching {
            val manifestBody = reader.readText("files/seed/schedules-v2/manifest.json")
            val manifest = json.decodeFromString<Manifest>(manifestBody)
            _manifest.value = manifest
            _scheduleVersion.value = manifest.version
            // The cached bundles are exactly the snapshot the bundled manifest
            // describes, so their hashes start as the bundled manifest's hashes.
            // A server line whose hash later differs from this is what we re-fetch.
            lineHashes = manifest.perLineHashes
        }
    }

    private val _manifest = MutableStateFlow<Manifest?>(null)
    val manifest: StateFlow<Manifest?> = _manifest.asStateFlow()

    private val _scheduleVersion = MutableStateFlow<Int?>(null)
    val scheduleVersion: StateFlow<Int?> = _scheduleVersion.asStateFlow()

    private val _lineBundles = MutableStateFlow<Map<String, LineSchedule>>(emptyMap())
    val lineBundles: StateFlow<Map<String, LineSchedule>> = _lineBundles.asStateFlow()

    // The per-line content hashes the CACHED bundles correspond to: seeded from
    // the bundled manifest on hydrate, updated as lines are re-fetched, and
    // compared against the server manifest to decide what changed. Without a
    // stored hash the old filter compared the server hash to itself (always
    // equal), so a changed line was never pulled and Android could not update a
    // timetable without an app release.
    private var lineHashes: Map<String, String> = emptyMap()

    private val _lastSyncAt = MutableStateFlow<Instant?>(null)
    val lastSyncAt: StateFlow<Instant?> = _lastSyncAt.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Returns true when at least one line was refreshed, false otherwise
     * (no network, server unreachable, or nothing changed).
     */
    suspend fun refresh(): RefreshOutcome {
        _isRefreshing.value = true
        try {
        val previousEtag = _manifest.value?.etag
        val result = schedulesService.fetchManifest(previousEtag).firstOrNull()
            ?: return RefreshOutcome.Failure("no result")

        return when (result) {
            is ManifestResult.NotModified -> {
                _lastSyncAt.value = Clock.System.now()
                RefreshOutcome.UpToDate
            }
            is ManifestResult.Failure -> RefreshOutcome.Failure(result.reason)
            is ManifestResult.Fresh -> {
                val manifest = result.manifest
                // nginx serves a file-derived HTTP ETag, not our computed one,
                // so we always get a body back. Short-circuit when the body
                // etag matches what we already cached.
                if (manifest.etag.isNotBlank() && manifest.etag == previousEtag) {
                    _lastSyncAt.value = Clock.System.now()
                    return RefreshOutcome.UpToDate
                }
                val current = _lineBundles.value.toMutableMap()
                val toFetch = linesToFetch(
                    cachedLineIds = current.keys,
                    storedHashes = lineHashes,
                    manifestHashes = manifest.perLineHashes,
                )

                val fetched = mutableSetOf<String>()
                for (lineId in toFetch) {
                    val bundle = schedulesService.fetchLineBundle(lineId).firstOrNull()
                    if (bundle != null) {
                        current[lineId] = bundle
                        fetched += lineId
                    }
                }
                lineHashes = hashesAfterFetch(lineHashes, manifest.perLineHashes, fetched)
                _lineBundles.value = current
                _lastSyncAt.value = Clock.System.now()
                // Only advance the cached manifest/etag when every attempted line
                // landed. On a partial failure keep the previous manifest so the
                // next refresh re-runs the per-line diff via the Fresh path: our
                // stored etag stays behind the server's, so a 304 / etag-equal
                // short-circuit can never strand the still-stale line until the
                // server happens to publish another manifest.
                if (shouldAdvanceManifest(toFetch, fetched)) {
                    _manifest.value = manifest
                    _scheduleVersion.value = manifest.version
                }
                RefreshOutcome.Refreshed(fetched.size)
            }
        }
        } finally {
            _isRefreshing.value = false
        }
    }

    sealed interface RefreshOutcome {
        data object UpToDate : RefreshOutcome
        data class Refreshed(val linesRefreshed: Int) : RefreshOutcome
        data object Skipped : RefreshOutcome
        data class Failure(val reason: String) : RefreshOutcome
    }

    companion object {
        /**
         * Which line ids must be pulled from the server: every line missing from
         * the cache, plus every line whose cached content hash differs from the
         * server manifest's hash. The previous inline filter compared
         * `manifest.perLineHashes[lid]` to that same entry's value (always
         * equal), so a changed line was never fetched and, with all lines
         * pre-hydrated from the bundle, nothing was ever fetched at all.
         */
        internal fun linesToFetch(
            cachedLineIds: Set<String>,
            storedHashes: Map<String, String>,
            manifestHashes: Map<String, String>,
        ): List<String> =
            manifestHashes.entries
                .filter { (lid, serverHash) -> lid !in cachedLineIds || storedHashes[lid] != serverHash }
                .map { it.key }

        /**
         * The stored per-line hashes after a fetch round: a line that fetched
         * successfully takes the server hash; a line that failed (not in
         * [fetchedLineIds]) keeps its previous hash so the next [linesToFetch]
         * still reports it as stale and retries it.
         */
        internal fun hashesAfterFetch(
            storedHashes: Map<String, String>,
            manifestHashes: Map<String, String>,
            fetchedLineIds: Set<String>,
        ): Map<String, String> =
            storedHashes.toMutableMap().apply {
                fetchedLineIds.forEach { lid -> manifestHashes[lid]?.let { this[lid] = it } }
            }

        /**
         * Advance the cached manifest/etag only when every attempted line landed
         * (an empty attempt list counts as success). On a partial failure the
         * manifest must stay behind so the next refresh re-runs the diff instead
         * of short-circuiting on an advanced etag and stranding the stale line.
         */
        internal fun shouldAdvanceManifest(
            attemptedLineIds: List<String>,
            fetchedLineIds: Set<String>,
        ): Boolean = attemptedLineIds.all { it in fetchedLineIds }
    }
}
