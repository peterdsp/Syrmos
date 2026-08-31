package com.syrmos.core.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

/**
 * The Ariadne cloud circuit breaker: a failure opens it for the cooldown so
 * repeated questions during an outage skip the cloud, the window elapses on its
 * own, and a success closes it early. Driven by a TestTimeSource so the timing is
 * deterministic.
 */
@OptIn(ExperimentalTime::class)
class CloudCircuitBreakerTest {

    @Test
    fun closedInitially() {
        val breaker = CloudCircuitBreaker(cooldown = 30.seconds, timeSource = TestTimeSource())
        assertFalse(breaker.isOpen(), "a fresh breaker must let the cloud be tried")
    }

    @Test
    fun opensOnFailureThenClosesAfterCooldown() {
        val time = TestTimeSource()
        val breaker = CloudCircuitBreaker(cooldown = 30.seconds, timeSource = time)

        breaker.recordFailure()
        assertTrue(breaker.isOpen(), "open immediately after a failure")

        time += 29.seconds
        assertTrue(breaker.isOpen(), "still open within the cooldown window")

        time += 2.seconds
        assertFalse(breaker.isOpen(), "closed once the cooldown has fully elapsed")
    }

    @Test
    fun successClosesImmediately() {
        val time = TestTimeSource()
        val breaker = CloudCircuitBreaker(cooldown = 30.seconds, timeSource = time)

        breaker.recordFailure()
        assertTrue(breaker.isOpen())

        breaker.recordSuccess()
        assertFalse(breaker.isOpen(), "a real reply closes the breaker before the cooldown")
    }
}
