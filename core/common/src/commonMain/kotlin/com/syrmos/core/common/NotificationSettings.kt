package com.syrmos.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

expect fun persistNotifPref(key: String, value: Boolean)
expect fun loadNotifPref(key: String, default: Boolean): Boolean
expect fun persistStringPref(key: String, value: String)
expect fun loadStringPref(key: String, default: String): String

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

    // Phase N J09: master opt-in for leave-by reminders. Off by default: the rider
    // asks to be reminded, reminders never appear unrequested. When off, no
    // leave-by notification is scheduled regardless of the saved-departure board.
    private val _leaveByReminders = MutableStateFlow(loadNotifPref("notif_leave_by", false))
    val leaveByReminders: StateFlow<Boolean> = _leaveByReminders.asStateFlow()

    fun setLeaveByReminders(enabled: Boolean) {
        _leaveByReminders.value = enabled
        persistNotifPref("notif_leave_by", enabled)
    }
}
