package com.syrmos.core.network

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A minimal single-endpoint circuit breaker with single-flight admission.
 *
 * [tryAcquire] returns true for at most one caller at a time and only when the
 * breaker is closed; every other caller (breaker open from a recent failure, or a
 * request already in flight) gets false and should use its fallback. The admitted
 * caller MUST later [release] the slot, and record the outcome with
 * [recordSuccess] (closes) or [recordOutage] (opens for [cooldown]). Time comes
 * from an injectable [TimeSource] so the cooldown window is deterministic in tests.
 *
 * Used for Ariadne's cloud chat: during an outage the FIRST question probes the
 * cloud (and waits out its timeout once) while every concurrent or subsequent
 * question falls straight to the local grounded engine, so a burst of questions
 * cannot each incur the full timeout or pile onto a degraded endpoint. A real,
 * surfaced reply closes the breaker; the first question after the cooldown probes
 * again with the full budget, so a viable-but-slow network is never downgraded.
 *
 * Confinement: this holds plain mutable state and is NOT thread-safe. Its callers
 * confine it to a single thread, which is what makes that safe: the Android
 * assistant drives it from its Main-dispatched scope and the iOS one is
 * `@MainActor`, so `tryAcquire`/`release`/`recordSuccess`/`recordOutage` all run on
 * the same thread with no data race, and the single-flight flag is observed
 * consistently across a suspended request.
 */
internal class CloudCircuitBreaker(
    private val cooldown: Duration,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var openedAt: TimeMark? = null
    private var inFlight = false

    /** Reserve the single cloud attempt. On true the caller MUST later [release]. */
    fun tryAcquire(): Boolean {
        if (inFlight) return false
        val opened = openedAt
        if (opened != null && opened.elapsedNow() < cooldown) return false
        inFlight = true
        return true
    }

    /** A real, surfaced reply: close the breaker. */
    fun recordSuccess() { openedAt = null }

    /** No usable reply (network error, timeout, offline provider, junk): open it. */
    fun recordOutage() { openedAt = timeSource.markNow() }

    /** Free the single-flight slot. Call from finally/defer, cancellation included. */
    fun release() { inFlight = false }
}
