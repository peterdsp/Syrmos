package com.syrmos.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RailPulseContributionSnapshot(
    val confirmed: Int,
    val qualityPercent: Int,
    val thisWeek: Int,
)

/**
 * Device-only RailPulse contribution progress. The store contains counters,
 * never a user identifier, report history, location, or free-form content.
 */
object RailPulseLocalStore {
    private const val CONFIRMED_KEY = "ichnos_v2_confirmed"
    private const val QUALITY_KEY = "ichnos_v2_quality"
    private const val WEEK_KEY = "ichnos_v2_week"

    private val _snapshot = MutableStateFlow(load())
    val snapshot: StateFlow<RailPulseContributionSnapshot> = _snapshot.asStateFlow()

    fun recordContribution() {
        update(
            _snapshot.value.copy(
                confirmed = _snapshot.value.confirmed + 1,
                thisWeek = _snapshot.value.thisWeek + 1,
            ),
        )
    }

    fun undoContribution() {
        update(
            _snapshot.value.copy(
                confirmed = (_snapshot.value.confirmed - 1).coerceAtLeast(0),
                thisWeek = (_snapshot.value.thisWeek - 1).coerceAtLeast(0),
            ),
        )
    }

    private fun load() = RailPulseContributionSnapshot(
        confirmed = loadStringPref(CONFIRMED_KEY, "0").toIntOrNull() ?: 0,
        qualityPercent = loadStringPref(QUALITY_KEY, "0").toIntOrNull() ?: 0,
        thisWeek = loadStringPref(WEEK_KEY, "0").toIntOrNull() ?: 0,
    )

    private fun update(value: RailPulseContributionSnapshot) {
        _snapshot.value = value
        persistStringPref(CONFIRMED_KEY, value.confirmed.toString())
        persistStringPref(QUALITY_KEY, value.qualityPercent.toString())
        persistStringPref(WEEK_KEY, value.thisWeek.toString())
    }
}
