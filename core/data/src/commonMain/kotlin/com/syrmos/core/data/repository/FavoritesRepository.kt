package com.syrmos.core.data.repository

import com.syrmos.core.database.SyrmosDatabase
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Persists user favorites against the bundled SQLDelight `favorite_entity`
 * table. This is the primitive Ariadne's "favorite this station" writes to and
 * a future favorites screen will read from, the same shape as the existing
 * repositories. Station favorites are keyed by station id under the "station"
 * type.
 */
@OptIn(ExperimentalTime::class)
class FavoritesRepository(
    private val database: SyrmosDatabase,
) {
    private val queries get() = database.syrmosDatabaseQueries

    fun isStationFavorite(stationId: String): Boolean =
        queries.isFavorite(TYPE_STATION, stationId).executeAsOne() > 0L

    /** Toggles the station's favorite state and returns the new value. */
    fun toggleStation(stationId: String): Boolean {
        return if (isStationFavorite(stationId)) {
            queries.deleteFavorite(TYPE_STATION, stationId)
            false
        } else {
            queries.insertFavorite(
                id = "$TYPE_STATION:$stationId",
                type = TYPE_STATION,
                reference_id = stationId,
                created_at = Clock.System.now().toString(),
            )
            true
        }
    }

    fun favoriteStationIds(): List<String> =
        queries.getFavoritesByType(TYPE_STATION).executeAsList().map { it.reference_id }

    private companion object {
        const val TYPE_STATION = "station"
    }
}
