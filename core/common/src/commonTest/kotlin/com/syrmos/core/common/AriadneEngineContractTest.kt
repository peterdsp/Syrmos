package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contracts for the two things Settings tells a rider about Ariadne: which
 * engine is answering, and what the optional model download is doing.
 *
 * The bug these pin: the Android engine readout was derived only from the
 * AICore/Gemini Nano feature status, so a rider who had just downloaded the
 * 1.1 GB GGUF was still told their device could not run an on-device model.
 * [AriadneEngineStatus.LOCAL_MODEL] is that second, independent path and it
 * must count as smart.
 */
class AriadneEngineContractTest {

    @Test fun a_downloaded_local_model_counts_as_a_smart_engine() {
        assertTrue(AriadneEngineStatus.LOCAL_MODEL.isSmart)
    }

    @Test fun gemini_nano_still_counts_as_a_smart_engine() {
        assertTrue(AriadneEngineStatus.AVAILABLE.isSmart)
    }

    @Test fun every_non_model_backed_status_is_not_smart() {
        listOf(
            AriadneEngineStatus.AICORE_MISSING,
            AriadneEngineStatus.MODEL_NOT_DOWNLOADED,
            AriadneEngineStatus.DEVICE_NOT_ELIGIBLE,
            AriadneEngineStatus.RULE_PARSER,
        ).forEach { assertFalse(it.isSmart, "$it must not claim Clever mode") }
    }

    @Test fun exactly_two_engine_states_are_smart() {
        assertEquals(2, AriadneEngineStatus.entries.count { it.isSmart })
    }

    @Test fun the_download_reports_verifying_and_storage_separately_from_error() {
        // A hash check and a full disk are not the same thing as a failed
        // transfer: each needs its own state or the UI cannot explain itself.
        val states = AriadneModelState.entries.toSet()
        assertTrue(AriadneModelState.VERIFYING in states)
        assertTrue(AriadneModelState.INSUFFICIENT_STORAGE in states)
        assertEquals(7, states.size)
    }

    @Test fun the_no_op_downloader_reports_unsupported_so_the_ui_hides_the_offer() {
        assertEquals(AriadneModelState.UNSUPPORTED, NoOpAriadneModelDownloader.state.value)
        assertEquals(0f, NoOpAriadneModelDownloader.progress.value)
    }
}
