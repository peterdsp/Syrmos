package com.syrmos.app.platform

import com.syrmos.core.common.AriadneEngineStatus

/**
 * Resolves the current Ariadne engine status for the Settings diagnostic row.
 * Suspends because the Android backer queries the on-device model feature status
 * asynchronously. Mirrors the iOS `AriadneBrain.availability` surface.
 */
expect suspend fun provideAriadneEngineStatus(): AriadneEngineStatus
