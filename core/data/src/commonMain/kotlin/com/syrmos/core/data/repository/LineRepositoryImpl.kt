package com.syrmos.core.data.repository

import com.syrmos.core.database.SyrmosDatabase
import com.syrmos.core.database.mapper.toDomain
import com.syrmos.core.data.seed.ResourceReader
import com.syrmos.core.data.seed.SeedLine
import com.syrmos.core.data.seed.SeedLinesPayload
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineColor
import com.syrmos.core.model.transit.LineStatus
import com.syrmos.core.model.transit.Region
import com.syrmos.core.model.transit.LineType
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LineRepositoryImpl(
    private val database: SyrmosDatabase,
    private val resourceReader: ResourceReader,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var seedLines: List<Line>? = null

    fun getAllLines(): Flow<List<Line>> = flow {
        val lines = database.syrmosDatabaseQueries.getAllLines().executeAsList()
            .map { it.toDomain() }
        emit(lines.ifEmpty { readSeedLines() })
    }

    fun getLineById(id: String): Flow<Line?> = flow {
        val line = database.syrmosDatabaseQueries.getLineById(id).executeAsOneOrNull()
            ?.toDomain()
        emit(line ?: readSeedLines().firstOrNull { it.id == id })
    }

    fun getLinesByType(type: LineType): Flow<List<Line>> = flow {
        val lines = database.syrmosDatabaseQueries.getLinesByType(type.name.lowercase())
            .executeAsList()
            .map { it.toDomain() }
        emit(lines.ifEmpty { readSeedLines().filter { it.type == type } })
    }

    /**
     * Reads the generator's `schedules-v2/lines.json`, the single source of truth
     * for lines. The old flat `seed/lines.json` was generated from hardcoded Swift
     * by a sync script broken since June 2026, so it could not carry region or
     * status and had drifted from the server. See
     * docs/plans/2026-07-17-server-as-single-source-for-lines.md.
     */
    private suspend fun readSeedLines(): List<Line> {
        seedLines?.let { return it }
        return json.decodeFromString<SeedLinesPayload>(
            resourceReader.readText("files/seed/schedules-v2/lines.json"),
        ).lines.map { seed ->
            Line(
                id = seed.id,
                name = seed.name,
                nameEl = seed.nameEl,
                type = LineType.valueOf(seed.type.uppercase()),
                color = LineColor.fromHexOrType(seed.color, seed.type),
                terminalA = seed.terminalA,
                terminalB = seed.terminalB,
                stationCount = seed.stationCount,
                region = Region.fromRaw(seed.region),
                status = LineStatus.fromRaw(seed.status),
            )
        }.also {
            seedLines = it
        }
    }
}
