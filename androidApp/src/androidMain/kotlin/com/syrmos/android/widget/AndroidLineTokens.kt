package com.syrmos.android.widget

import androidx.compose.ui.graphics.Color

/**
 * Android mirror of the iOS `SyrmosLineTokens`: single source of truth for line
 * colors and labels in the Glance widgets. Kept in sync with the Swift values.
 */
object AndroidLineTokens {
    fun color(lineId: String): Color = when (normalize(lineId)) {
        "M1" -> Color(0xFF30A050)      // metro green
        "M2" -> Color(0xFFD93333)      // metro red
        "M3" -> Color(0xFF1A5CB8)      // metro blue
        "T6", "T7" -> Color(0xFFF28C1C) // tram orange
        else -> Color(0xFF6B4DA8)      // suburban purple
    }

    fun label(lineId: String): String = normalize(lineId)

    fun normalize(id: String): String = if (id.startsWith("M3")) "M3" else id

    val allLines: List<String> = listOf("M1", "M2", "M3", "T6", "T7", "A1", "A2")
}
