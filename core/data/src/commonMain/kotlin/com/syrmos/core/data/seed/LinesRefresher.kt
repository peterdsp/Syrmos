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

                // Upsert memberships in place; deliberately do NOT delete-then-
                // replace. The server derives stationCount from the very list it
                // sends (generator.py: "stationCount": len(stops)), so a partial
                // payload is internally self-consistent and indistinguishable from
                // a complete one; deleting first would let such a payload truncate
                // the line. There is no independent completeness signal (no
                // versioned membership manifest), so upsert-only is the safe
                // choice: it never truncates. A stop legitimately removed upstream
                // can linger until a reseed, an accepted low-severity limitation.
                // The interchange resolver's own-stop guarantee still holds because
                // the server only ever emits a line's authoritative per-line
                // station ids, never another hub's id under it, so no wrong-id row
                // is inserted. INSERT OR REPLACE is composite-keyed on
                // (station_id, line_id), so this updates positions in place.
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
