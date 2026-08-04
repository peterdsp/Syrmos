package com.syrmos.core.common

import android.content.Context

private var notifContext: Context? = null

fun initNotificationSettingsPlatform(context: Context) {
    notifContext = context.applicationContext
}

actual fun persistNotifPref(key: String, value: Boolean) {
    val prefs = notifContext?.getSharedPreferences("syrmos_prefs", Context.MODE_PRIVATE) ?: return
    prefs.edit().putBoolean(key, value).apply()
}

actual fun loadNotifPref(key: String, default: Boolean): Boolean {
    val prefs = notifContext?.getSharedPreferences("syrmos_prefs", Context.MODE_PRIVATE) ?: return default
    return prefs.getBoolean(key, default)
}

actual fun persistStringPref(key: String, value: String) {
    val prefs = notifContext?.getSharedPreferences("syrmos_prefs", Context.MODE_PRIVATE) ?: return
    prefs.edit().putString(key, value).apply()
}

actual fun loadStringPref(key: String, default: String): String {
    val prefs = notifContext?.getSharedPreferences("syrmos_prefs", Context.MODE_PRIVATE) ?: return default
    return prefs.getString(key, default) ?: default
}
