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

    /** How the timeline moves to the current row: a glide, or a jump under Reduce Motion. */
    enum class Motion { GLIDE, JUMP }

    /**
     * A reader who asked the system for reduced motion gets the row placed
     * without a scroll animation; everyone else gets the glide. The map camera
     * keeps its own animation for now (it conveys distance, not decoration).
     */
    fun motion(reduceMotion: Boolean): Motion = if (reduceMotion) Motion.JUMP else Motion.GLIDE
}
