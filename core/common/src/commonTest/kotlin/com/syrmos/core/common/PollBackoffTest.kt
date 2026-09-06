package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exponential backoff with jitter for the live polls. rng is injected
 * (random01) so the jitter is deterministic in tests. Mirrored on iOS + web.
 */
class PollBackoffTest {

    // random01 = 0.5 -> multiplier 1.0 (no jitter), so raw delays are exact.
    private fun noJitter(failures: Int, base: Long, max: Long = 60_000L) =
        PollBackoff.nextDelayMillis(failures, base, max, random01 = 0.5)

    @Test
    fun successWaitsTheBaseInterval() {
        assertEquals(10_000L, noJitter(0, 10_000L))
        assertEquals(15_000L, noJitter(0, 15_000L))
    }

    @Test
    fun failuresBackOffExponentially() {
        assertEquals(20_000L, noJitter(1, 10_000L)) // 10s * 2
        assertEquals(40_000L, noJitter(2, 10_000L)) // 10s * 4
        assertEquals(60_000L, noJitter(3, 10_000L)) // 10s * 8 -> capped at 60s
    }

    @Test
    fun cappedAtMax() {
        assertEquals(60_000L, noJitter(10, 10_000L))
        assertEquals(30_000L, PollBackoff.nextDelayMillis(10, 10_000L, maxDelayMillis = 30_000L, random01 = 0.5))
    }

    @Test
    fun jitterStaysWithinBounds() {
        // At failures=2, raw = 40s; +/-25% -> [30s, 50s].
        val low = PollBackoff.nextDelayMillis(2, 10_000L, random01 = 0.0)
        val high = PollBackoff.nextDelayMillis(2, 10_000L, random01 = 1.0)
        assertEquals(30_000L, low)
        assertEquals(50_000L, high)
        // A spread of random draws all land inside the band and are not all equal.
        val samples = (0..20).map { PollBackoff.nextDelayMillis(2, 10_000L, random01 = it / 20.0) }
        assertTrue(samples.all { it in 30_000L..50_000L })
        assertTrue(samples.toSet().size > 1, "jitter should spread the delay, not fix it")
    }

    @Test
    fun jitterAppliesToTheBaseSuccessDelayToo() {
        // Desyncs clients even when healthy: base 10s +/-25% -> [7.5s, 12.5s].
        assertEquals(7_500L, PollBackoff.nextDelayMillis(0, 10_000L, random01 = 0.0))
        assertEquals(12_500L, PollBackoff.nextDelayMillis(0, 10_000L, random01 = 1.0))
    }

    @Test
    fun negativeFailuresTreatedAsZero() {
        assertEquals(10_000L, noJitter(-3, 10_000L))
    }

    @Test
    fun invalidConfigurationRejected() {
        assertFailsWith<IllegalArgumentException> { PollBackoff.nextDelayMillis(0, 0L) }
        assertFailsWith<IllegalArgumentException> { PollBackoff.nextDelayMillis(0, 10_000L, maxDelayMillis = 5_000L) }
        assertFailsWith<IllegalArgumentException> { PollBackoff.nextDelayMillis(0, 10_000L, jitterFraction = 1.5) }
        assertFailsWith<IllegalArgumentException> { PollBackoff.nextDelayMillis(0, 10_000L, random01 = 2.0) }
    }
}
