package com.syrmos.core.model.status

import kotlinx.datetime.Instant

/**
 * The temporal condition of a datum, kept strictly ORTHOGONAL to its provenance
 * ([com.syrmos.core.model.schedule.SourceConfidence]). The two axes are never
 * conflated: a value can be Scheduled + [NotApplicable], Live + [Fresh], or
 * Live + [Stale]. Connectivity / offline mode is deliberately not modelled here,
 * and it is not provenance either — a bundled-timetable answer is
 * `SourceConfidence.OFFLINE` regardless of whether the device happens to be
 * online at that moment.
 *
 * This supersedes the ad-hoc, process-wide `core.common.DataFreshness`
 * (LIVE / PREDICTED) for per-item use; that legacy enum is left untouched here
 * and migrating its callers is out of scope for this change.
 */
sealed interface Freshness {
    /** Within the freshness window; no age worth surfacing. */
    data object Fresh : Freshness

    /** Older than the window; carries its own per-item age, in seconds (never negative). */
    data class Stale(val ageSeconds: Long) : Freshness {
        init { require(ageSeconds >= 0) { "Stale.ageSeconds must be >= 0, was $ageSeconds" } }
    }

    /**
     * The datum exists but carries no trustworthy timestamp: missing, malformed,
     * or so far in the future it cannot be believed.
     */
    data object UnknownAge : Freshness

    /** Freshness does not apply (e.g. a static timetable minute, not a live feed). */
    data object NotApplicable : Freshness

    /** There is no datum at all (e.g. an empty live response). */
    data object Unavailable : Freshness
}

/** The default staleness window: a live datum older than this is [Freshness.Stale]. */
const val DEFAULT_FRESHNESS_WINDOW_SECONDS: Long = 90

/**
 * How far a timestamp may sit in the FUTURE (device clock skew) and still be
 * treated as just-now. Beyond this, the timestamp is not trusted and the datum
 * is [Freshness.UnknownAge] rather than silently "fresh".
 */
const val DEFAULT_FUTURE_SKEW_TOLERANCE_SECONDS: Long = 120

/**
 * Per-item freshness derived from that item's OWN timestamp — never a single
 * process-wide clock, so two items fetched together but stamped differently get
 * their own verdicts. Pure and clock-injected so it unit-tests without a real
 * clock.
 *
 * @param updatedAtEpochSeconds the item's own updatedAt (epoch seconds), or
 *   `null` when the item has no usable timestamp -> [Freshness.UnknownAge].
 * @param nowEpochSeconds the current instant (epoch seconds).
 * @param windowSeconds the staleness window (default [DEFAULT_FRESHNESS_WINDOW_SECONDS]); must be `>= 0`.
 * @param futureSkewToleranceSeconds tolerated future offset (default
 *   [DEFAULT_FUTURE_SKEW_TOLERANCE_SECONDS]); must be `>= 0`.
 *
 * Rules: age within `[0, window]` -> [Freshness.Fresh]; age `> window` ->
 * [Freshness.Stale] carrying the age. A future timestamp within
 * [futureSkewToleranceSeconds] is treated as just-now ([Freshness.Fresh]); a
 * timestamp further in the future is [Freshness.UnknownAge].
 */
fun freshnessFromEpoch(
    updatedAtEpochSeconds: Long?,
    nowEpochSeconds: Long,
    windowSeconds: Long = DEFAULT_FRESHNESS_WINDOW_SECONDS,
    futureSkewToleranceSeconds: Long = DEFAULT_FUTURE_SKEW_TOLERANCE_SECONDS,
): Freshness {
    require(windowSeconds >= 0) { "windowSeconds must be >= 0, was $windowSeconds" }
    require(futureSkewToleranceSeconds >= 0) { "futureSkewToleranceSeconds must be >= 0, was $futureSkewToleranceSeconds" }
    if (updatedAtEpochSeconds == null) return Freshness.UnknownAge
    val age = nowEpochSeconds - updatedAtEpochSeconds
    if (age < 0) {
        // Future timestamp: tolerate small device clock skew as just-now, but do
        // not trust one that sits far ahead of us.
        return if (-age <= futureSkewToleranceSeconds) Freshness.Fresh else Freshness.UnknownAge
    }
    return if (age <= windowSeconds) Freshness.Fresh else Freshness.Stale(age)
}

/**
 * Convenience over [freshnessFromEpoch] that parses an ISO-8601 `updatedAt`
 * string as the API payloads carry it. A `null`, blank or malformed string
 * yields [Freshness.UnknownAge]. When an item genuinely has no live datum at
 * all, the call site should use [Freshness.Unavailable] rather than passing a
 * null timestamp here.
 *
 * [windowSeconds] and [futureSkewToleranceSeconds] are validated up front, so a
 * bad configuration is rejected even when the timestamp itself is null or
 * malformed (it is never masked by an early [Freshness.UnknownAge] return).
 */
fun freshnessFromIso(
    updatedAtIso: String?,
    now: Instant,
    windowSeconds: Long = DEFAULT_FRESHNESS_WINDOW_SECONDS,
    futureSkewToleranceSeconds: Long = DEFAULT_FUTURE_SKEW_TOLERANCE_SECONDS,
): Freshness {
    require(windowSeconds >= 0) { "windowSeconds must be >= 0, was $windowSeconds" }
    require(futureSkewToleranceSeconds >= 0) { "futureSkewToleranceSeconds must be >= 0, was $futureSkewToleranceSeconds" }
    val epoch = parseIsoToEpochSeconds(updatedAtIso) ?: return Freshness.UnknownAge
    return freshnessFromEpoch(epoch, now.epochSeconds, windowSeconds, futureSkewToleranceSeconds)
}

/** Parses an ISO-8601 instant to epoch seconds, or `null` if blank or malformed. */
fun parseIsoToEpochSeconds(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso.trim()).epochSeconds }.getOrNull()
}
