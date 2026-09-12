package com.syrmos.core.domain.journey

import com.syrmos.core.model.journey.SavedJourney

/**
 * Pure list operations for locally-owned saved journeys (S08 / J05), the Kotlin
 * peer of web `SyrmosSavedJourneys` and iOS `SavedJourneyStore`. All ops take and
 * return an ordered `List<SavedJourney>` (display order == list order) so they are
 * trivially testable and identical across clients; persistence is layered on top
 * by each platform (SharedPreferences/DataStore on Android) via
 * `JourneyContract.encodeSavedJourneysRoot` / `decodeSavedJourneysRoot`.
 *
 * The de-duplication invariant is HARD and shared (see fixtures/journeys/saved.json
 * and the memory note on de-dup): at most one saved journey per (fromId, toId).
 */
object SavedJourneyStore {
    /**
     * Prepend a save, de-duplicating by (fromId, toId). If the pair already exists
     * its row is updated in place (its original id is preserved) with the new
     * entry's label/createdAt/preferences, and moved to the top; a new pair is
     * simply prepended. Never produces two rows for one pair.
     */
    fun save(list: List<SavedJourney>, entry: SavedJourney): List<SavedJourney> {
        val existing = list.firstOrNull { it.fromId == entry.fromId && it.toId == entry.toId }
        val merged = if (existing != null) {
            existing.copy(
                createdAt = entry.createdAt,
                label = entry.label?.trim()?.ifBlank { null },
                preferences = entry.preferences,
            )
        } else {
            entry.copy(label = entry.label?.trim()?.ifBlank { null })
        }
        val without = list.filterNot { it.fromId == entry.fromId && it.toId == entry.toId }
        return listOf(merged) + without
    }

    /** Set (or clear) the label on one saved journey. Blank clears to null. */
    fun rename(list: List<SavedJourney>, id: String, label: String?): List<SavedJourney> {
        val clean = label?.trim()?.ifBlank { null }
        return list.map { if (it.id == id) it.copy(label = clean) else it }
    }

    fun remove(list: List<SavedJourney>, id: String): List<SavedJourney> =
        list.filterNot { it.id == id }

    /**
     * Reorder to an explicit id sequence. Ids not present are dropped and unknown
     * ids ignored, so the caller controls the exact display order.
     */
    fun reorder(list: List<SavedJourney>, order: List<String>): List<SavedJourney> {
        val byId = list.associateBy { it.id }
        return order.mapNotNull { byId[it] }
    }
}
