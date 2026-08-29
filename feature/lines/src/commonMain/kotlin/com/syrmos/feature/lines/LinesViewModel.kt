package com.syrmos.feature.lines

import com.syrmos.core.domain.usecase.GetLinesUseCase
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.Region
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExploreSegment { DESTINATIONS, YOUR_NETWORK }

data class LinesUiState(
    val lines: List<Line> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedRegion: Region? = null,
    val selectedType: LineType? = null,
    val segment: ExploreSegment = ExploreSegment.DESTINATIONS,
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
            getLinesUseCase.getAllLines()
                // Without this, a throwing lines flow (e.g. a bundled-data parse
                // error) leaves isLoading stuck true — a permanent spinner — and
                // propagates the exception. Clear the spinner and stop instead.
                .catch { _uiState.update { it.copy(isLoading = false) } }
                .collect { lines ->
                    _uiState.update { it.copy(lines = lines, isLoading = false) }
                }
        }
    }

    fun onSegmentChanged(segment: ExploreSegment) {
        _uiState.update { it.copy(segment = segment) }
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
