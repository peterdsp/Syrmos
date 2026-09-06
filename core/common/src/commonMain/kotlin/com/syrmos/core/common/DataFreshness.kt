package com.syrmos.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Whether the arrivals on screen came from a recent live network fetch, or
 * are being predicted from the bundled schedule + projector.
 *
 * Surfacing only. The projector and 1s simulator already keep trains moving
 * with no network; this just lets the home surface tell the user which mode
 * they're looking at, so "4 min" reads as a confident prediction instead of a
 * silent guess.
 */
enum class DataFreshness {
    /** A live fetch succeeded within the freshness window. */
    LIVE,

    /** No live data yet, or the last live fetch is older than the window. */
    PREDICTED,
}

/**
 * Pure decision: given when live data last arrived and the current instant,
 * is the on-screen data LIVE or PREDICTED? Side-effect free so it unit-tests
 * without a clock and every platform's home surface reuses the same rule.
 */
object FreshnessEvaluator {
    /** Live data older than this reads as predicted-from-schedule. */
    const val DEFAULT_WINDOW_SECONDS: Long = 90

    fun evaluate(
        lastLiveUpdate: Instant?,
        now: Instant,
        windowSeconds: Long = DEFAULT_WINDOW_SECONDS,
    ): DataFreshness {
        if (lastLiveUpdate == null) return DataFreshness.PREDICTED
        val ageSeconds = (now - lastLiveUpdate).inWholeSeconds
        return if (ageSeconds in 0..windowSeconds) DataFreshness.LIVE else DataFreshness.PREDICTED
    }
}

/**
 * Process-wide record of the last successful live data fetch. Live network
 * paths (suburban trains poll, announcements refresh) call [markLive] on
 * success; home surfaces read [freshnessNow] to choose the offline-alive pill.
 *
 * Default state is PREDICTED, the honest default for the offline-first model:
 * until something actually reaches the network this session, every countdown
 * is projected from the bundled schedule.
 */
object LiveDataFreshness {
    private val _lastLiveUpdate = MutableStateFlow<Instant?>(null)
    val lastLiveUpdate: StateFlow<Instant?> = _lastLiveUpdate.asStateFlow()

    private val _retryRequested = MutableStateFlow(0L)
    val retryRequested: StateFlow<Long> = _retryRequested.asStateFlow()

    /**
     * Whether the device currently has a usable default network. Set by the
     * platform connectivity observer (Android [ConnectivityObserver], iOS
     * NWPathMonitor). Defaults to `true` so a platform that never reports
     * connectivity behaves as before (freshness alone drives the offline state).
     * This gives an INSTANT offline signal; freshness still catches the
     * "online but the API is unreachable" case the spec calls out.
     */
    private val _isNetworkAvailable = MutableStateFlow(true)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    fun markLive(at: Instant = Clock.System.now()) {
        _lastLiveUpdate.value = at
    }

    fun requestRetry() {
        _retryRequested.value = Clock.System.now().epochSeconds
    }

    fun setNetworkAvailable(available: Boolean) {
        _isNetworkAvailable.value = available
    }

    fun freshnessNow(windowSeconds: Long = FreshnessEvaluator.DEFAULT_WINDOW_SECONDS): DataFreshness =
        FreshnessEvaluator.evaluate(_lastLiveUpdate.value, Clock.System.now(), windowSeconds)
}
