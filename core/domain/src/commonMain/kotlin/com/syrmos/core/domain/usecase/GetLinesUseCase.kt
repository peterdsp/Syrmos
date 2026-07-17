package com.syrmos.core.domain.usecase

import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetLinesUseCase(
    private val lineRepository: LineRepositoryImpl,
) {
    /**
     * Every line, including ones that are built but not open.
     *
     * Use this only for drawing. A non-operational line renders greyed because the
     * track is real, but it must never reach a departure board, a route, a
     * last-train answer or a track picker. For anything a user would act on, use
     * [getOperationalLines].
     */
    fun getAllLines(): Flow<List<Line>> = lineRepository.getAllLines()

    /**
     * Lines that actually carry trains. The default for anything the user acts on.
     */
    fun getOperationalLines(): Flow<List<Line>> =
        lineRepository.getAllLines().map { lines -> lines.filter { it.isOperational } }

    fun getLinesByType(type: LineType): Flow<List<Line>> =
        lineRepository.getLinesByType(type)
            .map { lines -> lines.filter { it.isOperational } }
}
