package com.syrmos.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Lifecycle of the optional on-device model download (per platform). */
enum class AriadneModelState {
    /** This platform has no downloadable on-device model (no UI shown). */
    UNSUPPORTED,

    /** Supported, model not present yet. */
    NOT_DOWNLOADED,

    /** Downloading now; see [AriadneModelDownloader.progress] (0..1). */
    DOWNLOADING,

    /** Downloaded, verified, and ready to run. */
    READY,

    /** Download failed; the user can retry. Rule parser still answers. */
    ERROR,
}

/**
 * Drives the on-demand download of Ariadne's ~1.1 GB model for the shared UI. The
 * model is never bundled and never auto-pulled; the user opts in via [start].
 * Only Android supplies a real one today (llama.cpp); iOS uses its own native
 * path and Web has its own (wllama), so both use [NoOp].
 */
interface AriadneModelDownloader {
    val state: StateFlow<AriadneModelState>
    val progress: StateFlow<Float>
    fun start()
}

/** Default for platforms without a downloadable Compose-side model. */
object NoOpAriadneModelDownloader : AriadneModelDownloader {
    override val state: StateFlow<AriadneModelState> = MutableStateFlow(AriadneModelState.UNSUPPORTED)
    override val progress: StateFlow<Float> = MutableStateFlow(0f)
    override fun start() {}
}
