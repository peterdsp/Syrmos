package com.syrmos.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.syrmos.app.SyrmosApp
import com.syrmos.app.platform.setLocationPermissionRequester
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {

    private var pending: CompletableDeferred<Unit>? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        pending?.complete(Unit)
        pending = null
    }

    // Lets the departure-tracking ongoing notification show on Android 13+.
    // Asked once on launch; declining just means no Lock Screen countdown, the
    // in-app card still works.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

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

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

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

        setContent {
            SyrmosApp()
        }
    }

    override fun onResume() {
        super.onResume()
        // Nudge the Glance widgets when the user returns to the app. The
        // AppWidget update period is 30 min (Android minimum) which is too slow
        // for a "how long till the next train" tile, so we piggyback on the app
        // lifecycle: opening Syrmos and pressing Home again refreshes the
        // snapshot the widgets read within seconds.
        com.syrmos.android.widget.SnapshotWorker.refreshNow(this)
    }

    override fun onDestroy() {
        setLocationPermissionRequester(null)
        super.onDestroy()
    }
}
