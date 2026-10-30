package com.syrmos.app.platform

// The SwiftUI client owns its own "Open Settings" affordances
// (UIApplication.openSettingsURLString), so the shared Compose UI offers none.
actual fun canOpenAppSettings(): Boolean = false

actual fun openAppSettings() = Unit
