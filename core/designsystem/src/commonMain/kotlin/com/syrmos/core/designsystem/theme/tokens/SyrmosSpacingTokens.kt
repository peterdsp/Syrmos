package com.syrmos.core.designsystem.theme.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing on an 8pt grid (design doc section 2, task T2). [Raw] holds the
 * platform-neutral point values the Swift / CSS exports read; the [Dp]
 * properties are the Compose wrappers. Generous touch targets: [minTouchTarget]
 * is the floor for any tappable control.
 */
object SyrmosSpacingTokens {

    object Raw {
        const val none = 0
        const val xxs = 2
        const val xs = 4
        const val sm = 8
        const val md = 12
        const val lg = 16
        const val xl = 24
        const val xxl = 32
        const val xxxl = 48
        const val huge = 64
        const val minTouchTarget = 44
    }

    val none: Dp = Raw.none.dp
    val xxs: Dp = Raw.xxs.dp
    val xs: Dp = Raw.xs.dp
    val sm: Dp = Raw.sm.dp
    val md: Dp = Raw.md.dp
    val lg: Dp = Raw.lg.dp
    val xl: Dp = Raw.xl.dp
    val xxl: Dp = Raw.xxl.dp
    val xxxl: Dp = Raw.xxxl.dp
    val huge: Dp = Raw.huge.dp

    /** Minimum edge of any tappable control. */
    val minTouchTarget: Dp = Raw.minTouchTarget.dp
}
