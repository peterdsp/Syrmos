package com.syrmos.core.domain.assistant

/**
 * Turns a clever model's structured guess into a grounded [AssistantIntent].
 *
 * The model is prompted (see [classificationPrompt]) to emit a small JSON object
 * naming one intent and quoting the station / line the user mentioned. This
 * object parses that JSON without a serialization dependency and resolves the
 * quoted station / line to canonical ids against the bundled vocabulary using
 * the SAME accent-fold as the rule parser, so "sintagma" and "Σύνταγμα" both
 * land on Syntagma. The model never supplies an id, time, fare, or route; when a
 * required slot can't be grounded, this returns null so the caller falls back to
 * the rule parser rather than acting on a guess.
 *
 * Shared by every KMP clever backend (Android Gemini Nano today) and mirrored by
 * the iOS AriadneGuided.ground().
 */
object IntentGrounder {

    /** Parse [json] emitted by the model into a grounded intent, or null. */
    fun ground(json: String, vocabulary: AssistantVocabulary): AssistantIntent? {
        val kind = str(json, "intent").lowercase().trim()
        if (kind.isEmpty()) return null
        val station = resolveStation(str(json, "station"), vocabulary)
        val toStation = resolveStation(str(json, "toStation"), vocabulary)
        val line = resolveLine(str(json, "line"), vocabulary)
        val day = mapDay(str(json, "day"))

        return when (kind) {
            "showdepartures" -> AssistantIntent.ShowDepartures(station, line, day)
            "lasttrain" -> AssistantIntent.LastTrain(station, line)
            "firsttrain" -> AssistantIntent.FirstTrain(station, line)
            "stationaccessibility" -> station?.let { AssistantIntent.StationAccessibility(it) }
            "reversetrip" -> AssistantIntent.ReverseTrip
            "whichlines" -> station?.let { AssistantIntent.WhichLines(it) }
            "stopsbetween" -> {
                if (station == null && toStation == null) null
                else AssistantIntent.StopsBetween(station, toStation)
            }
            "findstation" -> {
                val q = str(json, "query").ifBlank { str(json, "station") }.trim()
                if (q.isEmpty()) null else AssistantIntent.FindStation(q)
            }
            "plantrip" -> {
                if (station == null && toStation == null) null
                else AssistantIntent.PlanTrip(station, toStation, bool(json, "lowExposure"))
            }
            "plantripbyarrival" -> {
                val abs = clockToAthensMinutes(str(json, "arriveByClock"))
                val rel = int(json, "arriveInMinutes").takeIf { it > 0 }
                if (abs == null && rel == null) null
                else AssistantIntent.PlanTripByArrival(station, toStation, abs, rel)
            }
            "traveltime" -> AssistantIntent.TravelTime(
                toStationId = toStation ?: station,
                fromStationId = if (toStation == null) null else station,
            )
            "explainline" -> line?.let { AssistantIntent.ExplainLine(it) }
            "explainfare" -> AssistantIntent.ExplainFare(bool(json, "airport"), station, toStation)
            "showalerts" -> AssistantIntent.ShowAlerts(line)
            "weatherat" -> AssistantIntent.WeatherAt(station)
            "wrongtrain" -> AssistantIntent.WrongTrain(station, line)
            "missedstop" -> AssistantIntent.MissedStop(station, toStation)
            "canistillmakeit" -> AssistantIntent.CanIStillMakeIt(toStation, station)
            "help" -> AssistantIntent.Help
            "outofscope" -> AssistantIntent.OutOfScope
            else -> null
        }
    }

    /** Resolve a free-text station mention to a bundled station id, or null. */
    fun resolveStation(text: String, vocabulary: AssistantVocabulary): String? {
        val needle = AthensTransitParser.fold(text.trim())
        if (needle.length < 3) return null
        var prefixHit: String? = null
        var containsHit: String? = null
        for (st in vocabulary.stations) {
            for (raw in st.names) {
                val name = AthensTransitParser.fold(raw)
                if (name == needle) return st.id
                if (prefixHit == null && (name.startsWith(needle) || needle.startsWith(name))) prefixHit = st.id
                if (containsHit == null && (name.contains(needle) || needle.contains(name))) containsHit = st.id
            }
        }
        return prefixHit ?: containsHit
    }

    /** Resolve a free-text line mention ("M2", "line 3", "tram") to a line id. */
    fun resolveLine(text: String, vocabulary: AssistantVocabulary): String? {
        val needle = AthensTransitParser.fold(text.trim())
        if (needle.isEmpty()) return null
        vocabulary.lines.firstOrNull { line -> line.aliases.any { AthensTransitParser.fold(it) == needle } }
            ?.let { return it.id }
        return vocabulary.lines.firstOrNull { line ->
            line.aliases.any { val a = AthensTransitParser.fold(it); a.isNotEmpty() && needle.contains(a) }
        }?.id
    }

