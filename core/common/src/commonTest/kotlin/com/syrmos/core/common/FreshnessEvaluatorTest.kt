package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant

/**
 * Pins the offline-alive pill's decision rule ([FreshnessEvaluator]). The home
 * surface reads LIVE only when a live fetch landed inside the freshness window;
 * everything else (never fetched, or aged past the window) reads PREDICTED, the
 * honest default for the offline-first model.
 */
class FreshnessEvaluatorTest {

    private val now = Instant.fromEpochSeconds(1_700_000_000)

    @Test
    fun null_last_update_is_predicted() {
        assertEquals(
            DataFreshness.PREDICTED,
            FreshnessEvaluator.evaluate(lastLiveUpdate = null, now = now),
        )
    }

    @Test
    fun fresh_fetch_within_window_is_live() {
        val last = now - 30.seconds
        assertEquals(
            DataFreshness.LIVE,
            FreshnessEvaluator.evaluate(lastLiveUpdate = last, now = now, windowSeconds = 90),
        )
    }

    @Test
    fun exactly_on_window_boundary_is_live() {
        val last = now - 90.seconds
        assertEquals(
            DataFreshness.LIVE,
            FreshnessEvaluator.evaluate(lastLiveUpdate = last, now = now, windowSeconds = 90),
        )
    }

    @Test
    fun stale_fetch_past_window_is_predicted() {
        val last = now - 91.seconds
        assertEquals(
            DataFreshness.PREDICTED,
            FreshnessEvaluator.evaluate(lastLiveUpdate = last, now = now, windowSeconds = 90),
        )
    }

    @Test
    fun future_timestamp_clock_skew_is_predicted() {
        // A last-update in the future (clock skew) must not read as live.
        val last = now + 10.seconds
        assertEquals(
            DataFreshness.PREDICTED,
            FreshnessEvaluator.evaluate(lastLiveUpdate = last, now = now, windowSeconds = 90),
        )
    }
}
