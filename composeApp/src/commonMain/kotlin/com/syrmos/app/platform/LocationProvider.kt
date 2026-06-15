package com.syrmos.app.platform

import com.syrmos.core.model.location.UserLocation

expect suspend fun requestUserLocation(): UserLocation?

/** Prompt the user for location permission if it hasn't been decided yet.
 *  No-op when permission is already granted or permanently denied. */
expect suspend fun requestLocationPermission()

/** Persistent first-launch gate shared by all platforms. */
expect fun readOnboardingCompleted(): Boolean
expect fun markOnboardingCompleted()
