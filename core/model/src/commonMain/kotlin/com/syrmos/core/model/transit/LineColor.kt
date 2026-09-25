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
        /**
         * Resolve a seed colour. An exact hex match wins; otherwise the NEAREST
         * palette colour by RGB distance, so a seed that carries the operator's
         * own shade (M3 "#0083C9", M2 "#E61E2A") still lands on blue and red
         * instead of every metro line falling back to green by type. The type
         * fallback only applies when the hex cannot be parsed at all.
         */
        fun fromHexOrType(hex: String, type: String): LineColor {
            entries.firstOrNull { it.hex.equals(hex, ignoreCase = true) }?.let { return it }
            val rgb = parseHex(hex)
            if (rgb != null) {
                return entries.minBy { c ->
                    val (r, g, b) = parseHex(c.hex) ?: Triple(0, 0, 0)
                    val dr = r - rgb.first; val dg = g - rgb.second; val db = b - rgb.third
                    dr * dr + dg * dg + db * db
                }
            }
            return when (type.lowercase()) {
                "metro" -> GREEN
                "tram" -> TRAM_ORANGE
                "suburban" -> SUBURBAN_PURPLE
                "scenic" -> SCENIC_OCHRE
                else -> GREEN
            }
        }

        private fun parseHex(hex: String): Triple<Int, Int, Int>? {
            val h = hex.trim().removePrefix("#")
            if (h.length != 6 || !h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
            return Triple(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
        }
    }
}
