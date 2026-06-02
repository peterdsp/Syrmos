package com.syrmos.core.domain.usecase

import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import kotlinx.coroutines.flow.Flow

class GetLinesUseCase(
    private val lineRepository: LineRepositoryImpl,
) {
    fun getAllLines(): Flow<List<Line>> = lineRepository.getAllLines()

    fun getLinesByType(type: LineType): Flow<List<Line>> =
        lineRepository.getLinesByType(type)
}
