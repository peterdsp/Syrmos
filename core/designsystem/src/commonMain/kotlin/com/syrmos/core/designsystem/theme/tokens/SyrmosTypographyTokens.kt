package com.syrmos.core.designsystem.theme.tokens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography roles (design doc section 2, task T2). One family with strong Greek
 * and Latin coverage (the platform system font), a tight display scale for the
 * "now" numerals, and tabular figures for clock times so they never jitter as
 * the countdown ticks.
 *
 * [Raw] holds the platform-neutral values (sizes in sp, line heights in sp,
 * weights as numeric font weights) the Swift / CSS exports read; the [TextStyle]
 * roles are the Compose wrappers. [displayNow] and [clock] request tabular
 * figures via `tnum`.
 */
object SyrmosTypographyTokens {

    object Raw {
        // weights
        const val regular = 400
        const val medium = 500
        const val semibold = 600
        const val bold = 700

        // size / lineHeight in sp
        const val displayNowSize = 44
        const val displayNowLine = 48
        const val displayPulseSize = 56
        const val displayPulseLine = 60
        const val contextTagSize = 10
        const val contextTagLine = 12
        const val headlineSize = 22
        const val headlineLine = 28
        const val titleSize = 17
        const val titleLine = 22
        const val bodySize = 15
        const val bodyLine = 20
        const val labelSize = 13
        const val labelLine = 16
        const val captionSize = 11
        const val captionLine = 14
        const val clockSize = 15
        const val clockLine = 18
    }

    /** The big countdown numeral ("4 min"), tabular so digits do not shift. */
    val displayNow = TextStyle(
        fontSize = Raw.displayNowSize.sp,
        lineHeight = Raw.displayNowLine.sp,
        fontWeight = FontWeight(Raw.bold),
        fontFeatureSettings = "tnum",
    )

    /** The proactive Home pulse countdown, intentionally dominant at a glance. */
    val displayPulse = TextStyle(
        fontSize = Raw.displayPulseSize.sp,
        lineHeight = Raw.displayPulseLine.sp,
        fontWeight = FontWeight.ExtraBold,
        fontFeatureSettings = "tnum",
    )

    /** Compact uppercase context label used by the adaptive Home pulse. */
    val contextTag = TextStyle(
        fontSize = Raw.contextTagSize.sp,
        lineHeight = Raw.contextTagLine.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
    )

    val headline = TextStyle(
        fontSize = Raw.headlineSize.sp,
        lineHeight = Raw.headlineLine.sp,
        fontWeight = FontWeight(Raw.semibold),
    )

    val title = TextStyle(
        fontSize = Raw.titleSize.sp,
        lineHeight = Raw.titleLine.sp,
        fontWeight = FontWeight(Raw.semibold),
    )

    val body = TextStyle(
        fontSize = Raw.bodySize.sp,
        lineHeight = Raw.bodyLine.sp,
        fontWeight = FontWeight(Raw.regular),
    )

    val label = TextStyle(
        fontSize = Raw.labelSize.sp,
        lineHeight = Raw.labelLine.sp,
        fontWeight = FontWeight(Raw.medium),
    )

    val caption = TextStyle(
        fontSize = Raw.captionSize.sp,
        lineHeight = Raw.captionLine.sp,
        fontWeight = FontWeight(Raw.medium),
    )

    /** Clock times (HH:MM), tabular so times do not jitter between updates. */
    val clock = TextStyle(
        fontSize = Raw.clockSize.sp,
        lineHeight = Raw.clockLine.sp,
        fontWeight = FontWeight(Raw.medium),
        fontFeatureSettings = "tnum",
    )
}
