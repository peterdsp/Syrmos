package com.syrmos.core.data.repository

import com.syrmos.core.common.extensions.distanceInMeters
import com.syrmos.core.database.SyrmosDatabase
import com.syrmos.core.database.mapper.toDomain
import com.syrmos.core.model.location.NearestStationResult
import com.syrmos.core.model.location.UserLocation
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StationRepositoryImpl(
    private val database: SyrmosDatabase,
) {
    fun getStationsOnLine(lineId: String): Flow<List<Station>> = flow {
        val stations = database.syrmosDatabaseQueries.getStationsOnLine(lineId)
            .executeAsList()
            .map { entity ->
                val lineIds = database.syrmosDatabaseQueries.getLinesAtStation(entity.id)
                    .executeAsList()
                    .map { it.id }
                entity.toDomain(lineIds)
            }
        emit(stations)
    }

    fun getStationById(id: String): Flow<Station?> = flow {
        val entity = database.syrmosDatabaseQueries.getStationById(id).executeAsOneOrNull()
        val station = entity?.let {
            val lineIds = database.syrmosDatabaseQueries.getLinesAtStation(it.id)
                .executeAsList()
                .map { line -> line.id }
            it.toDomain(lineIds)
        }
        emit(station)
    }

    fun searchStations(query: String): Flow<List<Station>> = flow {
        val pattern = "%$query%"
        val stations = database.syrmosDatabaseQueries.searchStations(pattern, pattern)
            .executeAsList()
            .map { entity ->
                val lineIds = database.syrmosDatabaseQueries.getLinesAtStation(entity.id)
                    .executeAsList()
                    .map { it.id }
                entity.toDomain(lineIds)
            }
        emit(stations)
    }

    fun findNearestStations(
        location: UserLocation,
        limit: Int = 5,
    ): Flow<List<NearestStationResult>> = flow {
        val allStations = database.syrmosDatabaseQueries.getAllStations().executeAsList()
        val nearest = allStations
            .map { entity ->
                val distance = distanceInMeters(
                    location.latitude, location.longitude,
                    entity.latitude, entity.longitude,
                )
                val lineIds = database.syrmosDatabaseQueries.getLinesAtStation(entity.id)
                    .executeAsList()
                    .map { it.id }
                NearestStationResult(
                    stationId = entity.id,
                    stationName = entity.name,
                    distanceMeters = distance,
                    lineIds = lineIds,
                )
            }
            .sortedBy { it.distanceMeters }
            .take(limit)
        emit(nearest)
    }

    fun getAllStations(): Flow<List<Station>> = flow {
        val stations = database.syrmosDatabaseQueries.getAllStations()
            .executeAsList()
            .map { entity ->
                val lineIds = database.syrmosDatabaseQueries.getLinesAtStation(entity.id)
                    .executeAsList()
                    .map { it.id }
                entity.toDomain(lineIds)
            }
        emit(stations)
    }

    fun getInterchangeStations(): Flow<List<Station>> = flow {
        val stations = database.syrmosDatabaseQueries.getInterchangeStations()
            .executeAsList()
            .map { entity ->
                val lineIds = database.syrmosDatabaseQueries.getLinesAtStation(entity.id)
                    .executeAsList()
                    .map { it.id }
                entity.toDomain(lineIds)
            }
        emit(stations)
    }
}
