package com.syrmos.core.data.repository

import com.syrmos.core.common.extensions.distanceInMeters
import com.syrmos.core.database.SyrmosDatabase
import com.syrmos.core.database.mapper.toDomain
import com.syrmos.core.data.seed.ResourceReader
import com.syrmos.core.data.seed.SeedStation
import com.syrmos.core.model.location.NearestStationResult
import com.syrmos.core.model.location.UserLocation
import com.syrmos.core.model.transit.Station
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StationRepositoryImpl(
    private val database: SyrmosDatabase,
    private val resourceReader: ResourceReader,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var seedStations: List<Station>? = null

    fun getStationsOnLine(lineId: String): Flow<List<Station>> = flow {
        val stations = database.syrmosDatabaseQueries.getStationsOnLine(lineId)
            .executeAsList()
            .map { entity ->
                val lineIds = database.syrmosDatabaseQueries.getLinesAtStation(entity.id)
                    .executeAsList()
                    .map { it.id }
                entity.toDomain(lineIds)
            }
        emit(stations.ifEmpty { readSeedStations().filter { lineId in it.lineIds } })
    }

    fun getStationById(id: String): Flow<Station?> = flow {
        val entity = database.syrmosDatabaseQueries.getStationById(id).executeAsOneOrNull()
        val station = entity?.let {
            val lineIds = database.syrmosDatabaseQueries.getLinesAtStation(it.id)
                .executeAsList()
                .map { line -> line.id }
            it.toDomain(lineIds)
        }
        emit(station ?: readSeedStations().firstOrNull { it.id == id })
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
        emit(
            stations.ifEmpty {
                val normalized = query.trim().lowercase()
                readSeedStations().filter {
                    it.name.lowercase().contains(normalized) ||
                        it.nameEl.lowercase().contains(normalized)
                }
            },
        )
    }

    fun findNearestStations(
        location: UserLocation,
        limit: Int = 5,
    ): Flow<List<NearestStationResult>> = flow {
        val allStations = database.syrmosDatabaseQueries.getAllStations().executeAsList()
        val nearest = if (allStations.isEmpty()) {
            readSeedStations().map { station ->
                val distance = distanceInMeters(
                    location.latitude, location.longitude,
                    station.latitude, station.longitude,
                )
                NearestStationResult(
                    stationId = station.id,
                    stationName = station.name,
                    distanceMeters = distance,
                    lineIds = station.lineIds,
                )
            }
        } else {
            allStations.map { entity ->
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
        }.sortedBy { it.distanceMeters }.take(limit)
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
        emit(stations.ifEmpty { readSeedStations() })
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
        emit(stations.ifEmpty { readSeedStations().filter { it.isInterchange } })
    }

    private suspend fun readSeedStations(): List<Station> {
        seedStations?.let { return it }
        return json.decodeFromString<List<SeedStation>>(
            resourceReader.readText("files/seed/stations.json"),
        ).map { seed ->
            Station(
                id = seed.id,
                name = seed.name,
                nameEl = seed.nameEl,
                latitude = seed.latitude,
                longitude = seed.longitude,
                lineIds = seed.lineIds,
                isInterchange = seed.isInterchange,
                accessibility = seed.accessibility,
                zone = seed.zone,
            )
        }.also {
            seedStations = it
        }
    }
}
