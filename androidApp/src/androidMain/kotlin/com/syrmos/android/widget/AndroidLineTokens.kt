package com.syrmos.android.widget

import androidx.compose.ui.graphics.Color

/**
 * Android widget line colors, kept in sync with the canonical SyrmosColorTokens
 * in core/designsystem/.../theme/tokens/SyrmosColorTokens.kt. The androidApp
 * module does not depend on core:designsystem (widget APK size), so these are
 * mirrored values. Update here when the tokens change.
 */
object AndroidLineTokens {
    fun color(lineId: String): Color = when (normalize(lineId)) {
        "M1" -> Color(0xFF00843D)      // SyrmosColorTokens.Raw.metroGreen
        "M2" -> Color(0xFFDA291C)      // SyrmosColorTokens.Raw.metroRed
        "M3" -> Color(0xFF0072CE)      // SyrmosColorTokens.Raw.metroBlue
        "T6", "T7" -> Color(0xFFF39800) // SyrmosColorTokens.Raw.tram
        else -> Color(0xFF6F2DA8)      // SyrmosColorTokens.Raw.suburban
    }

    fun label(lineId: String): String = normalize(lineId)

    fun normalize(id: String): String = if (id.startsWith("M3")) "M3" else id

    val allLines: List<String> = listOf("M1", "M2", "M3", "T6", "T7", "A1", "A2")
}
