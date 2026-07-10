package com.syrmos.app.platform

import com.syrmos.core.common.AriadneModelDownloader
import com.syrmos.core.common.NoOpAriadneModelDownloader

// This platform uses its own model path (iOS native / Web wllama), so the shared
// Compose downloader is a no-op and the shared UI hides the control.
actual fun provideModelDownloader(): AriadneModelDownloader = NoOpAriadneModelDownloader
