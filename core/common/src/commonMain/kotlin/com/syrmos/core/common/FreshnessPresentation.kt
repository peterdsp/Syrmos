package com.syrmos.core.common

/**
 * Phase N J07: the single rule for offline/predicted BANNER presentation, derived
 * from the existing shared freshness rule ([DataFreshness] / [LiveDataFreshness]).
 *
 * Surfaces must not hand-roll a connectivity-only check: "online but the API is
 * unreachable/stale" is a real degraded state ([PREDICTED]) that a bare
 * `isNetworkAvailable` gate would miss, so it is disclosed exactly like Home/Map
 * already do. Offline (no network) always wins over a stale live flag.
 *
 * Pure and side-effect free; mirrors web `web-freshness.js` and iOS
 * `FreshnessPresentation` exactly, covered by `fixtures/freshness/presentation.json`.
 */
enum class FreshnessBannerState { LIVE, PREDICTED, OFFLINE }

object FreshnessPresentation {
    fun evaluate(isNetworkAvailable: Boolean, isLive: Boolean): FreshnessBannerState = when {
        !isNetworkAvailable -> FreshnessBannerState.OFFLINE
        isLive -> FreshnessBannerState.LIVE
        else -> FreshnessBannerState.PREDICTED
    }

    /** A banner is shown for anything but a confident live state. */
    fun showsBanner(state: FreshnessBannerState): Boolean = state != FreshnessBannerState.LIVE
}
