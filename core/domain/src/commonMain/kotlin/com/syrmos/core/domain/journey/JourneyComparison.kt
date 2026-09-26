package com.syrmos.core.domain.journey

/**
 * Plan companion helpers (foldables master plan, Plan contract): compare the
 * route alternatives on the differences that are actually there, and keep the
 * selected itinerary by identity rather than by list position.
 *
 * Both are pure and language-neutral. iOS carries a twin in
 * `iosApp/Core/Journey/JourneyComparison.swift`; the fixtures in
 * `JourneyComparisonTest` and `JourneyComparisonTests` must agree.
 */
object JourneyComparison {
    /**
     * What one alternative has over the others.
     *
     * - [fastest]: shortest known duration, and at least one other option is slower.
     * - [fewestChanges]: fewest changes, and at least one other option has more.
     * - [minutesSlowerThanFastest]: whole minutes behind the fastest known
     *   option, null when this is the fastest, ties it, or its duration is unknown.
     * - [extraChanges]: changes beyond the option with the fewest (0 when it is one).
     *
     * A lone alternative gets neutral facts: there is nothing to compare against,
     * so a single route is never decorated as "fastest".
     */
    data class Facts(
        val fastest: Boolean = false,
        val fewestChanges: Boolean = false,
        val minutesSlowerThanFastest: Int? = null,
        val extraChanges: Int = 0,
    )

    /**
     * Facts for every alternative, index-aligned with the inputs. [durations] are
     * seconds (null when the option has no timed duration), [changes] the transfer
     * counts. The two lists must be the same length.
     */
    fun facts(durations: List<Int?>, changes: List<Int>): List<Facts> {
        require(durations.size == changes.size) { "durations and changes must align" }
        val n = durations.size
        if (n < 2) return List(n) { Facts() }

        val known = durations.filterNotNull()
        val minDur = known.minOrNull()
        val maxDur = known.maxOrNull()
        val durationsDiffer = minDur != null && maxDur != null && maxDur > minDur
        val minCh = changes.min()
        val changesDiffer = changes.max() > minCh

        return durations.indices.map { i ->
            val d = durations[i]
            val slower = if (d != null && minDur != null && d > minDur) {
                ((d - minDur + 30) / 60).takeIf { it > 0 }
            } else null
            Facts(
                fastest = durationsDiffer && d != null && d == minDur,
                fewestChanges = changesDiffer && changes[i] == minCh,
                minutesSlowerThanFastest = slower,
                extraChanges = changes[i] - minCh,
            )
        }
    }
}

/**
 * Selected-itinerary identity across a results refresh. Alternatives are keyed
 * by the option id (legs plus schedule identity), so a re-plan that reorders or
 * replaces options keeps the traveller's choice when it still exists and falls
 * back to the first option otherwise. Never an array index.
 */
object JourneySelection {
    /** The id to keep selected: [previous] when still offered, else the first id, else null. */
    fun retain(previous: String?, ids: List<String>): String? =
        if (previous != null && previous in ids) previous else ids.firstOrNull()

    /** Position of [selected] in [ids], or null when it is not offered. */
    fun index(selected: String?, ids: List<String>): Int? =
        selected?.let { s -> ids.indexOf(s).takeIf { it >= 0 } }
}
