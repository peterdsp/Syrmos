package com.syrmos.core.domain.reminder

import com.syrmos.core.common.loadStringPref
import com.syrmos.core.common.persistStringPref
import com.syrmos.core.model.reminder.ReminderContract
import com.syrmos.core.model.reminder.SavedDeparture
import com.syrmos.core.model.reminder.SavedDeparturesRoot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistence-bound holder for the Phase N J09 saved-departure board, the native
 * peer of the web localStorage store. One versioned blob lives under [KEY] via the
 * shared `loadStringPref`/`persistStringPref` (SharedPreferences on Android,
 * NSUserDefaults on iOS). All list logic is the shared, tested [SavedDepartureStore];
 * (de)serialization is `ReminderContract`, so the on-disk shape is byte-parity with
 * web and iOS.
 *
 * Lives in core/domain (not the app module) so both the feature UIs that add a
 * departure and the platform schedulers that observe it can share one instance.
 * An undecodable blob (newer schema / corrupt) reads back as an empty board but the
 * raw blob is NEVER overwritten until the next successful write, so a future-schema
 * store is not lost.
 */
object SavedDepartureRepository {
    const val KEY = "syrmos.saved-departures.v1"

    private val _departures = MutableStateFlow(load())
    val departures: StateFlow<List<SavedDeparture>> = _departures.asStateFlow()

    var lastDecodeFailed: Boolean = false
        private set

    private fun load(): List<SavedDeparture> {
        val raw = loadStringPref(KEY, "")
        return when (val res = ReminderContract.decodeSavedDeparturesRoot(raw)) {
            is ReminderContract.DecodeResult.Ok -> { lastDecodeFailed = false; res.value.savedDepartures }
            else -> { lastDecodeFailed = true; emptyList() }
        }
    }

    /** Reload from disk in case another surface changed it. */
    fun refresh() { _departures.value = load() }

    /** Add or update a saved departure (dedup by id, most-recent on top). */
    fun save(entry: SavedDeparture) { commit(SavedDepartureStore.save(_departures.value, entry)) }

    fun remove(id: String) { commit(SavedDepartureStore.remove(_departures.value, id)) }

    fun contains(id: String): Boolean = SavedDepartureStore.contains(_departures.value, id)

    /** Drop trains that have already left, self-healing a stale board. */
    fun pruneDeparted(nowEpochSeconds: Long) {
        val pruned = SavedDepartureStore.pruneDeparted(_departures.value, nowEpochSeconds)
        if (pruned.size != _departures.value.size) commit(pruned)
    }

    fun clear() { commit(emptyList()) }

    private fun commit(list: List<SavedDeparture>) {
        persistStringPref(KEY, ReminderContract.encodeSavedDeparturesRoot(SavedDeparturesRoot(savedDepartures = list)))
        lastDecodeFailed = false
        _departures.value = list
    }
}
