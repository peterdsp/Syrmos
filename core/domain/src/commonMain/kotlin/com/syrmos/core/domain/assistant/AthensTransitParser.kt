package com.syrmos.core.domain.assistant

import kotlin.math.abs

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
    val vocabulary: AssistantVocabulary,
) {
    fun parse(rawInput: String): AssistantIntent {
        val text = fold(rawInput)
        if (text.isBlank()) return AssistantIntent.OutOfScope

        // Easter egg check runs before anything else so the trigger words
        // aren't accidentally masked by a legitimate transit intent that
        // happens to share substrings. See EasterEggLiepur for the story.
        if (LIEPUR_TRIGGERS.any { text.contains(it) }) {
            return AssistantIntent.EasterEggLiepur
        }

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
            containsAny(text, FIND_WORDS) ||
            containsAny(text, FARE_WORDS) ||
            containsAny(text, FAVORITE_WORDS) ||
            containsAny(text, TIME_PHRASES) ||
            containsAny(text, LOCATION_PHRASES)

        // Weather as a routing constraint (raining -> low-exposure route),
        // OR a direct weather question. "weather" / "καιρός" / "moti" alone
        // is answered from cache; "weather at X" is anchored to a station;
        // "get me to X, it's raining" still goes to the routing branch.
        val weather = containsAny(text, WEATHER_WORDS)
        if (weather) {
            // Direct weather question: no plan cue, no "to" marker, but a
            // weather word. Optionally anchored to a station the user
            // named. Non-Athens weather stays out of scope because the
            // station matcher will find nothing and we bail below.
            val planning = containsAny(text, PLAN_PHRASES) ||
                TO_MARKERS.any { text.contains(it) } ||
                mentionedStations.size >= 2
            if (!planning) {
                return AssistantIntent.WeatherAt(stationId = mentionedStations.firstOrNull())
            }
        }

        // Nothing transit-related at all: decline.
        if (!strongTransit && !intentSignal && !weather) return AssistantIntent.OutOfScope

        // Pure context-set "I'm at X" is checked before planning, because the
        // Albanian "jam te X" contains " te ", which would otherwise read as a
        // "to" marker and turn the statement into a trip. Only fires with at
        // most one station, no plan cue, and no routing preference, so
        // "I'm at X, go to Y faster" still plans normally below.
        if (containsAny(text, LOCATION_PHRASES) &&
            mentionedStations.size <= 1 &&
            !containsAny(text, PLAN_PHRASES) &&
            RoutePreference.fromFolded(text) == RoutePreference.BALANCED &&
            !containsAny(text, TIME_PHRASES) &&
            !containsAny(text, FARE_WORDS) &&
            !containsAny(text, LAST_TRAIN_PHRASES)
        ) {
            return AssistantIntent.SetCurrentLocation(stationId = mentionedStations.firstOrNull())
        }

        // 0. Travel time / ETA ("how long to X", "how many minutes to X").
        //    Placed before fares because "how much time" shares the fare
        //    "how much" cue, and before planning because "how long to get to X"
        //    shares the plan "get to" cue. The origin defaults to the user's
        //    location, resolved by the caller (GPS → nearest station); only an
        //    explicitly named origin fills fromStationId here.
        if (containsAny(text, TIME_PHRASES)) {
            val (from, to) = resolveTripEndpoints(text, mentionedStations)
            val destination = to ?: from
            val origin = if (mentionedStations.size >= 2) from else null
            return if (destination == null) {
                AssistantIntent.NeedsClarification(
                    AssistantIntent.TravelTime(toStationId = null),
                    MissingSlot.DESTINATION_STATION,
                )
            } else {
                AssistantIntent.TravelTime(toStationId = destination, fromStationId = origin)
            }
        }

        // 0a. Fares. Checked before planning so "how much to the airport" is a
        //     fare question, not a trip. The airport flag surfaces the airport
        //     ticket specifically.
        if (containsAny(text, FARE_WORDS)) {
            val (from, to) = resolveTripEndpoints(text, mentionedStations)
            return AssistantIntent.ExplainFare(
                airport = containsAny(text, AIRPORT_WORDS),
                fromStationId = from,
                toStationId = to,
            )
        }

        // 0b. Favorites. Needs a station to act on.
        if (containsAny(text, FAVORITE_WORDS)) {
            return AssistantIntent.ToggleFavorite(stationId = mentionedStations.firstOrNull())
                .let { intent ->
                    if (intent.stationId == null) {
                        AssistantIntent.NeedsClarification(intent, MissingSlot.STATION)
                    } else {
                        intent
                    }
                }
        }

        // 1. Plan a trip. Triggered by an explicit "how do I get" phrase, an
        //    explicit "to" frame with a station, weather routing, or two
        //    distinct stations named. A bare "trains FROM X" is departures, not
        //    a trip, so "from" alone does not trigger planning.
        val hasToMarker = TO_MARKERS.any { text.contains(it) }
        val preference = RoutePreference.fromFolded(text)
        val planning = containsAny(text, PLAN_PHRASES) ||
            weather ||
            (preference != RoutePreference.BALANCED && mentionedStations.isNotEmpty()) ||
            (hasToMarker && mentionedStations.isNotEmpty()) ||
            mentionedStations.size >= 2
        if (planning) {
            // Before the standard PlanTrip, see if the user anchored an
            // arrival time. "I need to be at Piraeus by 21:30" / "airport
            // in 45 minutes" / "at 9pm at Syntagma".
            val target = extractTargetTime(text)
            if (target != null) {
                val (from, to) = resolveTripEndpoints(text, mentionedStations)
                val base = AssistantIntent.PlanTripByArrival(
                    fromStationId = from,
                    toStationId = to,
                    arriveByAthensMinutes = target.absoluteMinutes,
                    inMinutesFromNow = target.relativeMinutes,
                )
                return when {
                    to == null -> AssistantIntent.NeedsClarification(base, MissingSlot.DESTINATION_STATION)
                    from == null -> AssistantIntent.NeedsClarification(base, MissingSlot.ORIGIN_STATION)
                    else -> base
                }
            }
            // A single named station reached through a plan cue or a routing
            // preference ("how do I go airport faster") is the destination; the
            // origin comes from session context or a follow-up question. Without
            // such a cue, a lone station keeps the position-based reading.
            val singleDestination = mentionedStations.size == 1 && !hasToMarker &&
                (containsAny(text, PLAN_PHRASES) || preference != RoutePreference.BALANCED)
            val (from, to) = if (singleDestination) {
                null to mentionedStations[0]
            } else {
                resolveTripEndpoints(text, mentionedStations)
            }
            val base = AssistantIntent.PlanTrip(
                fromStationId = from,
                toStationId = to,
                lowExposure = weather,
                preference = preference,
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

        // 2b. Station operational status: "is X open / working / closed?".
        //     Needs either a named station or the word "station", so a general
        //     "any closures today?" still falls through to the alerts branch.
        //     Placed before Alerts so "is Syntagma closed" is a station-status
        //     question, not a network-wide alerts query.
        if (containsAny(text, STATION_STATUS_WORDS) &&
            (mentionedStations.isNotEmpty() || containsAny(text, STATION_NOUN_WORDS))
        ) {
            val station = mentionedStations.firstOrNull()
            val base = AssistantIntent.StationStatus(stationId = station)
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
        // Typo fallback: only when nothing matched exactly, so a clean query is
        // never overridden. Resolves "nikea" / "nkiea" / "sintagma".
        if (found.isEmpty()) {
            fuzzyMatchStation(text)?.let { found.add(it) }
        }
        return found.toList()
    }

    /**
     * Best-effort typo correction of a query to a single station. Compares each
     * 4+ char input token (that isn't a known vocabulary word) against every
     * station-name word and returns the closest station id, or null. Kept tight
     * to avoid mapping gibberish onto a stop: up to 2 edits always, a 3rd only
     * when the first letter matches on a 6+ char word (the "nkiea" case).
     */
    private fun fuzzyMatchStation(text: String): String? {
        val tokens = text.split(NON_ALNUM)
            .filter { it.length >= 4 && it !in STOPWORDS }
        if (tokens.isEmpty()) return null

        var bestId: String? = null
        var bestDist = Int.MAX_VALUE
        for (token in tokens) {
            for ((id, word) in stationWords) {
                if (abs(word.length - token.length) > 3) continue
                val dist = editDistance(token, word)
                val maxLen = maxOf(token.length, word.length)
                val accept = dist <= 2 || (dist == 3 && token[0] == word[0] && maxLen >= 6)
                if (accept && dist < bestDist) {
                    bestDist = dist
                    bestId = id
                }
            }
        }
        return bestId
    }

    /** Folded station-name words (4+ chars, excluding vocabulary words) for fuzzy. */
    private val stationWords: List<Pair<String, String>> by lazy {
        buildList {
            for (st in vocabulary.stations) {
                val seen = HashSet<String>()
                for (name in st.names) {
                    for (word in fold(name).split(' ')) {
                        if (word.length >= 4 && word !in STOPWORDS && seen.add(word)) {
                            add(st.id to word)
                        }
                    }
                }
            }
        }
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

    // MARK: - Time expressions

    /**
     * Fingerprint of a time anchor pulled out of the user's utterance.
     * Absolute minutes: 24h-clock Athens local ("at 21:30" -> 1290).
     * Relative minutes: minutes from now ("in 45 minutes" -> 45).
     * Exactly one is non-null; caller decides which to use.
     */
    internal data class TargetTime(val absoluteMinutes: Int?, val relativeMinutes: Int?)

    /**
     * Scan the folded text for a time anchor. Recognised trilingually:
     *  - "at 21:30" / "by 21:30" / "στις 21:30" / "deri 21:30"
     *  - "at 9 pm" / "at 9pm" / "9 μμ"
     *  - "in 45 minutes" / "σε 45 λεπτά" / "për 45 minuta"
     *  - "in 1 hour" / "σε 1 ώρα" / "për 1 orë"
     */
    private fun extractTargetTime(text: String): TargetTime? {
        // Absolute HH:MM
        val hhmm = Regex("""(\d{1,2})[:.](\d{2})""").find(text)
        if (hhmm != null) {
            val h = hhmm.groupValues[1].toInt()
            val m = hhmm.groupValues[2].toInt()
            if (h in 0..23 && m in 0..59) {
                return TargetTime(absoluteMinutes = h * 60 + m, relativeMinutes = null)
            }
        }

        // Absolute H am/pm/μμ/πμ (no minutes)
        val meridian = Regex("""(\d{1,2})\s*(am|pm|μμ|πμ)""").find(text)
        if (meridian != null) {
            var h = meridian.groupValues[1].toInt()
            val mark = meridian.groupValues[2]
            if (h in 1..12) {
                if (mark == "pm" || mark == "μμ") { if (h < 12) h += 12 }
                else if (mark == "am" || mark == "πμ") { if (h == 12) h = 0 }
                return TargetTime(absoluteMinutes = h * 60, relativeMinutes = null)
            }
        }

        // Relative: "in N min" / "σε N λεπτά" / "për N minuta"
        val relMin = Regex("""(\d+)\s*(min|minute|minutes|λεπτ|minut)""").find(text)
        if (relMin != null) {
            val n = relMin.groupValues[1].toInt()
            if (n in 1..(24 * 60)) return TargetTime(absoluteMinutes = null, relativeMinutes = n)
        }
        // Relative: "in N hour(s)" / "σε N ώρες" / "për N orë"
        val relHr = Regex("""(\d+)\s*(hour|hours|hr|h |ωρα|ωρε|ore |orë)""").find(text)
        if (relHr != null) {
            val n = relHr.groupValues[1].toInt()
            if (n in 1..12) return TargetTime(absoluteMinutes = null, relativeMinutes = n * 60)
        }

        return null
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
        private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

        /**
         * Optimal string alignment (Damerau-Levenshtein with adjacent
         * transpositions), so a swap like "nkiea" counts as a single edit.
         */
        fun editDistance(a: String, b: String): Int {
            val al = a.length
            val bl = b.length
            if (al == 0) return bl
            if (bl == 0) return al
            val d = Array(al + 1) { IntArray(bl + 1) }
            for (i in 0..al) d[i][0] = i
            for (j in 0..bl) d[0][j] = j
            for (i in 1..al) {
                for (j in 1..bl) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                        d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                    }
                }
            }
            return d[al][bl]
        }

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
            "how do i go", "how to go", "go to", "can i go", "can i still", "can i reach",
            "πως πα", "πως πη", "πως φτα", "διαδρομη", "για να πα", "προλαβαινω", "μπορω να παω",
            "si shkoj", "si te shkoj", "rruga", "udhetim", "a mund te shkoj", "a arrij",
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
        private val FARE_WORDS = listOf(
            "fare", "fares", "ticket", "tickets", "how much", "price", "cost", "cheap",
            "εισιτηρι", "ποσο κανει", "ποσο κοστιζει", "τιμη", "κοστος",
            "bilete", "sa kushton", "kushton", "cmim", "cmimi",
        )
        private val FAVORITE_WORDS = listOf(
            "favorite", "favourite", "save this", "bookmark", "add to favorites", "pin",
            "αγαπημεν", "αποθηκευσ", "προσθεσε στα αγαπημενα", "σημειωσε",
            "i preferuar", "te preferuarat", "ruaj", "shto te te preferuarat",
        )
        private val AIRPORT_WORDS = listOf(
            "airport", "αεροδρομιο", "aeroport",
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
        // Duration / ETA cues. Folded (accent-stripped, lowercase) at match
        // time, so Greek tonos and Latin accents converge.
        private val TIME_PHRASES = listOf(
            "how long", "how many minutes", "how many hours", "how long does it take",
            "how long to get", "how much time", "minutes away", "how far",
            "ποση ωρα", "ποσα λεπτα", "ποσες ωρες", "ποσο θελει", "ποση ωρα κανει",
            "sa gjate", "sa minuta", "sa ore", "sa larg", "sa kohe",
        )
        // Station operational-status cues. "closed"/"κλειστ"/"mbyll" overlap
        // ALERT_WORDS on purpose: with a named station they mean "is this stop
        // open?", which the status branch (ordered first) handles.
        private val STATION_STATUS_WORDS = listOf(
            "open", "working", "operating", "operational", "running", "closed", "shut",
            "ανοιχτ", "λειτουργει", "δουλευει", "κλειστ", "ανοιξ",
            "hapur", "punon", "funksionon", "mbyllur", "mbyll",
        )
        // Words meaning "station", so "is the station open?" resolves even
        // before the specific stop is named.
        private val STATION_NOUN_WORDS = listOf(
            "station", "σταθμ", "stacion",
        )
        // "I'm at / here / got off" context-set cues. Kept to multi-word or
        // distinctive tokens so a lone "in"/"on" can't trigger a location set.
        private val LOCATION_PHRASES = listOf(
            "i'm at", "im at", "i am at", "i'm in", "im in", "i'm on", "im on",
            "i'm here", "im here", "i am here", "i'm there", "im there", "i am there",
            "i reached", "i just reached", "i arrived", "i got off", "i just got off",
            "currently at", "i'm inside", "im inside",
            "ειμαι στο", "ειμαι στη", "ειμαι στον", "ειμαι εδω", "ειμαι μεσα",
            "εφτασα", "μολις εφτασα", "κατεβηκα", "μολις κατεβηκα",
            "jam te", "jam ne", "jam ketu", "jam brenda", "arrita", "zbrita", "sapo zbrita",
        )
        private val TOMORROW_WORDS = listOf("tomorrow", "αυριο", "neser")
        private val WEEKEND_WORDS = listOf("weekend", "σαββατοκυριακο", "fundjave")
        private val SATURDAY_WORDS = listOf("saturday", "σαββατο", "te shtune", "shtune")
        private val SUNDAY_WORDS = listOf("sunday", "κυριακη", "te diel", "diel")

        // Easter egg triggers. Substring match on folded text — "Liepuras",
        // "λιεπουρας", "Λιεπ", "liepurashi" all resolve. Kept as substrings
        // (not word-boundary) so silly variants still fire.
        private val LIEPUR_TRIGGERS = listOf("liepur", "λιεπ")

        // Single-word vocabulary tokens the fuzzy matcher must never "correct"
        // into a station (so "trains" stays a departures cue, not a stop).
        private val STOPWORDS: Set<String> = (
            TRANSIT_NOUNS + DEPARTURE_WORDS + FIND_WORDS + LINE_WORDS + FARE_WORDS +
                FAVORITE_WORDS + AIRPORT_WORDS + ALERT_WORDS + MAP_WORDS + WEATHER_WORDS +
                TOMORROW_WORDS + WEEKEND_WORDS + SATURDAY_WORDS + SUNDAY_WORDS
            ).map { fold(it) }.filter { it.length >= 4 && !it.contains(' ') }.toSet()
    }
}
