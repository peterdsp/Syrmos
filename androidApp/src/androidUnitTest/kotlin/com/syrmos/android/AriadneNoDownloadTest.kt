package com.syrmos.android

import com.syrmos.app.platform.provideModelDownloader
import com.syrmos.core.common.AriadneModelState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Android must offer no Ariadne model download, exactly like iOS and Web.
 *
 * Ariadne is complete at install. Android was the one client that also offered
 * a ~1.1 GB GGUF, first as a card above the conversation and then as a Settings
 * row, which made the assistant look unfinished and made Android behave
 * differently from iOS for no gain. UNSUPPORTED is what makes the shared UI
 * render no control at all, so pin it here.
 */
class AriadneNoDownloadTest {

    @Test fun android_reports_no_downloadable_model() {
        assertEquals(AriadneModelState.UNSUPPORTED, provideModelDownloader().state.value)
    }

    @Test fun android_reports_no_download_progress() {
        assertEquals(0f, provideModelDownloader().progress.value)
    }

    @Test fun starting_a_download_does_nothing() {
        val downloader = provideModelDownloader()
        downloader.start()
        assertEquals(AriadneModelState.UNSUPPORTED, downloader.state.value)
    }
}
