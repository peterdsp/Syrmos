package com.syrmos.app.platform

import android.content.Context
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.proofreading.Proofreader
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import com.syrmos.core.domain.assistant.AssistantQueryNormalizer
import com.syrmos.core.domain.assistant.NoOpQueryNormalizer
import kotlinx.coroutines.guava.await

actual fun provideQueryNormalizer(): AssistantQueryNormalizer {
    val context = androidPlatformContext() ?: return NoOpQueryNormalizer
    return AndroidProofreadingNormalizer(context)
}

/**
 * On-device text proofreading (Gemini Nano via ML Kit GenAI / AICore). Cleans
 * spelling and grammar before Ariadne's deterministic parser runs. It only acts
 * when the model is already downloaded on the device (a capable device with the
 * feature present); on every other device [normalize] returns null and the
 * parser's fuzzy matcher handles the raw text. It never throws into the caller
 * and never blocks a query on a multi-megabyte model download.
 */
private class AndroidProofreadingNormalizer(context: Context) : AssistantQueryNormalizer {
    private val proofreader: Proofreader = Proofreading.getClient(
        ProofreaderOptions.builder(context)
            .setInputType(ProofreaderOptions.InputType.KEYBOARD)
            .setLanguage(ProofreaderOptions.Language.ENGLISH)
            .build(),
    )

    override suspend fun normalize(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        return try {
            if (proofreader.checkFeatureStatus().await() != FeatureStatus.AVAILABLE) return null
            val request = ProofreadingRequest.builder(text).build()
            val result = proofreader.runInference(request).await()
            result.results.firstOrNull()?.text?.takeIf { it.isNotBlank() && it != text }
        } catch (_: Throwable) {
            null
        }
    }
}
