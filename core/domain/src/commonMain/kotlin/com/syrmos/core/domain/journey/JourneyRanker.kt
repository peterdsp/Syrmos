package com.syrmos.core.domain.journey

import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.Leg
import com.syrmos.core.model.journey.Ranking

/**
 * Ranks, de-duplicates and caps a set of candidate itineraries (prompt 7.2 / S04).
 *
 *  - Sort by the requested objective, then arrival, changes, walking, stable id.
 *  - Deduplicate identical leg sequences (never show the same trip twice).
 *  - Keep at most three materially distinct options; never pad to three.
 *  - Badge the top option with the objective label ONLY when that ranking is
 *    genuinely true (its objective metric is known); other options carry no badge.
 *
 * Pure; mirrors web `web-journey-ranker.js`. Unknown metrics (null) sort last so
 * an option with no known duration/walking can never masquerade as the best.
 */
object JourneyRanker {

    const val MAX_OPTIONS = 3

    /** Stable identity of a trip by its ordered legs, for dedup. */
    fun legSignature(option: JourneyOption): String =
        option.legs.joinToString("|") { legToken(it) }

    private fun legToken(l: Leg): String =
        "${l.kind}:${l.lineId ?: ""}:${l.fromId}:${l.toId}"

    fun rank(options: List<JourneyOption>, ranking: Ranking): List<JourneyOption> {
        val sorted = options.sortedWith(comparatorFor(ranking))

        // Dedup identical leg sequences, keeping the best-ranked occurrence.
        val seen = HashSet<String>()
        val distinct = sorted.filter { seen.add(legSignature(it)) }

        val top = distinct.take(MAX_OPTIONS)
        return top.mapIndexed { index, opt ->
            val badge = if (index == 0) badgeFor(ranking, opt) else null
            if (opt.rankingBadge == badge) opt else opt.copy(rankingBadge = badge)
        }
    }

    private fun comparatorFor(ranking: Ranking): Comparator<JourneyOption> {
        val primary: Comparator<JourneyOption> = when (ranking) {
            Ranking.FASTEST -> compareBy(nullsLast()) { it.durationSeconds }
            Ranking.FEWEST_CHANGES -> compareBy { it.transferCount }
            Ranking.LEAST_WALKING -> compareBy(nullsLast()) { it.walkingSeconds }
        }
        return primary
            .thenBy(nullsLast()) { it.arrivalInstant }
            .thenBy { it.transferCount }
            .thenBy(nullsLast()) { it.walkingSeconds }
            .thenBy { it.id }
    }

    // The label only holds if the objective metric is actually known.
    private fun badgeFor(ranking: Ranking, opt: JourneyOption): String? = when (ranking) {
        Ranking.FASTEST -> if (opt.durationSeconds != null) "fastest" else null
        Ranking.FEWEST_CHANGES -> "fewestChanges"
        Ranking.LEAST_WALKING -> if (opt.walkingSeconds != null) "leastWalking" else null
    }
}
