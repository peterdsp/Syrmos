package com.syrmos.app.platform

import com.syrmos.core.common.AriadneModelManifest
import com.syrmos.core.domain.assistant.AssistantClassifier
import com.syrmos.core.domain.assistant.AssistantIntent
import com.syrmos.core.domain.assistant.AssistantVocabulary
import com.syrmos.core.domain.assistant.IntentGrounder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise

// Web "clever" tier: the same GGUF model that runs on iOS/Android, executed in
// the browser by wllama (llama.cpp compiled to WASM). The heavy lifting lives in
// resources/llm/ariadne-wllama.js (window.AriadneLLM); this actual just hands it
// the prompt and grounds the JSON it returns.
//
// The model is large, so the first call only warms it up and returns '' (the
// bridge caches the weights and reports 'ready' later); IntentGrounder.ground()
// turns '' into null so the caller runs the deterministic rule parser meanwhile.
// Nothing blocks on the multi-hundred-MB load.
@JsFun("(base, model, prompt) => window.AriadneLLM.classify(base, model, prompt)")
private external fun jsClassify(base: JsString, model: JsString, prompt: JsString): Promise<JsString>

// All web LLM assets are served under /llm/ (see wasmJsMain/resources/llm and the
// CI web-staging step that drops the model there).
private const val LLM_BASE = "llm/"

actual fun provideAssistantClassifier(): AssistantClassifier = WllamaAssistantClassifier

private object WllamaAssistantClassifier : AssistantClassifier {
    override suspend fun classify(input: String, vocabulary: AssistantVocabulary): AssistantIntent? {
        val prompt = IntentGrounder.classificationPrompt(input)
        val json = try {
            jsClassify(LLM_BASE.toJsString(), AriadneModelManifest.FILE_NAME.toJsString(), prompt.toJsString())
                .awaitJs()
                .toString()
        } catch (_: Throwable) {
            return null
        }
        return IntentGrounder.ground(json, vocabulary)
    }
}

private suspend fun <T : JsAny> Promise<T>.awaitJs(): T = suspendCoroutine { cont ->
    then(
        onFulfilled = { value: T ->
            cont.resume(value)
            value
        },
        onRejected = { error: JsAny ->
            cont.resumeWithException(Exception(error.toString()))
            error
        },
    )
}
