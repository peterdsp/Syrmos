package com.syrmos.core.domain.assistant

/**
 * Optional on-device LLM classifier seam for Ariadne on the KMP platforms, the
 * "clever" tier that mirrors the iOS Foundation Models path (AriadneGuided).
 * An implementation lets a capable device's model (Gemini Nano via ML Kit GenAI
 * on Android) do the UNDERSTANDING step: it classifies the user's message into
 * one approved [AssistantIntent] and quotes the station / line the user named.
 *
 * Grounding is preserved exactly: the model only picks an intent and quotes
 * text; [IntentGrounder] resolves those quotes to canonical ids against the
 * bundled vocabulary (never trusting the model for an id or a fact), and the
 * existing deterministic dispatch produces every answer. When no clever model is
 * present the default is a no-op and the caller runs the rule parser, so nothing
 * degrades on dummy devices, iOS (which uses its own native path), or web.
 */
fun interface AssistantClassifier {
    /**
     * A grounded intent for [input], or null to fall back to the rule parser.
     * [vocabulary] is used to resolve the station / line the model quoted.
     */
    suspend fun classify(input: String, vocabulary: AssistantVocabulary): AssistantIntent?
}

object NoOpAssistantClassifier : AssistantClassifier {
    override suspend fun classify(input: String, vocabulary: AssistantVocabulary): AssistantIntent? = null
}
