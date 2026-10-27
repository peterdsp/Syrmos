package com.syrmos.android.proactive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import com.syrmos.android.push.BroadcastNotifier
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.CoachDeparture
import com.syrmos.core.common.CoachGuard
import com.syrmos.core.common.CoachGuardInput
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.common.NearbyCoachCopy
import com.syrmos.core.common.NearbyCoachNotification
import com.syrmos.core.common.NearbyDepartureCoach
import com.syrmos.core.common.NearbyDirection
import com.syrmos.core.common.NotificationSettings
import com.syrmos.core.common.extensions.distanceInMeters
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.data.sync.ClosuresRepository
import com.syrmos.core.data.sync.ScheduleSyncRepository
import com.syrmos.core.data.sync.StationOffsetsRepository
import com.syrmos.core.domain.usecase.ComputeDeparturesFromBandsUseCase
import com.syrmos.core.domain.usecase.GetLastTrainUseCase
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.context.GlobalContext

/**
 * Turns a geofence DWELL into the nearby next-train notification (3.0.2):
 * guards (opt-in, GO active, quiet hours, speed, per-station cooldown, daily cap),
 * then per (line, destination) the next two departures from the OFFLINE projector,
 * walk minutes from the distance, and the coach headline in the app language.
 * A closed station gets the closure copy instead of times.
 */
object NearbyDepartureNotifier {
    const val CHANNEL_ID = "nearby_departures"
    private const val TAG = "NearbyNotifier"
    private const val NOTIF_BASE = 7200
    private val athens = TimeZone.of("Europe/Athens")

    suspend fun handle(context: Context, stationId: String, location: Location?) {
        val koin = GlobalContext.get()
        val now = Clock.System.now()
        val nowSec = now.epochSeconds
        val local = now.toLocalDateTime(athens)
        val athensDate = local.date.toString()
        val prefs = NearbyPrefs(context)
        val station = koin.get<StationRepositoryImpl>().getStationById(stationId).firstOrNull() ?: return
        val guard = NearbyDepartureCoach.guard(
            CoachGuardInput(
                enabled = NotificationSettings.nearbyDepartures.value,
                goJourneyActive = com.syrmos.app.journey.ActiveJourneyRepository.active.value != null,
                athensHour = local.hour,
                speedKmh = location?.takeIf { it.hasSpeed() }?.speed?.let { it * 3.6 },
                lastNotifiedForStationEpochSeconds = prefs.lastNotified(station.name),
                notificationsToday = prefs.countToday(athensDate),
                now = nowSec,
            ),
        )
        if (guard != CoachGuard.ALLOW) {
            Log.i(TAG, "skip ${station.id}: ${guard.key}")
            return
        }
        val lang = LocalizationManager.language.value
        val distance = location?.let { distanceInMeters(it.latitude, it.longitude, station.latitude, station.longitude).toDouble() } ?: 0.0
        val walk = NearbyDepartureCoach.walkMinutes(distance)

        val closures = koin.get<ClosuresRepository>()
        runCatching { closures.hydrateFromBundleIfNeeded() }
        val effect = closures.effectForStation(station.id, nowSec)
        val closed = effect.active && !effect.canBoard

        val directions = if (closed) emptyList() else directionsFor(station, lang)
        val content = NearbyCoachNotification.build(
            stationId = station.id,
            stationName = stationLabel(station, lang),
            walkMinutes = walk,
            directions = directions,
            stationClosed = closed,
            athensDate = athensDate,
            lang = lang,
        ) ?: return
        post(context, station.id, content.title, (content.lines + content.headline).joinToString("\n"), content.headline)
        prefs.record(station.name, nowSec, athensDate)
    }

