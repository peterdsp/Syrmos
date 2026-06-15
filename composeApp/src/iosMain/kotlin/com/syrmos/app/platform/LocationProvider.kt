package com.syrmos.app.platform

import com.syrmos.core.model.location.UserLocation
import platform.Foundation.NSUserDefaults

actual suspend fun requestUserLocation(): UserLocation? {
    // iOS uses native SwiftUI LocationService, not this KMP path
    return null
}

actual suspend fun requestLocationPermission() {
    // iOS onboarding is the native SwiftUI flow which owns the permission UI.
}

private const val ONBOARDING_KEY = "syrmos.onboarding.completed.v1"

actual fun readOnboardingCompleted(): Boolean =
    NSUserDefaults.standardUserDefaults.boolForKey(ONBOARDING_KEY)

actual fun markOnboardingCompleted() {
    NSUserDefaults.standardUserDefaults.setBool(true, ONBOARDING_KEY)
}
