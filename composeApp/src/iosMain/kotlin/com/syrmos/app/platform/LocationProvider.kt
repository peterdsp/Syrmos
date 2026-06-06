package com.syrmos.app.platform

import com.syrmos.core.model.location.UserLocation

actual suspend fun requestUserLocation(): UserLocation? {
    // iOS uses native SwiftUI LocationService, not this KMP path
    return null
}
