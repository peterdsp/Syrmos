package com.syrmos.core.data.seed

import com.syrmos.core.database.SyrmosDatabase
import kotlinx.serialization.json.Json

class DataSeeder(
    private val database: SyrmosDatabase,
    private val resourceReader: ResourceReader,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfNeeded() {
        val currentVersion = database.syrmosQueries.getMetadata("seed_version")
            .executeAsOneOrNull()
        if (currentVersion != null && currentVersion >= SEED_VERSION) return

        seed()
    }

    private suspend fun seed() {
        val lines = json.decodeFromString<List<SeedLine>>(
            resourceReader.readText("files/seed/lines.json")
        )
        val stations = json.decodeFromString<List<SeedStation>>(
            resourceReader.readText("files/seed/stations.json")
        )
        val transfers = json.decodeFromString<List<SeedTransfer>>(
            resourceReader.readText("files/seed/transfers.json")
        )
        val frequencies = json.decodeFromString<List<SeedFrequency>>(
            resourceReader.readText("files/seed/frequencies.json")
        )

        database.transaction {
            database.syrmosQueries.deleteAllSchedules()
            database.syrmosQueries.deleteAllFrequencies()
            database.syrmosQueries.deleteAllTransfers()
            database.syrmosQueries.deleteAllStationLines()
            database.syrmosQueries.deleteAllStations()
            database.syrmosQueries.deleteAllLines()

            lines.forEach { line ->
                database.syrmosQueries.insertLine(
                    id = line.id,
                    name = line.name,
                    name_el = line.nameEl,
                    type = line.type,
                    color = line.color,
                    terminal_a = line.terminalA,
                    terminal_b = line.terminalB,
                    station_count = line.stationCount.toLong(),
                )
            }

            val stationPositions = mutableMapOf<String, MutableMap<String, Int>>()
            stations.forEach { station ->
                database.syrmosQueries.insertStation(
                    id = station.id,
                    name = station.name,
                    name_el = station.nameEl,
                    latitude = station.latitude,
                    longitude = station.longitude,
                    is_interchange = if (station.isInterchange) 1L else 0L,
                    accessibility = if (station.accessibility) 1L else 0L,
                    zone = station.zone.toLong(),
                )
                station.lineIds.forEach { lineId ->
                    val linePositions = stationPositions.getOrPut(lineId) { mutableMapOf() }
                    val position = linePositions.size
                    linePositions[station.id] = position
                    database.syrmosQueries.insertStationLine(
                        station_id = station.id,
                        line_id = lineId,
                        position_on_line = position.toLong(),
                    )
                }
            }

            transfers.forEach { transfer ->
                database.syrmosQueries.insertTransfer(
                    station_id = transfer.stationId,
                    from_line_id = transfer.fromLineId,
                    to_line_id = transfer.toLineId,
                    walking_minutes = transfer.walkingMinutes.toLong(),
                )
            }

            frequencies.forEach { freq ->
                database.syrmosQueries.insertFrequency(
                    line_id = freq.lineId,
                    day_type = freq.dayType,
                    time_range = freq.timeRange,
                    frequency_minutes = freq.frequencyMinutes.toLong(),
                )
            }

            database.syrmosQueries.setMetadata("seed_version", SEED_VERSION)
        }
    }

    companion object {
        const val SEED_VERSION = "1"
    }
}
