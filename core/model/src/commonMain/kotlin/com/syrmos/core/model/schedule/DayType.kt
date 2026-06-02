package com.syrmos.core.model.schedule

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DayType {
    @SerialName("weekday") WEEKDAY,
    @SerialName("friday") FRIDAY,
    @SerialName("saturday") SATURDAY,
    @SerialName("sunday") SUNDAY,
}
