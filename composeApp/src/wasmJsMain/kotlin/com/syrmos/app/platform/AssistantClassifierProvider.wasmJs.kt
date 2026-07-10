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
// The model (~1.1 GB) is downloaded ON DEMAND after the user opts in, never
// bundled and never auto-pulled, so the web app stays lightweight. Until it is
// ready, classify() returns null and the caller runs the deterministic rule
// parser. Only the small wllama runtime ships with the app.
@JsFun("(prompt) => window.AriadneLLM.classify(prompt)")
private external fun jsClassify(prompt: JsString): Promise<JsString>

@JsFun("(assets, modelUrl) => window.AriadneLLM.download(assets, modelUrl)")
private external fun jsDownload(assets: JsString, modelUrl: JsString)

@JsFun("() => window.AriadneLLM.status()")
private external fun jsStatus(): JsString

// The small wllama runtime (wasm + grammar) is served under /llm/. The model is
// fetched from its pinned manifest URL, not from our origin.
private const val LLM_ASSETS = "llm/"

/** Start the one-time, user-triggered model download. Safe to call repeatedly. */
fun startAriadneModelDownload() {
    jsDownload(LLM_ASSETS.toJsString(), AriadneModelManifest.URL.toJsString())
}

/** 'idle' | 'loading' | 'ready' | 'error' for the download/engine UI. */
fun ariadneModelStatus(): String = jsStatus().toString()

actual fun provideAssistantClassifier(): AssistantClassifier = WllamaAssistantClassifier

private object WllamaAssistantClassifier : AssistantClassifier {
    override suspend fun classify(input: String, vocabulary: AssistantVocabulary): AssistantIntent? {
        val prompt = IntentGrounder.classificationPrompt(input)
        val json = try {
            jsClassify(prompt.toJsString()).awaitJs().toString()
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
