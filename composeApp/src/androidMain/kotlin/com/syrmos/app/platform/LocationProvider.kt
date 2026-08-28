package com.syrmos.app.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.syrmos.core.model.location.UserLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private var appContext: Context? = null
private var permissionRequester: (suspend () -> Unit)? = null
private var notifPermissionRequester: (suspend () -> Unit)? = null

fun initAndroidPlatform(context: Context) {
    com.syrmos.core.common.initLocalization(context)
    com.syrmos.core.common.initThemePlatform(context)
    com.syrmos.core.common.initNotificationSettingsPlatform(context)
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

fun setNotificationPermissionRequester(requester: (suspend () -> Unit)?) {
    notifPermissionRequester = requester
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

actual suspend fun requestNotificationPermission() {
    val ctx = appContext ?: return
    if (android.os.Build.VERSION.SDK_INT >= 33 &&
        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        notifPermissionRequester?.invoke()
    }
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

private val _pendingAssistantQuery = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
val pendingAssistantQuery: kotlinx.coroutines.flow.StateFlow<String?> = _pendingAssistantQuery

fun setPendingAssistantQuery(query: String?) {
    _pendingAssistantQuery.value = query
}

actual fun consumePendingAssistantQuery(): String? {
    val current = _pendingAssistantQuery.value
    _pendingAssistantQuery.value = null
    return current
}

data class NotificationDeepLink(val type: String, val alertId: String?)

private val _pendingNotificationDeepLink = kotlinx.coroutines.flow.MutableStateFlow<NotificationDeepLink?>(null)

fun setPendingNotificationDeepLink(type: String, alertId: String?) {
    _pendingNotificationDeepLink.value = NotificationDeepLink(type, alertId)
    com.syrmos.app.NotificationNavBus.post(type, alertId)
}

actual fun consumePendingNotificationDeepLink(): Pair<String, String?>? {
    val current = _pendingNotificationDeepLink.value ?: return null
    _pendingNotificationDeepLink.value = null
    return current.type to current.alertId
}

private const val SELECTED_TAB_KEY = "syrmos.selectedTab"
private const val SELECTED_DESKTOP_SECTION_KEY = "syrmos.selectedDesktopSection"

actual fun readSelectedTabId(): String? =
    appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.getString(SELECTED_TAB_KEY, null)

actual fun writeSelectedTabId(tabId: String) {
    appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ?.edit()?.putString(SELECTED_TAB_KEY, tabId)?.apply()
}

actual fun readSelectedDesktopSectionId(): String? =
    appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ?.getString(SELECTED_DESKTOP_SECTION_KEY, null)

actual fun writeSelectedDesktopSectionId(sectionId: String) {
    appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ?.edit()?.putString(SELECTED_DESKTOP_SECTION_KEY, sectionId)?.apply()
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
            // Pass an explicit Looper: this runs on a background coroutine
            // dispatcher (no Looper), and the 4-arg overload creates its callback
            // Handler on the CURRENT thread, which crashes with "Can't create
            // handler ... that has not called Looper.prepare()". Deliver callbacks
            // on the main Looper instead (they only resume the coroutine).
            lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            cont.invokeOnCancellation { lm.removeUpdates(listener) }
        }
    }
}
