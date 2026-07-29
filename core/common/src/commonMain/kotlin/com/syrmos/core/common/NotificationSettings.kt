package com.syrmos.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

expect fun persistNotifPref(key: String, value: Boolean)
expect fun loadNotifPref(key: String, default: Boolean): Boolean

object NotificationSettings {
    private val _serviceAlerts = MutableStateFlow(loadNotifPref("notif_service_alerts", true))
    val serviceAlerts: StateFlow<Boolean> = _serviceAlerts.asStateFlow()

    private val _weatherAlerts = MutableStateFlow(loadNotifPref("notif_weather_alerts", true))
    val weatherAlerts: StateFlow<Boolean> = _weatherAlerts.asStateFlow()

    private val _nearbyAlerts = MutableStateFlow(loadNotifPref("notif_nearby_alerts", true))
    val nearbyAlerts: StateFlow<Boolean> = _nearbyAlerts.asStateFlow()

    private val _morningDigest = MutableStateFlow(loadNotifPref("notif_morning_digest", true))
    val morningDigest: StateFlow<Boolean> = _morningDigest.asStateFlow()

    fun setServiceAlerts(enabled: Boolean) {
        _serviceAlerts.value = enabled
        persistNotifPref("notif_service_alerts", enabled)
    }

    fun setWeatherAlerts(enabled: Boolean) {
        _weatherAlerts.value = enabled
        persistNotifPref("notif_weather_alerts", enabled)
    }

    fun setNearbyAlerts(enabled: Boolean) {
        _nearbyAlerts.value = enabled
        persistNotifPref("notif_nearby_alerts", enabled)
    }

    fun setMorningDigest(enabled: Boolean) {
        _morningDigest.value = enabled
        persistNotifPref("notif_morning_digest", enabled)
    }
}
