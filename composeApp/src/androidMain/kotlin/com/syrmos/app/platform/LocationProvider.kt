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
private var permissionRequester: (suspend () -> Unit)? = null

fun initAndroidPlatform(context: Context) {
    com.syrmos.core.common.initLocalization(context)
    com.syrmos.core.common.initThemePlatform(context)
}

fun initLocationProvider(context: Context) {
    appContext = context.applicationContext
}

/** The stored application context, once [initLocationProvider] has run. */
internal fun androidPlatformContext(): Context? = appContext

/** Called from MainActivity so the Compose layer can trigger the
 *  ActivityResultLauncher without holding a direct Activity reference. */
fun setLocationPermissionRequester(requester: (suspend () -> Unit)?) {
    permissionRequester = requester
}

fun hasLocationPermission(): Boolean {
    val ctx = appContext ?: return false
    return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

actual suspend fun requestLocationPermission() {
    if (hasLocationPermission()) return
    permissionRequester?.invoke()
}

private const val ONBOARDING_KEY = "syrmos.onboarding.completed.v1"
private const val PREFS_NAME = "syrmos_prefs"

actual fun readOnboardingCompleted(): Boolean {
    val ctx = appContext ?: return false
    return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(ONBOARDING_KEY, false)
}

actual fun markOnboardingCompleted() {
    val ctx = appContext ?: return
    ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(ONBOARDING_KEY, true)
        .apply()
}

private const val WHATS_NEW_KEY = "syrmos.whatsnew.version"

actual fun readLastWhatsNewVersion(): String? =
    appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.getString(WHATS_NEW_KEY, null)

actual fun markWhatsNewSeen(version: String) {
    appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ?.edit()?.putString(WHATS_NEW_KEY, version)?.apply()
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
