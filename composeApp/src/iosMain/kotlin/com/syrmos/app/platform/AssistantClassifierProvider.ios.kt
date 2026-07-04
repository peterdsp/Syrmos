package com.syrmos.app.platform

import com.syrmos.core.domain.assistant.AssistantClassifier
import com.syrmos.core.domain.assistant.NoOpAssistantClassifier

// The shipping iOS app is native SwiftUI and uses its own Foundation Models
// classifier (AriadneGuided); this Compose seam stays a no-op on iOS.
actual fun provideAssistantClassifier(): AssistantClassifier = NoOpAssistantClassifier
