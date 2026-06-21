package com.syrmos.core.common

import platform.Foundation.NSUserDefaults

actual fun persistTheme(theme: AppThemeMode) {
    NSUserDefaults.standardUserDefaults.setObject(theme.name, forKey = "syrmos_theme")
}

actual fun loadPersistedTheme(): AppThemeMode? {
    val name = NSUserDefaults.standardUserDefaults.stringForKey("syrmos_theme") ?: return null
    return AppThemeMode.entries.firstOrNull { it.name == name }
}
