package com.syrmos.core.common

/**
 * Which engine currently backs Ariadne, surfaced in Settings so the user
 * understands whether an on-device model is cleaning up their question
 * ("Clever mode") or the deterministic rule parser is handling it directly.
 *
 * On Android the smart engine is Gemini Nano via ML Kit GenAI / AICore; the
 * status is derived from the proofreading feature status. Web and the Compose
 * iOS target have no smart seam and always report [RULE_PARSER]. The rule parser
 * is never absent, so Ariadne always works regardless of this status.
 */
enum class AriadneEngineStatus {
    /** On-device model ready; Ariadne runs in Clever mode. */
    AVAILABLE,

    /** No AICore on this device (pre-Pixel 9 / non-Samsung flagship). ML Kit
     *  cannot always separate this from [DEVICE_NOT_ELIGIBLE]. */
    AICORE_MISSING,

    /** AICore present but the model is not downloaded yet, or downloading. */
    MODEL_NOT_DOWNLOADED,

    /** Hardware can't run on-device GenAI. */
    DEVICE_NOT_ELIGIBLE,

    /** Platform has no smart seam (Web); Ariadne uses the rule parser directly. */
    RULE_PARSER;

    /** True only when the smart, model-backed engine is live. */
    val isSmart: Boolean get() = this == AVAILABLE
}
