package com.syrmos.core.common

/**
 * Which engine currently backs Ariadne, surfaced in Settings so the user
 * understands whether an on-device model is cleaning up their question
 * ("Clever mode") or the deterministic rule parser is handling it directly.
 *
 * Android has two independent smart paths and this status must not conflate
 * them: Gemini Nano via ML Kit GenAI / AICore (system-supplied, reported by the
 * proofreading feature status) and the GGUF the user downloaded themselves, run
 * by the bundled llama.cpp ([LOCAL_MODEL]). A rider who paid 1.1 GB for the
 * second one must not be told the device cannot run an on-device model. Web and
 * the Compose iOS target have no smart seam and always report [RULE_PARSER].
 * The rule parser is never absent, so Ariadne always works regardless.
 */
enum class AriadneEngineStatus {
    /** On-device model ready; Ariadne runs in Clever mode. */
    AVAILABLE,

    /** The downloaded GGUF is present and the native runtime loaded it. This
     *  outranks every AICore verdict: the model is here and it runs. */
    LOCAL_MODEL,

    /** No AICore on this device (pre-Pixel 9 / non-Samsung flagship). ML Kit
     *  cannot always separate this from [DEVICE_NOT_ELIGIBLE]. */
    AICORE_MISSING,

    /** AICore present but the model is not downloaded yet, or downloading. */
    MODEL_NOT_DOWNLOADED,

    /** Hardware can't run on-device GenAI. */
    DEVICE_NOT_ELIGIBLE,

    /** Platform has no smart seam (Web); Ariadne uses the rule parser directly. */
    RULE_PARSER;

    /** True only when a smart, model-backed engine is live, by either path. */
    val isSmart: Boolean get() = this == AVAILABLE || this == LOCAL_MODEL
}
