package com.syrmos.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.syrmos.core.common.LiveDataFreshness

/**
 * Fires [LiveDataFreshness.requestRetry] the instant the device regains
 * connectivity, so the home surface refreshes immediately on reconnect instead
 * of waiting up to 60s for the next poll. This is the Android counterpart to
 * the iOS NWPathMonitor in DataFreshness.swift, which already does exactly this
 * (parity #7). The shared retry channel already exists: HomeViewModel collects
 * [LiveDataFreshness.retryRequested] and probes on every bump.
 *
 * Registered once from [SyrmosApplication.onCreate] for the whole process
 * lifetime, so there is no unregister/leak concern (the callback is meant to
 * live as long as the app). requestRetry() only bumps a StateFlow, so it is
 * cheap, thread-safe from the binder callback thread, and idempotent.
 */
class ConnectivityObserver(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // A usable default network appeared (cold start while online, or an
            // offline -> online transition). Flag online and ask the home surface
            // to re-probe.
            LiveDataFreshness.setNetworkAvailable(true)
            LiveDataFreshness.requestRetry()
        }

        override fun onLost(network: Network) {
            // The default network went away: flag offline instantly so the map's
            // offline indicator appears without waiting for the freshness window
            // to decay. Live polls keep running and recover on their own once
            // onAvailable fires again.
            LiveDataFreshness.setNetworkAvailable(false)
        }
    }

    /**
     * Begins observing the system default network. registerDefaultNetworkCallback
     * requires API 24; minSdk is 26, so it is always available. Wrapped in
     * runCatching because a few OEM/emulator builds throw on registration; a
     * failure just means we fall back to the existing 60s poll, never a crash.
     */
    fun start() {
        val cm = connectivityManager ?: return
        runCatching { cm.registerDefaultNetworkCallback(callback) }
    }
}
