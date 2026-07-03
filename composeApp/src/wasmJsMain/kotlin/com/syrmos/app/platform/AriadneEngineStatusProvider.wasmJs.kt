package com.syrmos.app.platform

import com.syrmos.core.common.AriadneEngineStatus

// Web runs the deterministic rule parser directly; there is no on-device model.
actual suspend fun provideAriadneEngineStatus(): AriadneEngineStatus = AriadneEngineStatus.RULE_PARSER
