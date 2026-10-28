package com.syrmos.android.proactive

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.syrmos.android.push.BroadcastNotifier
import com.syrmos.app.platform.CalendarEventInfo
import com.syrmos.app.platform.hasAirportCalendarPermission
import com.syrmos.app.platform.loadUpcomingCalendarEvents
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.CalendarEligibility
import com.syrmos.core.common.CalendarEventKind
import com.syrmos.core.common.CalendarLeaveBy
import com.syrmos.core.common.CalendarLeaveByCopy
import com.syrmos.core.common.CalendarNotificationKind
import com.syrmos.core.common.CalendarPlanInput
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.common.NearbyDepartureCoach
import com.syrmos.core.common.NotificationSettings
import com.syrmos.core.common.extensions.distanceInMeters
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.data.sync.AnnouncementsRepository
import com.syrmos.core.data.sync.ScheduleSyncRepository
import com.syrmos.core.data.sync.StationOffsetsRepository
import com.syrmos.core.domain.usecase.ComputeDeparturesFromBandsUseCase
import com.syrmos.core.domain.usecase.PlanByArrivalUseCase
import com.syrmos.core.domain.usecase.PlanJourneyUseCase
import com.syrmos.core.model.location.UserLocation
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/**
 * 3.0.2 calendar leave-by for every event with a location in the next 48 h.
 *
 * For each eligible event: classify it (shared [CalendarLeaveBy]), resolve the
 * destination (airport keywords to ATH/SKG, then a station name in the location,
 * then the platform Geocoder bounded to Attica / Thessaloniki; unresolved events
 * are skipped, never guessed), take the origin from the last known location, plan
 * with the existing arrive-by planner, compute leaveAt, and schedule the headsUp /
 * leaveNow (and flight checklist) alarms. WorkManager re-checks at leaveAt - 60
 * and - 20 min; alarms move only when leaveAt shifts by 5 min or more. Without a
 * location at re-check time the earlier schedule is kept. All on device.
 */
object CalendarLeaveByScheduler {
    private const val TAG = "CalendarLeaveBy"
    private const val PREFS = "syrmos_calendar_leave_by"
    private const val ATH_STATION = "M3_AER"
    private val SKG = 40.5197 to 22.9709
    private val athens = TimeZone.of("Europe/Athens")

    private data class Box(val south: Double, val west: Double, val north: Double, val east: Double)
    private val ATTICA = Box(37.60, 23.30, 38.35, 24.10)
    private val THESSALONIKI = Box(40.45, 22.75, 40.75, 23.15)

