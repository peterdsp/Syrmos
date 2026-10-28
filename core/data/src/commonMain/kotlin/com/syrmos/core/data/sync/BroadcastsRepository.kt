package com.syrmos.core.data.sync

import com.syrmos.core.common.Broadcast
import com.syrmos.core.common.Broadcasts
import com.syrmos.core.common.loadStringPref
import com.syrmos.core.common.persistStringPref
import com.syrmos.core.network.SyrmosClosuresService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Active admin broadcasts for the Home banner (3.0.2). Reads the last week of
 * `/api/broadcasts` (the server only returns unexpired ones, newest first) and
 * caches the body on device so the banner survives a cold start offline. The
 * banner hides broadcasts the rider dismissed; notifications are handled by the
 * platform (push, or the polling fallback in the Android AlertCheckWorker).
 */
class BroadcastsRepository(
    private val service: SyrmosClosuresService,
) {
    private val _broadcasts = MutableStateFlow(
        runCatching { ClosuresPayloadParser.parseBroadcasts(loadStringPref(CACHE_KEY, "")) }.getOrNull().orEmpty(),
    )
    val broadcasts: StateFlow<List<Broadcast>> = _broadcasts.asStateFlow()

    private val _dismissed = MutableStateFlow(
        runCatching { loadStringPref(DISMISSED_KEY, "") }.getOrDefault("").split(',').filter { it.isNotBlank() }.toSet(),
    )
    val dismissed: StateFlow<Set<String>> = _dismissed.asStateFlow()

    suspend fun refresh() {
        val since = ClosuresRepository.nowEpochSeconds() - WINDOW_SECONDS
        val body = service.fetchBroadcastsBody(since) ?: return
        val parsed = ClosuresPayloadParser.parseBroadcasts(body) ?: return
        _broadcasts.value = parsed
        runCatching { persistStringPref(CACHE_KEY, body) }
    }

    fun dismiss(id: String) {
        val next = _dismissed.value + id
        _dismissed.value = next
        runCatching { persistStringPref(DISMISSED_KEY, next.takeLast(100).joinToString(",")) }
    }

    fun visible(favoriteStationIds: Set<String>, region: String?, now: Long = ClosuresRepository.nowEpochSeconds()): List<Broadcast> =
        Broadcasts.visible(_broadcasts.value, now, favoriteStationIds, region).filter { it.id !in _dismissed.value }

    private fun <T> Set<T>.takeLast(n: Int): List<T> = toList().takeLast(n)

    companion object {
        private const val CACHE_KEY = "broadcasts_cache_v1"
        private const val DISMISSED_KEY = "broadcasts_dismissed_v1"
        private const val WINDOW_SECONDS = 7L * 24 * 3600
    }
}
