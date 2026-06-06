package com.syrmos.core.common

actual fun detectSystemLanguage(): AppLanguage {
    return AppLanguage.ENGLISH
}

actual fun persistLanguage(lang: AppLanguage) {
    // Web handles persistence in web-map.js / localStorage
}

actual fun loadPersistedLanguage(): AppLanguage? {
    return null
}
