package com.syrmos.core.network

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the live/departures per-call timeout (audit #15). The whole point is a
 * fast fallback to the bundled/simulated layer when the Pi is unreachable, so
 * the value must stay in the iOS-parity 6-10s band and well below the 30s
 * HttpClient default. Raising it back toward the default would silently reinstate
 * the ~30s hang, so this test fails if it drifts.
 */
class NetworkTimeoutsTest {

    private val clientDefaultMs = 30_000L

    @Test
    fun liveTimeoutStaysInTheFastFallbackBand() {
        assertTrue(
            LIVE_REQUEST_TIMEOUT_MS in 6_000L..10_000L,
            "live/departures timeout must stay 6-10s (iOS parity), was $LIVE_REQUEST_TIMEOUT_MS",
        )
    }

    @Test
    fun liveTimeoutIsWellBelowTheClientDefault() {
        assertTrue(
            LIVE_REQUEST_TIMEOUT_MS < clientDefaultMs,
            "live timeout must be shorter than the 30s client default or the fast fallback is lost",
        )
    }
}
