package com.syrmos.app.platform

import com.syrmos.core.common.AriadneModelDownloader
import com.syrmos.core.common.AriadneModelState
import com.syrmos.llm.LlamaBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

actual fun provideModelDownloader(): AriadneModelDownloader = AndroidModelDownloader

/**
 * Real Android downloader: streams the ~1.1 GB model into app storage on opt-in,
 * exposing live state + progress for the shared UI. If the native llama.cpp libs
 * couldn't load, it reports UNSUPPORTED so the UI hides the control (the rule
 * parser still answers).
 */
private object AndroidModelDownloader : AriadneModelDownloader {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val nativeReady = LlamaBridge.available

    init { AriadneModelStore.isReady() }  // sync initial READY if already present

    override val state: StateFlow<AriadneModelState> =
        AriadneModelStore.statusFlow
            .map { s -> if (!nativeReady) AriadneModelState.UNSUPPORTED else map(s) }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                if (!nativeReady) AriadneModelState.UNSUPPORTED else map(AriadneModelStore.status),
            )

    override val progress: StateFlow<Float> = AriadneModelStore.progressFlow

    override fun start() {
        if (!nativeReady) return
        scope.launch { AriadneModelStore.download() }
    }

    private fun map(s: AriadneModelStore.Status): AriadneModelState = when (s) {
        AriadneModelStore.Status.NOT_DOWNLOADED -> AriadneModelState.NOT_DOWNLOADED
        AriadneModelStore.Status.DOWNLOADING -> AriadneModelState.DOWNLOADING
        AriadneModelStore.Status.READY -> AriadneModelState.READY
        AriadneModelStore.Status.ERROR -> AriadneModelState.ERROR
    }
}
