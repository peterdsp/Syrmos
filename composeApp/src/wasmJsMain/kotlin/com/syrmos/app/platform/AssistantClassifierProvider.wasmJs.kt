package com.syrmos.app.platform

import com.syrmos.core.domain.assistant.AssistantClassifier
import com.syrmos.core.domain.assistant.NoOpAssistantClassifier

// Web has no on-device LLM; it runs the deterministic parser directly and gets
// its "cleverer than dummy" lift from RAG retrieval in the web assistant layer.
actual fun provideAssistantClassifier(): AssistantClassifier = NoOpAssistantClassifier
