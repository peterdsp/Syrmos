package com.syrmos.app.platform

import com.syrmos.feature.schedule.AirportCalendarTrip

expect fun hasAirportCalendarPermission(): Boolean
expect suspend fun requestAirportCalendarPermission(): Boolean
expect suspend fun loadAirportCalendarTrips(): List<AirportCalendarTrip>
