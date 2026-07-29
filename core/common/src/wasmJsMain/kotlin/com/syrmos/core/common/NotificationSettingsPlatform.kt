package com.syrmos.core.common

actual fun persistNotifPref(key: String, value: Boolean) {
}

actual fun loadNotifPref(key: String, default: Boolean): Boolean {
    return default
}
