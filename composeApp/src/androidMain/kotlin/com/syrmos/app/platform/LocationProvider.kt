package com.syrmos.app.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.syrmos.core.model.location.UserLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private var appContext: Context? = null

fun initAndroidPlatform(context: Context) {
    com.syrmos.core.common.initLocalization(context)
}

fun initLocationProvider(context: Context) {
    appContext = context.applicationContext
}

fun hasLocationPermission(): Boolean {
    val ctx = appContext ?: return false
    return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
actual suspend fun requestUserLocation(): UserLocation? = withContext(Dispatchers.IO) {
    val ctx = appContext ?: return@withContext null
    if (!hasLocationPermission()) return@withContext null

    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null

    val lastKnown = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

    if (lastKnown != null) {
        return@withContext UserLocation(lastKnown.latitude, lastKnown.longitude)
    }

    withTimeoutOrNull(5_000L) {
        suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resume(UserLocation(location.latitude, location.longitude))
                }
                override fun onProviderDisabled(provider: String) {}
                override fun onProviderEnabled(provider: String) {}
                @Deprecated("Deprecated") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            val provider = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            lm.requestLocationUpdates(provider, 0L, 0f, listener)
            cont.invokeOnCancellation { lm.removeUpdates(listener) }
        }
    }
}
