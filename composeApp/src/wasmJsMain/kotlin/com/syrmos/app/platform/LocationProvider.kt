package com.syrmos.app.platform

import com.syrmos.core.model.location.UserLocation
import kotlinx.browser.localStorage

actual suspend fun requestUserLocation(): UserLocation? {
    // Web uses JS geolocation API in web-map.js, not this KMP path
    return null
}

actual suspend fun requestLocationPermission() {
    // Web onboarding handles this through navigator.geolocation in the
    // hosting HTML/JS; the Compose layer doesn't prompt directly.
}

private const val ONBOARDING_KEY = "syrmos.onboarding.completed.v1"

actual fun readOnboardingCompleted(): Boolean =
    localStorage.getItem(ONBOARDING_KEY) == "1"

actual fun markOnboardingCompleted() {
    localStorage.setItem(ONBOARDING_KEY, "1")
}

private const val WHATS_NEW_KEY = "syrmos.whatsnew.version"

// The web build shows its own what's-new card (web-map.js); this stays wired
// for API parity.
actual fun readLastWhatsNewVersion(): String? = localStorage.getItem(WHATS_NEW_KEY)

actual fun markWhatsNewSeen(version: String) {
    localStorage.setItem(WHATS_NEW_KEY, version)
}

actual fun consumePendingAssistantQuery(): String? = null
