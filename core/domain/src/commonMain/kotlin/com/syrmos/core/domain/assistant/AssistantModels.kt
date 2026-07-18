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

    /**
     * The first / earliest train of the day at a station, optionally on a line.
     * The mirror of [LastTrain]: "when does service start", "first metro",
     * "πρώτο τρένο", "treni i parë". Answered deterministically from the
     * earliest scheduled departure, never estimated.
     */
    data class FirstTrain(
        val stationId: String?,
        val lineId: String?,
    ) : AssistantIntent

    /**
     * "Is X accessible / step-free / wheelchair friendly?" / "does X have a
     * lift?". Answered from the bundled per-station accessibility flag, never
     * invented. Null [stationId] asks which station.
     */
    data class StationAccessibility(val stationId: String?) : AssistantIntent

    /**
     * A context-only follow-up: "and back?" / "return" / "the other way" /
     * "και πίσω" / "kthimi". Ariadne has no slots to fill here; the resolver
     * reverses [AssistantSessionContext.lastRoute] (swaps origin/destination)
     * and re-plans. With no remembered route it asks for a trip first.
     */
    data object ReverseTrip : AssistantIntent

    /** Free-text station lookup. */
    data class FindStation(val query: String) : AssistantIntent

    /** Point-to-point routing across the network. */
    data class PlanTrip(
        val fromStationId: String?,
        val toStationId: String?,
        val lowExposure: Boolean = false,
        val preference: RoutePreference = RoutePreference.BALANCED,
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

    /**
     * "Is X open / working / closed?". Operational status for one station.
     * Ariadne has no live per-station status feed, so the honest answer leads
     * with any matching STASY advisory (via [ServiceAdvisoryMatcher]); absent
     * one, it falls back to the timetable and says so, never asserting "open".
     */
    data class StationStatus(val stationId: String?) : AssistantIntent

    /**
     * "I'm at X" / "I'm here" / "I got off at X". Pure context-set: records the
     * user's current station in [AssistantSessionContext] so later follow-ups
     * ("go airport faster") need no "from where?". [stationId] is null for a
     * bare "I'm here", which the resolver anchors to GPS / last known station.
     */
    data class SetCurrentLocation(val stationId: String?) : AssistantIntent

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
     * Direct weather query for a station or the current location. Falls
     * back to central Athens when [stationId] is null AND the caller has
     * no location. Ariadne answers from the WeatherRepository cache when
     * available so this stays offline-safe.
     */
    data class WeatherAt(val stationId: String?) : AssistantIntent

    /**
     * "I need to be at X by 21:30" — plan a trip backwards from a target
     * arrival time. The target is expressed as Athens-local minutes from
     * midnight, or as minutes from now when the user said "in 45 min".
     * Handler subtracts the estimated trip duration to suggest the latest
     * reasonable departure, and flags "tight" vs "impossible".
     */
    data class PlanTripByArrival(
        val fromStationId: String?,
        val toStationId: String?,
        val arriveByAthensMinutes: Int?,   // e.g. 21*60+30 = 1290
        val inMinutesFromNow: Int?,
    ) : AssistantIntent

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
