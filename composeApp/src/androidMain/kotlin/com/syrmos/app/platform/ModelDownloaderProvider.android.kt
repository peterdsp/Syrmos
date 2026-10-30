package com.syrmos.app.platform

import com.syrmos.core.common.AriadneModelDownloader
import com.syrmos.core.common.NoOpAriadneModelDownloader

/**
 * Android offers no model download, exactly like iOS and Web.
 *
 * Ariadne is complete when the app is installed: every supported transit task is
 * answered from bundled, versioned data by the deterministic parser and the
 * app's own tools, with the cloud assistant on top. Android used to be the one
 * client that also dangled a ~1.1 GB GGUF download in front of the rider, first
 * above the conversation and then in Settings. Both read as "the assistant is
 * not finished until you pay for this", which is false, and it made Android
 * behave differently from iOS for no user-visible gain.
 *
 * The no-op reports UNSUPPORTED, so the shared UI renders no control at all.
 */
actual fun provideModelDownloader(): AriadneModelDownloader = NoOpAriadneModelDownloader
