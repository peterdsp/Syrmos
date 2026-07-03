package com.syrmos.android.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.glance.appwidget.updateAll
import com.syrmos.core.domain.usecase.GetNextDeparturesUseCase
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
        val deps = getNextDepartures
            .invoke(PRIMARY_STATION_ID, PRIMARY_LINE, Direction.OUTBOUND, limit = 5)
            .firstOrNull()
            .orEmpty()
        if (deps.isEmpty()) return BundledFallback.snapshot(now)
        val rows = deps.map { d ->
            WidgetRow(
                lineId = AndroidLineTokens.label(d.lineId),
                destination = terminus(d.lineId, d.direction, d.serviceType),
                minutes = d.minutesAway,
                time = d.time,
            )
        }
        return WidgetSnapshot(stationName = PRIMARY_STATION_NAME, lastTrain = rows.lastOrNull()?.time, rows = rows, updatedEpoch = now)
    }

    private fun terminus(lineId: String, direction: Direction, serviceType: String?): String {
        if (serviceType == "airport") return "Airport"
        return when (AndroidLineTokens.normalize(lineId)) {
            "M3" -> if (direction == Direction.OUTBOUND) "Doukissis Plakentias" else "Dimotiko Theatro"
            "M2" -> if (direction == Direction.OUTBOUND) "Elliniko" else "Anthoupoli"
            "M1" -> if (direction == Direction.OUTBOUND) "Kifisia" else "Piraeus"
            else -> if (direction == Direction.OUTBOUND) "Outbound" else "Inbound"
        }
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
