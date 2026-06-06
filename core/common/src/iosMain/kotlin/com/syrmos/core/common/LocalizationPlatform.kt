package com.syrmos.core.common

import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

actual fun detectSystemLanguage(): AppLanguage {
    val locale = NSLocale.currentLocale.languageCode
    return if (locale == "el") AppLanguage.GREEK else AppLanguage.ENGLISH
}

actual fun persistLanguage(lang: AppLanguage) {
    NSUserDefaults.standardUserDefaults.setObject(lang.code, forKey = "syrmos_language")
}

actual fun loadPersistedLanguage(): AppLanguage? {
    val code = NSUserDefaults.standardUserDefaults.stringForKey("syrmos_language") ?: return null
    return AppLanguage.entries.firstOrNull { it.code == code }
}
