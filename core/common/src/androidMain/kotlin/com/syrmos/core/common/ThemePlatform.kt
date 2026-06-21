package com.syrmos.core.common

import android.content.Context

private var appContext: Context? = null

fun initThemePlatform(context: Context) {
    appContext = context.applicationContext
}

actual fun persistTheme(theme: AppThemeMode) {
    val prefs = appContext?.getSharedPreferences("syrmos_prefs", Context.MODE_PRIVATE) ?: return
    prefs.edit().putString("theme", theme.name).apply()
}

actual fun loadPersistedTheme(): AppThemeMode? {
    val prefs = appContext?.getSharedPreferences("syrmos_prefs", Context.MODE_PRIVATE) ?: return null
    val name = prefs.getString("theme", null) ?: return null
    return AppThemeMode.entries.firstOrNull { it.name == name }
}
