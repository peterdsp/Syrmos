package com.syrmos.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The "track this departure" primitive that Tier 2 (Live Activity / Dynamic
 * Island on iOS, ongoing notification on Android, pinned card on Web) is built
 * on. A single departure the user has chosen to follow, plus the deterministic
 * countdown derived from its target time.
 *
 * Pure and offline: the countdown is just arithmetic against the device clock,
 * so it keeps ticking with no network, exactly like the projector it came from.
 */
/**
 * One stop on the tracked train's route. Ordered lists of these on a
 * [TrackedDeparture] drive the station-strip visualisation in the tracking
 * card, where the train icon interpolates between dots as the countdown
 * ticks down.
 */
data class TrackedRouteStop(
    val stationId: String,
    val stationName: String,
)

data class TrackedDeparture(
    val lineId: String,
    val stationId: String,
    val stationName: String,
    val destination: String,
    /** Scheduled clock time, HH:MM, for display. */
    val scheduledTime: String,
    /** Unix epoch second the train is expected, used for the live countdown. */
    val targetEpochSeconds: Long,
    /**
     * Ordered stops on the way to the tracked departure, ordered in the
     * direction of travel with the tracked station always last. Up to six
     * items in practice (target plus up to five upstream stations) so the
     * strip stays readable. Empty when the caller couldn't resolve the
     * line's stations; the tracking card falls back to a plain progress
     * bar in that case.
     */
    val routeStations: List<TrackedRouteStop> = emptyList(),
    val directionKey: String = "outbound",
    val currentStationName: String? = null,
) {
    /** Whole minutes left until the train, floored at 0. Null once it's due. */
    fun minutesRemaining(nowEpochSeconds: Long): Int {
        val secs = targetEpochSeconds - nowEpochSeconds
        if (secs <= 0) return 0
        // Round up so "59s left" still reads as 1 min, not 0.
        return ((secs + 59) / 60).toInt()
    }

    /** True once the train is due or has passed, so trackers can auto-clear. */
    fun isDue(nowEpochSeconds: Long): Boolean = nowEpochSeconds >= targetEpochSeconds
}

/**
 * Process-wide holder of the single actively-tracked departure. Platform
 * surfaces (Compose card, SwiftUI card, Live Activity, notification) observe
 * [active]; starting a new track replaces any previous one.
 */
object DepartureTracking {
    private val _active = MutableStateFlow<TrackedDeparture?>(null)
    val active: StateFlow<TrackedDeparture?> = _active.asStateFlow()

    fun track(departure: TrackedDeparture) {
        _active.value = departure
    }

    fun stop() {
        _active.value = null
    }
}
