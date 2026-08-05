package com.syrmos.app

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

sealed interface NotificationNavEvent {
    data class Alert(val alertId: String) : NotificationNavEvent
    data class Station(val stationId: String) : NotificationNavEvent
    data object Weather : NotificationNavEvent
    data object Home : NotificationNavEvent
}

object NotificationNavBus {
    private val incoming = Channel<NotificationNavEvent>(Channel.BUFFERED)
    private val home = Channel<NotificationNavEvent>(Channel.BUFFERED)

    val events: Flow<NotificationNavEvent> = incoming.receiveAsFlow()
    val homeEvents: Flow<NotificationNavEvent> = home.receiveAsFlow()

    fun post(type: String, contentId: String?) {
        val event = when (type.lowercase()) {
            "service_alert" -> contentId?.takeIf { it.isNotBlank() }
                ?.let(NotificationNavEvent::Alert) ?: NotificationNavEvent.Home
            "weather_alert" -> NotificationNavEvent.Weather
            "nearby_alert", "station" -> contentId?.takeIf { it.isNotBlank() }
                ?.let(NotificationNavEvent::Station) ?: NotificationNavEvent.Home
            else -> NotificationNavEvent.Home
        }
        incoming.trySend(event)
    }

    fun dispatchToHome(event: NotificationNavEvent) {
        home.trySend(event)
    }
}
