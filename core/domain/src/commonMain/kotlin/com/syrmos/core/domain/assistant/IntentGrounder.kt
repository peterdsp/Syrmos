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
     * The prompt for the clever model. It demands strict JSON only, mirroring the
     * pack's ariadne_intent_schema.json flattened to one object, and forbids the
     * model from producing any fact.
     */
    fun classificationPrompt(input: String): String = """
        You are Ariadne, the offline assistant inside Syrmos for Athens metro, tram, and suburban rail.
        You are NOT the source of transit facts. Read the user's message and output ONLY a single JSON
        object, no prose, no code fence, with exactly these fields:
        {"intent":"<one of: showDepartures,lastTrain,findStation,planTrip,planTripByArrival,travelTime,explainLine,explainFare,showAlerts,weatherAt,help,outOfScope>",
         "station":"<origin/primary station exactly as the user said, or empty>",
         "toStation":"<destination station exactly as the user said, or empty>",
         "line":"<line like M2 or line 3 or tram, or empty>",
         "query":"<free-text station search when intent is findStation, or empty>",
         "airport":<true only if clearly about the airport>,
         "lowExposure":<true if the user wants a sheltered route>,
         "day":"<one of: today,tomorrow,weekend,saturday,sunday>",
         "arriveByClock":"<HH:mm in Athens time when the user must arrive by a time, or empty>",
         "arriveInMinutes":<minutes-from-now the user must arrive within, or 0>}
        Never output a station id, a time, a fare, or a route. If the message is not about Athens public
        transport, use outOfScope. If they ask what you can do, use help.
        User: ${input.trim()}
        JSON:
    """.trimIndent()
}
