package com.syrmos.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cross-tab handoff for opening the canonical map train detail sheet. */
object TrainSelectionBus {
    private val _requestedTrainId = MutableStateFlow<String?>(null)
    val requestedTrainId: StateFlow<String?> = _requestedTrainId.asStateFlow()

    fun request(trainId: String) {
        _requestedTrainId.value = trainId
    }

    fun consume() {
        _requestedTrainId.value = null
    }
}
