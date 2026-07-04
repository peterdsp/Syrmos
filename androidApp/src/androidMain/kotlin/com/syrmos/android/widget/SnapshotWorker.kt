package com.syrmos.android.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.glance.appwidget.updateAll
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.domain.usecase.GetNextDeparturesUseCase
import com.syrmos.core.model.location.UserLocation
import com.syrmos.core.model.transit.Direction
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Writes the widget snapshot every ~30 minutes (and on demand when the user
 * returns to the app). Projects the next departures for the primary station via
 * the shared KMP use case, so the Glance widgets render real, offline-projected
 * times without each widget process touching the schedule engine. Falls back to
 * the bundled arithmetic when the use case yields nothing, so the snapshot is
 * never empty.
 */
class SnapshotWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val getNextDepartures: GetNextDeparturesUseCase by inject()
    private val stationRepository: StationRepositoryImpl by inject()

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val snapshot = runCatching { project(now) }.getOrNull()
            ?: BundledFallback.snapshot(now)
        SnapshotStore.write(applicationContext, snapshot)
        // Push the fresh snapshot to every Glance family.
        runCatching {
            NextTrainGlanceWidget().updateAll(applicationContext)
            LiveDeparturesGlanceWidget().updateAll(applicationContext)
            LineStatusGlanceWidget().updateAll(applicationContext)
            NearMeGlanceWidget().updateAll(applicationContext)
        }
        return Result.success()
    }

    private suspend fun project(now: Long): WidgetSnapshot {
        // Prefer the nearest station to the device's last-known location; fall
        // back to the pinned primary station when location is unavailable.
        val nearest = nearestStations()
        val stationId = nearest.firstOrNull()?.stationId ?: PRIMARY_STATION_ID
        val stationName = nearest.firstOrNull()?.stationName ?: PRIMARY_STATION_NAME
        val line = nearest.firstOrNull()?.lineIds?.firstOrNull() ?: PRIMARY_LINE

        val nearby = nearest.take(3).map { r ->
            NearbyStation(
                name = r.stationName,
                lineIds = r.lineIds.map { AndroidLineTokens.label(it) }.distinct(),
                // ~80 m/min average walking pace.
                walkMinutes = (r.distanceMeters / 80).coerceAtLeast(1),
            )
        }

        val deps = getNextDepartures
            .invoke(stationId, line, Direction.OUTBOUND, limit = 5)
            .firstOrNull()
            .orEmpty()
        if (deps.isEmpty()) return BundledFallback.snapshot(now).copy(nearby = nearby)
        val rows = deps.map { d ->
            WidgetRow(
                lineId = AndroidLineTokens.label(d.lineId),
                destination = terminusFor(d.lineId, d.direction, d.serviceType),
                minutes = d.minutesAway,
                time = d.time,
            )
        }
        return WidgetSnapshot(
            stationName = stationName,
            lastTrain = rows.lastOrNull()?.time,
            rows = rows,
            updatedEpoch = now,
            nearby = nearby,
        )
    }

    /// The nearest stations to the device's last-known location (empty when we
    /// have no location permission or no fix yet). Uses the platform
    /// LocationManager's cached fix, so there is no active GPS session or
    /// background-location requirement.
    private suspend fun nearestStations(): List<com.syrmos.core.model.location.NearestStationResult> {
        val loc = lastKnownLocation() ?: return emptyList()
        return runCatching {
            stationRepository
                .findNearestStations(UserLocation(loc.first, loc.second), limit = 3)
                .firstOrNull()
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun lastKnownLocation(): Pair<Double, Double>? {
        val ctx = applicationContext
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val manager = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        val best = providers.mapNotNull { p ->
            try {
                if (manager.isProviderEnabled(p)) manager.getLastKnownLocation(p) else null
            } catch (_: SecurityException) {
                null
            }
        }.maxByOrNull { it.time } ?: return null
        return best.latitude to best.longitude
    }

    companion object {
        private const val PRIMARY_STATION_ID = "syntagma"
        private const val PRIMARY_STATION_NAME = "Syntagma"
        private const val PRIMARY_LINE = "M3"
        private const val UNIQUE_PERIODIC = "syrmos_widget_snapshot"

        /** Enqueue the 30-minute refresh (idempotent). */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SnapshotWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** One-off refresh, e.g. when the user returns to the app. */
        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<SnapshotWorker>().build())
        }
    }
}
