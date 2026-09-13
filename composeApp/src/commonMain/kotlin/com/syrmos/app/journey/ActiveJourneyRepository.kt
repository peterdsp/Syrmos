package com.syrmos.app.journey

import com.syrmos.core.common.loadStringPref
import com.syrmos.core.common.persistStringPref
import com.syrmos.core.domain.journey.ActiveJourneyStore
import com.syrmos.core.model.journey.ActiveJourney
import com.syrmos.core.model.journey.JourneyContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistence-bound holder for the single live GO session (the [ActiveJourney]),
 * the native peer of the web `SyrmosActiveJourney` localStorage store. One
 * versioned blob lives under [KEY] via the shared `loadStringPref`/`persistStringPref`
 * (SharedPreferences on Android, NSUserDefaults on iOS). All lifecycle logic is the
 * shared, tested [ActiveJourneyStore]; (de)serialization is `JourneyContract`, so the
 * on-disk shape is byte-parity with web and iOS.
 *
 * A session that has ENDED (or a corrupt/newer-schema blob) reads back as no live
 * session, so the UI never offers Resume for a finished trip. As with saved journeys
 * an undecodable blob is surfaced as "no session" but the raw blob is NEVER
 * overwritten until the next successful write, so a future-schema store is not lost.
 */
object ActiveJourneyRepository {
    const val KEY = "syrmos.active-journey.v1"

    private val _active = MutableStateFlow(load())
    /** The live, resumable session, or null when there is none. */
    val active: StateFlow<ActiveJourney?> = _active.asStateFlow()

    /** True when the persisted blob could not be decoded (newer schema / corrupt). */
    var lastDecodeFailed: Boolean = false
        private set

    private fun load(): ActiveJourney? {
        val raw = loadStringPref(KEY, "")
        if (raw.isBlank()) { lastDecodeFailed = false; return null }
        return when (val res = JourneyContract.decodeActiveJourney(raw)) {
            is JourneyContract.DecodeResult.Ok -> {
                lastDecodeFailed = false
                res.value.takeIf { ActiveJourneyStore.isResumable(it) }
            }
            else -> { lastDecodeFailed = true; null }
        }
    }

    /** Reload from disk (e.g. on screen appear) in case another surface changed it. */
    fun refresh() { _active.value = load() }

    /** Persist (or clear, when null) the live session and publish it. */
    fun set(journey: ActiveJourney) {
        persistStringPref(KEY, JourneyContract.encodeActiveJourney(journey))
        lastDecodeFailed = false
        _active.value = journey
    }

    /** End the live session: nothing to resume afterwards. */
    fun clear() {
        persistStringPref(KEY, "")
        lastDecodeFailed = false
        _active.value = null
    }

    /** Time-based id for a new session. */
    fun newId(): String = "go-" + kotlinx.datetime.Clock.System.now().toEpochMilliseconds().toString(36)
}
