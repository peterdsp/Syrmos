package com.syrmos.core.model.transit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LineColor(val hex: String) {
    @SerialName("green") GREEN("#00843D"),
    @SerialName("red") RED("#DA291C"),
    @SerialName("blue") BLUE("#0072CE"),
    @SerialName("tram") TRAM_ORANGE("#F39800"),
    @SerialName("suburban") SUBURBAN_PURPLE("#6F2DA8"),
    @SerialName("scenic") SCENIC_OCHRE("#B8860B"),
    ;

    companion object {
        fun fromHexOrType(hex: String, type: String): LineColor =
            entries.firstOrNull { it.hex.equals(hex, ignoreCase = true) }
                ?: when (type.lowercase()) {
                    "metro" -> GREEN
                    "tram" -> TRAM_ORANGE
                    "suburban" -> SUBURBAN_PURPLE
                    "scenic" -> SCENIC_OCHRE
                    else -> GREEN
                }
    }
}
