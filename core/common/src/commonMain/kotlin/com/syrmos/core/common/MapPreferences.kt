package com.syrmos.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MapPreferences {
    private val _showLiveVehicles = MutableStateFlow(loadNotifPref("map_show_live_vehicles", true))
    val showLiveVehicles: StateFlow<Boolean> = _showLiveVehicles.asStateFlow()

    private val _defaultRegion = MutableStateFlow(loadStringPref("map_default_region", "athens"))
    val defaultRegion: StateFlow<String> = _defaultRegion.asStateFlow()

    fun setShowLiveVehicles(enabled: Boolean) {
        _showLiveVehicles.value = enabled
        persistNotifPref("map_show_live_vehicles", enabled)
    }

    fun setDefaultRegion(region: String) {
        _defaultRegion.value = region
        persistStringPref("map_default_region", region)
    }
}
