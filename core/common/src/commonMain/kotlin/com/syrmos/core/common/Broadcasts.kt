package com.syrmos.core.common

/**
 * Syrmos 3.0.2: admin broadcasts ("Announcements from Syrmos").
 *
 * Delivered by push when the device registered a token, and by polling
 * `GET /api/broadcasts?since=` otherwise (see docs/3.0.2-CONTRACTS.md). This file
 * holds the pure rules both paths share: audience matching, push topics, expiry,
 * and the polling dedupe so a broadcast already delivered by push, or already
 * seen, never produces a second notification.
 */
data class Broadcast(
    val id: String,
    val title: LocalizedText,
    val body: LocalizedText,
    val audience: String = "all",
    val sentAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long? = null,
    val deepLink: String? = null,
    val closureId: String? = null,
)

object Broadcasts {

    fun isExpired(b: Broadcast, now: Long): Boolean {
        val exp = b.expiresAtEpochSeconds ?: return false
        return now >= exp
    }

    /**
     * Shown when the audience is `all`, names a favourite line or station, or the
     * selected region. Unknown audience shapes are not shown.
     */
    fun audienceMatches(
        audience: String,
        favoriteLineIds: Set<String>,
        favoriteStationIds: Set<String>,
        region: String?,
    ): Boolean {
        val a = audience.trim()
        return when {
            a.isEmpty() || a == "all" -> true
            a.startsWith("line:") -> a.removePrefix("line:") in favoriteLineIds
            a.startsWith("station:") -> a.removePrefix("station:") in favoriteStationIds
            a.startsWith("region:") -> region != null && a.removePrefix("region:") == region
            else -> false
        }
    }

    /** Line ids implied by favourite per-line station ids (`M3_NIK` -> `M3`). */
    fun favoriteLineIds(favoriteStationIds: Collection<String>): Set<String> =
        favoriteStationIds.mapNotNull { StationClosures.lineIdOfStation(it) }.toSet()

    /** Topics for `POST /api/push/register`: all, region, favourite lines and stations. */
    fun pushTopics(favoriteStationIds: Collection<String>, region: String?): List<String> = buildList {
        add("all")
        if (region == "athens" || region == "thessaloniki") add("region.$region")
        favoriteLineIds(favoriteStationIds).sorted().forEach { add("line.$it") }
        favoriteStationIds.toSet().sorted().forEach { add("station.$it") }
    }

    /** Active broadcasts for the Home banner/inbox, newest first, deduped by id. */
    fun visible(
        broadcasts: List<Broadcast>,
        now: Long,
        favoriteStationIds: Set<String>,
        region: String?,
    ): List<Broadcast> {
        val lines = favoriteLineIds(favoriteStationIds)
        return broadcasts
            .distinctBy { it.id }
            .filter { !isExpired(it, now) && audienceMatches(it.audience, lines, favoriteStationIds, region) }
            .sortedByDescending { it.sentAtEpochSeconds }
    }

    /**
     * Broadcasts the polling fallback should post as local notifications: visible,
     * not delivered by push, not seen before. Dedupe by id.
     */
    fun toNotify(
        fetched: List<Broadcast>,
        deliveredIds: Set<String>,
        seenIds: Set<String>,
        now: Long,
        favoriteStationIds: Set<String>,
        region: String?,
    ): List<Broadcast> =
        visible(fetched, now, favoriteStationIds, region)
            .filter { it.id !in deliveredIds && it.id !in seenIds }

    /** The `since` cursor for the next poll: newest sentAt seen, never moving backwards. */
    fun nextSince(previous: Long, fetched: List<Broadcast>): Long =
        maxOf(previous, fetched.maxOfOrNull { it.sentAtEpochSeconds } ?: previous)
}
