package com.syrmos.core.domain.journey

import com.syrmos.core.model.journey.FeasibilityStatus
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

    // Comfortable-first ordering class for the RECOMMENDED default: a known
    // comfortable journey outranks a tight one, a tight one outranks an unknown,
    // and a missed connection sorts last so it can never lead the list. This keeps
    // the deterministic total ordering (feasibility class, then the fastest chain),
    // and bounds the extra journey time accepted to the materially-distinct
    // candidates the planner returns for the same trip rather than an invented
    // minute penalty (finding 7, product decision option 2).
    private fun feasibilityClass(opt: JourneyOption): Int = when (opt.feasibility.status) {
        FeasibilityStatus.COMFORTABLE -> 0
        FeasibilityStatus.TIGHT -> 1
        FeasibilityStatus.UNKNOWN -> 2
        FeasibilityStatus.MISSED -> 3
    }

    private fun comparatorFor(ranking: Ranking): Comparator<JourneyOption> {
        val primary: Comparator<JourneyOption> = when (ranking) {
            // Comfortable-first, then the fastest objective as the in-class tie-break.
            Ranking.RECOMMENDED -> compareBy<JourneyOption> { feasibilityClass(it) }
                .thenBy(nullsLast()) { it.durationSeconds }
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

    // The label only holds if the objective is genuinely true. For RECOMMENDED the
    // top option is badged only when it is actually feasible (never a missed one).
    private fun badgeFor(ranking: Ranking, opt: JourneyOption): String? = when (ranking) {
        Ranking.RECOMMENDED -> if (opt.feasibility.status != FeasibilityStatus.MISSED) "recommended" else null
        Ranking.FASTEST -> if (opt.durationSeconds != null) "fastest" else null
        Ranking.FEWEST_CHANGES -> "fewestChanges"
        Ranking.LEAST_WALKING -> if (opt.walkingSeconds != null) "leastWalking" else null
    }
}
