package com.syrmos.core.model.transit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LineColor(val hex: String) {
    @SerialName("green") GREEN("#00843D"),
    @SerialName("red") RED("#E61E2A"),
    @SerialName("blue") BLUE("#0083C9"),
    @SerialName("tram") TRAM_ORANGE("#F39800"),
    @SerialName("suburban") SUBURBAN_PURPLE("#EE2625"),
    ;

    companion object {
        fun fromHexOrType(hex: String, type: String): LineColor =
            entries.firstOrNull { it.hex.equals(hex, ignoreCase = true) }
                ?: when (type.lowercase()) {
                    "metro" -> GREEN
                    "tram" -> TRAM_ORANGE
                    "suburban" -> SUBURBAN_PURPLE
                    else -> GREEN
                }
    }
}
