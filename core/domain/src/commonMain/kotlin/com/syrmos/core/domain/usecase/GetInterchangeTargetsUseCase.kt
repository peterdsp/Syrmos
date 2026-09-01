package com.syrmos.core.domain.usecase

import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.data.sync.ScheduleSyncRepository
import com.syrmos.core.domain.interchange.InterchangeResolver
import com.syrmos.core.domain.interchange.InterchangeTarget
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.flow.first

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
    suspend fun invoke(station: Station): List<InterchangeTarget> {
        val lines = lineRepository.getAllLines().first()
        val stationsByLine = lines.associate { line ->
            line.id to stationRepository.getStationsOnLine(line.id).first()
        }
        // Bundled offline snapshot (hydrated at cold start), so this works with no
        // network: a line has a timetable when it carries frequency bands or trips.
        val bundles = scheduleSync.lineBundles.value
        return InterchangeResolver.resolve(
            hubLatitude = station.latitude,
            hubLongitude = station.longitude,
            currentLineId = "",
            lines = lines,
            stationsByLine = stationsByLine,
            hasSchedule = { lineId ->
                bundles[lineId]?.let { it.trips.isNotEmpty() || it.bands.isNotEmpty() } ?: false
            },
        )
    }
}
