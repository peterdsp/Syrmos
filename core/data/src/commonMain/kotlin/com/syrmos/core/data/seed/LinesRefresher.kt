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

        database.transaction {
            remoteLines.forEach { line ->
                database.syrmosDatabaseQueries.insertLine(
                    id = line.id,
                    name = line.name,
                    name_el = line.nameEl,
                    type = line.type.lowercase(),
                    color = line.color,
                    terminal_a = line.terminalA,
                    terminal_b = line.terminalB,
                    station_count = line.stationCount.toLong(),
                    // This upserts over the seeded rows, so region and status must
                    // be carried through. Dropping them here would silently promote
                    // an under-construction line back to live on the first refresh,
                    // after the seed had it right.
                    region = line.region,
                    status = line.status,
                )

                // Replace this line's membership set atomically ONLY when the
                // payload is complete (declared stationCount, unique ids): clear
                // its existing station_line rows, then insert the returned set, so
                // a stop removed or re-keyed upstream cannot linger as a stale
                // (station_id, line_id) row that would feed a wrong stop to the
                // interchange resolver. A partial or duplicated snapshot is NOT
                // authoritative, so the line keeps its prior memberships rather
                // than being truncated to the incomplete set.
                if (isCompleteMembership(line.stations.map { it.id }, line.stationCount)) {
                    database.syrmosDatabaseQueries.deleteStationLinesForLine(line.id)
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
                        }
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
         * Whether a line's station payload is complete enough to REPLACE its
         * stored memberships: non-empty, all ids unique, and exactly the declared
         * [declaredCount]. A partial or duplicated snapshot fails this, so the
         * refresh keeps the line's prior memberships instead of truncating them to
         * the incomplete set. In the bundled data every line's stations.size
         * equals its stationCount with no duplicate ids, so complete payloads pass.
         */
        internal fun isCompleteMembership(stationIds: List<String>, declaredCount: Int): Boolean =
            stationIds.isNotEmpty() &&
                stationIds.size == declaredCount &&
                stationIds.toSet().size == stationIds.size
    }
}
