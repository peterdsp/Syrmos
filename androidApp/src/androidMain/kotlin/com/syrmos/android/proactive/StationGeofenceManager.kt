package com.syrmos.android.proactive

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import com.syrmos.core.common.NearbyDepartureCoach
import com.syrmos.core.common.NotificationSettings
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.model.location.UserLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/**
 * 3.0.2 nearby next-train notifications: keeps geofences (radius 1000 m, DWELL
 * 60 s) around the nearest [NearbyDepartureCoach.MONITORED_STATIONS] stations and
 * re-picks them when the rider moves a significant distance (a low-power location
 * update with a 2 km minimum displacement wakes [GeofenceBroadcastReceiver]).
 *
 * Opt-in only: does nothing unless the Settings toggle is on and both fine and
 * background location are granted. Location stays on the device.
 */
object StationGeofenceManager {
    private const val TAG = "StationGeofence"
    private const val REPICK_DISPLACEMENT_METERS = 2000f
    private const val REQUEST_GEOFENCE = 7100
    private const val REQUEST_LOCATION = 7101

    fun hasPermissions(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val background = Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine && background
    }

    /** Re-registers the geofence set from the last known location, or clears it when off. */
    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        if (!NotificationSettings.nearbyDepartures.value || !hasPermissions(app)) {
            stop(app)
            return
        }
        val location = lastLocation(app) ?: return
        register(app, location)
    }

    @SuppressLint("MissingPermission")
    suspend fun register(context: Context, location: Location) = withContext(Dispatchers.IO) {
        val stations = runCatching {
            GlobalContext.get().get<StationRepositoryImpl>()
                .findNearestStations(UserLocation(location.latitude, location.longitude), limit = NearbyDepartureCoach.MONITORED_STATIONS * 2)
                .firstOrNull().orEmpty()
        }.getOrDefault(emptyList())
            // One fence per physical station: interchanges list a per-line id each.
            .distinctBy { it.stationName }
            .take(NearbyDepartureCoach.MONITORED_STATIONS)
        if (stations.isEmpty()) return@withContext
        val repo = GlobalContext.get().get<StationRepositoryImpl>()
        val fences = stations.mapNotNull { s ->
            val st = runCatching { repo.getStationById(s.stationId).firstOrNull() }.getOrNull() ?: return@mapNotNull null
            Geofence.Builder()
                .setRequestId(s.stationId)
                .setCircularRegion(st.latitude, st.longitude, NearbyDepartureCoach.GEOFENCE_RADIUS_METERS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(NearbyDepartureCoach.GEOFENCE_DWELL_MILLIS)
                .build()
        }
        if (fences.isEmpty()) return@withContext
        val client = LocationServices.getGeofencingClient(context)
        runCatching { Tasks.await(client.removeGeofences(geofencePendingIntent(context)), 10, TimeUnit.SECONDS) }
        val request = GeofencingRequest.Builder()
            // Already inside a fence when registering: report it once the rider dwells.
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofences(fences)
            .build()
        runCatching {
            Tasks.await(client.addGeofences(request, geofencePendingIntent(context)), 10, TimeUnit.SECONDS)
            Log.i(TAG, "monitoring ${fences.size} stations")
        }.onFailure { Log.w(TAG, "addGeofences failed", it) }
        runCatching {
            val update = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, TimeUnit.MINUTES.toMillis(15))
                .setMinUpdateDistanceMeters(REPICK_DISPLACEMENT_METERS)
                .build()
            LocationServices.getFusedLocationProviderClient(context)
                .requestLocationUpdates(update, locationPendingIntent(context))
        }
    }

    fun stop(context: Context) {
        runCatching { LocationServices.getGeofencingClient(context).removeGeofences(geofencePendingIntent(context)) }
        runCatching { LocationServices.getFusedLocationProviderClient(context).removeLocationUpdates(locationPendingIntent(context)) }
    }

    @SuppressLint("MissingPermission")
    suspend fun lastLocation(context: Context): Location? = withContext(Dispatchers.IO) {
        val fused = runCatching {
            Tasks.await(LocationServices.getFusedLocationProviderClient(context).lastLocation, 10, TimeUnit.SECONDS)
        }.getOrNull()
        if (fused != null) return@withContext fused
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { manager.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    // Geofencing requires a mutable PendingIntent (it fills in the event extras).
    private fun geofencePendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_GEOFENCE,
            Intent(context, GeofenceBroadcastReceiver::class.java).setAction(GeofenceBroadcastReceiver.ACTION_GEOFENCE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    private fun locationPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_LOCATION,
            Intent(context, GeofenceBroadcastReceiver::class.java).setAction(GeofenceBroadcastReceiver.ACTION_LOCATION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
}
