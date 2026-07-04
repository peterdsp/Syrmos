package com.syrmos.android.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.GlanceId
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.domain.usecase.GetNextDeparturesUseCase
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Resolves the snapshot a single Glance widget instance should render, honoring
 * its per-instance [WidgetConfig] (the Android equivalent of iOS Edit Widget):
 *
 *  - Nearest mode (default): reuse the shared snapshot the SnapshotWorker writes.
 *  - Pinned station: project that station's departures on demand via the shared
 *    KMP use case, fully offline.
 *  - Optional line filter: keep only the chosen line's rows (ignored when the
 *    station doesn't serve it, matching the iOS behavior).
 *
 * Also exposes the station list for the configuration Activity's picker.
 */
object WidgetProjection : KoinComponent {
    private val getNextDepartures: GetNextDeparturesUseCase by inject()
    private val stationRepository: StationRepositoryImpl by inject()

    /** All stations, name-deduped and alphabetized, for the config picker. */
    suspend fun stationsForPicker(): List<Station> =
        runCatching {
            stationRepository.getAllStations().firstOrNull().orEmpty()
                .sortedBy { it.name }
                .distinctBy { it.name }
        }.getOrDefault(emptyList())

    /** The snapshot for the widget with [appWidgetId], per its saved config. */
    suspend fun snapshotFor(context: Context, appWidgetId: Int): WidgetSnapshot {
        val cfg = WidgetConfigStore.read(context, appWidgetId)
        val base = if (cfg.useNearest || cfg.stationId == null) {
            SnapshotStore.read(context).takeIf { it.rows.isNotEmpty() } ?: BundledFallback.snapshot()
        } else {
            runCatching { projectStation(cfg.stationId, cfg.stationName ?: cfg.stationId, cfg.line) }
                .getOrDefault(BundledFallback.snapshot().copy(stationName = cfg.stationName ?: "—"))
        }
        return applyLineFilter(base, cfg.line)
    }

    private suspend fun projectStation(stationId: String, stationName: String, lineFilter: String?): WidgetSnapshot {
        val now = System.currentTimeMillis()
        val line = lineFilter ?: primaryLine(stationId)
        val deps = getNextDepartures
            .invoke(stationId, line, Direction.OUTBOUND, limit = 5)
            .firstOrNull()
            .orEmpty()
        if (deps.isEmpty()) return BundledFallback.snapshot(now).copy(stationName = stationName)
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
        )
    }

    /** Best line to project for a pinned station: its first served line, else M3. */
    private suspend fun primaryLine(stationId: String): String =
        runCatching {
            stationRepository.getStationById(stationId).firstOrNull()?.lineIds?.firstOrNull()
        }.getOrNull() ?: "M3"

    private fun applyLineFilter(snapshot: WidgetSnapshot, line: String?): WidgetSnapshot {
        if (line == null) return snapshot
        val target = AndroidLineTokens.normalize(line)
        val filtered = snapshot.rows.filter { AndroidLineTokens.normalize(it.lineId) == target }
        // Keep the unfiltered rows when the filter matches nothing, so a pinned
        // widget never goes blank because of a line the station doesn't serve.
        return if (filtered.isEmpty()) snapshot else snapshot.copy(rows = filtered)
    }
}

/**
 * Shared terminus label for a projected departure, used by both the
 * SnapshotWorker (nearest snapshot) and WidgetProjection (pinned snapshot).
 */
internal fun terminusFor(lineId: String, direction: Direction, serviceType: String?): String {
    if (serviceType == "airport") return "Airport"
    return when (AndroidLineTokens.normalize(lineId)) {
        "M3" -> if (direction == Direction.OUTBOUND) "Doukissis Plakentias" else "Dimotiko Theatro"
        "M2" -> if (direction == Direction.OUTBOUND) "Elliniko" else "Anthoupoli"
        "M1" -> if (direction == Direction.OUTBOUND) "Kifisia" else "Piraeus"
        else -> if (direction == Direction.OUTBOUND) "Outbound" else "Inbound"
    }
}

/** Resolves the platform appWidgetId for a Glance id (config lookups need it). */
suspend fun GlanceAppWidgetManager.appWidgetIdOrZero(id: GlanceId): Int =
    runCatching { getAppWidgetId(id) }.getOrDefault(0)
