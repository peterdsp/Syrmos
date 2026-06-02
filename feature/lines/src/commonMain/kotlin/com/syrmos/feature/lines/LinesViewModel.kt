package com.syrmos.feature.lines

import com.syrmos.core.domain.usecase.GetLinesUseCase
import com.syrmos.core.model.transit.Line
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LinesUiState(
    val lines: List<Line> = emptyList(),
    val isLoading: Boolean = true,
)

class LinesViewModel(
    private val getLinesUseCase: GetLinesUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(LinesUiState())
    val uiState: StateFlow<LinesUiState> = _uiState.asStateFlow()

    init {
        loadLines()
    }

    private fun loadLines() {
        scope.launch {
            getLinesUseCase.getAllLines().collect { lines ->
                _uiState.update { it.copy(lines = lines, isLoading = false) }
            }
        }
    }
}
