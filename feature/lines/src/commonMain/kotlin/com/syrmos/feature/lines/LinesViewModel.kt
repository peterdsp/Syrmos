package com.syrmos.feature.lines

import com.syrmos.core.domain.usecase.GetLinesUseCase
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.Region
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
    val searchQuery: String = "",
    val selectedRegion: Region? = null,
    val selectedType: LineType? = null,
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

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onRegionSelected(region: Region?) {
        _uiState.update { it.copy(selectedRegion = region) }
    }

    fun onTypeSelected(type: LineType?) {
        _uiState.update { it.copy(selectedType = type) }
    }
}
