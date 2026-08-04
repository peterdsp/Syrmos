package com.syrmos.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface AriadneNavEvent {
    data class Station(val stationId: String) : AriadneNavEvent
    data class Line(val lineId: String) : AriadneNavEvent
}

object AriadneNavBus {
    private val _events = MutableSharedFlow<AriadneNavEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AriadneNavEvent> = _events.asSharedFlow()
    fun navigate(event: AriadneNavEvent) { _events.tryEmit(event) }
}
