package com.syrmos.android.notification

import android.content.Context

object NotificationPrefs {
    private const val PREFS_NAME = "syrmos_prefs"
    private const val KEY_SEEN_ALERT_IDS = "seen_alert_ids"
    private const val KEY_LAST_WEATHER_DATE = "last_weather_notif_date"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSeenAlertIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SEEN_ALERT_IDS, emptySet()) ?: emptySet()

    fun saveSeenAlertIds(context: Context, ids: Set<String>) {
        prefs(context).edit().putStringSet(KEY_SEEN_ALERT_IDS, ids).apply()
    }

    fun serviceAlertsEnabled(context: Context): Boolean =
        prefs(context).getBoolean("notif_service_alerts", true)

    fun weatherAlertsEnabled(context: Context): Boolean =
        prefs(context).getBoolean("notif_weather_alerts", true)

    fun nearbyAlertsEnabled(context: Context): Boolean =
        prefs(context).getBoolean("notif_nearby_alerts", true)

    fun lastWeatherNotifDate(context: Context): String =
        prefs(context).getString(KEY_LAST_WEATHER_DATE, "") ?: ""

    fun setLastWeatherNotifDate(context: Context, date: String) {
        prefs(context).edit().putString(KEY_LAST_WEATHER_DATE, date).apply()
    }
}
