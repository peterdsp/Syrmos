package com.syrmos.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.syrmos.app.SyrmosApp
import com.syrmos.app.platform.setLocationPermissionRequester
import com.syrmos.app.platform.setAirportCalendarPermissionRequester
import com.syrmos.app.platform.setNotificationPermissionRequester
import com.syrmos.app.platform.setPendingAssistantQuery
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var pending: CompletableDeferred<Unit>? = null
    private var notifPending: CompletableDeferred<Unit>? = null
    private var calendarPending: CompletableDeferred<Unit>? = null
    private var backgroundLocationPending: CompletableDeferred<Unit>? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        pending?.complete(Unit)
        pending = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notifPending?.complete(Unit)
        notifPending = null
    }

    private val calendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        calendarPending?.complete(Unit)
        calendarPending = null
    }

    // 3.0.2 nearby next-train: "Allow all the time" location. On Android 11+ the
    // system answers this request by opening the app's location settings page.
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        backgroundLocationPending?.complete(Unit)
        backgroundLocationPending = null
    }

    // Location for the "Near Me" Glance widget (nearest station + walking
    // distance). Asked once on launch when not already granted; declining just
    // keeps the widget on its pinned-station fallback. On grant we refresh the
    // snapshot so the widget picks up the nearest station right away.
    private val startupLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            com.syrmos.android.widget.SnapshotWorker.refreshNow(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Drop the launch theme (full-screen StartScreen image set on
        // the activity in AndroidManifest.xml) BEFORE super so the
        // splash is only visible during the cold-start window. After
        // super.onCreate the regular Theme.Syrmos paints behind the
        // Compose tree as normal.
        setTheme(R.style.Theme_Syrmos)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ask for location on launch when it hasn't been granted, so the
        // "Near Me" widget can resolve the nearest station. The OS stops
        // showing the dialog after the user has permanently declined, so this
        // does not nag. Onboarding still asks in-context via the requester below.
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            startupLocationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }

        // Service broadcasts (closures, disruptions) arrive over FCM, which is
        // silently dropped on API 33+ when POST_NOTIFICATIONS was never granted.
        // Location was asked on launch but this was not, so push could never
        // reach a rider who had not opened a notification-specific screen.
        // Pre-33 grants notifications at install time, so there is nothing to ask.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Permission is requested from the onboarding flow now, not on launch.
        setLocationPermissionRequester {
            val deferred = CompletableDeferred<Unit>()
            pending = deferred
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
            deferred.await()
        }

        setNotificationPermissionRequester {
            val deferred = CompletableDeferred<Unit>()
            notifPending = deferred
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            deferred.await()
        }

        setAirportCalendarPermissionRequester {
            val deferred = CompletableDeferred<Unit>()
            calendarPending = deferred
            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            deferred.await()
        }

        installProactiveHooks()

        handleAssistantIntent(intent)
        handleNotificationIntent(intent)

        setContent {
            SyrmosApp()
        }
    }

    /**
     * 3.0.2 Settings toggles: run the permission flow when a feature is turned on
     * (switching it back off if the rider declines), and start or stop the work.
     */
    private fun installProactiveHooks() {
        val app = applicationContext
        com.syrmos.core.common.ProactiveFeatureHooks.onBroadcastsChanged = { on ->
            if (on) {
                lifecycleScope.launch {
                    com.syrmos.app.platform.requestNotificationPermission()
                    com.syrmos.android.push.SyrmosPush.sync(app, force = true)
                    com.syrmos.android.notification.AlertCheckWorker.pollBroadcastsNow(app)
                }
            } else {
                com.syrmos.android.push.SyrmosPush.unregister(app)
            }
        }
        // Nearby next-train notifications need ACCESS_BACKGROUND_LOCATION, which is
        // held back until the Play Console location declaration is approved. Until
        // then the hook stays unset (the Settings toggle hides) and geofences are
        // never registered.
        if (BuildConfig.NEARBY_NOTIFICATIONS) com.syrmos.core.common.ProactiveFeatureHooks.onNearbyDeparturesChanged = { on ->
            lifecycleScope.launch {
                if (on) {
                    com.syrmos.app.platform.requestNotificationPermission()
                    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        val d = CompletableDeferred<Unit>()
                        pending = d
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                        d.await()
                    }
                    // Android 10+ grants background location in a separate step.
                    if (Build.VERSION.SDK_INT >= 29 &&
                        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                        checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
                    ) {
                        val d = CompletableDeferred<Unit>()
                        backgroundLocationPending = d
                        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        d.await()
                    }
                    if (!com.syrmos.android.proactive.StationGeofenceManager.hasPermissions(app)) {
                        com.syrmos.core.common.NotificationSettings.setNearbyDepartures(false)
                    }
                }
                com.syrmos.android.proactive.StationGeofenceManager.refresh(app)
            }
        }
        com.syrmos.core.common.ProactiveFeatureHooks.onCalendarRemindersChanged = { on ->
            lifecycleScope.launch {
                if (on) {
                    com.syrmos.app.platform.requestNotificationPermission()
                    if (!com.syrmos.app.platform.requestAirportCalendarPermission()) {
                        com.syrmos.core.common.NotificationSettings.setCalendarReminders(false)
                    }
                }
                com.syrmos.android.proactive.CalendarLeaveByScheduler.planAll(app)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAssistantIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleAssistantIntent(intent: Intent?) {
        val question = intent?.getStringExtra("question")
        val station = intent?.getStringExtra("station")
        val line = intent?.getStringExtra("line")
        val query = question
            ?: station?.let { "Next departure from $it" }
            ?: line?.let { "Service alerts for line $it" }
        if (query != null) {
            setPendingAssistantQuery(query)
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        // 3.0.2 broadcasts carry a deep link ("syrmos://station/M2_SYN"): from our
        // own notification (notif_deep_link) or from the system-shown FCM one,
        // whose data keys arrive as extras (deepLink, broadcastId).
        val deepLink = intent?.getStringExtra("notif_deep_link") ?: intent?.getStringExtra("deepLink")
        intent?.getStringExtra("broadcastId")?.let {
            com.syrmos.android.push.BroadcastPrefs.addDelivered(this, it)
        }
        val stationFromLink = deepLink?.takeIf { it.startsWith("syrmos://station/") }?.removePrefix("syrmos://station/")
        if (!stationFromLink.isNullOrBlank()) {
            com.syrmos.app.platform.setPendingNotificationDeepLink("station", stationFromLink)
            return
        }
        val notifType = intent?.getStringExtra("notif_type") ?: return
        val alertId = intent.getStringExtra("notif_alert_id")
        com.syrmos.app.platform.setPendingNotificationDeepLink(notifType, alertId)
    }

    override fun onResume() {
        super.onResume()
        // Nudge the Glance widgets when the user returns to the app. The
        // AppWidget update period is 30 min (Android minimum) which is too slow
        // for a "how long till the next train" tile, so we piggyback on the app
        // lifecycle: opening Syrmos and pressing Home again refreshes the
        // snapshot the widgets read within seconds.
        com.syrmos.android.widget.SnapshotWorker.refreshNow(this)
        // 3.0.2: poll broadcasts on app open (push may be off or unconfigured),
        // keep the push registration current, and refresh geofences and calendar
        // reminders with the latest location. Each is a no-op when its toggle is off.
        com.syrmos.android.notification.AlertCheckWorker.pollBroadcastsNow(this)
        com.syrmos.android.push.SyrmosPush.sync(this)
        val app = applicationContext
        lifecycleScope.launch {
            runCatching { com.syrmos.android.proactive.StationGeofenceManager.refresh(app) }
            runCatching { com.syrmos.android.proactive.CalendarLeaveByScheduler.planAll(app) }
        }
    }

    override fun onDestroy() {
        com.syrmos.core.common.ProactiveFeatureHooks.onBroadcastsChanged = null
        com.syrmos.core.common.ProactiveFeatureHooks.onNearbyDeparturesChanged = null
        com.syrmos.core.common.ProactiveFeatureHooks.onCalendarRemindersChanged = null
        setLocationPermissionRequester(null)
        setAirportCalendarPermissionRequester(null)
        super.onDestroy()
    }
}
