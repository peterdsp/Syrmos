package com.syrmos.core.data.sync

import com.syrmos.core.data.seed.ResourceReader
import com.syrmos.core.network.STASYAnnouncement
import com.syrmos.core.network.STASYAnnouncementService
import com.syrmos.core.network.STASYFeed
import com.syrmos.core.network.STASYServiceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Offline-first STASY service-status + announcement feed.
 *
 * Mirrors [ScheduleSyncRepository]'s shape so the home screen has the same
 * cold-start guarantees as the departures board:
 *  - StateFlow<STASYFeed> for hot in-memory access.
 *  - hydrateFromBundleIfNeeded() loads files/seed/schedules-v2/announcements.json
 *    so first launch with no network still renders today's "Trains until 21:40"
 *    pill and any active alerts baked at build time.
 *  - refresh() pulls /api/announcements via [STASYAnnouncementService]; on
 *    failure the StateFlow keeps its prior value (silent).
 */
class AnnouncementsRepository(
    private val service: STASYAnnouncementService,
    private val resourceReader: ResourceReader? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _feed = MutableStateFlow(STASYFeed(status = null, announcements = emptyList()))
    val feed: StateFlow<STASYFeed> = _feed.asStateFlow()

    /** Cold-start hydration from the bundled snapshot. Safe to call multiple
     *  times; a live refresh won't be clobbered because we only hydrate when
     *  the in-memory feed is empty. */
    suspend fun hydrateFromBundleIfNeeded() {
        val current = _feed.value
        if (current.status != null || current.announcements.isNotEmpty()) return
        val reader = resourceReader ?: return
        val body = runCatching {
            reader.readText("files/seed/schedules-v2/announcements.json")
        }.getOrNull() ?: return
        if (body.isBlank() || body == "{}") return
        val payload = runCatching {
            json.decodeFromString<BundledPayload>(body)
        }.getOrNull() ?: return
        _feed.value = STASYFeed(
            status = payload.status?.toModel(),
            announcements = payload.announcements.map { it.toModel() },
        )
    }

    /** Live refresh from /api/announcements. Silent on network failure. */
    suspend fun refresh() {
        val latest = service.fetchFeed().firstOrNull() ?: return
        if (latest.status != null || latest.announcements.isNotEmpty()) {
            _feed.value = latest
        }
    }

    @Serializable
    private data class BundledPayload(
        @SerialName("updatedAt") val updatedAt: String? = null,
        val count: Int = 0,
        val status: BundledStatus? = null,
        val announcements: List<BundledAnnouncement> = emptyList(),
    )

    @Serializable
    private data class BundledStatus(
        val status: String = "unknown",
        val rawMessage: String = "",
        @SerialName("rawMessageEn") val rawMessageEn: String = "",
        val serviceUntil: String? = null,
    ) {
        fun toModel(): STASYServiceStatus = STASYServiceStatus(
            status = status,
            rawMessage = rawMessage,
            rawMessageEn = rawMessageEn.ifBlank { rawMessage },
            serviceUntil = serviceUntil,
        )
    }

    @Serializable
    private data class BundledAnnouncement(
        val id: String,
        val title: String,
        @SerialName("titleEn") val titleEn: String = "",
        val date: String = "",
        val url: String = "",
        val category: String = "",
        @SerialName("affectedLines") val affectedLines: List<String> = emptyList(),
        val severity: String = "info",
        @SerialName("validFrom") val validFrom: String? = null,
        @SerialName("validUntil") val validUntil: String? = null,
    ) {
        fun toModel(): STASYAnnouncement = STASYAnnouncement(
            id = id,
            title = title,
            titleEn = titleEn.ifBlank { title },
            date = date,
            url = url,
            isServiceAlert = category == "serviceAlert",
            affectedLines = affectedLines,
            severity = severity.ifBlank { "info" },
            validFrom = validFrom,
            validUntil = validUntil,
        )
    }
}
