package com.syrmos.android

import android.Manifest
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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Drop the launch theme (full-screen StartScreen image set on
        // the activity in AndroidManifest.xml) BEFORE super so the
        // splash is only visible during the cold-start window. After
        // super.onCreate the regular Theme.Syrmos paints behind the
        // Compose tree as normal.
        setTheme(R.style.Theme_Syrmos)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

    override fun onDestroy() {
        setLocationPermissionRequester(null)
        super.onDestroy()
    }
}
