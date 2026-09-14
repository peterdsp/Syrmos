package com.syrmos.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.content.pm.PackageManager
import com.syrmos.app.journey.ActiveJourneyRepository
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.model.journey.ActiveJourney
import com.syrmos.core.model.journey.JourneyPhase
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Phase N J08: the Android glance surface for an active GO journey, the peer of the
 * iOS journey Live Activity. Surfaces the started journey as an ongoing
 * notification showing the current action (from the stored [JourneyPhase]) and the
 * origin -> destination pair, updated on every step.
 *
 * Observes the shared [ActiveJourneyRepository] `active` flow (the same source the
 * in-app GO screen and the iOS Live Activity read), so all surfaces stay in sync by
 * construction, and cancels the notification when the session ends. A standard
 * ongoing notification (no promoted Live Update yet); Android 16 ProgressStyle
 * promotion is a later, device-gated slice.
 */
class JourneyNotifier(
    private val context: Context,
    private val stationRepo: StationRepositoryImpl,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var stations: Map<String, Station> = emptyMap()

    fun start() {
        ensureChannel()
        scope.launch {
            stations = runCatching {
                stationRepo.getAllStations().first().associateBy { it.id }
            }.getOrDefault(emptyMap())
            ActiveJourneyRepository.active.collect { active ->
                if (active == null) manager.cancel(NOTIFICATION_ID) else post(active)
            }
        }
    }

    private fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            channelName(LocalizationManager.language.value),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
    }

    private fun post(active: ActiveJourney) {
        // POST_NOTIFICATIONS is runtime-gated on Android 13+. If declined, the
        // in-app GO screen still works; we just don't post.
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val lang = LocalizationManager.language.value
        val legs = active.itinerarySnapshot.legs
        val fromId = legs.firstOrNull()?.fromId
        val toId = legs.lastOrNull()?.toId
        val pair = "${name(fromId, lang)} → ${name(toId, lang)}"

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(actionLabel(active.phase, lang))
            .setContentText(pair)
            .setStyle(Notification.BigTextStyle().bigText(pair))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun name(id: String?, lang: AppLanguage): String {
        val s = id?.let { stations[it] } ?: return id ?: "?"
        return if (lang == AppLanguage.GREEK) s.nameEl else s.name
    }

    private fun channelName(lang: AppLanguage) = when (lang) {
        AppLanguage.GREEK -> "Ενεργό ταξίδι"
        AppLanguage.ALBANIAN -> "Udhëtim aktiv"
        AppLanguage.ITALIAN -> "Viaggio attivo"
        else -> "Active journey"
    }

    /** The one current action, from the stored phase, mirroring the GO hero + Live Activity. */
    private fun actionLabel(phase: JourneyPhase, lang: AppLanguage): String = when (phase) {
        JourneyPhase.READY_TO_BOARD -> t(lang, "Ready to board", "Έτοιμος για επιβίβαση", "Gati për të hipur", "Pronto a salire")
        JourneyPhase.RIDING -> t(lang, "On the train", "Στο τρένο", "Në tren", "Sul treno")
        JourneyPhase.ALIGHT_SOON -> t(lang, "Get off soon", "Κατέβα σύντομα", "Zbrit së shpejti", "Scendi a breve")
        JourneyPhase.TRANSFER -> t(lang, "Change here", "Αλλαγή εδώ", "Ndërro këtu", "Cambia qui")
        JourneyPhase.WAITING_FOR_NEXT_LEG -> t(lang, "Waiting for next train", "Αναμονή επόμενου τρένου", "Duke pritur trenin tjetër", "In attesa del prossimo treno")
        JourneyPhase.LOCATION_UNCERTAIN -> t(lang, "Confirm your stop", "Επιβεβαίωσε τη στάση", "Konfirmo ndalesën", "Conferma la fermata")
        JourneyPhase.ARRIVED -> t(lang, "Arrived", "Άφιξη", "Mbërritur", "Arrivato")
        JourneyPhase.ENDED -> t(lang, "Journey in progress", "Διαδρομή σε εξέλιξη", "Udhëtim në vazhdim", "Viaggio in corso")
    }

    private fun t(lang: AppLanguage, en: String, el: String, sq: String, it: String) = when (lang) {
        AppLanguage.GREEK -> el
        AppLanguage.ALBANIAN -> sq
        AppLanguage.ITALIAN -> it
        else -> en
    }

    companion object {
        private const val CHANNEL_ID = "journey_tracking"
        private const val NOTIFICATION_ID = 4301
    }
}
