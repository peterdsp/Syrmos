package com.syrmos.app.platform

import com.syrmos.core.model.location.UserLocation

expect suspend fun requestUserLocation(): UserLocation?
