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
 * Behaviour (overlay-only, matching the iOS reference): the seed is
 * authoritative; /api/lines may only ADD what the seed lacks, never mutate it.
 * - A seeded line row is LEFT ALONE (its status/region/terminals are never
 *   overwritten); only a genuinely new line id is inserted.
 * - A seeded station row is LEFT ALONE (interchange/accessibility/zone flags
 *   preserved); only a genuinely new station id is inserted.
 * - Membership rows are added for a new line's full ordered stop list and for
 *   a new station attaching to a seeded line; a seeded line's existing
 *   membership order is never rewritten.
 * - New stations from the API (e.g. the 2022 Piraeus tram extension) are
 *   inserted with sensible defaults.
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
                val lineIsNovel = line.id !in knownLineIds
                if (lineIsNovel) {
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

                line.stations.forEachIndexed { index, station ->
                    // Add the station row itself only when it is novel, so a seeded
                    // station's interchange/accessibility/zone flags are never
                    // clobbered.
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
                    }
                    // Insert the membership when the LINE is new (so a new line gets
                    // its full ordered stop list, including stops that are already
                    // seeded interchanges) OR the STATION is new (attach a new stop
                    // to a seeded line). Skip only when both are already known -
                    // that is the seeded-membership reorder we must avoid.
                    if (shouldAttachMembership(lineIsNovel, station.id in knownStationIds)) {
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

    companion object {
        /**
         * Overlay-only membership rule: a station-to-line row is (re)written only
         * when the line is genuinely new (it needs its full ordered stop list,
         * including stops that are already-seeded interchanges) or the station is
         * genuinely new (attach it to a seeded line). When both are already known
         * the seeded membership is left untouched - writing it would reorder a
         * seeded line's stops, the exact divergence overlay-only removes.
         */
        internal fun shouldAttachMembership(lineIsNovel: Boolean, stationIsKnown: Boolean): Boolean =
            lineIsNovel || !stationIsKnown
    }
}
