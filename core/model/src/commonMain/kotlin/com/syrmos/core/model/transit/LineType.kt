package com.syrmos.core.model.transit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LineType {
    @SerialName("metro") METRO,
    @SerialName("tram") TRAM,
    @SerialName("suburban") SUBURBAN,
}
