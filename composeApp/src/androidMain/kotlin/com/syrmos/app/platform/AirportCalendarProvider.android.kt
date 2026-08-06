package com.syrmos.app.platform

import android.Manifest
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.syrmos.feature.schedule.AirportCalendarTrip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private var airportCalendarPermissionRequester: (suspend () -> Unit)? = null

fun setAirportCalendarPermissionRequester(requester: (suspend () -> Unit)?) {
    airportCalendarPermissionRequester = requester
}

actual fun hasAirportCalendarPermission(): Boolean {
    val context = androidPlatformContext() ?: return false
    return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
}

actual suspend fun requestAirportCalendarPermission(): Boolean {
    if (!hasAirportCalendarPermission()) airportCalendarPermissionRequester?.invoke()
    return hasAirportCalendarPermission()
}

actual suspend fun loadAirportCalendarTrips(): List<AirportCalendarTrip> = withContext(Dispatchers.IO) {
    val context = androidPlatformContext() ?: return@withContext emptyList()
    if (!hasAirportCalendarPermission()) return@withContext emptyList()
    val now = System.currentTimeMillis()
    val until = now + 8L * 24L * 60L * 60L * 1000L
    val projection = arrayOf(
        CalendarContract.Events._ID,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.EVENT_LOCATION,
    )
    val selection = "${CalendarContract.Events.DELETED}=0 AND ${CalendarContract.Events.DTSTART}>=? AND ${CalendarContract.Events.DTSTART}<?"
    val args = arrayOf(now.toString(), until.toString())
    val trips = mutableListOf<AirportCalendarTrip>()
    context.contentResolver.query(
        CalendarContract.Events.CONTENT_URI,
        projection,
        selection,
        args,
        "${CalendarContract.Events.DTSTART} ASC",
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
        val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
        val startIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
        val locationIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
        while (cursor.moveToNext() && trips.size < 20) {
            val title = cursor.getString(titleIndex).orEmpty()
            val location = cursor.getString(locationIndex).orEmpty()
            if ((title + " " + location).isAirportTripText()) {
                trips += AirportCalendarTrip(
                    id = cursor.getLong(idIndex).toString(),
                    title = title.ifBlank { "Airport trip" },
                    startEpochMillis = cursor.getLong(startIndex),
                    location = location,
                )
            }
        }
    }
    trips
}

private fun String.isAirportTripText(): Boolean {
    val value = lowercase()
    return listOf(
        "airport", "flight", "ath", "aeroporto", "aeroporti", "volo", "fluturim",
        "αεροδρο", "πτηση", "πτήση", "m3", "x95", "x93",
    ).any(value::contains)
}
