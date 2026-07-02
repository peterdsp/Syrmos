package com.syrmos.app.platform

import com.syrmos.core.domain.assistant.AssistantQueryNormalizer
import com.syrmos.core.domain.assistant.NoOpQueryNormalizer

// The shipping iOS app is native SwiftUI and uses its own Foundation Models
// path (AriadneBrain); this Compose seam stays a no-op on iOS.
actual fun provideQueryNormalizer(): AssistantQueryNormalizer = NoOpQueryNormalizer
