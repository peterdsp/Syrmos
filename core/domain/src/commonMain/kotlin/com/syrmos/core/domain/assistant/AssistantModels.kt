package com.syrmos.core.domain.assistant

/**
 * Ariadne, the offline Athens transit assistant.
 *
 * Named for Ariadne's thread, the guide out of the labyrinth. The contract is
 * deliberately narrow: Ariadne is an intent router, not a chatbot. Natural
 * language is parsed into one of a fixed set of [AssistantIntent] values and
 * dispatched to the deterministic use cases the app already ships. The parser
 * never invents a transit fact. It chooses an approved action and fills its
 * slots; the projector, planner, and repositories produce the answer.
 *
 * Everything here is pure and offline. Greek, Albanian and English are all
 * first-class: the rule parser ([AthensTransitParser]) handles all three so no
 * supported language silently degrades, which is the floor an on-device model
 * (Apple Foundation Models / Gemini Nano) can later sit on top of.
 */
sealed interface AssistantIntent {
    /** Next departures from a station, optionally filtered to a line / day. */
    data class ShowDepartures(
        val stationId: String?,
        val lineId: String?,
        val day: DayContext = DayContext.TODAY,
    ) : AssistantIntent

    /** Tonight's last train on a line at a station. */
    data class LastTrain(
        val stationId: String?,
        val lineId: String?,
    ) : AssistantIntent

    /** Free-text station lookup. */
    data class FindStation(val query: String) : AssistantIntent

    /** Point-to-point routing across the network. */
    data class PlanTrip(
        val fromStationId: String?,
        val toStationId: String?,
        val lowExposure: Boolean = false,
    ) : AssistantIntent

    /**
     * "How long / how many minutes to reach X". Duration of a trip whose origin
     * defaults to the user's current location (resolved by the caller via GPS →
     * nearest station). [fromStationId] is only set when the user named an
     * explicit origin; otherwise the caller uses location or asks for it.
     */
    data class TravelTime(
        val toStationId: String?,
        val fromStationId: String? = null,
    ) : AssistantIntent

    /** Line overview (terminals, span, stations). */
    data class ExplainLine(val lineId: String) : AssistantIntent

    /**
     * Ticket prices. [airport] is the keyword hint; [fromStationId]/[toStationId]
     * let the resolver derive an airport fare from the actual journey (either
     * endpoint being an airport station) rather than the word alone.
     */
    data class ExplainFare(
        val airport: Boolean = false,
        val fromStationId: String? = null,
        val toStationId: String? = null,
    ) : AssistantIntent

    /** Add/remove a station from favorites. */
    data class ToggleFavorite(val stationId: String?) : AssistantIntent

    /** Current service alerts / status, optionally for one line. */
    data class ShowAlerts(val lineId: String? = null) : AssistantIntent

    /** Open the map, optionally focused on a station. */
    data class OpenMap(val stationId: String? = null) : AssistantIntent

    /** "What can you do", app usage help. */
    data object Help : AssistantIntent

    /**
     * In scope (mentions transit) but a required slot is missing, so Ariadne
     * must ask one focused question before it can act.
     */
    data class NeedsClarification(
        val base: AssistantIntent,
        val missing: MissingSlot,
    ) : AssistantIntent

    /** Outside Athens transit and the app. Ariadne politely declines. */
    data object OutOfScope : AssistantIntent

    /**
     * Easter egg: someone said "liepur" / "λιεπ" / "liepuras" or a close
     * variant. Ariadne answers with a random cat joke. Deliberately opaque
     * from the outside — the trigger words aren't documented anywhere the
     * user could stumble onto them.
     */
    data object EasterEggLiepur : AssistantIntent
}

enum class DayContext { TODAY, TOMORROW, WEEKEND, SATURDAY, SUNDAY }

enum class MissingSlot { ORIGIN_STATION, DESTINATION_STATION, STATION }

/** What Ariadne understands about the network, supplied by the app's repos. */
data class AssistantVocabulary(
    val stations: List<StationVocab>,
    val lines: List<LineVocab>,
)

data class StationVocab(
    val id: String,
    /** Display names in every language plus any aliases, used for matching. */
    val names: List<String>,
    val lineIds: List<String>,
)

data class LineVocab(
    val id: String,
    /** "m2", "line 2", "γραμμή 2", "metro 2", terminals, "airport", etc. */
    val aliases: List<String>,
)
