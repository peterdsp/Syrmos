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
        }
    }

    private val _manifest = MutableStateFlow<Manifest?>(null)
    val manifest: StateFlow<Manifest?> = _manifest.asStateFlow()

    private val _scheduleVersion = MutableStateFlow<Int?>(null)
    val scheduleVersion: StateFlow<Int?> = _scheduleVersion.asStateFlow()

    private val _lineBundles = MutableStateFlow<Map<String, LineSchedule>>(emptyMap())
    val lineBundles: StateFlow<Map<String, LineSchedule>> = _lineBundles.asStateFlow()

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
                val toFetch = manifest.perLineHashes.entries
                    .filter { (lid, hash) ->
                        // Re-fetch when first time, hash changed, or bundle missing.
                        current[lid]?.let { _ -> manifest.perLineHashes[lid] != hash } ?: true
                    }

                var refreshed = 0
                for ((lineId, _) in toFetch) {
                    val bundle = schedulesService.fetchLineBundle(lineId).firstOrNull()
                    if (bundle != null) {
                        current[lineId] = bundle
                        refreshed++
                    }
                }
                _manifest.value = manifest
                _scheduleVersion.value = manifest.version
                _lineBundles.value = current
                _lastSyncAt.value = Clock.System.now()
                RefreshOutcome.Refreshed(refreshed)
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
}
