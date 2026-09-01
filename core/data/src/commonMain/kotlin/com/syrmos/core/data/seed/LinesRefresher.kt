package com.syrmos.core.data.seed

import com.syrmos.core.database.SyrmosDatabase
import com.syrmos.core.network.SyrmosLinesService
import kotlinx.coroutines.flow.firstOrNull

/**
 * Refreshes lines and stations in the local SQLite database from the Pi API
 * (api-syrmos.peterdsp.dev/api/lines).
 *
 * Offline-first contract: the database is seeded from bundled JSON at first
 * launch via [DataSeeder]. This refresher runs AFTER that seed completes and
 * is a best-effort overlay. On network failure or any other problem we
 * silently keep whatever the DB already contained. The app therefore
 * continues to work fully offline; this just lets us ship station fixes
 * (new tram stops, renamed stations) without an app release.
 *
 * Behaviour:
 * - Line rows are upserted (safe — API has the same columns as the seed).
 * - Station rows for stations the DB already knows about are LEFT ALONE
 *   so we don't clobber the interchange/accessibility/zone flags from the
 *   bundled seed.
 * - New stations from the API (e.g. the 2022 Piraeus tram extension) are
 *   inserted with sensible defaults.
 * - Station-to-line relations are upserted so the order matches the API.
 * Schedules, frequencies and transfers are not touched — they remain
 * managed by the bundled seed since they're domain-specific.
 */
class LinesRefresher(
    private val database: SyrmosDatabase,
    private val linesService: SyrmosLinesService,
) {
    suspend fun refresh() {
        val payload = runCatching { linesService.fetchLines().firstOrNull() }
            .getOrNull() ?: return
        val remoteLines = payload.lines.ifEmpty { return }

        // Collect the ids the DB already has so we can skip overwriting them.
        val knownStationIds = database.syrmosDatabaseQueries
            .getAllStations()
            .executeAsList()
            .mapTo(HashSet()) { it.id }
        val knownLineIds = database.syrmosDatabaseQueries
            .getAllLines()
            .executeAsList()
            .mapTo(HashSet()) { it.id }

        // Overlay-only, matching the iOS reference (SyrmosLinesService, which only
        // ever collects genuinely novel stations and never touches line
        // status/region or station order). Android previously UPSERTED each line's
        // status/region from the payload and REORDERED every membership in place,
        // so a drifted or partial /api/lines payload could silently flip a seeded
        // line's status (under_construction <-> operational), relabel its region,
        // or reorder its stops - none of which iOS does. The seed is authoritative;
        // /api/lines may only ADD lines/stations the seed lacks.
        database.transaction {
            remoteLines.forEach { line ->
                // Only insert a genuinely NEW line; never overwrite a seeded line's
                // status/region/terminals.
                if (line.id !in knownLineIds) {
                    database.syrmosDatabaseQueries.insertLine(
                        id = line.id,
                        name = line.name,
                        name_el = line.nameEl,
                        type = line.type.lowercase(),
                        color = line.color,
                        terminal_a = line.terminalA,
                        terminal_b = line.terminalB,
                        station_count = line.stationCount.toLong(),
                        region = line.region,
                        status = line.status,
                    )
                }

                // Only add NOVEL stations and their membership. A known station's
                // membership/order is left exactly as seeded (no reorder). This
                // also never truncates a line: it only ever adds rows.
                line.stations.forEachIndexed { index, station ->
                    if (station.id !in knownStationIds) {
                        database.syrmosDatabaseQueries.insertStation(
                            id = station.id,
                            name = station.name,
                            name_el = station.nameEl,
                            name_sq = null,
                            latitude = station.lat,
                            longitude = station.lng,
                            is_interchange = 0L,
                            accessibility = 0L,
                            zone = 1L,
                            region = line.region,
                            source_confidence = "scheduled",
                        )
                        database.syrmosDatabaseQueries.insertStationLine(
                            station_id = station.id,
                            line_id = line.id,
                            position_on_line = index.toLong(),
                        )
                    }
                }
            }
        }
    }
}
