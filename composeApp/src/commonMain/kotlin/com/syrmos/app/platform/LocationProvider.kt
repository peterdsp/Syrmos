package com.syrmos.app.platform

import com.syrmos.core.model.location.UserLocation

expect suspend fun requestUserLocation(): UserLocation?

/** Prompt the user for location permission if it hasn't been decided yet.
 *  No-op when permission is already granted or permanently denied. */
expect suspend fun requestLocationPermission()

/** Persistent first-launch gate shared by all platforms. */
expect fun readOnboardingCompleted(): Boolean
expect fun markOnboardingCompleted()

/** Last app version whose "what's new" card the user has seen, or null. Used to
 *  show the highlights once per release after an install or update. */
expect fun readLastWhatsNewVersion(): String?
expect fun markWhatsNewSeen(version: String)

expect fun consumePendingAssistantQuery(): String?

expect fun consumePendingNotificationDeepLink(): Pair<String, String?>?

expect suspend fun requestNotificationPermission()
