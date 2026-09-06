package com.syrmos.core.common

import kotlin.math.min
import kotlin.random.Random

/**
 * Exponential backoff with jitter for the live polling loops (trains,
 * live-positions, airport buses). A healthy loop waits its base interval; after
 * consecutive failures it waits `min(base * 2^failures, max)`, and every delay is
 * spread by +/- [jitterFraction] so many installed clients never retry in
 * lockstep (thundering herd) against the single Pi feed. The failure counter is
 * reset to 0 on the next success, so the loop snaps back to its normal cadence.
 *
 * Pure and rng-injected so it unit-tests without a clock or a real RNG. Mirrored
 * on iOS (`PollBackoff.nextDelaySeconds`) and web (`pollBackoffMs`).
 */
object PollBackoff {
    /** Cap so a long outage never pushes the retry interval past a minute. */
    const val DEFAULT_MAX_DELAY_MS: Long = 60_000L
    const val DEFAULT_JITTER_FRACTION: Double = 0.25

    /** Exponent cap: 2^16 * base already exceeds any sane max, and it keeps the shift safe. */
    private const val MAX_FAILURES = 16

    fun nextDelayMillis(
        consecutiveFailures: Int,
        baseDelayMillis: Long,
        maxDelayMillis: Long = DEFAULT_MAX_DELAY_MS,
        jitterFraction: Double = DEFAULT_JITTER_FRACTION,
        random01: Double = Random.nextDouble(),
    ): Long {
        require(baseDelayMillis > 0) { "baseDelayMillis must be > 0, was $baseDelayMillis" }
        require(maxDelayMillis >= baseDelayMillis) {
            "maxDelayMillis ($maxDelayMillis) must be >= baseDelayMillis ($baseDelayMillis)"
        }
        require(jitterFraction in 0.0..1.0) { "jitterFraction must be in [0,1], was $jitterFraction" }
        require(random01 in 0.0..1.0) { "random01 must be in [0,1], was $random01" }
        val failures = consecutiveFailures.coerceIn(0, MAX_FAILURES)
        val exp = if (failures == 0) baseDelayMillis else baseDelayMillis shl failures
        // shl can only be positive here (failures <= 16), but guard anyway.
        val raw = min(if (exp <= 0) maxDelayMillis else exp, maxDelayMillis)
        val multiplier = (1.0 - jitterFraction) + (2.0 * jitterFraction * random01)
        return (raw * multiplier).toLong().coerceAtLeast(1L)
    }
}
