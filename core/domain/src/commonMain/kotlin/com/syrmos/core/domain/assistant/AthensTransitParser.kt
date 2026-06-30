package com.syrmos.core.domain.assistant

/**
 * Pure, offline, trilingual (EN / EL / SQ) rule parser that turns a user
 * utterance into an [AssistantIntent]. No model, no network, no per-call state.
 *
 * Design rules:
 *  - It only ever returns an approved intent. It cannot produce a transit fact.
 *  - It is conservative: if nothing transit-related is detected, it returns
 *    [AssistantIntent.OutOfScope] so Ariadne declines instead of guessing.
 *  - When the intent is clear but a required slot (origin / destination /
 *    station) is missing, it wraps the intent in [AssistantIntent.NeedsClarification]
 *    so the caller can ask one focused question rather than fabricate a default.
 *
 * Greek and Albanian are matched on accent-folded, lowercased text so
 * "Σύνταγμα", "syntagma" and "Πλατεία Συντάγματος" all resolve.
 */
class AthensTransitParser(
    private val vocabulary: AssistantVocabulary,
) {
    fun parse(rawInput: String): AssistantIntent {
        val text = fold(rawInput)
        if (text.isBlank()) return AssistantIntent.OutOfScope

        val mentionedStations = matchStations(text)
        val mentionedLine = matchLine(text)
        val day = matchDay(text)

        // Help / capabilities, allowed even with no other signal.
        if (containsAny(text, HELP_PHRASES)) return AssistantIntent.Help

        val strongTransit = mentionedStations.isNotEmpty() ||
            mentionedLine != null ||
            containsAny(text, TRANSIT_NOUNS)
        // Recognised transit intents count as in-scope even without a named
        // station/line, so "any service alerts?" or "show the map" are handled.
        val intentSignal = containsAny(text, ALERT_WORDS) ||
            containsAny(text, MAP_WORDS) ||
            containsAny(text, LAST_TRAIN_PHRASES) ||
            containsAny(text, PLAN_PHRASES) ||
            containsAny(text, FIND_WORDS)

        // Weather is allowed ONLY as a routing constraint, never as a topic.
        // "weather in london" stays out of scope; "raining, get me to X" routes
        // with the low-exposure flag set.
        val weather = containsAny(text, WEATHER_WORDS)
        if (weather && !strongTransit) return AssistantIntent.OutOfScope

        // Nothing transit-related at all: decline.
        if (!strongTransit && !intentSignal && !weather) return AssistantIntent.OutOfScope

        // 1. Plan a trip. Triggered by an explicit "how do I get" phrase, an
        //    explicit "to" frame with a station, weather routing, or two
        //    distinct stations named. A bare "trains FROM X" is departures, not
        //    a trip, so "from" alone does not trigger planning.
        val hasToMarker = TO_MARKERS.any { text.contains(it) }
        val planning = containsAny(text, PLAN_PHRASES) ||
            weather ||
            (hasToMarker && mentionedStations.isNotEmpty()) ||
            mentionedStations.size >= 2
        if (planning) {
            val (from, to) = resolveTripEndpoints(text, mentionedStations)
            val base = AssistantIntent.PlanTrip(
                fromStationId = from,
                toStationId = to,
                lowExposure = weather,
            )
            return when {
                to == null -> AssistantIntent.NeedsClarification(base, MissingSlot.DESTINATION_STATION)
                from == null -> AssistantIntent.NeedsClarification(base, MissingSlot.ORIGIN_STATION)
                else -> base
            }
        }

        // 2. Last train tonight.
        if (containsAny(text, LAST_TRAIN_PHRASES)) {
            val station = mentionedStations.firstOrNull()
            val base = AssistantIntent.LastTrain(stationId = station, lineId = mentionedLine)
            return if (station == null) {
                AssistantIntent.NeedsClarification(base, MissingSlot.STATION)
            } else {
                base
            }
        }

        // 3. Alerts / service status.
        if (containsAny(text, ALERT_WORDS)) {
            return AssistantIntent.ShowAlerts(lineId = mentionedLine)
        }

        // 4. Open on the map.
        if (containsAny(text, MAP_WORDS)) {
            return AssistantIntent.OpenMap(stationId = mentionedStations.firstOrNull())
        }

        // 5. Explain a line: a line is named with no station, no departures cue,
        //    and either a "line/about" word or a bare line id like "M2".
        if (mentionedLine != null && mentionedStations.isEmpty() &&
            !containsAny(text, DEPARTURE_WORDS) &&
            (containsAny(text, LINE_WORDS) || isBareLineQuery(text))
        ) {
            return AssistantIntent.ExplainLine(mentionedLine)
        }

        // 6. Departures: the default when a station and/or line is present, or a
        //    departures cue is used. This is the most common ask.
        if (mentionedStations.isNotEmpty() || containsAny(text, DEPARTURE_WORDS) || mentionedLine != null) {
            val station = mentionedStations.firstOrNull()
            val base = AssistantIntent.ShowDepartures(
                stationId = station,
                lineId = mentionedLine,
                day = day,
            )
            // A line with no station ("next M2 train") still answers from the
            // line origin, so it is not under-specified. A pure "next trains"
            // with neither needs a station.
            return if (station == null && mentionedLine == null) {
                AssistantIntent.NeedsClarification(base, MissingSlot.STATION)
            } else {
                base
            }
        }

        // 7. Find a station by name.
        if (containsAny(text, FIND_WORDS) && mentionedStations.isEmpty()) {
            return AssistantIntent.FindStation(query = rawInput.trim())
        }

        return AssistantIntent.OutOfScope
    }

    // MARK: - Matching

    private fun matchStations(text: String): List<String> {
        // Longest-name-first so "dimotiko theatro" wins over "theatro".
        val ordered = vocabulary.stations
            .flatMap { st -> st.names.map { name -> Triple(st.id, fold(name), name.length) } }
            .filter { it.second.length >= 3 }
            .sortedByDescending { it.second.length }
        val found = LinkedHashSet<String>()
        var scratch = text
        for ((id, name, _) in ordered) {
            if (id in found) continue
            if (scratch.contains(name)) {
                found.add(id)
                // Blank out the matched span so a substring of it can't double-match.
                scratch = scratch.replace(name, " ".repeat(name.length))
            }
        }
        return found.toList()
    }

    private fun matchLine(text: String): String? {
        val ordered = vocabulary.lines
            .flatMap { line -> line.aliases.map { it to line.id } }
            .map { fold(it.first) to it.second }
            .filter { it.first.length >= 2 }
            .sortedByDescending { it.first.length }
        for ((alias, id) in ordered) {
            if (containsToken(text, alias)) return id
        }
        return null
    }

    private fun matchDay(text: String): DayContext = when {
        containsAny(text, TOMORROW_WORDS) -> DayContext.TOMORROW
        containsAny(text, WEEKEND_WORDS) -> DayContext.WEEKEND
        containsAny(text, SATURDAY_WORDS) -> DayContext.SATURDAY
        containsAny(text, SUNDAY_WORDS) -> DayContext.SUNDAY
        else -> DayContext.TODAY
    }

    /**
     * Splits the two named stations into (origin, destination) by their actual
     * position in the sentence. matchStations returns ids ordered by name
     * length, not reading order, so a trip must be resolved against the text:
     * the earlier-mentioned station is the origin, the later one the
     * destination, which a "to" marker confirms.
     */
    private fun resolveTripEndpoints(
        text: String,
        stations: List<String>,
    ): Pair<String?, String?> {
        if (stations.isEmpty()) return null to null
        if (stations.size == 1) {
            // "to the airport" with no origin: treat the single station as the
            // destination so we ask for the origin, which is the natural gap.
            val toMarkerBeforeOnly = TO_MARKERS.any { text.contains(it) }
            return if (toMarkerBeforeOnly) null to stations[0] else stations[0] to null
        }
        val byPosition = stations.sortedBy { positionOf(text, it) }
        return byPosition[0] to byPosition[1]
    }

    /** Earliest index in [text] of any folded name for [stationId]. */
    private fun positionOf(text: String, stationId: String): Int {
        val names = vocabulary.stations.firstOrNull { it.id == stationId }?.names ?: return Int.MAX_VALUE
        return names
            .map { text.indexOf(fold(it)) }
            .filter { it >= 0 }
            .minOrNull() ?: Int.MAX_VALUE
    }

    private fun isBareLineQuery(text: String): Boolean {
        // "m2", "line 2", "γραμμη 2" with little else.
        return text.split(WHITESPACE).count { it.isNotBlank() } <= 3
    }

    // MARK: - Token helpers

    private fun containsAny(text: String, needles: List<String>): Boolean =
        needles.any { containsToken(text, fold(it)) }

    /**
     * Word-ish containment: matches [needle] as a substring but guards the
     * common false positive where a short token sits inside a longer word.
     * Multi-word needles match as plain substrings.
     */
    private fun containsToken(text: String, needle: String): Boolean {
        if (needle.isBlank()) return false
        if (needle.contains(' ')) return text.contains(needle)
        var idx = text.indexOf(needle)
        while (idx >= 0) {
            val before = if (idx == 0) ' ' else text[idx - 1]
            val afterIdx = idx + needle.length
            val after = if (afterIdx >= text.length) ' ' else text[afterIdx]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            idx = text.indexOf(needle, idx + 1)
        }
        return false
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")

        // Accent-fold Greek + Latin diacritics and lowercase, so EL/SQ/EN
        // converge. Greek tonos/dialytika are stripped; Latin accents too.
        fun fold(input: String): String {
            val sb = StringBuilder(input.length)
            for (ch in input.lowercase()) {
                sb.append(foldChar(ch))
            }
            return sb.toString()
        }

        private fun foldChar(ch: Char): Char = when (ch) {
            'ά' -> 'α'; 'έ' -> 'ε'; 'ή' -> 'η'; 'ί', 'ϊ', 'ΐ' -> 'ι'
            'ό' -> 'ο'; 'ύ', 'ϋ', 'ΰ' -> 'υ'; 'ώ' -> 'ω'; 'ς' -> 'σ'
            'à', 'á', 'â', 'ä', 'ã' -> 'a'
            'è', 'é', 'ê', 'ë' -> 'e'
            'ì', 'í', 'î', 'ï' -> 'i'
            'ò', 'ó', 'ô', 'ö', 'õ' -> 'o'
            'ù', 'ú', 'û', 'ü' -> 'u'
            'ç' -> 'c'
            else -> ch
        }

        // Vocabulary. Kept compact and accent-folded at match time.
        private val TRANSIT_NOUNS = listOf(
            "train", "trains", "metro", "tram", "station", "departure", "departures",
            "τρεν", "μετρο", "τραμ", "σταθμ", "δρομολογ", "αναχωρη", "συρμ", "προαστιακ",
            "tren", "stacion", "nisje",
        )
        private val DEPARTURE_WORDS = listOf(
            "next", "departure", "departures", "when", "trains", "leave", "leaving", "schedule",
            "επομεν", "αναχωρη", "ποτε", "δρομολογ", "φευγει", "τρεν",
            "ardhsh", "kur", "nisje", "tren", "trena",
        )
        private val LAST_TRAIN_PHRASES = listOf(
            "last train", "last metro", "last one", "leave by",
            "τελευται", "τελευταιο τρεν", "τελευταιος",
            "treni i fundit", "fundit", "i fundit", "tren i fundit",
        )
        // Strong trip cues only. Bare "from" / "από" / "nga" are deliberately
        // excluded: "trains from Syntagma" is a departures query, not a trip.
        // Two named stations or an explicit "to" frame trigger planning instead.
        private val PLAN_PHRASES = listOf(
            "how do i get", "how to get", "get to", "get me to", "route",
            "πως πα", "πως πη", "πως φτα", "διαδρομη", "για να πα",
            "si shkoj", "si te shkoj", "rruga", "udhetim",
        )
        private val TO_MARKERS = listOf(
            " to ", " for ", "->", "→", " προς ", " για ", " te ", " per ", " ne ",
        )
        private val FIND_WORDS = listOf(
            "where is", "find", "locate", "nearest", "near me", "closest",
            "που ειναι", "βρες", "κοντιν", "κοντα μου", "πλησιεστερ",
            "ku eshte", "gjej", "me afert", "afer meje",
        )
        private val LINE_WORDS = listOf(
            "line", "about", "tell me about",
            "γραμμη", "σχετικα",
            "linja", "rreth",
        )
        private val ALERT_WORDS = listOf(
            "alert", "alerts", "status", "disruption", "delay", "delays", "problem", "closed", "closure",
            "ειδοποι", "κατασταση", "καθυστερη", "προβλημα", "κλειστ", "διακοπη",
            "njoftim", "vonese", "problem", "mbyll",
        )
        private val MAP_WORDS = listOf(
            "map", "show on map", "on the map",
            "χαρτη", "στον χαρτη",
            "harta", "ne harte",
        )
        private val HELP_PHRASES = listOf(
            "what can you do", "help", "how do you work", "what do you do", "who are you",
            "τι μπορεις", "βοηθεια", "πως δουλευ", "ποιος εισαι",
            "si funksionon", "ndihme", "cfare mund", "kush je",
        )
        private val WEATHER_WORDS = listOf(
            "rain", "raining", "rainy", "weather", "storm", "wet",
            "βροχη", "βρεχει", "καιρο", "κακοκαιρ",
            "shi", "moti", "stuhi",
        )
        private val TOMORROW_WORDS = listOf("tomorrow", "αυριο", "neser")
        private val WEEKEND_WORDS = listOf("weekend", "σαββατοκυριακο", "fundjave")
        private val SATURDAY_WORDS = listOf("saturday", "σαββατο", "te shtune", "shtune")
        private val SUNDAY_WORDS = listOf("sunday", "κυριακη", "te diel", "diel")
    }
}
