package com.syrmos.core.data.repository

import com.syrmos.core.common.map.LatLng
import com.syrmos.core.data.seed.ResourceReader
import com.syrmos.core.data.seed.SeedShapesPayload
import kotlinx.serialization.json.Json

/**
 * Loads the bundled per-line OSM route geometry (`schedules-v2/shapes.json`) so
 * the on-device train simulator can place vehicles ALONG the track instead of on
 * a straight chord between stations — the same geometry the web map uses. The
 * file is static, so the parsed result is memoised after the first read.
 *
 * The parse step is exposed as a pure [parseShapes] so it is unit-testable
 * without a platform [ResourceReader].
 */
class LineGeometryRepositoryImpl(
    private val resourceReader: ResourceReader,
) {
    private var cached: Map<String, List<LatLng>>? = null

    /** Per-line polyline as (lat,lng) points. Empty map if the seed is missing. */
    suspend fun getLineGeometry(): Map<String, List<LatLng>> {
        cached?.let { return it }
        val parsed = try {
            parseShapes(resourceReader.readText(SHAPES_PATH))
        } catch (e: Exception) {
            // Missing/corrupt seed must never crash the map — the simulator just
            // falls back to chord positioning (its default when geometry is empty).
            emptyMap()
        }
        cached = parsed
        return parsed
    }

    companion object {
        const val SHAPES_PATH: String = "files/seed/schedules-v2/shapes.json"

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Parse shapes.json text into per-line polylines. Each coordinate is a
         * `[lat, lng]` pair; pairs with fewer than two numbers are dropped, and a
         * line whose polyline ends up empty is omitted entirely so callers can
         * treat "present" as "usable".
         */
        fun parseShapes(text: String): Map<String, List<LatLng>> {
            val payload = json.decodeFromString<SeedShapesPayload>(text)
            return payload.shapes
                .mapValues { (_, shape) ->
                    shape.coordinates.mapNotNull { c ->
                        if (c.size >= 2) LatLng(c[0], c[1]) else null
                    }
                }
                .filterValues { it.isNotEmpty() }
        }
    }
}
