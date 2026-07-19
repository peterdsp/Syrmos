package com.syrmos.core.designsystem.theme.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner radii for the consistent, calm shape language (design doc section 2,
 * task T2). [Raw] holds the platform-neutral point values the Swift / CSS
 * exports read; the [Dp] values and [RoundedCornerShape]s are the Compose
 * wrappers. [pill] is an effectively-fully-rounded radius for chips and badges.
 */
object SyrmosShapeTokens {

    object Raw {
        const val sm = 8
        const val md = 12
        const val lg = 16
        const val xl = 24
        const val pill = 999
    }

    val sm: Dp = Raw.sm.dp
    val md: Dp = Raw.md.dp
    val lg: Dp = Raw.lg.dp
    val xl: Dp = Raw.xl.dp
    val pill: Dp = Raw.pill.dp

    val shapeSm = RoundedCornerShape(sm)
    val shapeMd = RoundedCornerShape(md)
    val shapeLg = RoundedCornerShape(lg)
    val shapeXl = RoundedCornerShape(xl)
    val shapePill = RoundedCornerShape(pill)
}
