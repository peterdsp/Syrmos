package com.syrmos.core.data.repository

import com.syrmos.core.database.SyrmosDatabase
import com.syrmos.core.database.mapper.toDomain
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LineRepositoryImpl(
    private val database: SyrmosDatabase,
) {
    fun getAllLines(): Flow<List<Line>> = flow {
        val lines = database.syrmosDatabaseQueries.getAllLines().executeAsList()
            .map { it.toDomain() }
        emit(lines)
    }

    fun getLineById(id: String): Flow<Line?> = flow {
        val line = database.syrmosDatabaseQueries.getLineById(id).executeAsOneOrNull()
            ?.toDomain()
        emit(line)
    }

    fun getLinesByType(type: LineType): Flow<List<Line>> = flow {
        val lines = database.syrmosDatabaseQueries.getLinesByType(type.name.lowercase())
            .executeAsList()
            .map { it.toDomain() }
        emit(lines)
    }
}
