package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Mirrors fixtures/freshness/presentation.json case-for-case, so the Kotlin
 * FreshnessPresentation and web web-freshness.js agree for the same golden inputs
 * (Phase N J07 unified freshness/offline presentation).
 */
class FreshnessPresentationTest {

    @Test fun online_live() {
        val s = FreshnessPresentation.evaluate(isNetworkAvailable = true, isLive = true)
        assertEquals(FreshnessBannerState.LIVE, s)
        assertFalse(FreshnessPresentation.showsBanner(s))
    }

    @Test fun online_predicted() {
        val s = FreshnessPresentation.evaluate(isNetworkAvailable = true, isLive = false)
        assertEquals(FreshnessBannerState.PREDICTED, s)
        assertTrue(FreshnessPresentation.showsBanner(s))
    }

    @Test fun offline_wins_over_live() {
        val s = FreshnessPresentation.evaluate(isNetworkAvailable = false, isLive = true)
        assertEquals(FreshnessBannerState.OFFLINE, s)
        assertTrue(FreshnessPresentation.showsBanner(s))
    }

    @Test fun offline_predicted() {
        val s = FreshnessPresentation.evaluate(isNetworkAvailable = false, isLive = false)
        assertEquals(FreshnessBannerState.OFFLINE, s)
        assertTrue(FreshnessPresentation.showsBanner(s))
    }
}
