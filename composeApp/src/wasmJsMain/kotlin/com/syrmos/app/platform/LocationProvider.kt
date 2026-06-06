package com.syrmos.app.platform

import com.syrmos.core.model.location.UserLocation

actual suspend fun requestUserLocation(): UserLocation? {
    // Web uses JS geolocation API in web-map.js, not this KMP path
    return null
}
