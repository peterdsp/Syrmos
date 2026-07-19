package com.syrmos.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import com.syrmos.core.designsystem.theme.tokens.SyrmosShapeTokens
import com.syrmos.core.designsystem.theme.tokens.SyrmosTypographyTokens

/**
 * The whole-app offline state, shown beautifully rather than hidden (design doc
 * section 8), distinct from the per-answer [SourceConfidenceChip]. A calm,
 * legible pill, for example "Offline snapshot active, updated 2h ago". The
 * catalog name is shared across platforms (task T4).
 */
@Composable
fun OfflinePill(
    message: String,
    modifier: Modifier = Modifier,
) {
    val tint = SyrmosColorTokens.offline
    Row(
        modifier = modifier
            .clip(SyrmosShapeTokens.shapePill)
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Text(
            text = message,
            style = SyrmosTypographyTokens.label,
            color = SyrmosColorTokens.onSurfaceMuted,
        )
    }
}
