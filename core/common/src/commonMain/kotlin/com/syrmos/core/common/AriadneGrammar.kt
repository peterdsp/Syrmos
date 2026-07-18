package com.syrmos.core.common

/**
 * The GBNF grammar that constrains Ariadne's on-device model to emit exactly the
 * flat intent JSON that IntentGrounder.ground() parses. Shared by the native
 * llama.cpp backends (Android JNI, iOS) so every platform locks the model to the
 * same schema; the model can only choose an approved intent and quote free-text
 * slots, never an id, a time, a fare, or prose. Kept in sync with the web copy
 * at composeApp/src/wasmJsMain/resources/llm/ariadne-grammar.gbnf.
 */
object AriadneGrammar {
    const val GBNF: String = """root ::= "{\"intent\":" intent ",\"station\":" str ",\"toStation\":" str ",\"line\":" str ",\"query\":" str ",\"airport\":" bool ",\"lowExposure\":" bool ",\"day\":" day ",\"arriveByClock\":" str ",\"arriveInMinutes\":" int "}"
intent ::= "\"showDepartures\"" | "\"lastTrain\"" | "\"firstTrain\"" | "\"stationAccessibility\"" | "\"reverseTrip\"" | "\"findStation\"" | "\"planTrip\"" | "\"planTripByArrival\"" | "\"travelTime\"" | "\"explainLine\"" | "\"explainFare\"" | "\"showAlerts\"" | "\"weatherAt\"" | "\"help\"" | "\"outOfScope\""
day ::= "\"today\"" | "\"tomorrow\"" | "\"weekend\"" | "\"saturday\"" | "\"sunday\""
bool ::= "true" | "false"
int ::= "0" | [1-9] [0-9]{0,3}
str ::= "\"" schar{0,40} "\""
schar ::= [a-zA-Z0-9 .,:/-]
"""
}
