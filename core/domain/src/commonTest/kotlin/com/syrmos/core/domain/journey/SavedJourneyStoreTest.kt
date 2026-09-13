package com.syrmos.core.domain.journey

import com.syrmos.core.model.journey.JourneyContract
import com.syrmos.core.model.journey.JourneyPreferences
import com.syrmos.core.model.journey.Ranking
import com.syrmos.core.model.journey.SavedJourney
import com.syrmos.core.model.journey.SavedJourneysRoot
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Kotlin peer of web `web-tests/saved-journeys.test.js`, mirroring the golden
 * cases in fixtures/journeys/saved.json. Proves the pure store ops produce the
 * same ordered ids and honour the HARD de-dup invariant (one row per
 * (fromId,toId)), and that the encoded root is byte-parity with the web store.
 * Cases are inlined (commonTest has no file IO); the JSON fixture is the shared
 * source of truth for the web/iOS sides.
 */
class SavedJourneyStoreTest {

    private fun sj(
        id: String, from: String, to: String, createdAt: String,
        label: String? = null, ranking: Ranking = Ranking.FASTEST,
    ) = SavedJourney(
        id = id, fromId = from, toId = to,
        createdAt = Instant.parse(createdAt), label = label,
        preferences = JourneyPreferences(ranking = ranking),
    )

    private fun ids(list: List<SavedJourney>) = list.map { it.id }

    @Test
    fun savePrependsNewestFirst() {
        val before = listOf(sj("a", "M1_PIR", "M1_OMO", "2026-01-15T08:00:00+02:00"))
        val result = SavedJourneyStore.save(
            before, sj("b", "M2_SYN", "M2_ELL", "2026-01-15T09:00:00+02:00", label = "Home"),
        )
        assertEquals(listOf("b", "a"), ids(result))
    }

    @Test
    fun saveDedupsSamePairKeepsIdMovesToTop() {
        val before = listOf(
            sj("a", "M1_PIR", "M1_OMO", "2026-01-15T08:00:00+02:00"),
            sj("b", "M2_SYN", "M2_ELL", "2026-01-15T08:30:00+02:00", label = "Home"),
        )
        val result = SavedJourneyStore.save(
            before,
            sj("c", "M1_PIR", "M1_OMO", "2026-01-15T09:00:00+02:00", label = "Office", ranking = Ranking.FEWEST_CHANGES),
        )
        assertEquals(listOf("a", "b"), ids(result), "kept original id, moved to top, no dup")
        val top = result.first()
        assertEquals("a", top.id)
        assertEquals("Office", top.label)
        assertEquals(Ranking.FEWEST_CHANGES, top.preferences.ranking)
        assertEquals(Instant.parse("2026-01-15T09:00:00+02:00"), top.createdAt)
    }

    @Test
    fun renameSetsLabelAndBlankClearsToNull() {
        val before = listOf(sj("a", "M1_PIR", "M1_OMO", "2026-01-15T08:00:00+02:00", label = "Work"))
        assertEquals("Gym", SavedJourneyStore.rename(before, "a", "Gym").first().label)
        assertEquals(null, SavedJourneyStore.rename(before, "a", "   ").first().label)
    }

    @Test
    fun removeDeletesById() {
        val before = listOf(
            sj("a", "M1_PIR", "M1_OMO", "2026-01-15T08:00:00+02:00"),
            sj("b", "M2_SYN", "M2_ELL", "2026-01-15T08:30:00+02:00", label = "Home"),
        )
        assertEquals(listOf("b"), ids(SavedJourneyStore.remove(before, "a")))
    }

    @Test
    fun reorderAppliesIdSequenceDroppingUnknown() {
        val before = listOf(
            sj("a", "M1_PIR", "M1_OMO", "2026-01-15T08:00:00+02:00"),
            sj("b", "M2_SYN", "M2_ELL", "2026-01-15T08:30:00+02:00"),
            sj("c", "M3_AIR", "M3_SYN", "2026-01-15T09:00:00+02:00"),
        )
        assertEquals(listOf("c", "a", "b"), ids(SavedJourneyStore.reorder(before, listOf("c", "a", "b", "zzz"))))
    }

    @Test
    fun deDupIsAHardInvariant() {
        var list = emptyList<SavedJourney>()
        list = SavedJourneyStore.save(list, sj("x1", "A", "B", "2026-01-15T08:00:00+02:00", label = "one"))
        list = SavedJourneyStore.save(list, sj("x2", "A", "B", "2026-01-15T08:01:00+02:00", label = "two"))
        list = SavedJourneyStore.save(list, sj("x3", "A", "B", "2026-01-15T08:02:00+02:00", label = "three"))
        assertEquals(1, list.size, "one row for one pair")
        assertEquals("x1", list.first().id, "original id preserved")
        assertEquals("three", list.first().label, "latest label wins")
    }

    @Test
    fun rootEncodeMatchesTheFixtureShapeAndRoundTrips() {
        // Empty root == the fixture emptyRoot: {"schemaVersion":1,"savedJourneys":[]}.
        assertEquals(
            "{\"schemaVersion\":1,\"savedJourneys\":[]}",
            JourneyContract.encodeSavedJourneysRoot(SavedJourneysRoot()),
        )
        val root = SavedJourneysRoot(savedJourneys = listOf(sj("a", "M1_PIR", "M1_OMO", "2026-01-15T08:00:00+02:00")))
        val decoded = JourneyContract.decodeSavedJourneysRoot(JourneyContract.encodeSavedJourneysRoot(root))
        assertTrue(decoded is JourneyContract.DecodeResult.Ok)
        assertEquals(listOf("a"), ids(decoded.value.savedJourneys))
    }

    @Test
    fun decodeIsAtomicAndVersioned() {
        assertTrue(JourneyContract.decodeSavedJourneysRoot("") is JourneyContract.DecodeResult.Ok)
        val unsupported = JourneyContract.decodeSavedJourneysRoot("{\"schemaVersion\":2,\"savedJourneys\":[]}")
        assertTrue(unsupported is JourneyContract.DecodeResult.Unsupported)
        assertEquals(2, unsupported.foundVersion)
        assertTrue(JourneyContract.decodeSavedJourneysRoot("{not json") is JourneyContract.DecodeResult.Corrupt)
    }
}
