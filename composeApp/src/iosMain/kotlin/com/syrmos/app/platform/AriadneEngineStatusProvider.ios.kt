package com.syrmos.app.platform

import com.syrmos.core.common.AriadneEngineStatus

// The Compose iOS target has no smart seam; the shipping iOS app is native
// SwiftUI and uses its own Foundation Models path (AriadneBrain.availability).
actual suspend fun provideAriadneEngineStatus(): AriadneEngineStatus = AriadneEngineStatus.RULE_PARSER
