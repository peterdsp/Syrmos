package com.syrmos.app.platform

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.syrmos.core.domain.assistant.AssistantClassifier
import com.syrmos.core.domain.assistant.AssistantVocabulary
import com.syrmos.core.domain.assistant.AssistantIntent
import com.syrmos.core.domain.assistant.IntentGrounder
import com.syrmos.core.domain.assistant.NoOpAssistantClassifier

actual fun provideAssistantClassifier(): AssistantClassifier = AndroidGeminiNanoClassifier

/**
 * Clever-Android tier: Gemini Nano via ML Kit GenAI Prompt API. It runs the
 * pack's classification prompt on-device, gets back a small JSON object, and
 * hands it to [IntentGrounder], which resolves the quoted station / line to
 * canonical ids (the model never supplies an id or a fact) and produces a
 * grounded [AssistantIntent] for the existing deterministic dispatch.
 *
 * It only acts when Gemini Nano is already available on the device (a capable
 * device with the feature downloaded); otherwise [classify] returns null and the
 * caller runs the deterministic rule parser. It never throws into the caller and
 * never blocks a query on a multi-gigabyte model download.
 */
private object AndroidGeminiNanoClassifier : AssistantClassifier {
    private val model: GenerativeModel by lazy { Generation.getClient() }

    override suspend fun classify(input: String, vocabulary: AssistantVocabulary): AssistantIntent? {
        val text = input.trim()
        if (text.isEmpty()) return null
        return try {
            // checkStatus() and generateContent() are suspend functions on the
            // GenerativeModel interface (they take a Continuation), so they are
            // awaited directly here — no ListenableFuture bridging.
            if (model.checkStatus() != FeatureStatus.AVAILABLE) return null
            val response = model.generateContent(IntentGrounder.classificationPrompt(text))
            val json = response.candidates.firstOrNull()?.text ?: return null
            IntentGrounder.ground(json, vocabulary)
        } catch (_: Throwable) {
            null
        }
    }
}
