package com.syrmos.core.designsystem.theme.tokens

import androidx.compose.ui.graphics.Color

/**
 * The canonical colour tokens for Syrmos 2.0 "Hellenic Rail Atlas" (design doc
 * section 2). This Kotlin module is the single semantic source; iOS (a generated
 * Swift file) and web (CSS custom properties) mirror it, and the existing
 * per-platform colour files become mirror targets (design doc section 11, task
 * T2). Change a value here, then regenerate the exports (task T3).
 *
 * Identity is light-first: transit is glanced at outdoors, in sunlight, on
 * platforms. Dark mode is a graphite night variant of the same system, never the
 * foundation. Hard rule: line colours are service data, not brand decoration, so
 * a line colour only activates when the user is dealing with a line, route,
 * departure, interchange, or map segment. Screen chrome stays neutral
 * station-white with the single Aegean-blue brand core.
 *
 * [Raw] holds the platform-neutral ARGB values (the mirror source of truth); the
 * [Color] properties below are the Compose wrappers consumers use.
 */
object SyrmosColorTokens {

    /** Platform-neutral ARGB longs. The Swift / CSS exports read these. */
    object Raw {
        // Brand: a single Aegean / rail blue. The only chrome accent.
        const val brand = 0xFF1466B8
        const val brandStrong = 0xFF0F4E8C
        const val brandOnDark = 0xFF8ECAFF

        // Light-first surfaces: warm station-white, stone/marble, clean cards.
        const val surface = 0xFFF7F5F1          // station-white primary
        const val surfaceMuted = 0xFFEFEBE4     // light stone / marble secondary
        const val surfaceCard = 0xFFFFFFFF      // clean white cards
        const val onSurface = 0xFF14181F        // near-black text
        const val onSurfaceMuted = 0xFF5B636E   // station-sign grey secondary text
        const val outline = 0xFFE0DACF          // thin warm rail-line separators

        // Graphite night + underground variant of the same system.
        const val surfaceDark = 0xFF0F1216
        const val surfaceMutedDark = 0xFF171B21
        const val surfaceCardDark = 0xFF1B2028
        const val onSurfaceDark = 0xFFE6ECF5
        const val onSurfaceMutedDark = 0xFF9AA3AF
        const val outlineDark = 0xFF2A2F37

        // Line-service semantics (mode legend). Individual lines still carry
        // their own explicit colour; these are the per-mode legend / fallback.
        const val metroGreen = 0xFF00843D       // M1
        const val metroRed = 0xFFDA291C         // M2
        const val metroBlue = 0xFF0072CE        // M3
        const val tram = 0xFFF39800             // tram orange
        const val suburban = 0xFF6F2DA8         // suburban purple
        const val national = 0xFF2A5C8A         // regional / national steel-blue
        const val scenic = 0xFFB8860B           // scenic ochre / gold
        const val bus = 0xFFB45309              // rail-replacement bus amber

        // Source-confidence + state (design doc section 7). Calm, never alarming.
        const val live = 0xFF059669
        const val scheduled = 0xFF2563EB
        const val offline = 0xFF6B7280
        const val estimated = 0xFFB45309
        const val warning = 0xFFD97706
        const val disruption = 0xFFDC2626

        // Arrival urgency (existing functional colours, kept).
        const val arrivalSoon = 0xFF2E7D32
        const val arrivalModerate = 0xFFE65100
        const val arrivalFar = 0xFF757575
    }

    // --- Brand -------------------------------------------------------------
    val brand = Color(Raw.brand)
    val brandStrong = Color(Raw.brandStrong)
    val brandOnDark = Color(Raw.brandOnDark)

    // --- Surfaces (light) --------------------------------------------------
    val surface = Color(Raw.surface)
    val surfaceMuted = Color(Raw.surfaceMuted)
    val surfaceCard = Color(Raw.surfaceCard)
    val onSurface = Color(Raw.onSurface)
    val onSurfaceMuted = Color(Raw.onSurfaceMuted)
    val outline = Color(Raw.outline)

    // --- Surfaces (dark) ---------------------------------------------------
    val surfaceDark = Color(Raw.surfaceDark)
    val surfaceMutedDark = Color(Raw.surfaceMutedDark)
    val surfaceCardDark = Color(Raw.surfaceCardDark)
    val onSurfaceDark = Color(Raw.onSurfaceDark)
    val onSurfaceMutedDark = Color(Raw.onSurfaceMutedDark)
    val outlineDark = Color(Raw.outlineDark)

    // --- Line-service semantics -------------------------------------------
    val metroGreen = Color(Raw.metroGreen)
    val metroRed = Color(Raw.metroRed)
    val metroBlue = Color(Raw.metroBlue)
    val tram = Color(Raw.tram)
    val suburban = Color(Raw.suburban)
    val national = Color(Raw.national)
    val scenic = Color(Raw.scenic)
    val bus = Color(Raw.bus)

    // --- Source-confidence + state ----------------------------------------
    val live = Color(Raw.live)
    val scheduled = Color(Raw.scheduled)
    val offline = Color(Raw.offline)
    val estimated = Color(Raw.estimated)
    val warning = Color(Raw.warning)
    val disruption = Color(Raw.disruption)

    // --- Arrival urgency ---------------------------------------------------
    val arrivalSoon = Color(Raw.arrivalSoon)
    val arrivalModerate = Color(Raw.arrivalModerate)
    val arrivalFar = Color(Raw.arrivalFar)
}
