package com.syrmos.app.platform

import com.syrmos.feature.schedule.AirportCalendarTrip

actual fun hasAirportCalendarPermission(): Boolean = false
actual suspend fun requestAirportCalendarPermission(): Boolean = false
actual suspend fun loadAirportCalendarTrips(): List<AirportCalendarTrip> = emptyList()
