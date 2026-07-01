package com.syrmos.core.domain.assistant

/**
 * Optional on-device LLM front-end seam for Ariadne on the KMP platforms,
 * mirroring the iOS Foundation Models path. An implementation must ONLY rewrite
 * fuzzy natural language into clean text ("trains aprt sat nite" ->
 * "trains to airport saturday night"); the deterministic [AthensTransitParser]
 * still classifies and validates the result, so the model can never invent a
 * transit fact.
 *
 * The default is a no-op, so Android and Web run the rule parser directly with
 * no behaviour change. An Android build can supply a Gemini Nano / ML Kit GenAI
 * backed normalizer here once a stable on-device prompting API is available.
 */
fun interface AssistantQueryNormalizer {
    /** Cleaned query, or null to use the raw text. */
    suspend fun normalize(input: String): String?
}

object NoOpQueryNormalizer : AssistantQueryNormalizer {
    override suspend fun normalize(input: String): String? = null
}