    /** Next two departures per (line, destination) from the bundled bands projector. */
    private suspend fun directionsFor(station: Station, lang: AppLanguage): List<NearbyDirection> {
        val koin = GlobalContext.get()
        runCatching { koin.get<ScheduleSyncRepository>().hydrateFromBundleIfNeeded() }
        runCatching { koin.get<StationOffsetsRepository>().hydrateFromBundleIfNeeded() }
        val projector = koin.get<ComputeDeparturesFromBandsUseCase>()
        val lastTrain = koin.get<GetLastTrainUseCase>()
        val lines = runCatching { koin.get<com.syrmos.core.data.repository.LineRepositoryImpl>().getAllLines().firstOrNull().orEmpty() }
            .getOrDefault(emptyList())
        // Terminal names come in English; show the station's own Greek name in Greek.
        val allStations = runCatching { koin.get<StationRepositoryImpl>().getAllStations().firstOrNull().orEmpty() }
            .getOrDefault(emptyList())
        val byName = allStations.associateBy { it.name.lowercase() }
        // Seed names abbreviate ("Douk. Plakentias" for the "Doukissis Plakentias" terminal).
        fun matchTerminal(terminal: String): Station? = byName[terminal.lowercase()] ?: allStations.firstOrNull { st ->
            val a = st.name.lowercase().split(' ').filter { it.isNotBlank() }
            val b = terminal.lowercase().split(' ').filter { it.isNotBlank() }
            a.size == b.size && a.zip(b).all { (x, y) -> y.startsWith(x.trimEnd('.')) }
        }
        val out = mutableListOf<NearbyDirection>()
        for (lineId in station.lineIds.distinct()) {
            val line = lines.firstOrNull { it.id == lineId }
            for (direction in Direction.entries) {
                val deps = runCatching {
                    projector.invoke(lineIds = if (lineId == "M3") listOf("M3", "M3_AIR") else listOf(lineId), direction = direction, limit = 6, stationId = station.id)
                }.getOrDefault(emptyList())
                if (deps.isEmpty()) continue
                val last = runCatching { lastTrain.invoke(station.id, lineId, direction) }.getOrNull()
                // Same rule as the station board: outbound airport runs are their own
                // direction ("Airport"), everything else goes to the line terminal.
                deps.groupBy { direction == Direction.OUTBOUND && (it.lineId == "M3_AIR" || it.serviceType == "airport") }
                    .forEach { (airport, list) ->
                        val terminal = if (direction == Direction.OUTBOUND) line?.terminalB else line?.terminalA
                        val destination = when {
                            airport -> airportLabel(lang)
                            !terminal.isNullOrBlank() -> matchTerminal(terminal)?.let { stationLabel(it, lang) }
                                ?: com.syrmos.core.common.StationNameTranslator.localizeEnglish(terminal, lang)
                            else -> return@forEach
                        }
                        if (!airport && terminal.equals(station.name, ignoreCase = true)) return@forEach
                        out += NearbyDirection(
                            lineId = lineId,
                            destination = destination,
                            departures = list.distinctBy { it.time }.sortedBy { it.minutesAway }.take(2).map { d ->
                                CoachDeparture(d.minutesAway, isLast = last != null && last.time == d.time)
                            },
                        )
                    }
            }
        }
        return out.distinctBy { it.lineId to it.destination }
    }

    private fun stationLabel(station: Station, lang: AppLanguage): String =
        if (lang == AppLanguage.GREEK) station.nameEl.ifBlank { station.name } else station.name

    private fun airportLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "Αεροδρόμιο"
        AppLanguage.ALBANIAN -> "Aeroporti"
        AppLanguage.ITALIAN -> "Aeroporto"
        else -> "Airport"
    }

    private fun post(context: Context, stationId: String, title: String, body: String, summary: String) {
        if (!BroadcastNotifier.canPost(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, NearbyCoachCopy.channelName(LocalizationManager.language.value), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notif_type", "nearby_station")
            putExtra("notif_alert_id", stationId)
        }
        val content = launch?.let {
            PendingIntent.getActivity(context, stationId.hashCode(), it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .apply { if (content != null) setContentIntent(content) }
            .build()
        manager.notify(NOTIF_BASE + (stationId.hashCode() and 0xFFF), notification)
    }
}

/** Cooldown per physical station (keyed by name so interchange ids share it) and the daily cap. */
internal class NearbyPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("syrmos_nearby", Context.MODE_PRIVATE)

    fun lastNotified(stationKey: String): Long? =
        prefs.getLong("last_$stationKey", 0L).takeIf { it > 0 }

    fun countToday(athensDate: String): Int =
        if (prefs.getString("day", null) == athensDate) prefs.getInt("count", 0) else 0

    fun record(stationKey: String, now: Long, athensDate: String) {
        val count = countToday(athensDate) + 1
        prefs.edit().putLong("last_$stationKey", now).putString("day", athensDate).putInt("count", count).apply()
    }
}
