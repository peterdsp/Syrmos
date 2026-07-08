package com.syrmos.core.domain.assistant

import com.syrmos.core.model.transit.Direction

/**
 * The small amount of conversation memory that lets Ariadne feel like a co-pilot
 * instead of a stateless Q&A box. It is NOT chat memory in the LLM sense: just
 * the handful of facts a transit companion needs so follow-ups don't re-ask what
 * the user already said.
 *
 * "I'm at Syntagma" sets [currentStation]; a later "go airport faster" then needs
 * no "from where?" because the origin is already known. "I'm on M3 towards the
 * airport" sets [currentLine] / [currentDirection]. [lastDestination],
 * [lastRoute], and [lastIntent] let "how do I go faster?" refer back to the trip
 * just discussed.
 *
 * Everything is nullable and immutable; the ViewModel replaces the whole context
 * with [with] on each turn so state is easy to reason about and to mirror on iOS.
 */
data class AssistantSessionContext(
    val currentStation: String? = null,
    val currentLine: String? = null,
    val currentDirection: Direction? = null,
    val lastDestination: String? = null,
    val lastRoute: RouteMemory? = null,
    val lastIntent: AssistantIntent? = null,
) {
    fun withCurrentStation(stationId: String?): AssistantSessionContext =
        copy(currentStation = stationId)

    fun withLine(lineId: String?, direction: Direction? = currentDirection): AssistantSessionContext =
        copy(currentLine = lineId, currentDirection = direction)

    fun remembering(intent: AssistantIntent, destination: String? = lastDestination, route: RouteMemory? = lastRoute): AssistantSessionContext =
        copy(lastIntent = intent, lastDestination = destination, lastRoute = route)

    /** The best-known origin for a trip the user didn't fully specify. */
    fun originOr(explicit: String?): String? = explicit ?: currentStation

    companion object {
        val EMPTY = AssistantSessionContext()
    }
}

/**
 * Just enough of the last route to answer a follow-up ("faster?") without
 * recomputing from scratch or re-asking endpoints. The heavy JourneyResult
 * stays in the use-case layer; this is the conversational breadcrumb.
 */
data class RouteMemory(
    val fromStationId: String,
    val toStationId: String,
    val preference: RoutePreference = RoutePreference.BALANCED,
    val totalMinutes: Int? = null,
    val transferCount: Int? = null,
)
