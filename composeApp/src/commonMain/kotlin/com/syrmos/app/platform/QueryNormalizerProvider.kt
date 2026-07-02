package com.syrmos.app.platform

import com.syrmos.core.domain.assistant.AssistantQueryNormalizer

/**
 * Platform on-device query normalizer for Ariadne. On capable Android devices
 * this is a real proofreading model (Gemini Nano via ML Kit GenAI) that cleans
 * up spelling/grammar before the deterministic parser runs; on every other
 * target it is a no-op and the parser's fuzzy matcher handles the raw text.
 * (The native iOS app uses its own Foundation Models path, not this seam.)
 */
expect fun provideQueryNormalizer(): AssistantQueryNormalizer
