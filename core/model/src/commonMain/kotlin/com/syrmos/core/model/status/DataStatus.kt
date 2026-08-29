package com.syrmos.core.model.status

import com.syrmos.core.model.schedule.SourceConfidence

/**
 * The full, orthogonal status of an on-screen datum: WHERE it came from
 * ([provenance], a [SourceConfidence]) and its TEMPORAL condition ([freshness],
 * a [Freshness]). Both axes are retained together; the display state is DERIVED
 * via [resolveDataStatusDisplay] and never replaces them — the provenance must
 * survive so callers can still branch on how the value was produced.
 */
data class DataStatus(
    val provenance: SourceConfidence,
    val freshness: Freshness,
) {
    fun display(): DataStatusDisplay = resolveDataStatusDisplay(this)
}

/**
 * The resolved display bucket. Language-free: the platform UI (SwiftUI / Compose
 * / web) maps each kind to a localized label and formats
 * [DataStatusDisplay.ageSeconds] with a locale-aware duration formatter.
 *
 * [LIVE_UNKNOWN_AGE] is a live source whose recency we cannot vouch for — it is
 * deliberately distinct from [LIVE] and must never render as a plain "live"
 * badge.
 */
enum class DisplayKind {
    LIVE,
    LIVE_UNKNOWN_AGE,
    SCHEDULED,
    ESTIMATED,
    CACHED,
    OFFLINE,
    OPERATOR,
    UNAVAILABLE,
}

/**
 * Structured, language-free display state. It carries NO UI copy and NO
 * preformatted age: [kind] selects the localized label, [ageSeconds] (present
 * only for [DisplayKind.CACHED]) feeds a platform duration formatter, and
 * [status] preserves the full provenance + freshness so nothing is discarded.
 */
data class DataStatusDisplay(
    val kind: DisplayKind,
    val ageSeconds: Long?,
    val status: DataStatus,
)

/**
 * Provenance-first resolution of a [DataStatus] to a [DataStatusDisplay].
 *
 * Provenance sets the reading; freshness only refines a LIVE datum:
 * fresh -> [DisplayKind.LIVE], stale -> [DisplayKind.CACHED] (with the age),
 * unknown age -> [DisplayKind.LIVE_UNKNOWN_AGE] (never a plain live badge), and
 * the invalid combinations (live + not-applicable, live + unavailable) ->
 * [DisplayKind.UNAVAILABLE]. OFFLINE is the bundled-timetable source (a
 * provenance), not a statement about device connectivity — nothing here inspects
 * a connectivity flag.
 *
 * A [Freshness.Unavailable] means no datum exists and resolves to
 * [DisplayKind.UNAVAILABLE] for every provenance, never to a usable state.
 */
fun resolveDataStatusDisplay(status: DataStatus): DataStatusDisplay {
    // No datum exists: Unavailable can never read as a usable state, whatever the
    // provenance. Guarded up front so this holds for every combination.
    if (status.freshness is Freshness.Unavailable) {
        return DataStatusDisplay(DisplayKind.UNAVAILABLE, null, status)
    }
    val resolved: Pair<DisplayKind, Long?> = when (status.provenance) {
        SourceConfidence.LIVE -> when (val f = status.freshness) {
            is Freshness.Fresh -> DisplayKind.LIVE to null
            is Freshness.Stale -> DisplayKind.CACHED to f.ageSeconds
            is Freshness.UnknownAge -> DisplayKind.LIVE_UNKNOWN_AGE to null
            is Freshness.NotApplicable -> DisplayKind.UNAVAILABLE to null
            is Freshness.Unavailable -> DisplayKind.UNAVAILABLE to null
        }
        SourceConfidence.SCHEDULED -> DisplayKind.SCHEDULED to null
        SourceConfidence.ESTIMATED -> DisplayKind.ESTIMATED to null
        SourceConfidence.OFFLINE -> DisplayKind.OFFLINE to null
        SourceConfidence.OPERATOR_LINK -> DisplayKind.OPERATOR to null
        SourceConfidence.UNKNOWN -> DisplayKind.UNAVAILABLE to null
    }
    return DataStatusDisplay(resolved.first, resolved.second, status)
}
