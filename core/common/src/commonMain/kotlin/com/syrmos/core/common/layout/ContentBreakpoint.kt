package com.syrmos.core.common.layout

// Syrmos 3.0 content breakpoints (prompt section 4.1). A PURE mapping from the
// available content width/height to a layout mode plus the resolved content
// metrics, so every client applies one identical rule. This is the canonical
// definition: Android Compose consumes it directly; the iOS (SwiftUI) and web
// (JS) mirrors must reproduce the same thresholds and the same collapse rules.
//
// `W` is the available app content width AFTER system bars, each safe-area inset,
// a visible keyboard, and native navigation have taken their space (never the
// physical screen width). These are Syrmos content breakpoints, not a redefinition
// of platform size classes: use size classes for container behaviour and this for
// content fit.

/** The four content modes. */
enum class ContentMode { COMPACT, MEDIUM, EXPANDED, WIDE }

/**
 * Resolved layout metrics for a window. All values are logical units (dp/pt/px).
 * `primaryPaneWidth`/`secondaryPaneWidth` are non-null only in a two-pane mode.
 */
data class ContentLayout(
    val mode: ContentMode,
    val outerInset: Int,
    /** Single-column content width (Compact/Medium), else the primary pane width. */
    val contentWidth: Int,
    val primaryPaneWidth: Int? = null,
    val secondaryPaneWidth: Int? = null,
    val columnGap: Int = 0,
    /** True when a short window / large text forced a single scrolling task column. */
    val singleColumnFallback: Boolean = false,
)

object ContentBreakpoint {
    /** A two-pane layout needs at least this much for its secondary pane. */
    const val MIN_SECONDARY_PANE = 360

    /** Below this height we drop to one scrolling column (map opens separately). */
    const val SHORT_HEIGHT = 480

    const val WIDE_CANVAS_MAX = 1600

    /**
     * Resolve the layout for an available content rectangle.
     *
     * @param width available content width W (logical units)
     * @param height available content height H (logical units)
     * @param forceSingleColumn set when accessibility text (or any caller policy)
     *   requires one scrolling column regardless of width.
     */
    fun resolve(width: Int, height: Int, forceSingleColumn: Boolean = false): ContentLayout {
        val short = height < SHORT_HEIGHT

        // A short window or an accessibility-text request never squeezes three
        // panes above a keyboard: one scrolling task column, map disclosed separately.
        if (short || forceSingleColumn) {
            val (inset, w) = readableColumn(width)
            return ContentLayout(
                mode = if (width >= 600) ContentMode.MEDIUM else ContentMode.COMPACT,
                outerInset = inset,
                contentWidth = w,
                singleColumnFallback = true,
            )
        }

        return when {
            width < 600 -> ContentLayout(
                mode = ContentMode.COMPACT,
                outerInset = 16,
                contentWidth = width - 32,
            )
            width < 840 -> {
                val (inset, w) = readableColumn(width)
                ContentLayout(ContentMode.MEDIUM, inset, w)
            }
            width < 1200 -> twoPane(width, primary = 360, outerInset = 24)
            else -> twoPane(
                width = minOf(width, WIDE_CANVAS_MAX),
                primary = 400,
                outerInset = 32,
            )
        }
    }

    // Medium: one readable column min(W-48, 680), centered, 24 outer minimum.
    private fun readableColumn(width: Int): Pair<Int, Int> {
        val contentWidth = minOf(width - 48, 680)
        val inset = maxOf(24, (width - contentWidth) / 2)
        return inset to contentWidth
    }

    // Expanded/Wide: fixed primary pane, 24 gap, secondary takes the remainder.
    // If the secondary pane cannot reach MIN_SECONDARY_PANE, collapse to Medium.
    private fun twoPane(width: Int, primary: Int, outerInset: Int): ContentLayout {
        val gap = 24
        val secondary = width - (outerInset * 2) - primary - gap
        if (secondary < MIN_SECONDARY_PANE) {
            val (inset, w) = readableColumn(width)
            return ContentLayout(ContentMode.MEDIUM, inset, w)
        }
        return ContentLayout(
            mode = if (primary >= 400) ContentMode.WIDE else ContentMode.EXPANDED,
            outerInset = outerInset,
            contentWidth = primary,
            primaryPaneWidth = primary,
            secondaryPaneWidth = secondary,
            columnGap = gap,
        )
    }
}
