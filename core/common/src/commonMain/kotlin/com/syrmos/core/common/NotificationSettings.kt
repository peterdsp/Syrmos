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

    // 3.0.2: "Announcements from Syrmos" (admin broadcasts via push or polling).
    // On by default, like service alerts; turning it off unregisters the push token.
    private val _broadcasts = MutableStateFlow(loadNotifPref("notif_broadcasts", true))
    val broadcasts: StateFlow<Boolean> = _broadcasts.asStateFlow()

    fun setBroadcasts(enabled: Boolean) {
        _broadcasts.value = enabled
        persistNotifPref("notif_broadcasts", enabled)
    }

    // 3.0.2: next-train notification when dwelling near a station. Off by default:
    // it needs background location, which the rider grants explicitly.
    private val _nearbyDepartures = MutableStateFlow(loadNotifPref("notif_nearby_departures", false))
    val nearbyDepartures: StateFlow<Boolean> = _nearbyDepartures.asStateFlow()

    fun setNearbyDepartures(enabled: Boolean) {
        _nearbyDepartures.value = enabled
        persistNotifPref("notif_nearby_departures", enabled)
    }

    // 3.0.2: calendar leave-by for every event with a location. Off by default
    // (needs calendar access); per-kind toggles default on once enabled.
    private val _calendarReminders = MutableStateFlow(loadNotifPref("notif_calendar", false))
    val calendarReminders: StateFlow<Boolean> = _calendarReminders.asStateFlow()

    fun setCalendarReminders(enabled: Boolean) {
        _calendarReminders.value = enabled
        persistNotifPref("notif_calendar", enabled)
    }

    private val _calendarKinds = MutableStateFlow(
        CalendarEventKind.entries.associateWith { loadNotifPref("notif_calendar_${it.key}", true) },
    )
    val calendarKinds: StateFlow<Map<CalendarEventKind, Boolean>> = _calendarKinds.asStateFlow()

    fun setCalendarKind(kind: CalendarEventKind, enabled: Boolean) {
        _calendarKinds.value = _calendarKinds.value + (kind to enabled)
        persistNotifPref("notif_calendar_${kind.key}", enabled)
    }

    private val _flightBufferMinutes = MutableStateFlow(
        loadStringPref("notif_calendar_flight_buffer", CalendarLeaveBy.DEFAULT_FLIGHT_BUFFER.toString())
            .toIntOrNull()?.takeIf { it in CalendarLeaveBy.FLIGHT_BUFFER_CHOICES }
            ?: CalendarLeaveBy.DEFAULT_FLIGHT_BUFFER,
    )
    val flightBufferMinutes: StateFlow<Int> = _flightBufferMinutes.asStateFlow()

    fun setFlightBufferMinutes(minutes: Int) {
        if (minutes !in CalendarLeaveBy.FLIGHT_BUFFER_CHOICES) return
        _flightBufferMinutes.value = minutes
        persistStringPref("notif_calendar_flight_buffer", minutes.toString())
    }
}

/**
 * Platform hooks the Settings toggles call after flipping a 3.0.2 preference, so
 * the Android app can run the permission flow (background location, calendar),
 * start or stop geofencing, and register or unregister the push token. Each hook
 * may switch the preference back off when the rider declines a permission. Null
 * on platforms that do not support the feature.
 */
object ProactiveFeatureHooks {
    var onBroadcastsChanged: ((Boolean) -> Unit)? = null
    var onNearbyDeparturesChanged: ((Boolean) -> Unit)? = null
    var onCalendarRemindersChanged: ((Boolean) -> Unit)? = null

    /** True when the platform supports nearby and calendar notifications (Android). */
    val proactiveSupported: Boolean get() = onNearbyDeparturesChanged != null
}
