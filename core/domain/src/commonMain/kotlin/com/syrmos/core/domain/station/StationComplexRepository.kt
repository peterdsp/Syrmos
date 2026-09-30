package com.syrmos.core.domain.station

import com.syrmos.core.data.seed.ResourceReader
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The reviewed station-complex registry, read from the bundled seed.
 *
 * A *station complex* is the thing a rider walks into: one name, several real
 * boarding areas, several boarding stop ids. Athens is five ids (`M2_STA`,
 * `A1_ATH`, `A3_ATH`, `A4_ATH`, `GR_ATH`) that no client joined, so the Home
 * board could never show its metro and railway departures together.
 *
 * Membership is reviewed data, never name similarity, rider distance or
 * proximity alone. `scripts/build-station-complexes.mjs` records the rules, the
 * reason behind each name-differing merge and each deliberate split.
 */
class StationComplexRepository(
    private val resourceReader: ResourceReader,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cached: List<StationComplexBoard.Complex>? = null

    suspend fun complexes(): List<StationComplexBoard.Complex> {
        cached?.let { return it }
        val loaded = runCatching {
            json.decodeFromString<SeedRegistry>(resourceReader.readText(REGISTRY_PATH))
                .complexes
                .map { it.toDomain() }
        }.getOrElse { emptyList() }
        cached = loaded
        return loaded
    }

    /**
     * The complex a boarding stop belongs to, or null when the registry does not
     * claim it. A station outside the registry is still a station: the caller
     * builds a single-station complex from its own boarding stops.
     */
    suspend fun complexForStop(stopId: String): StationComplexBoard.Complex? =
        complexes().firstOrNull { complex ->
            complex.areas.any { stopId in it.stopIds }
        }

    private companion object {
        const val REGISTRY_PATH = "files/seed/station-complexes.json"
    }

    @Serializable
    private data class SeedRegistry(
        val version: Int = 1,
        val complexes: List<SeedComplex> = emptyList(),
    )

    @Serializable
    private data class SeedComplex(
        val id: String,
        val name: String,
        val nameEl: String = "",
        val nameSq: String = "",
        val nameIt: String = "",
        val areas: List<SeedArea> = emptyList(),
    ) {
        fun toDomain(): StationComplexBoard.Complex = StationComplexBoard.Complex(
            id = id,
            name = name,
            nameEl = nameEl.ifBlank { name },
            nameSq = nameSq.ifBlank { name },
            nameIt = nameIt.ifBlank { name },
            areas = areas.map { it.toDomain() },
        )
    }

    @Serializable
    private data class SeedArea(
        val id: String,
        val name: String,
        val nameEl: String = "",
        val nameSq: String = "",
        val nameIt: String = "",
        @SerialName("stopIds") val stopIds: List<String> = emptyList(),
    ) {
        fun toDomain(): StationComplexBoard.Complex.Area = StationComplexBoard.Complex.Area(
            id = id,
            name = name,
            nameEl = nameEl.ifBlank { name },
            nameSq = nameSq.ifBlank { name },
            nameIt = nameIt.ifBlank { name },
            stopIds = stopIds,
        )
    }
}
