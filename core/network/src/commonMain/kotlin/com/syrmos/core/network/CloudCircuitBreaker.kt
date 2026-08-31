package com.syrmos.core.network

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A minimal single-endpoint circuit breaker.
 *
 * After [recordFailure] the breaker is [isOpen] for [cooldown]; callers should
 * then skip the endpoint and use their fallback. [recordSuccess] closes it
 * immediately. Time comes from an injectable [TimeSource] so the cooldown window
 * is deterministic in tests.
 *
 * Used for Ariadne's cloud chat: a failed turn (network error, timeout, or an
 * offline-provider reply) opens the breaker so the next questions during the same
 * outage answer instantly from the local engine instead of each waiting out the
 * full request timeout. The first question after the cooldown retries with the
 * full budget, so a viable-but-slow network is never downgraded.
 *
 * Confinement: this holds plain mutable state and is NOT thread-safe. Its callers
 * confine it to a single thread, which is what makes that safe: the Android
 * assistant drives it from its Main-dispatched scope and the iOS one is
 * `@MainActor`, so every isOpen / recordFailure / recordSuccess runs on the same
 * thread with no data race. It does not enforce single-flight, so two questions
 * fired in the same instant at the cooldown boundary could each retry once; that
 * is harmless (both run on the confining thread and cost at most one extra cloud
 * attempt before falling back).
 */
internal class CloudCircuitBreaker(
    private val cooldown: Duration,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var openedAt: TimeMark? = null

    fun isOpen(): Boolean = openedAt?.let { it.elapsedNow() < cooldown } ?: false

    fun recordFailure() { openedAt = timeSource.markNow() }

    fun recordSuccess() { openedAt = null }
}
