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
        if (!shouldReseed(currentVersion, SEED_VERSION)) return

        seed()
    }

    private suspend fun seed() {
        // schedules-v2 is the generator's payload and the single source of truth
        // for lines. The legacy flat seed/lines.json came from hardcoded Swift via
        // a sync script broken since June 2026, so it carries neither region nor
        // status. See docs/plans/2026-07-17-server-as-single-source-for-lines.md.
        val lines = json.decodeFromString<SeedLinesPayload>(
            resourceReader.readText("files/seed/schedules-v2/lines.json")
        ).lines
        val stations = json.decodeFromString<List<SeedStation>>(
            resourceReader.readText("files/seed/stations.json")
        )
        val transfers = json.decodeFromString<List<SeedTransfer>>(
            resourceReader.readText("files/seed/transfers.json")
        )
        val routes = json.decodeFromString<List<SeedRoute>>(
            resourceReader.readText("files/seed/routes.json")
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
                    // Must be persisted, not defaulted: getAllLines() prefers the
                    // database, so a line seeded without its status would read back
                    // as operational and an under-construction line would look live.
                    region = line.region,
                    status = line.status,
                )
            }

            stations.forEach { station ->
                database.syrmosDatabaseQueries.insertStation(
                    id = station.id,
                    name = station.name,
                    name_el = station.nameEl,
                    name_sq = station.nameSq,
                    latitude = station.latitude,
                    longitude = station.longitude,
                    is_interchange = if (station.isInterchange) 1L else 0L,
                    accessibility = if (station.accessibility) 1L else 0L,
                    zone = station.zone.toLong(),
                    region = station.region,
                    source_confidence = station.sourceConfidence,
                )
            }

            routes.forEach { route ->
                route.stationIds.forEachIndexed { position, stationId ->
                    database.syrmosDatabaseQueries.insertStationLine(
                        station_id = stationId,
                        line_id = route.lineId,
                        position_on_line = position.toLong(),
                    )
                }
            }

            // Lines absent from routes.json (most buses: VL1, DX1, KP1, TL1,
            // PU1/PU2, X3/2X, ...) would otherwise have no ordered station_line
            // rows, so getStationsOnLine falls back to the globally-ordered
            // stations.json, giving their stops a wrong, backtracking order, or
            // none at all (X3/2X stops live only in the nested lines.json).
            // Seed their ordering from the nested lines.json stations, inserting
            // any station that exists only there.
            val routeLineIds = routes.mapTo(mutableSetOf()) { it.lineId }
            val knownStationIds = stations.mapTo(mutableSetOf()) { it.id }
            lines.forEach { line ->
                nestedStationOrder(line, routeLineIds).forEachIndexed { position, s ->
                    if (s.id !in knownStationIds) {
                        database.syrmosDatabaseQueries.insertStation(
                            id = s.id,
                            name = s.name,
                            name_el = s.nameEl,
                            name_sq = null,
                            latitude = s.lat,
                            longitude = s.lng,
                            is_interchange = 0L,
                            accessibility = if (s.accessibility) 1L else 0L,
                            zone = s.zone.toLong(),
                            region = s.region,
                            source_confidence = "scheduled",
                        )
                        knownStationIds.add(s.id)
                    }
                    database.syrmosDatabaseQueries.insertStationLine(
                        station_id = s.id,
                        line_id = line.id,
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
        seedTripSchedules(lines.map { it.id })
    }

    private suspend fun seedSchedules() {
        val scheduleFiles = listOf(
            "files/seed/schedules/metro_line1_outbound.json",
            "files/seed/schedules/metro_line1_inbound.json",
            "files/seed/schedules/metro_line2_outbound.json",
            "files/seed/schedules/metro_line3_airport_outbound.json",
            "files/seed/schedules/metro_line3_airport_inbound.json",
            "files/seed/schedules/tram_t6_outbound.json",
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

    /**
     * Expand the bundled per-train `trips` (national rail + rail-replacement
     * buses) into schedule_entity so the offline departures fallback returns
     * them. These lines carry a full timetable and no frequency bands, so
     * without this the bottom sheet showed "no departures" offline even though
     * the map animated a moving vehicle from the same trips. Metro/tram/suburban
     * files have an empty `trips` and are skipped here (they use bands).
     *
     * The bundle's dayType vocabulary (mon_thu/fri/sat/sun) is mapped to the
     * schedule_entity convention (weekday/friday/saturday/sunday) that
     * getNextDepartures queries with, otherwise the rows never match.
     */
    private suspend fun seedTripSchedules(lineIds: List<String>) {
        val files = lineIds.mapNotNull { lineId ->
            try {
                json.decodeFromString<SeedLineScheduleFile>(
                    resourceReader.readText("files/seed/schedules-v2/$lineId.json")
                )
            } catch (_: Exception) {
                null
            }
        }.filter { it.trips.isNotEmpty() }

        if (files.isEmpty()) return

        database.transaction {
            files.forEach { file ->
                file.trips.forEach { trip ->
                    val dayType = mapBundleDayType(trip.dayType)
                    val direction = trip.direction.lowercase()
                    if (dayType == null || direction.isBlank()) return@forEach
                    trip.stops.forEach { stop ->
                        if (stop.stationId.isBlank() || stop.departureTime.isBlank()) return@forEach
                        database.syrmosDatabaseQueries.insertSchedule(
                            line_id = file.lineId.ifBlank { return@forEach },
                            station_id = stop.stationId,
                            direction = direction,
                            day_type = dayType,
                            departure_time = stop.departureTime,
                            notes = null,
                        )
                    }
                }
            }
        }
    }

    private fun mapBundleDayType(bundleDayType: String): String? = when (bundleDayType) {
        "mon_thu" -> "weekday"
        "fri" -> "friday"
        "sat" -> "saturday"
        "sun" -> "sunday"
        // Already-canonical values pass through; anything unknown is skipped so
        // it can never masquerade as today's service.
        "weekday", "friday", "saturday", "sunday" -> bundleDayType
        else -> null
    }

    companion object {
        // Bumped to 10: lines absent from routes.json (most buses) now get
        // ordered station_line rows seeded from the nested lines.json stations,
        // so their map geometry follows the real stop order. Without a bump an
        // existing install keeps its old rows and the buses stay mis-ordered.
        const val SEED_VERSION = "10"

        /**
         * Whether the bundled seed must be (re)written. Compares versions
         * NUMERICALLY: they are stored as strings and SEED_VERSION crossed into
         * two digits at 10, so a lexicographic String compare reads "9" >= "10"
         * as true and a version-9 install would never pick up the version-10
         * data (skipping exactly the bus-ordering repair this bump ships). An
         * unset or unparseable stored version always reseeds.
         */
        internal fun shouldReseed(storedVersion: String?, targetVersion: String): Boolean {
            val target = targetVersion.toIntOrNull() ?: return true
            val stored = storedVersion?.toIntOrNull() ?: return true
            return stored < target
        }

        /**
         * The ordered nested stations to seed into station_line_entity for a
         * line that is ABSENT from routes.json. routes.json only lists 21 of the
         * 33 lines; the rest (most buses: VL1, DX1, KP1, TL1, PU1/PU2, X3/2X, the
         * Thessaloniki airport links, ...) would otherwise have no ordered
         * station_line rows, so getStationsOnLine falls back to the globally
         * ordered stations.json and the route draws as a backtracking zig-zag or,
         * for lines whose stops exist only here (X3/2X), draws nothing. Returns
         * empty for a line already in routes.json (the routes loop seeds it) so a
         * stop is never double-inserted or reordered.
         */
        internal fun nestedStationOrder(
            line: SeedLine,
            routeLineIds: Set<String>,
        ): List<SeedLineStation> =
            if (line.id in routeLineIds) emptyList() else line.stations

        /**
         * The final ordered station ids the map will draw for [line] via
         * getStationsOnLine: routes.json order when present, else the nested
         * lines.json order. Every drawable line (in particular every bus) must
         * resolve to at least two ordered stops or its polyline degenerates.
         */
        internal fun resolveOrderedStationIds(
            line: SeedLine,
            routesByLine: Map<String, List<String>>,
        ): List<String> =
            routesByLine[line.id] ?: line.stations.map { it.id }
    }
}
