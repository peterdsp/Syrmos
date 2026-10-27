package com.syrmos.app.platform

import android.Manifest
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.CalendarLeaveBy
import com.syrmos.core.common.LocalizationManager
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
            // Shared 3.0.2 classifier: "ATH" matches only as a whole token, so a
            // "Meeting in Athens" event is no longer listed as an airport trip.
            if (CalendarLeaveBy.isAirportTrip(title, location)) {
                trips += AirportCalendarTrip(
                    id = cursor.getLong(idIndex).toString(),
                    title = title.ifBlank { fallbackTripTitle() },
                    startEpochMillis = cursor.getLong(startIndex),
                    location = location,
                )
            }
        }
    }
    trips
}

/** Title for a calendar event that matched as an airport trip but has no title of its own. */
private fun fallbackTripTitle(): String = when (LocalizationManager.language.value) {
    AppLanguage.GREEK -> "Ταξίδι αεροδρομίου"
    AppLanguage.ALBANIAN -> "Udhëtim aeroporti"
    AppLanguage.ITALIAN -> "Viaggio in aeroporto"
    else -> "Airport trip"
}

/**
 * 3.0.2 calendar leave-by: one upcoming calendar event instance, read on device.
 * [selfAttendeeStatus] is CalendarContract.Attendees.ATTENDEE_STATUS_* (declined = 2).
 */
data class CalendarEventInfo(
    val id: String,
    val title: String,
    val location: String,
    val startEpochMillis: Long,
    val allDay: Boolean,
    val selfAttendeeStatus: Int,
) {
    val declined: Boolean get() = selfAttendeeStatus == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
}

/**
 * Every event instance (recurring ones expanded) starting in the next
 * [horizonMillis], with title, location, start, all-day flag and the rider's own
 * attendee status. Empty without calendar permission. Nothing leaves the device.
 */
suspend fun loadUpcomingCalendarEvents(
    horizonMillis: Long = 48L * 3600L * 1000L,
): List<CalendarEventInfo> = withContext(Dispatchers.IO) {
    val context = androidPlatformContext() ?: return@withContext emptyList()
    if (!hasAirportCalendarPermission()) return@withContext emptyList()
    val now = System.currentTimeMillis()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        android.content.ContentUris.appendId(it, now)
        android.content.ContentUris.appendId(it, now + horizonMillis)
    }.build()
    val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.SELF_ATTENDEE_STATUS,
    )
    val out = mutableListOf<CalendarEventInfo>()
    runCatching {
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && out.size < 50) {
                val begin = c.getLong(1)
                out += CalendarEventInfo(
                    id = "${c.getLong(0)}_$begin",
                    title = c.getString(2).orEmpty(),
                    location = c.getString(3).orEmpty(),
                    startEpochMillis = begin,
                    allDay = c.getInt(4) != 0,
                    selfAttendeeStatus = c.getInt(5),
                )
            }
        }
    }
    out
}
