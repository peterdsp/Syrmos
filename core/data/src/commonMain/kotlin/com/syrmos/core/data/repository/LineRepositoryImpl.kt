package com.syrmos.core.data.repository

import com.syrmos.core.database.SyrmosDatabase
import com.syrmos.core.database.mapper.toDomain
import com.syrmos.core.data.seed.ResourceReader
import com.syrmos.core.data.seed.SeedLine
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineColor
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

    private suspend fun readSeedLines(): List<Line> {
        seedLines?.let { return it }
        return json.decodeFromString<List<SeedLine>>(
            resourceReader.readText("files/seed/lines.json"),
        ).map { seed ->
            Line(
                id = seed.id,
                name = seed.name,
                nameEl = seed.nameEl,
                type = LineType.valueOf(seed.type.uppercase()),
                color = LineColor.entries.first { it.hex == seed.color },
                terminalA = seed.terminalA,
                terminalB = seed.terminalB,
                stationCount = seed.stationCount,
            )
        }.also {
            seedLines = it
        }
    }
}
