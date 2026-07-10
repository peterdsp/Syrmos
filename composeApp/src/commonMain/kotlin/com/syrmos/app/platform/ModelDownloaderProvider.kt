package com.syrmos.app.platform

import com.syrmos.core.common.AriadneModelDownloader

/**
 * Platform provider for the on-demand model download that the shared Ariadne UI
 * shows. Android returns a real downloader (llama.cpp model into app storage);
 * iOS (native path) and Web (wllama) return the no-op, so the shared UI hides the
 * control there.
 */
expect fun provideModelDownloader(): AriadneModelDownloader
