package com.syrmos.app.platform

import com.syrmos.core.domain.assistant.AssistantClassifier

/**
 * Platform on-device clever classifier for Ariadne. On capable Android devices
 * this is Gemini Nano (via ML Kit GenAI Prompt API) doing guided intent
 * classification; on every other KMP target it is a no-op and the deterministic
 * rule parser classifies directly. (The native iOS app uses its own Foundation
 * Models path — AriadneGuided — not this seam.)
 */
expect fun provideAssistantClassifier(): AssistantClassifier
