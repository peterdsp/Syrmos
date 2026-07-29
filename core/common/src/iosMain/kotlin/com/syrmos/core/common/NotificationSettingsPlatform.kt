package com.syrmos.core.common

import platform.Foundation.NSUserDefaults

actual fun persistNotifPref(key: String, value: Boolean) {
    NSUserDefaults.standardUserDefaults.setBool(value, forKey = "syrmos_$key")
}

actual fun loadNotifPref(key: String, default: Boolean): Boolean {
    val defaults = NSUserDefaults.standardUserDefaults
    if (defaults.objectForKey("syrmos_$key") == null) return default
    return defaults.boolForKey("syrmos_$key")
}
