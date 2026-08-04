package com.syrmos.core.common

actual fun persistNotifPref(key: String, value: Boolean) {
}

actual fun loadNotifPref(key: String, default: Boolean): Boolean {
    return default
}

actual fun persistStringPref(key: String, value: String) {
}

actual fun loadStringPref(key: String, default: String): String {
    return default
}
