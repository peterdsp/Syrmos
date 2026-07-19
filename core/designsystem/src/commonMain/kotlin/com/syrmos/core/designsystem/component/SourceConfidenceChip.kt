package com.syrmos.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import com.syrmos.core.designsystem.theme.tokens.SyrmosShapeTokens
import com.syrmos.core.designsystem.theme.tokens.SyrmosTypographyTokens

/**
 * Syrmos knows what it knows and says so (design doc section 7, a headline 2.0.0
 * pillar). A calm, never-alarming chip that states how certain an answer is. It
 * appears on departure cards, station detail, route results, and Ariadne answers.
 * The catalog name is shared across platforms (task T4).
 */
enum class SourceConfidence {
    /** A real-time position or arrival (for example a live suburban position). */
    LIVE,

    /** A scheduled timetable departure, not a live one. */
    SCHEDULED,

    /** Served from the bundled offline snapshot. */
    OFFLINE,

    /** Estimated from a frequency band rather than an exact time. */
    ESTIMATED,

    /** The operator must be checked for live status. */
    OPERATOR_LINK,

    /** No live disruption data is available. */
    UNKNOWN,
}

private fun SourceConfidence.color(): Color = when (this) {
    SourceConfidence.LIVE -> SyrmosColorTokens.live
    SourceConfidence.SCHEDULED -> SyrmosColorTokens.scheduled
    SourceConfidence.OFFLINE -> SyrmosColorTokens.offline
    SourceConfidence.ESTIMATED -> SyrmosColorTokens.estimated
    SourceConfidence.OPERATOR_LINK -> SyrmosColorTokens.brand
    SourceConfidence.UNKNOWN -> SyrmosColorTokens.offline
}

/** English default label; callers pass a localised [label] for EL / SQ. */
private fun SourceConfidence.defaultLabel(): String = when (this) {
    SourceConfidence.LIVE -> "Live"
    SourceConfidence.SCHEDULED -> "Scheduled"
    SourceConfidence.OFFLINE -> "Offline snapshot"
    SourceConfidence.ESTIMATED -> "Estimated"
    SourceConfidence.OPERATOR_LINK -> "Check operator"
    SourceConfidence.UNKNOWN -> "No live data"
}

@Composable
fun SourceConfidenceChip(
    confidence: SourceConfidence,
    modifier: Modifier = Modifier,
    label: String = confidence.defaultLabel(),
) {
    val tint = confidence.color()
    Row(
        modifier = modifier
            .clip(SyrmosShapeTokens.shapePill)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Text(
            text = label,
            style = SyrmosTypographyTokens.label,
            color = tint,
        )
    }
}
