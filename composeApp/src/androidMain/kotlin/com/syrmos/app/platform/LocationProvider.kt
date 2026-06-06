package com.syrmos.app.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.syrmos.core.model.location.UserLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private var appContext: Context? = null

fun initLocationProvider(context: Context) {
    appContext = context.applicationContext
}

actual suspend fun requestUserLocation(): UserLocation? = withContext(Dispatchers.IO) {
    val ctx = appContext ?: return@withContext null
    val hasPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!hasPerm) return@withContext null

    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null
    val location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        ?: return@withContext null

    UserLocation(location.latitude, location.longitude)
}
