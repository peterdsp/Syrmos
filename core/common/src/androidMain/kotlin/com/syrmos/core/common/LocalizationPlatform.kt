package com.syrmos.core.common

import android.content.Context
import java.util.Locale

private var appContext: Context? = null

fun initLocalization(context: Context) {
    appContext = context.applicationContext
}

actual fun detectSystemLanguage(): AppLanguage {
    val locale = Locale.getDefault().language
    return if (locale == "el") AppLanguage.GREEK else AppLanguage.ENGLISH
}

actual fun persistLanguage(lang: AppLanguage) {
    val prefs = appContext?.getSharedPreferences("syrmos_prefs", Context.MODE_PRIVATE) ?: return
    prefs.edit().putString("language", lang.code).apply()
}

actual fun loadPersistedLanguage(): AppLanguage? {
    val prefs = appContext?.getSharedPreferences("syrmos_prefs", Context.MODE_PRIVATE) ?: return null
    val code = prefs.getString("language", null) ?: return null
    return AppLanguage.entries.firstOrNull { it.code == code }
}
