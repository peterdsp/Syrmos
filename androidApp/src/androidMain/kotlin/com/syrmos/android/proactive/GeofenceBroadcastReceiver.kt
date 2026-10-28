package com.syrmos.android.proactive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Geofence DWELL events (rider lingered 60 s within 1 km of a monitored station)
 * and the significant-move location updates that re-pick the monitored set.
 * Work runs under goAsync so the projection finishes within the broadcast window.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                if (LocationResult.hasResult(intent)) {
                    LocationResult.extractResult(intent)?.lastLocation?.let { StationGeofenceManager.register(app, it) }
                    return@launch
                }
                val event = GeofencingEvent.fromIntent(intent) ?: return@launch
                if (event.hasError()) {
                    Log.w(TAG, "geofence error ${event.errorCode}")
                    return@launch
                }
                if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_DWELL) return@launch
                val location = event.triggeringLocation
                val ids = event.triggeringGeofences.orEmpty().map { it.requestId }
                val stationId = nearest(app, ids, location) ?: return@launch
                NearbyDepartureNotifier.handle(app, stationId, location)
            } catch (e: Exception) {
                Log.w(TAG, "nearby handling failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun nearest(context: Context, ids: List<String>, location: Location?): String? {
        if (ids.size <= 1 || location == null) return ids.firstOrNull()
        val repo = org.koin.core.context.GlobalContext.get().get<com.syrmos.core.data.repository.StationRepositoryImpl>()
        return ids.minByOrNull { id ->
            val s = repo.getStationById(id).firstOrNull()
            if (s == null) Int.MAX_VALUE
            else com.syrmos.core.common.extensions.distanceInMeters(location.latitude, location.longitude, s.latitude, s.longitude)
        }
    }

    companion object {
        private const val TAG = "GeofenceReceiver"
        const val ACTION_GEOFENCE = "com.syrmos.android.NEARBY_GEOFENCE"
        const val ACTION_LOCATION = "com.syrmos.android.NEARBY_LOCATION"
    }
}
