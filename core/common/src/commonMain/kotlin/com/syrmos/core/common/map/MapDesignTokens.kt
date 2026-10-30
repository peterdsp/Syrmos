package com.syrmos.core.common.map

/**
 * The single source of truth for the Syrmos map's marker + line design, shared
 * by all three platforms. Android (Compose + osmdroid) consumes this object
 * directly (feature/map already depends on core:common); iOS (SwiftUI + MapKit)
 * and web (Leaflet) mirror it verbatim in the `MapDesignTokens` enum in
 * `iosApp/iosApp/DesignSystem/SyrmosColors.swift` and the `MAP_TOKENS` object in
 * `composeApp/src/wasmJsMain/resources/web-map.js`
 * (the same manual-mirror pattern as AriadneGrammar / AthensTransitParser).
 * Change a value here, then mirror it, so the three maps never drift.
 *
 * The dot sizes are a design baseline in logical points; each platform applies
 * them at its own scale (web CSS px, iOS points, Android draws a bitmap at these
 * px then anchors it centred). The ratios, colours, dash patterns and the
 * glyph-visibility rule are identical everywhere.
 */
object MapDesignTokens {

    // --- Station dot markers -------------------------------------------------
    /** Country zoom: the smallest dot. */
    const val DOT_COUNTRY = 10
    /** City / district zoom: the default dot. */
    const val DOT_CITY = 13
    /** Selected stop: a little larger, with a colour halo. */
    const val DOT_SELECTED = 18
    /** Width of the crisp white ring around a dot. */
    const val RING_WIDTH = 1.5
    /** Width of the soft line-colour halo drawn around a selected dot. */
    const val SELECTED_HALO_WIDTH = 3.0
    /** Inner white cap radius as a fraction of the dot radius (district+ zoom). */
    const val INNER_CAP_RATIO = 0.34
    /** Interchange ring thickness as a fraction of the dot radius. */
    const val INTERCHANGE_RING_RATIO = 0.28

    /** Show the mode glyph only when the stop is selected or zoomed in this far. */
    const val GLYPH_MIN_ZOOM = 14

    /**
     * Below this zoom the country would be a field of confetti if every one of
     * the ~390 stops were drawn, so minor stops are hidden and only the network
     * skeleton shows: the coloured line strokes plus the interchange hubs (and
     * any selected stop). Zoom into a city (>= this) to resolve every station.
     */
    const val MINOR_STOP_MIN_ZOOM = 10

    /**
     * The `is_interchange` flag is over-applied in the data (a whole metro line
     * can be flagged), so even the interchange set is ~58 loose dots, too busy
     * for a whole-country view. Below this zoom we drop to only the *major* hubs:
     * a station whose lines span 2+ distinct [line types][com.syrmos] (a genuine
     * cross-modal transfer, e.g. metro + suburban, metro + tram, bus + suburban).
     * That leaves ~16 real junctions Greece-wide and lets the line strokes carry
     * the rest. Three tiers total: major hubs (< this) -> all interchanges
     * (< [MINOR_STOP_MIN_ZOOM]) -> all stops. Each platform derives "major hub"
     * from its own line-type lookup; the rule (2+ distinct types) is identical.
     */
    const val MAJOR_HUB_MIN_ZOOM = 8

    /**
     * At or below this zoom (the whole-Greece / country view) NO station dots
     * draw at all: the coloured line network alone carries the map, which reads
     * far cleaner than a scatter of ~20 hub rings floating over the sea-heavy
     * Aegean view. Station dots begin resolving above this (major hubs first).
     */
    const val LINES_ONLY_MAX_ZOOM = 7

    // --- Vehicle capsule markers ---------------------------------------------
    /** Capsule width in logical points. */
    const val VEHICLE_W = 24
    /** Capsule height in logical points. */
    const val VEHICLE_H = 13
    /** Capsule corner radius. */
    const val VEHICLE_RADIUS = 6
    /** White border width around a capsule. */
    const val VEHICLE_BORDER = 2

    // --- Tap targets ---------------------------------------------------------
    /**
     * Minimum tap target in logical points, from the Android and Apple
     * accessibility guidance (48dp / 44pt; take the larger).
     *
     * This matters more than it looks on a map: a marker's artwork is usually
     * also its hit rect, so a dot drawn at [DOT_CITY] is a 13-point tap target,
     * which a finger cannot reliably hit. Marker bitmaps are therefore padded
     * out to this size transparently: the dot keeps its design size and the
     * touchable area grows around it.
     */
    const val MIN_TAP_TARGET = 48

    /**
     * The edge length, in logical points, of the square bitmap a station pin is
     * drawn into: the dot, plus room for the selected halo, never smaller than
     * [MIN_TAP_TARGET].
     *
     * Platforms multiply this by their own scale factor (Android density, iOS
     * screen scale). Drawing these point values as raw pixels is the bug this
     * function exists to prevent: it silently shrinks every dot as screen
     * density rises, which on a 2.75x phone turned a 22-point dot into roughly
     * 8dp of tappable area.
     */
    fun pinBitmapSize(dotSize: Int, selected: Boolean): Double {
        val halo = if (selected) dotSize * 0.5 else 2.0
        val drawn = dotSize + halo * 2
        return if (drawn > MIN_TAP_TARGET) drawn else MIN_TAP_TARGET.toDouble()
    }

    // --- Line strokes --------------------------------------------------------
    /** Suspended / not-yet-open lines render greyed. */
    const val GREYED_COLOR = "#94A3B8"
    /** Dash pattern (on, off) for a rail-replacement bus line. */
    val BUS_DASH = intArrayOf(2, 7)
    /** Dash pattern (on, off) for a greyed / suspended line. */
    val GREYED_DASH = intArrayOf(6, 8)
}
