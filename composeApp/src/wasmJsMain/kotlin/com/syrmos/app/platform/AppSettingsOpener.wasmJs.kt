package com.syrmos.app.platform

// A browser has no per-app settings page to open.
actual fun canOpenAppSettings(): Boolean = false

actual fun openAppSettings() = Unit
