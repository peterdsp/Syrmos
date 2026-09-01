package com.syrmos.core.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the per-line schedule-sync diff. The shipped filter compared the server
 * manifest hash to itself, so a changed line was never pulled and, with all
 * lines pre-hydrated from the bundle, Android could not update a timetable
 * without an app release. linesToFetch fixes it by comparing the CACHED hash to
 * the server hash.
 */
class ScheduleSyncRepositoryTest {

    @Test
    fun nothingToFetchWhenAllLinesCachedAndHashesMatch() {
        val hashes = mapOf("M1" to "a", "M2" to "b", "T6" to "c")
        val toFetch = ScheduleSyncRepository.linesToFetch(
            cachedLineIds = hashes.keys,
            storedHashes = hashes,
            manifestHashes = hashes,
        )
        assertTrue(toFetch.isEmpty(), "up-to-date lines must not be re-fetched")
    }

    @Test
    fun refetchesOnlyTheChangedLine() {
        // The regression: cached hash for M1 is stale vs the server manifest.
        // The old self-comparison returned nothing here; the fix returns [M1].
        val stored = mapOf("M1" to "old", "M2" to "b", "T6" to "c")
        val server = mapOf("M1" to "new", "M2" to "b", "T6" to "c")
        val toFetch = ScheduleSyncRepository.linesToFetch(
            cachedLineIds = stored.keys,
            storedHashes = stored,
            manifestHashes = server,
        )
        assertEquals(listOf("M1"), toFetch)
    }

    @Test
    fun fetchesLinesMissingFromCache() {
        val stored = mapOf("M1" to "a")
        val server = mapOf("M1" to "a", "M2" to "b")
        val toFetch = ScheduleSyncRepository.linesToFetch(
            cachedLineIds = setOf("M1"),
            storedHashes = stored,
            manifestHashes = server,
        )
        assertEquals(listOf("M2"), toFetch)
    }

    @Test
    fun fetchesWhenCachedButHashUnknown() {
        // Bundle present in cache but no stored hash for it (null != serverHash).
        val toFetch = ScheduleSyncRepository.linesToFetch(
            cachedLineIds = setOf("M1"),
            storedHashes = emptyMap(),
            manifestHashes = mapOf("M1" to "a"),
        )
        assertEquals(listOf("M1"), toFetch)
    }

    @Test
    fun fetchesANewManifestLineAndTheChangedOneTogether() {
        val stored = mapOf("M1" to "a", "M2" to "b")
        val server = mapOf("M1" to "a", "M2" to "B2", "M3" to "c")
        val toFetch = ScheduleSyncRepository.linesToFetch(
            cachedLineIds = setOf("M1", "M2"),
            storedHashes = stored,
            manifestHashes = server,
        ).toSet()
        assertEquals(setOf("M2", "M3"), toFetch, "changed M2 + new M3, but not unchanged M1")
    }

    // --- partial-failure retry (the stranding Codex flagged) ---

    @Test
    fun aFailedLineKeepsItsStaleHashAndIsRetriedNextRound() {
        val server = mapOf("M1" to "new", "M2" to "b")
        val stored = mapOf("M1" to "old", "M2" to "b")
        val attempted = ScheduleSyncRepository.linesToFetch(setOf("M1", "M2"), stored, server)
        assertEquals(listOf("M1"), attempted)

        // M1 fetch failed this round (fetched is empty).
        val after = ScheduleSyncRepository.hashesAfterFetch(stored, server, fetchedLineIds = emptySet())
        assertEquals("old", after["M1"], "a failed line must keep its stale hash")

        // Next round still reports M1 as needing a fetch (not stranded).
        val retry = ScheduleSyncRepository.linesToFetch(setOf("M1", "M2"), after, server)
        assertEquals(listOf("M1"), retry)
    }

    @Test
    fun aSucceededLineTakesTheServerHashAndIsNotRefetched() {
        val server = mapOf("M1" to "new")
        val stored = mapOf("M1" to "old")
        val after = ScheduleSyncRepository.hashesAfterFetch(stored, server, fetchedLineIds = setOf("M1"))
        assertEquals("new", after["M1"])
        assertTrue(ScheduleSyncRepository.linesToFetch(setOf("M1"), after, server).isEmpty())
    }

    @Test
    fun manifestAdvancesOnlyWhenEveryAttemptedLineLanded() {
        assertTrue(
            ScheduleSyncRepository.shouldAdvanceManifest(emptyList(), emptySet()),
            "nothing to fetch counts as success",
        )
        assertTrue(ScheduleSyncRepository.shouldAdvanceManifest(listOf("M1"), setOf("M1")))
        assertTrue(
            !ScheduleSyncRepository.shouldAdvanceManifest(listOf("M1", "M2"), setOf("M1")),
            "a partial failure must not advance the manifest",
        )
    }
}
