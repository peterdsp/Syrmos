package com.syrmos.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.Broadcast
import com.syrmos.core.common.BroadcastCopy
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens

/**
 * 3.0.2: active "Announcements from Syrmos" on Home. Shows the newest active
 * broadcast in full and how many more there are; tap expands the inbox, the
 * cross dismisses one for this device.
 */
@Composable
internal fun BroadcastBanner(
    broadcasts: List<Broadcast>,
    lang: AppLanguage,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: (String) -> Unit,
) {
    if (broadcasts.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = SyrmosColorTokens.brand.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "📣 " + BroadcastCopy.inboxTitle(lang) +
                        if (broadcasts.size > 1) " · ${broadcasts.size}" else "",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SyrmosColorTokens.brand,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
            val shown = if (expanded) broadcasts else broadcasts.take(1)
            shown.forEach { b ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = b.title.text(lang),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = b.body.text(lang),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "✕",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onDismiss(b.id) }.padding(4.dp),
                    )
                }
            }
        }
    }
}
