package com.syrmos.core.data.repository

import com.syrmos.core.common.extensions.distanceInMeters
import com.syrmos.core.common.extensions.normalizeForSearch
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

    /**
     * All (line, stop) coordinate rows in a SINGLE query, grouped by line id.
     * Only id + coordinates are populated (other Station fields are placeholders)
     * because the sole consumer, the interchange resolver, reads just those. This
     * replaces a per-line getStationsOnLine fan-out (~485 selects per station
     * change).
     *
     * Sourced ONLY from station_line_entity, whose (station_id, line_id) rows are
     * the authoritative per-line memberships. Returns empty when the DB is
     * unseeded: the interchange section simply does not appear, which is correct.
     * A Station.lineIds-based seed fallback is deliberately NOT used, because
     * lineIds lists every line at a hub (bundled M1_PIR lists M3), so it would put
     * M1_PIR under M3 and hand the resolver a wrong own-stop id (M3 -> M1_PIR).
     */
    fun getStationCoordinatesByLine(): Map<String, List<Station>> =
        database.syrmosDatabaseQueries.getAllStationLineCoordinates().executeAsList()
            .groupBy { it.lineId }
            .mapValues { (lineId, group) ->
                group.map { row ->
                    Station(
                        id = row.stationId,
                        name = row.stationId,
                        nameEl = row.stationId,
                        latitude = row.latitude,
                        longitude = row.longitude,
                        lineIds = listOf(lineId),
                    )
                }
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
        // Filter in memory with a diacritic- and case-folding predicate, matching
        // the iOS in-memory Browse-All filter. The old SQL LIKE path could not do
        // this: SQLite LIKE is case-insensitive for ASCII only, so a stored Greek
        // name (Αττική) never matched a typed lowercase query, and no path folded
        // tonos. Matching the entity rows (which already carry name/name_el/
        // name_sq/id) means the per-row lineId fan-out only runs for the handful
        // of stations that actually matched.
        val normalized = query.trim().normalizeForSearch()
        // Defense-in-depth: never contains("") against every station (it matches
        // all). The use-case already guards this, but keep the repo honest if
        // called directly.
        if (normalized.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        val entities = database.syrmosDatabaseQueries.getAllStations().executeAsList()
        val matched = if (entities.isNotEmpty()) {
            entities
                .filter { matchesNormalized(it.name, it.name_el, it.name_sq, it.id, normalized) }
                .map { entity ->
                    val lineIds = database.syrmosDatabaseQueries.getLinesAtStation(entity.id)
                        .executeAsList()
                        .map { it.id }
                    entity.toDomain(lineIds)
                }
        } else {
            readSeedStations().filter { matchesQuery(it, normalized) }
        }
        emit(matched)
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
                // Carry the Albanian name so the offline search fallback can
                // match it; the seed has name_sq for ~200 stations and it was
                // being dropped here.
                nameSq = seed.nameSq,
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

    companion object {
        /**
         * Whether [station] matches an already-[normalizeForSearch]d search
         * query across its English, Greek and Albanian names and its station
         * code. Mirrors the iOS Browse-All filter so all platforms match the
         * same stations.
         */
        internal fun matchesQuery(station: Station, normalizedQuery: String): Boolean =
            matchesNormalized(station.name, station.nameEl, station.nameSq, station.id, normalizedQuery)

        /**
         * Field-level match used by both the DB and seed paths. Both the fields
         * and the (already-normalized) query are folded with [normalizeForSearch]
         * so case and diacritics are ignored uniformly, e.g. a typed "attiki"
         * matches the stored "Αττική" and "οδος" matches "Οδός". The caller MUST
         * pass a query that has already been through [normalizeForSearch].
         */
        internal fun matchesNormalized(
            name: String,
            nameEl: String,
            nameSq: String?,
            id: String,
            normalizedQuery: String,
        ): Boolean =
            name.normalizeForSearch().contains(normalizedQuery) ||
                nameEl.normalizeForSearch().contains(normalizedQuery) ||
                nameSq?.normalizeForSearch()?.contains(normalizedQuery) == true ||
                id.normalizeForSearch().contains(normalizedQuery)
    }
}
