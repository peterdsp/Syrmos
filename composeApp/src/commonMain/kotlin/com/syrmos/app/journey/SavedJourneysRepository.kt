package com.syrmos.app.journey

import com.syrmos.core.common.loadStringPref
import com.syrmos.core.common.persistStringPref
import com.syrmos.core.domain.journey.SavedJourneyStore
import com.syrmos.core.model.journey.JourneyContract
import com.syrmos.core.model.journey.SavedJourney
import com.syrmos.core.model.journey.SavedJourneysRoot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistence-bound saved-journeys store for the native app (S08 / J05), the peer
 * of the web `SyrmosSavedJourneys` localStorage store. The ordered list lives in
 * one versioned blob under [KEY] via the shared `loadStringPref`/`persistStringPref`
 * (SharedPreferences on Android, NSUserDefaults on iOS). Pure list logic is reused
 * from `SavedJourneyStore` and (de)serialization from `JourneyContract`, so the
 * on-disk shape is byte-parity with web and the de-dup invariant is identical.
 *
 * A decode that comes back unsupported/corrupt is surfaced as an empty in-memory
 * list but the raw blob is NEVER overwritten until the next successful save, so a
 * newer-schema store from a future build is not clobbered by an older one.
 */
object SavedJourneysRepository {
    const val KEY = "syrmos.saved-journeys.v1"

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<SavedJourney>> = _items.asStateFlow()

    /** True when the persisted blob could not be decoded (newer schema / corrupt). */
    var lastDecodeFailed: Boolean = false
        private set

    private fun load(): List<SavedJourney> =
        when (val res = JourneyContract.decodeSavedJourneysRoot(loadStringPref(KEY, ""))) {
            is JourneyContract.DecodeResult.Ok -> { lastDecodeFailed = false; res.value.savedJourneys }
            else -> { lastDecodeFailed = true; emptyList() }
        }

    private fun persist(list: List<SavedJourney>): List<SavedJourney> {
        // Only overwrite the blob once we have a decodable, current-version list in
        // hand, so an unsupported future store is preserved until the user acts.
        persistStringPref(KEY, JourneyContract.encodeSavedJourneysRoot(SavedJourneysRoot(savedJourneys = list)))
        lastDecodeFailed = false
        _items.value = list
        return list
    }

    /** Reload from disk (e.g. on screen appear) in case another surface changed it. */
    fun refresh() { _items.value = load() }

    fun save(entry: SavedJourney) = persist(SavedJourneyStore.save(_items.value, entry))
    fun rename(id: String, label: String?) = persist(SavedJourneyStore.rename(_items.value, id, label))
    fun remove(id: String) = persist(SavedJourneyStore.remove(_items.value, id))
    fun reorder(order: List<String>) = persist(SavedJourneyStore.reorder(_items.value, order))

    fun isSaved(fromId: String, toId: String): Boolean =
        _items.value.any { it.fromId == fromId && it.toId == toId }

    /** Time-based id; de-dup is by (fromId,toId), never by this id. */
    fun newId(): String = "sj-" + kotlinx.datetime.Clock.System.now().toEpochMilliseconds().toString(36)
}
