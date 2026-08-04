package com.syrmos.app.platform

import com.syrmos.core.model.location.UserLocation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise
import kotlinx.browser.localStorage

@JsFun("""() => new Promise((resolve, reject) => {
    if (!navigator.geolocation) { reject('no geolocation'); return; }
    navigator.geolocation.getCurrentPosition(
        pos => resolve(pos.coords.latitude + ',' + pos.coords.longitude),
        err => reject(err.message || 'denied'),
        { enableHighAccuracy: true, timeout: 10000 }
    );
})""")
private external fun jsGetLocation(): Promise<JsString>

actual suspend fun requestUserLocation(): UserLocation? {
    return try {
        val result = jsGetLocation().awaitLocation()
        val parts = result.split(",")
        if (parts.size == 2) {
            val lat = parts[0].toDoubleOrNull()
            val lon = parts[1].toDoubleOrNull()
            if (lat != null && lon != null) UserLocation(lat, lon) else null
        } else null
    } catch (_: Throwable) {
        null
    }
}

private suspend fun Promise<JsString>.awaitLocation(): String = suspendCoroutine { cont ->
    then(
        onFulfilled = { value: JsString ->
            cont.resume(value.toString())
            value
        },
        onRejected = { error: JsAny ->
            cont.resumeWithException(Exception(error.toString()))
            error
        },
    )
}

actual suspend fun requestLocationPermission() {
    // Calling requestUserLocation() triggers the browser permission prompt.
    requestUserLocation()
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

actual fun consumePendingNotificationDeepLink(): Pair<String, String?>? = null

actual suspend fun requestNotificationPermission() {
    // Web does not support push notification permission via this path.
}
