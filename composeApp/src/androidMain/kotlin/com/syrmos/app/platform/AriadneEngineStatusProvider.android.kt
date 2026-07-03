package com.syrmos.app.platform

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.Proofreading
import com.syrmos.core.common.AriadneEngineStatus
import kotlinx.coroutines.guava.await

/**
 * Maps the ML Kit GenAI proofreading feature status (Gemini Nano via AICore) to
 * the shared [AriadneEngineStatus]. This is a read-only diagnostic: it never
 * downloads the model and never blocks Ariadne, which always has the rule parser
 * as its floor.
 *
 * ML Kit reports UNAVAILABLE both for devices without AICore and for ineligible
 * hardware, so we can't reliably split AICORE_MISSING from DEVICE_NOT_ELIGIBLE
 * here; we report the honest DEVICE_NOT_ELIGIBLE.
 */
actual suspend fun provideAriadneEngineStatus(): AriadneEngineStatus {
    val context = androidPlatformContext() ?: return AriadneEngineStatus.RULE_PARSER
    return try {
        val proofreader = Proofreading.getClient(
            ProofreaderOptions.builder(context)
                .setInputType(ProofreaderOptions.InputType.KEYBOARD)
                .setLanguage(ProofreaderOptions.Language.ENGLISH)
                .build(),
        )
        val status = proofreader.checkFeatureStatus().await()
        proofreader.close()
        when (status) {
            FeatureStatus.AVAILABLE -> AriadneEngineStatus.AVAILABLE
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> AriadneEngineStatus.MODEL_NOT_DOWNLOADED
            else -> AriadneEngineStatus.DEVICE_NOT_ELIGIBLE
        }
    } catch (_: Throwable) {
        // No AICore APK / feature not present on this device.
        AriadneEngineStatus.AICORE_MISSING
    }
}
