package com.syrmos.core.data.seed

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the completeness check that decides whether a line refresh may REPLACE
 * its stored station memberships. Only a complete, unique, declared-count
 * payload is authoritative; a partial or duplicated snapshot must keep the prior
 * memberships rather than truncate them (the interchange resolver relies on
 * accurate per-line stops).
 */
class LinesRefresherTest {

    @Test
    fun completePayloadReplaces() {
        assertTrue(LinesRefresher.isCompleteMembership(listOf("a", "b", "c"), declaredCount = 3))
    }

    @Test
    fun emptyPayloadIsNotComplete() {
        assertFalse(LinesRefresher.isCompleteMembership(emptyList(), declaredCount = 0))
        assertFalse(LinesRefresher.isCompleteMembership(emptyList(), declaredCount = 5))
    }

    @Test
    fun partialPayloadIsNotComplete() {
        // Server sent 1 of 3 stops: must not truncate the line to that one stop.
        assertFalse(LinesRefresher.isCompleteMembership(listOf("a"), declaredCount = 3))
    }

    @Test
    fun duplicateIdsAreNotComplete() {
        // size matches the declared count but a duplicate hides a missing stop.
        assertFalse(LinesRefresher.isCompleteMembership(listOf("a", "a", "b"), declaredCount = 3))
        assertFalse(LinesRefresher.isCompleteMembership(listOf("a", "a"), declaredCount = 2))
    }
}
