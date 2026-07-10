package com.syrmos.app.platform

import com.syrmos.core.common.AriadneGrammar
import com.syrmos.core.domain.assistant.AssistantClassifier
import com.syrmos.core.domain.assistant.AssistantIntent
import com.syrmos.core.domain.assistant.AssistantVocabulary
import com.syrmos.core.domain.assistant.IntentGrounder
import com.syrmos.llm.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

actual fun provideAssistantClassifier(): AssistantClassifier = LlamaAssistantClassifier

/**
 * Android "clever" tier: the same GGUF model that runs on iOS/Web, executed on
 * device by a pinned llama.cpp (via [LlamaBridge]). It does the UNDERSTANDING
 * step only, grammar-locked to the intent JSON; [IntentGrounder] resolves the
 * quoted station/line to canonical ids (the model never supplies an id or a
 * fact) and the deterministic dispatch produces every answer.
 *
 * It only acts when the native libs loaded AND the user has downloaded the model
 * ([AriadneModelStore]); otherwise [classify] returns null and the caller runs
 * the rule parser. It never blocks a query on the multi-hundred-MB load: the
 * first call after the model is ready warms it up and returns null, and later
 * calls run the model.
 */
private object LlamaAssistantClassifier : AssistantClassifier {
    private val mutex = Mutex()
    @Volatile private var handle: Long = 0L
    @Volatile private var loading = false

    override suspend fun classify(input: String, vocabulary: AssistantVocabulary): AssistantIntent? {
        if (!LlamaBridge.available || !AriadneModelStore.isReady()) return null
        if (!ensureLoaded()) return null
        return withContext(Dispatchers.Default) {
            val prompt = IntentGrounder.classificationPrompt(input)
            val json = try {
                LlamaBridge.nativeComplete(handle, prompt, 160, AriadneGrammar.GBNF)
            } catch (_: Throwable) {
                return@withContext null
            }
            if (json.isBlank()) null else IntentGrounder.ground(json, vocabulary)
        }
    }

    /** Loads the model once, off the calling path. Returns true only when a
     *  handle is ready now; the warm-up call returns false (rule parser answers). */
    private suspend fun ensureLoaded(): Boolean {
        if (handle != 0L) return true
        if (loading) return false
        return mutex.withLock {
            if (handle != 0L) return@withLock true
            loading = true
            val path = AriadneModelStore.modelFile()?.absolutePath
            val h = if (path != null) withContext(Dispatchers.IO) {
                try { LlamaBridge.nativeLoadModel(path, 1024) } catch (_: Throwable) { 0L }
            } else 0L
            handle = h
            loading = false
            h != 0L
        }
    }
}