    /** "21:30" -> Athens minutes-of-day, or null when not a valid clock. */
    fun clockToAthensMinutes(clock: String): Int? {
        val parts = clock.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun mapDay(day: String): DayContext = when (day.lowercase().trim()) {
        "tomorrow" -> DayContext.TOMORROW
        "weekend" -> DayContext.WEEKEND
        "saturday" -> DayContext.SATURDAY
        "sunday" -> DayContext.SUNDAY
        else -> DayContext.TODAY
    }

    // --- Minimal JSON field extraction (no serialization dependency) ---

    private fun str(json: String, key: String): String {
        val re = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val raw = re.find(json)?.groupValues?.get(1) ?: return ""
        return raw.replace("\\\"", "\"").replace("\\\\", "\\").trim()
    }

    private fun bool(json: String, key: String): Boolean =
        Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*(true|false)").find(json)?.groupValues?.get(1) == "true"

    private fun int(json: String, key: String): Int =
        Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*(-?\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /**
     * The prompt for the clever model, designed as a raw few-shot completion (the
     * on-device llama.cpp / wllama backends run this with a GBNF grammar that
     * locks the output to the exact flat JSON below, so the model can only choose
     * an approved intent and quote free-text slots, never emit a fact).
     *
     * The worked examples span English, Greek, and Albanian on purpose: a browser
     * benchmark showed a small model classifying English acceptably but failing
     * Greek and Albanian without in-context examples. Keep all three languages
     * represented here so Albanian and Greek stay first-class.
     */
    fun classificationPrompt(input: String): String = """
        Task: classify a message about Athens metro/tram/suburban rail into ONE intent and quote the stations. Output ONLY the JSON object.
        Intents: showDepartures (next trains from a station), lastTrain (last/final train), firstTrain (first/earliest train of the day), stationAccessibility (is a station step-free / wheelchair / lift), reverseTrip (and back / return the last trip), whichLines (which lines serve a station), stopsBetween (how many stops / how far between two stations), planTrip (how to go from A to B), planTripByArrival (arrive by a time), travelTime (how long), explainFare (ticket price/cost), explainLine (about a line), showAlerts (delays/strikes/closures), findStation (where is a station), weatherAt, help (what can you do), outOfScope (not about Athens transit).
        Fields: intent, station, toStation, line, query, airport(bool), lowExposure(bool), day(today/tomorrow/weekend/saturday/sunday), arriveByClock(HH:mm or empty), arriveInMinutes(int). Never output an id, a time, a fare, or a route.

        Message: next trains from ambelokipi
        JSON: {"intent":"showDepartures","station":"ambelokipi","toStation":"","line":"","query":"","airport":false,"lowExposure":false,"day":"today","arriveByClock":"","arriveInMinutes":0}
        Message: last train from syntagma
        JSON: {"intent":"lastTrain","station":"syntagma","toStation":"","line":"","query":"","airport":false,"lowExposure":false,"day":"today","arriveByClock":"","arriveInMinutes":0}
        Message: πρωτο τρενο απο το μοναστηρακι
        JSON: {"intent":"firstTrain","station":"μοναστηρακι","toStation":"","line":"","query":"","airport":false,"lowExposure":false,"day":"today","arriveByClock":"","arriveInMinutes":0}
        Message: a eshte pireas i aksesueshem
        JSON: {"intent":"stationAccessibility","station":"pireas","toStation":"","line":"","query":"","airport":false,"lowExposure":false,"day":"today","arriveByClock":"","arriveInMinutes":0}
        Message: and back
        JSON: {"intent":"reverseTrip","station":"","toStation":"","line":"","query":"","airport":false,"lowExposure":false,"day":"today","arriveByClock":"","arriveInMinutes":0}
        Message: ποιες γραμμες περνανε απο το syntagma
        JSON: {"intent":"whichLines","station":"syntagma","toStation":"","line":"","query":"","airport":false,"lowExposure":false,"day":"today","arriveByClock":"","arriveInMinutes":0}
        Message: how many stops from monastiraki to piraeus
        JSON: {"intent":"stopsBetween","station":"monastiraki","toStation":"piraeus","line":"","query":"","airport":false,"lowExposure":false,"day":"today","arriveByClock":"","arriveInMinutes":0}
        Message: πως παω απο μοναστηρακι στο αεροδρομιο
        JSON: {"intent":"planTrip","station":"μοναστηρακι","toStation":"αεροδρομιο","line":"","query":"","airport":true,"lowExposure":false,"day":"today","arriveByClock":"","arriveInMinutes":0}
        Message: how much is a ticket to the airport
        JSON: {"intent":"explainFare","station":"","toStation":"airport","line":"","query":"","airport":true,"lowExposure":false,"day":"today","arriveByClock":"","arriveInMinutes":0}
        Message: kur niset treni i fundit per Pire
        JSON: {"intent":"lastTrain","station":"Pire","toStation":"","line":"","query":"","airport":false,"lowExposure":false,"day":"today","arriveByClock":"","arriveInMinutes":0}
        Message: what can you do
        JSON: {"intent":"help","station":"","toStation":"","line":"","query":"","airport":false,"lowExposure":false,"day":"today","arriveByClock":"","arriveInMinutes":0}
        Message: ${input.trim()}
        JSON:
    """.trimIndent()
}
