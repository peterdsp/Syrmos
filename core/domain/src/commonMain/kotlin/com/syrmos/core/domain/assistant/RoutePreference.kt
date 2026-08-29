package com.syrmos.core.domain.assistant

/**
 * How the user wants a route optimised, parsed from cues like "faster",
 * "easiest", or "fewer changes". Ariadne stays a co-pilot: she picks a
 * preference, the deterministic planner ranks the options, and she explains the
 * trade-off ("this is faster, but the other is easier") rather than deciding
 * silently.
 *
 * [FASTEST] minimises total minutes. [FEWEST_CHANGES] minimises transfers even
 * at a small time cost, which is also the right default for luggage, strollers,
 * and limited mobility. [BALANCED] is the neutral default when the user gave no
 * cue.
 */
enum class RoutePreference {
    BALANCED,
    FASTEST,
    FEWEST_CHANGES,
    ;

    companion object {
        /** Accent-folded, lowercased cue words per preference (EN / EL / SQ). */
        private val FASTEST_CUES = listOf(
            "faster", "fastest", "quicker", "quickest", "fast", "quick", "speed",
            "γρηγορα", "γρηγορο", "γρηγορη", "πιο γρηγορα", "ταχυτερ", "ταχυτητα",
            "shpejt", "me shpejt", "shpejte", "shpejtesi",
        )
        private val FEWEST_CHANGES_CUES = listOf(
            // "direct" (English) is deliberately absent: it is a prefix of the
            // extremely common plan phrase "directions", so it would tag every
            // "directions to X" as a fewest-changes preference and rank a slow
            // one-seat ride above a fast one-transfer route. Albanian "direkt"
            // stays (different spelling, no such collision).
            "easiest", "easier", "easy", "simplest", "simpler", "simple",
            "fewer change", "fewer changes", "no change", "no changes", "less walking",
            "fewest", "without changing", "straight",
            "ευκολ", "απλ", "απευθειας", "χωρις αλλαγ", "λιγοτερες αλλαγ", "λιγοτερο περπατ",
            "lehte", "me lehte", "thjesht", "direkt", "pa nderrim", "me pak nderrim", "me pak ecje",
        )

        // Match a cue only where it BEGINS a word, so a stem still catches its
        // inflections ("ευκολ" -> "ευκολη") but a cue can never fire mid-word
        // ("fast" inside "breakfast", "speed" inside "godspeed").
        private fun containsAtWordStart(folded: String, cue: String): Boolean {
            var from = 0
            while (true) {
                val idx = folded.indexOf(cue, from)
                if (idx < 0) return false
                if (idx == 0 || !folded[idx - 1].isLetterOrDigit()) return true
                from = idx + 1
            }
        }

        /**
         * Reads a preference out of already-folded text ([AthensTransitParser.fold]).
         * Fewest-changes wins ties because "easy direct" leans toward simplicity.
         */
        fun fromFolded(folded: String): RoutePreference = when {
            FEWEST_CHANGES_CUES.any { containsAtWordStart(folded, it) } -> FEWEST_CHANGES
            FASTEST_CUES.any { containsAtWordStart(folded, it) } -> FASTEST
            else -> BALANCED
        }
    }
}
