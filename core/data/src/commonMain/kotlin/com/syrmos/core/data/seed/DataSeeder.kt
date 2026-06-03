package com.syrmos.core.data.seed

import com.syrmos.core.database.SyrmosDatabase
import kotlinx.serialization.json.Json

class DataSeeder(
    private val database: SyrmosDatabase,
    private val resourceReader: ResourceReader,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfNeeded() {
        val currentVersion = database.syrmosDatabaseQueries.getMetadata("seed_version")
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
            database.syrmosDatabaseQueries.deleteAllSchedules()
            database.syrmosDatabaseQueries.deleteAllFrequencies()
            database.syrmosDatabaseQueries.deleteAllTransfers()
            database.syrmosDatabaseQueries.deleteAllStationLines()
            database.syrmosDatabaseQueries.deleteAllStations()
            database.syrmosDatabaseQueries.deleteAllLines()

            lines.forEach { line ->
                database.syrmosDatabaseQueries.insertLine(
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
                database.syrmosDatabaseQueries.insertStation(
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
                    database.syrmosDatabaseQueries.insertStationLine(
                        station_id = station.id,
                        line_id = lineId,
                        position_on_line = position.toLong(),
                    )
                }
            }

            transfers.forEach { transfer ->
                database.syrmosDatabaseQueries.insertTransfer(
                    station_id = transfer.stationId,
                    from_line_id = transfer.fromLineId,
                    to_line_id = transfer.toLineId,
                    walking_minutes = transfer.walkingMinutes.toLong(),
                )
            }

            frequencies.forEach { freq ->
                database.syrmosDatabaseQueries.insertFrequency(
                    line_id = freq.lineId,
                    day_type = freq.dayType,
                    time_range = freq.timeRange,
                    frequency_minutes = freq.frequencyMinutes.toLong(),
                )
            }

            database.syrmosDatabaseQueries.setMetadata("seed_version", SEED_VERSION)
        }

        seedSchedules()
    }

    private suspend fun seedSchedules() {
        val scheduleFiles = listOf(
            "files/seed/schedules/metro_line1_outbound.json",
            "files/seed/schedules/metro_line1_inbound.json",
            "files/seed/schedules/metro_line2_outbound.json",
            "files/seed/schedules/metro_line3_airport_outbound.json",
            "files/seed/schedules/metro_line3_airport_inbound.json",
            "files/seed/schedules/tram_t6_outbound.json",
            "files/seed/schedules/suburban_p1_outbound.json",
            "files/seed/schedules/suburban_p1_inbound.json",
        )

        // Read all schedule files first (suspend), then batch insert
        val schedules = scheduleFiles.mapNotNull { path ->
            try {
                json.decodeFromString<SeedScheduleFile>(resourceReader.readText(path))
            } catch (_: Exception) {
                null
            }
        }

        database.transaction {
            schedules.forEach { schedule ->
                schedule.stationDepartures.forEach { (stationId, times) ->
                    times.forEach { time ->
                        database.syrmosDatabaseQueries.insertSchedule(
                            line_id = schedule.lineId,
                            station_id = stationId,
                            direction = schedule.direction,
                            day_type = schedule.dayType,
                            departure_time = time,
                            notes = null,
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val SEED_VERSION = "3"
    }
}
