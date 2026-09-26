package com.syrmos.core.domain.go

/**
 * Keeps manual browsing of the GO timeline stable (master plan, GO contract):
 * the timeline never snaps back on its own; instead a "Back to now" action is
 * offered while the current stop is out of view. Shared with iOS
 * (`GoTimelineFocus` in GoJourneyView.swift). All values in one coordinate
 * space and unit (pixels or points).
 */
object GoTimelineFocus {
    /** Whether the current row lies fully inside the viewport. */
    fun isVisible(rowTop: Float, rowBottom: Float, viewportTop: Float, viewportBottom: Float): Boolean =
        rowTop >= viewportTop && rowBottom <= viewportBottom

    /**
     * The scroll offset that places the current row a third of the way down the
     * viewport (the eye's resting line, with the next stops below), clamped to
     * the scrollable range.
     */
    fun targetOffset(rowTopInContent: Float, viewportHeight: Float, maxOffset: Float): Float =
        (rowTopInContent - viewportHeight / 3f).coerceIn(0f, maxOffset.coerceAtLeast(0f))
}
