package com.syrmos.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import com.syrmos.core.model.alerts.AlertSeverity
import com.syrmos.core.model.transit.LineColor

@Composable
fun LineColorIndicator(
    lineColor: LineColor,
    size: Dp = 12.dp,
    disruptionSeverity: AlertSeverity? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(size + if (disruptionSeverity == null) 0.dp else 3.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(size)
                .clip(CircleShape)
                .background(lineColor.toComposeColor()),
        )
        DisruptionDot(
            severity = disruptionSeverity,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
fun DisruptionDot(
    severity: AlertSeverity?,
    modifier: Modifier = Modifier,
    size: Dp = 7.dp,
) {
    if (severity == null || severity == AlertSeverity.INFO) return
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (severity == AlertSeverity.CLOSURE) {
                    SyrmosColorTokens.disruption
                } else {
                    SyrmosColorTokens.warning
                },
            ),
    )
}

fun LineColor.toComposeColor(): Color = when (this) {
    LineColor.GREEN -> SyrmosColorTokens.metroGreen
    LineColor.RED -> SyrmosColorTokens.metroRed
    LineColor.BLUE -> SyrmosColorTokens.metroBlue
    LineColor.TRAM_ORANGE -> SyrmosColorTokens.tram
    LineColor.SUBURBAN_PURPLE -> SyrmosColorTokens.suburban
    LineColor.SCENIC_OCHRE -> SyrmosColorTokens.scenic
}
