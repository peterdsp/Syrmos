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

    /** Transfer finished; hashing 1.1 GB to check it against the manifest.
     *  This takes seconds, so it needs its own label: a progress bar frozen at
     *  100% with no explanation reads as a hang. */
    VERIFYING,

    /** Not enough free space for the model. Reported from a preflight check, so
     *  the user is told before 1.1 GB of transfer instead of after it. */
    INSUFFICIENT_STORAGE,

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