    suspend fun planAll(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!NotificationSettings.calendarReminders.value || !hasAirportCalendarPermission()) {
            cancelAll(app)
            return@withContext
        }
        val now = System.currentTimeMillis() / 1000
        val events = loadUpcomingCalendarEvents()
        val location = StationGeofenceManager.lastLocation(app)
        if (location == null) {
            Log.i(TAG, "no location; keeping the earlier schedule")
            return@withContext
        }
        val koin = GlobalContext.get()
        runCatching { koin.get<ScheduleSyncRepository>().hydrateFromBundleIfNeeded() }
        runCatching { koin.get<StationOffsetsRepository>().hydrateFromBundleIfNeeded() }
        val stations = runCatching { koin.get<StationRepositoryImpl>().getAllStations().first() }.getOrDefault(emptyList())
        val origin = nearestStation(stations, location.latitude, location.longitude) ?: return@withContext
        val originWalk = NearbyDepartureCoach.walkMinutes(
            distanceInMeters(location.latitude, location.longitude, origin.latitude, origin.longitude).toDouble(),
        )
        val kinds = NotificationSettings.calendarKinds.value
        val keep = mutableSetOf<String>()
        for (e in events) {
            val kind = CalendarLeaveBy.classify(e.title, e.location)
            if (kinds[kind] == false) continue
            if (e.location.isBlank() && kind == CalendarEventKind.DEFAULT) continue
            val destination = resolveDestination(app, e, kind, stations)
            val eligibility = CalendarLeaveBy.eligibility(
                startEpochSeconds = e.startEpochMillis / 1000, now = now,
                allDay = e.allDay, declined = e.declined, destinationResolved = destination != null,
            )
            if (eligibility != CalendarEligibility.ELIGIBLE || destination == null || destination.id == origin.id) continue
            val planned = planEvent(e, kind, origin, originWalk, destination, now) ?: continue
            keep += e.id
            val oldLeaveAt = prefs.getLong("leave_${e.id}", Long.MIN_VALUE)
            if (oldLeaveAt != Long.MIN_VALUE && !CalendarLeaveBy.needsReschedule(oldLeaveAt, planned.leaveAt)) continue
            schedule(app, e, planned, now)
            prefs.edit().putLong("leave_${e.id}", planned.leaveAt).apply()
        }
        // Events that disappeared, moved out of the window or were declined.
        prefs.all.keys.filter { it.startsWith("leave_") }.map { it.removePrefix("leave_") }
            .filter { it !in keep }
            .forEach { id -> cancelEvent(app, id); prefs.edit().remove("leave_$id").apply() }
    }

    private class Planned(
        val kind: CalendarEventKind,
        val leaveAt: Long,
        val bufferMinutes: Int,
        val notifications: List<Pair<CalendarNotificationKind, Long>>,
        val lineId: String,
        val originName: String,
        val firstDepartureEpoch: Long?,
        val originWalkMinutes: Int,
    )

    private suspend fun planEvent(
        e: CalendarEventInfo,
        kind: CalendarEventKind,
        origin: Station,
        originWalk: Int,
        destination: Station,
        now: Long,
    ): Planned? {
        val koin = GlobalContext.get()
        val start = e.startEpochMillis / 1000
        val buffer = CalendarLeaveBy.bufferMinutes(kind, NotificationSettings.flightBufferMinutes.value)
        val arriveBy = start - buffer * 60L
        val route = runCatching { koin.get<PlanJourneyUseCase>().invoke(origin.id, destination.id).first() }.getOrNull() ?: return null
        val rideLegs = route.segments.filter { !it.isTransfer }
        val firstLine = rideLegs.firstOrNull()?.lineId ?: return null
        // The arrive-by planner gives real departures when the event is today
        // (Athens); for later days the route estimate is used.
        val sameDay = Instant.fromEpochSeconds(arriveBy).toLocalDateTime(athens).date ==
            Instant.fromEpochSeconds(now).toLocalDateTime(athens).date
        val arriveLocal = Instant.fromEpochSeconds(arriveBy).toLocalDateTime(athens)
        val solution = if (sameDay) runCatching {
            koin.get<PlanByArrivalUseCase>().invoke(origin.id, destination.id, arriveLocal.hour * 60 + arriveLocal.minute)
        }.getOrNull() else null
        val rideMinutes = solution?.let { it.projectedArrivalMinutes - it.firstLegDepartureMinutes } ?: route.totalMinutes
        val headway = firstLegHeadway(origin.id, firstLine)
        val disrupted = runCatching {
            val d = koin.get<AnnouncementsRepository>().lineDisruptions.value
            rideLegs.any { (d[it.lineId]?.rank ?: 0) >= 1 }
        }.getOrDefault(false)
        val plan = CalendarLeaveBy.plan(
            CalendarPlanInput(
                title = e.title, location = e.location, startEpochSeconds = start,
                journeyMinutes = rideMinutes + originWalk, transfers = (rideLegs.size - 1).coerceAtLeast(0),
                firstLegHeadwayMinutes = headway, raining = false, alertOnRoute = disrupted,
                flightBufferMinutes = NotificationSettings.flightBufferMinutes.value,
            ),
            now,
        )
        val firstDep = solution?.let { arriveBy - (arriveLocal.hour * 60 + arriveLocal.minute - it.firstLegDepartureMinutes) * 60L }
        return Planned(
            kind = plan.kind,
            leaveAt = plan.leaveAtEpochSeconds,
            bufferMinutes = plan.bufferMinutes,
            notifications = plan.notifications.map { it.kind to it.atEpochSeconds },
            lineId = firstLine,
            originName = stationLabel(origin, LocalizationManager.language.value),
            firstDepartureEpoch = firstDep,
            originWalkMinutes = originWalk,
        )
    }

    private fun firstLegHeadway(stationId: String, lineId: String): Int {
        val projector = runCatching { GlobalContext.get().get<ComputeDeparturesFromBandsUseCase>() }.getOrNull() ?: return 6
        val gaps = Direction.entries.mapNotNull { dir ->
            val deps = runCatching { projector.invoke(listOf(lineId), dir, limit = 3, stationId = stationId) }.getOrDefault(emptyList())
            if (deps.size >= 2) deps[1].minutesAway - deps[0].minutesAway else null
        }.filter { it > 0 }
        return gaps.minOrNull() ?: 6
    }

    private suspend fun resolveDestination(
        context: Context,
        e: CalendarEventInfo,
        kind: CalendarEventKind,
        stations: List<Station>,
    ): Station? {
        val text = CalendarLeaveBy.fold("${e.title} ${e.location}")
        if (kind == CalendarEventKind.FLIGHT) {
            val skg = Regex("(^|[^a-z])skg([^a-z]|$)").containsMatchIn(text) ||
                listOf("thessaloniki", "θεσσαλονικ", "makedonia", "μακεδονια").any { it in text }
            return if (skg) nearestStation(stations, SKG.first, SKG.second, maxMeters = 3000.0)
            else stations.firstOrNull { it.id == ATH_STATION }
        }
        val loc = CalendarLeaveBy.fold(e.location)
        if (loc.isBlank()) return null
        // A station name inside the location text, longest name first so
        // "Agios Antonios" beats "Agios". Short names are too ambiguous.
        stations.flatMap { s -> listOf(s.name, s.nameEl).filter { it.length >= 4 }.map { CalendarLeaveBy.fold(it) to s } }
            .sortedByDescending { it.first.length }
            .firstOrNull { (name, _) -> Regex("(^|[^0-9a-zα-ω])" + Regex.escape(name) + "([^0-9a-zα-ω]|$)").containsMatchIn(loc) }
            ?.let { return it.second }
        return geocode(context, e.location, stations)
    }

    @Suppress("DEPRECATION")
    private fun geocode(context: Context, query: String, stations: List<Station>): Station? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context)
        for (box in listOf(ATTICA, THESSALONIKI)) {
            val hit = runCatching { geocoder.getFromLocationName(query, 1, box.south, box.west, box.north, box.east) }
                .getOrNull()?.firstOrNull() ?: continue
            if (hit.latitude !in box.south..box.north || hit.longitude !in box.west..box.east) continue
            return nearestStation(stations, hit.latitude, hit.longitude, maxMeters = 2500.0)
        }
        return null
    }

    private fun nearestStation(stations: List<Station>, lat: Double, lon: Double, maxMeters: Double = Double.MAX_VALUE): Station? =
        stations.minByOrNull { distanceInMeters(lat, lon, it.latitude, it.longitude) }
            ?.takeIf { distanceInMeters(lat, lon, it.latitude, it.longitude).toDouble() <= maxMeters }

    private fun stationLabel(s: Station, lang: AppLanguage) =
        if (lang == AppLanguage.GREEK) s.nameEl.ifBlank { s.name } else s.name

    private fun schedule(context: Context, e: CalendarEventInfo, p: Planned, now: Long) {
        cancelEvent(context, e.id)
        val lang = LocalizationManager.language.value
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
        for ((kind, at) in p.notifications) {
            if (at <= now) continue
            val next = p.firstDepartureEpoch?.let { ((it - p.leaveAt) / 60).coerceAtLeast(0) }
                ?: p.originWalkMinutes.toLong()
            val params = mapOf(
                "title" to e.title.ifBlank { e.location },
                "line" to p.lineId,
                "station" to p.originName,
                "next" to next.toString(),
                "buffer" to p.bufferMinutes.toString(),
                "fare" to "",
            )
            val title = CalendarLeaveByCopy.title(kind, lang)
            val body = CalendarLeaveByCopy.body(kind, p.kind, lang, params)
            val pi = alarmIntent(context, e.id, kind, title, body)
            if (canExact) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at * 1000, pi)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at * 1000, pi)
        }
        // Re-check at leaveAt - 60 and - 20 min with fresh location and data.
        for (minutes in listOf(60L, 20L)) {
            val delay = p.leaveAt - minutes * 60 - now
            if (delay <= 0) continue
            WorkManager.getInstance(context).enqueueUniqueWork(
                "calendar_recheck_${e.id}_$minutes",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CalendarRecheckWorker>().setInitialDelay(delay, TimeUnit.SECONDS).build(),
            )
        }
        Log.i(TAG, "scheduled ${e.id} (${p.kind.key}) leaveAt=${p.leaveAt}")
    }

    private fun cancelEvent(context: Context, id: String) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        CalendarNotificationKind.entries.forEach { alarm.cancel(alarmIntent(context, id, it, null, null)) }
    }

    fun cancelAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.all.keys.filter { it.startsWith("leave_") }.forEach { cancelEvent(context, it.removePrefix("leave_")) }
        prefs.edit().clear().apply()
    }

    private fun alarmIntent(context: Context, eventId: String, kind: CalendarNotificationKind, title: String?, body: String?): PendingIntent {
        val key = "$eventId|${kind.key}"
        val intent = Intent(context, CalendarLeaveByReceiver::class.java).apply {
            action = "com.syrmos.android.CALENDAR_LEAVE_BY.$key"
            putExtra(CalendarLeaveByReceiver.EXTRA_ID, key.hashCode())
            if (title != null) putExtra(CalendarLeaveByReceiver.EXTRA_TITLE, title)
            if (body != null) putExtra(CalendarLeaveByReceiver.EXTRA_BODY, body)
        }
        return PendingIntent.getBroadcast(context, key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

/** WorkManager re-check at leaveAt - 60 / - 20 min (see [CalendarLeaveByScheduler]). */
class CalendarRecheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { CalendarLeaveByScheduler.planAll(applicationContext) }
        return Result.success()
    }
}

/** Posts a scheduled calendar leave-by notification on channel `calendar_leave_by`. */
class CalendarLeaveByReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!NotificationSettings.calendarReminders.value || !BroadcastNotifier.canPost(context)) return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CalendarLeaveByCopy.channelName(LocalizationManager.language.value), NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val id = intent.getIntExtra(EXTRA_ID, 7300)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, id, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val n = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .apply { if (open != null) setContentIntent(open) }
            .build()
        manager.notify(id, n)
    }

    companion object {
        const val CHANNEL_ID = "calendar_leave_by"
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
    }
}
