package com.syrmos.core.domain.usecase

import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.data.sync.ScheduleSyncRepository
import com.syrmos.core.domain.interchange.InterchangeResolver
import com.syrmos.core.domain.interchange.InterchangeTarget
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The operational, scheduled lines serving the same physical hub as a station,
 * each paired with the station id to open on that line, nearest first. Delegates
 * the algorithm to [InterchangeResolver] (a proximity port of iOS PR #48).
 *
 * `currentLineId` is empty because a station screen is not scoped to a single
 * line, so every nearby line, including the ones the station is already on, is an
 * actionable transfer (tap to see that line's timetable at this hub). Resolution
 * uses each line's OWN nearest stop id, never the hub's stored lineIds.
 */
class GetInterchangeTargetsUseCase(
    private val lineRepository: LineRepositoryImpl,
    private val stationRepository: StationRepositoryImpl,
    private val scheduleSync: ScheduleSyncRepository,
) {
    suspend fun invoke(station: Station): List<InterchangeTarget> = withContext(Dispatchers.Default) {
        // Bundled offline snapshot (hydrated at cold start), so this works with no
        // network: a line has a timetable when it carries frequency bands or trips.
        val bundles = scheduleSync.lineBundles.value
        fun hasSchedule(lineId: String): Boolean =
            bundles[lineId]?.let { it.trips.isNotEmpty() || it.bands.isNotEmpty() } ?: false

        // Filter to eligible lines BEFORE loading their stops: only operational,
        // scheduled lines can ever be a target, so querying stops for all 33 lines
        // (hundreds of synchronous selects on every station change) is wasteful.
        // Runs off the main dispatcher so it never stalls the departures poller.
        val eligible = lineRepository.getAllLines().first()
            .filter { it.isOperational && hasSchedule(it.id) }
        val stationsByLine = eligible.associate { line ->
            line.id to stationRepository.getStationsOnLine(line.id).first()
        }
        InterchangeResolver.resolve(
            hubLatitude = station.latitude,
            hubLongitude = station.longitude,
            currentLineId = "",
            lines = eligible,
            stationsByLine = stationsByLine,
            hasSchedule = ::hasSchedule,
        )
    }
}
