package com.syrmos.core.model.transit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LineColor(val hex: String) {
    @SerialName("green") GREEN("#00843D"),
    @SerialName("red") RED("#DA291C"),
    @SerialName("blue") BLUE("#0072CE"),
    @SerialName("tram") TRAM_ORANGE("#E87722"),
    @SerialName("suburban") SUBURBAN_PURPLE("#6F2DA8"),
}
