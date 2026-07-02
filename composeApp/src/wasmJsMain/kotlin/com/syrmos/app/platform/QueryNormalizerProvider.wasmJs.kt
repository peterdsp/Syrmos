package com.syrmos.app.platform

import com.syrmos.core.domain.assistant.AssistantQueryNormalizer
import com.syrmos.core.domain.assistant.NoOpQueryNormalizer

// Web runs the deterministic parser (with its fuzzy matcher) directly.
actual fun provideQueryNormalizer(): AssistantQueryNormalizer = NoOpQueryNormalizer
