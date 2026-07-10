package com.syrmos.llm

/**
 * Kotlin front for the JNI shim (libsyrmos_llama.so, built from a pinned
 * llama.cpp). The JNI symbol names are Java_com_syrmos_llm_LlamaBridge_*, so this
 * class MUST stay at com.syrmos.llm.LlamaBridge.
 *
 * The vendored native libs (arm64-v8a under src/androidMain/jniLibs) are loaded
 * in dependency order. If they can't load (e.g. an unsupported ABI, or a stripped
 * build), [available] is false and Ariadne stays on the deterministic rule
 * parser, so nothing crashes and nothing regresses.
 */
object LlamaBridge {
    val available: Boolean = try {
        // ggml first (base -> cpu -> ggml), then llama, then our shim.
        System.loadLibrary("ggml-base")
        System.loadLibrary("ggml-cpu")
        System.loadLibrary("ggml")
        System.loadLibrary("llama")
        System.loadLibrary("syrmos_llama")
        nativeInit()
        true
    } catch (_: Throwable) {
        false
    }

    private external fun nativeInit()

    /** Load a GGUF model, returning an opaque handle (0 on failure). */
    external fun nativeLoadModel(path: String, nCtx: Int): Long

    /** Grammar-constrained greedy completion. Returns the raw text (may be ""). */
    external fun nativeComplete(handle: Long, prompt: String, maxTokens: Int, grammar: String): String

    external fun nativeFree(handle: Long)
}
