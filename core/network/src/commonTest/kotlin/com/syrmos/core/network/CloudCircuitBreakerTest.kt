package com.syrmos.core.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

/**
 * The Ariadne cloud circuit breaker with single-flight admission: only one
 * attempt is admitted at a time, an outage opens it for the cooldown, the window
 * elapses on its own, and a success keeps it closed. Driven by a TestTimeSource
 * so the timing is deterministic.
 */
@OptIn(ExperimentalTime::class)
class CloudCircuitBreakerTest {

    @Test
    fun acquiresWhenClosed() {
        val breaker = CloudCircuitBreaker(cooldown = 30.seconds, timeSource = TestTimeSource())
        assertTrue(breaker.tryAcquire(), "a fresh breaker admits the first attempt")
    }

    @Test
    fun singleFlightBlocksConcurrentAttempts() {
        val breaker = CloudCircuitBreaker(cooldown = 30.seconds, timeSource = TestTimeSource())
        assertTrue(breaker.tryAcquire(), "first attempt is admitted")
        assertFalse(breaker.tryAcquire(), "a second attempt is blocked while one is in flight")
        breaker.recordSuccess()
        breaker.release()
        assertTrue(breaker.tryAcquire(), "admitted again once the slot is released")
    }

    @Test
    fun outageOpensThenClosesAfterCooldown() {
        val time = TestTimeSource()
        val breaker = CloudCircuitBreaker(cooldown = 30.seconds, timeSource = time)

        assertTrue(breaker.tryAcquire())
        breaker.recordOutage()
        breaker.release()
        assertFalse(breaker.tryAcquire(), "open right after an outage")

        time += 29.seconds
        assertFalse(breaker.tryAcquire(), "still open within the cooldown window")

        time += 2.seconds
        assertTrue(breaker.tryAcquire(), "closed once the cooldown has fully elapsed")
    }

    @Test
    fun successKeepsClosed() {
        val breaker = CloudCircuitBreaker(cooldown = 30.seconds, timeSource = TestTimeSource())
        assertTrue(breaker.tryAcquire())
        breaker.recordSuccess()
        breaker.release()
        assertTrue(breaker.tryAcquire(), "a real reply keeps the breaker closed")
    }

    @Test
    fun releaseWithoutOutageDoesNotOpen() {
        // The cancellation path: release the slot without recording an outage.
        val breaker = CloudCircuitBreaker(cooldown = 30.seconds, timeSource = TestTimeSource())
        assertTrue(breaker.tryAcquire())
        breaker.release()
        assertTrue(breaker.tryAcquire(), "a cancelled attempt must not open the breaker")
    }
}
