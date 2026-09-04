package com.syrmos.core.model.status

import kotlinx.datetime.Instant

/**
 * Rendering-facing freshness of a REAL-GPS live vehicle marker (a suburban /
 * national train position, or an airport-bus position) computed from that
 * vehicle's OWN `updatedAt` against the current instant.
 *
 * This is the map/marker counterpart of the per-item [Freshness] axis: where
 * [Freshness] answers "fresh vs stale vs unknown-age", this collapses the answer
 * into the three buckets a moving-dot renderer actually needs, adding the
 * [EXPIRED] tier that [Freshness] does not model:
 *
 * - [LIVE]    — within the fresh window: draw as a live, boardable position.
 * - [STALE]   — older than the fresh window but still recent enough to be the
 *               best-known last position: draw DE-EMPHASISED and label it with
 *               its age ("updated Nm ago"). It must NEVER render as a plain live
 *               dot — showing an aged position as live is the exact dishonesty
 *               the data-status rules forbid.
 * - [EXPIRED] — so old it is no longer meaningful: the renderer drops the marker
 *               (and, where a schedule-projected equivalent exists, that takes
 *               over instead).
 *
 * A missing / blank / malformed timestamp resolves to [STALE] with a `null`
 * age: we hold a position but cannot vouch for its recency, so it is never shown
 * as live. A timestamp in the future beyond the clock-skew tolerance is treated
 * the same way.
 */
enum class LiveVehicleState { LIVE, STALE, EXPIRED }

/**
 * The classification of a single live vehicle: its [state] plus the age of its
 * position in seconds (`null` when the timestamp was missing/unusable, or when
 * the position is within the future-skew tolerance and treated as just-now).
 */
data class LiveVehicleFreshness(
    val state: LiveVehicleState,
    val ageSeconds: Long?,
)

/**
 * A live vehicle's position within this many seconds is [LiveVehicleState.LIVE].
 * Matches [DEFAULT_FRESHNESS_WINDOW_SECONDS] so the marker layer and the
 * per-item [Freshness] axis agree on what "fresh" means.
 */
const val LIVE_VEHICLE_FRESH_WINDOW_SECONDS: Long = 90

/**
 * Older than [LIVE_VEHICLE_FRESH_WINDOW_SECONDS] but within this many seconds is
 * [LiveVehicleState.STALE] (shown de-emphasised, with its age). Beyond it the
 * position is [LiveVehicleState.EXPIRED] and the marker is dropped. Ten minutes:
 * long enough that a brief signal loss keeps the last-known dot on screen
 * (clearly marked), short enough that a truly dead feed hands the map back to
 * the schedule projector.
 */
const val LIVE_VEHICLE_EXPIRY_SECONDS: Long = 600

/**
 * Pure classification of a live vehicle from its own `updatedAt` (epoch
 * seconds). Clock-injected so it unit-tests without a real clock.
 *
 * @param updatedAtEpochSeconds the vehicle's own position timestamp (epoch
 *   seconds), or `null` when it carries none -> [LiveVehicleState.STALE].
 * @param nowEpochSeconds the current instant (epoch seconds).
 * @param freshWindowSeconds the LIVE window (default
 *   [LIVE_VEHICLE_FRESH_WINDOW_SECONDS]); must be `>= 0`.
 * @param expirySeconds the STALE->EXPIRED cutoff (default
 *   [LIVE_VEHICLE_EXPIRY_SECONDS]); must be `>= freshWindowSeconds`.
 * @param futureSkewToleranceSeconds tolerated future offset for device clock
 *   skew (default [DEFAULT_FUTURE_SKEW_TOLERANCE_SECONDS]); must be `>= 0`.
 */
fun classifyLiveVehicle(
    updatedAtEpochSeconds: Long?,
    nowEpochSeconds: Long,
    freshWindowSeconds: Long = LIVE_VEHICLE_FRESH_WINDOW_SECONDS,
    expirySeconds: Long = LIVE_VEHICLE_EXPIRY_SECONDS,
    futureSkewToleranceSeconds: Long = DEFAULT_FUTURE_SKEW_TOLERANCE_SECONDS,
): LiveVehicleFreshness {
    require(freshWindowSeconds >= 0) { "freshWindowSeconds must be >= 0, was $freshWindowSeconds" }
    require(expirySeconds >= freshWindowSeconds) {
        "expirySeconds ($expirySeconds) must be >= freshWindowSeconds ($freshWindowSeconds)"
    }
    require(futureSkewToleranceSeconds >= 0) {
        "futureSkewToleranceSeconds must be >= 0, was $futureSkewToleranceSeconds"
    }
    // No usable timestamp: we hold a position but cannot vouch for it, so it is
    // never LIVE. STALE with no age so the UI shows a de-emphasised dot without a
    // fabricated "N min ago".
    if (updatedAtEpochSeconds == null) return LiveVehicleFreshness(LiveVehicleState.STALE, null)
    val age = nowEpochSeconds - updatedAtEpochSeconds
    if (age < 0) {
        // Future timestamp: tolerate small device clock skew as just-now, but do
        // not trust one that sits far ahead of us.
        return if (-age <= futureSkewToleranceSeconds) {
            LiveVehicleFreshness(LiveVehicleState.LIVE, 0)
        } else {
            LiveVehicleFreshness(LiveVehicleState.STALE, null)
        }
    }
    return when {
        age <= freshWindowSeconds -> LiveVehicleFreshness(LiveVehicleState.LIVE, age)
        age <= expirySeconds -> LiveVehicleFreshness(LiveVehicleState.STALE, age)
        else -> LiveVehicleFreshness(LiveVehicleState.EXPIRED, age)
    }
}

/**
 * Convenience over [classifyLiveVehicle] that parses an ISO-8601 `updatedAt`
 * string as the live feeds carry it. A `null`, blank or malformed string
 * resolves to [LiveVehicleState.STALE] with a `null` age.
 */
fun classifyLiveVehicleIso(
    updatedAtIso: String?,
    now: Instant,
    freshWindowSeconds: Long = LIVE_VEHICLE_FRESH_WINDOW_SECONDS,
    expirySeconds: Long = LIVE_VEHICLE_EXPIRY_SECONDS,
    futureSkewToleranceSeconds: Long = DEFAULT_FUTURE_SKEW_TOLERANCE_SECONDS,
): LiveVehicleFreshness {
    require(freshWindowSeconds >= 0) { "freshWindowSeconds must be >= 0, was $freshWindowSeconds" }
    require(expirySeconds >= freshWindowSeconds) {
        "expirySeconds ($expirySeconds) must be >= freshWindowSeconds ($freshWindowSeconds)"
    }
    require(futureSkewToleranceSeconds >= 0) {
        "futureSkewToleranceSeconds must be >= 0, was $futureSkewToleranceSeconds"
    }
    val epoch = parseIsoToEpochSeconds(updatedAtIso)
        ?: return LiveVehicleFreshness(LiveVehicleState.STALE, null)
    return classifyLiveVehicle(
        epoch,
        now.epochSeconds,
        freshWindowSeconds,
        expirySeconds,
        futureSkewToleranceSeconds,
    )
}
